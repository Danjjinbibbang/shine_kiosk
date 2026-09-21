import { useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import { couponBalanceMessage, openSms } from '../shared/sms'
import type { Coupon, Order, OrderStatus } from '../shared/types'
import { PAY_LABEL, won } from '../shared/types'
import { EditOrderModal } from './EditOrderModal'

interface Props {
  status: OrderStatus
  tick: number
  onChanged: () => void
  onToast: (msg: string) => void
}

export function OrdersTab({ status, tick, onChanged, onToast }: Props) {
  const [orders, setOrders] = useState<Order[] | null>(null)
  const [coupons, setCoupons] = useState<Record<number, Coupon>>({})
  const [editing, setEditing] = useState<Order | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    let alive = true
    api.orders(status).then(async (list) => {
      if (!alive) return
      setOrders(list)
      // 쿠폰 주문은 잔액 문자를 위해 쿠폰(번호·잔액)을 미리 받아 둔다. 완료 탭은 시점상 값이 바뀌었을 수 있어 매번 새로 받는다.
      const ids = [...new Set(list.filter((o) => o.couponId !== null).map((o) => o.couponId!))]
      const found: Record<number, Coupon> = {}
      await Promise.all(ids.map(async (id) => {
        try { found[id] = await api.getCoupon(id) } catch { /* 삭제된 쿠폰 등 */ }
      }))
      if (alive) setCoupons(found)
    }).catch(() => {})
    return () => { alive = false }
  }, [status, tick])

  /** 문자 앱을 열 수 있으면 열고 true. 번호 없으면 false. */
  const sendBalanceSms = (o: Order): boolean => {
    const coupon = o.couponId !== null ? coupons[o.couponId] : undefined
    if (!coupon?.phone) return false
    openSms(coupon.phone, couponBalanceMessage(o, coupon))
    return true
  }

  const run = async (id: number, action: () => Promise<void>, doneMsg: string) => {
    setBusyId(id)
    try {
      await action()
      onToast(doneMsg)
      onChanged()
    } catch (e) {
      onToast(e instanceof ApiError ? e.message : '처리하지 못했습니다.')
    } finally {
      setBusyId(null)
    }
  }

  if (!orders) return <div className="empty">불러오는 중…</div>
  if (orders.length === 0) {
    return <div className="empty">{status === 'PENDING' ? '만들 주문이 없습니다 🎉' : '오늘 완료된 주문이 없습니다'}</div>
  }

  return (
    <>
      {orders.map((o) => (
        <OrderCard key={o.id} order={o} busy={busyId === o.id}
          canSms={o.couponId !== null && !!coupons[o.couponId]?.phone}
          onSms={() => { if (!sendBalanceSms(o)) onToast('쿠폰에 전화번호가 없어 문자를 못 보냅니다') }}
          onDone={() => {
            // 문자 앱은 탭 직후(사용자 동작 안)에 열어야 하므로 서버 호출보다 먼저
            const sent = o.couponId !== null ? sendBalanceSms(o) : false
            void run(o.id, () => api.doneOrder(o.id),
              `${orderLabel(o)} ${o.customerName}님 완료` + (o.couponId !== null && !sent ? ' (번호 없어 문자 생략)' : ''))
          }}
          onReopen={() => run(o.id, () => api.reopenOrder(o.id), `${orderLabel(o)} 다시 만들 것으로 이동`)}
          onCancel={() => {
            if (window.confirm(`${orderLabel(o)} ${o.customerName}님 주문을 취소할까요?${o.couponId ? '\n쿠폰 차감액(무료 1잔 포함)은 되돌려집니다.' : ''}`)) {
              void run(o.id, () => api.cancelOrder(o.id), `${orderLabel(o)} 취소됨`)
            }
          }}
          onEdit={() => setEditing(o)} />
      ))}
      {editing && (
        <EditOrderModal order={editing} onClose={() => setEditing(null)}
          onSaved={() => { setEditing(null); onToast('수정했습니다'); onChanged() }} />
      )}
    </>
  )
}

interface CardProps {
  order: Order
  busy: boolean
  canSms: boolean
  onSms: () => void
  onDone: () => void
  onReopen: () => void
  onCancel: () => void
  onEdit: () => void
}

/** "현금 1,000원" / "5,000원 = 무료 1잔(아메리카노 ICE) + 쿠폰 3,000원 + 현금 1,000원" */
function payLine(o: Order): string {
  const parts: string[] = []
  if (o.staffFreeAmount) parts.push(`사역자 무료 ${won(o.staffFreeAmount)}`)
  if (o.freeAmount) parts.push(`무료 1잔(${o.freeItemName ?? won(o.freeAmount)})`)
  if (o.couponAmount) parts.push(`쿠폰 ${won(o.couponAmount)}`)
  if (o.cashAmount) parts.push(`현금 ${won(o.cashAmount)}`)
  if (o.transferAmount) parts.push(`이체 ${won(o.transferAmount)}`)
  if (parts.length === 0) return `${PAY_LABEL[o.payMethod]} ${won(o.totalAmount)}`
  if (parts.length === 1) return parts[0]
  return `${won(o.totalAmount + o.staffFreeAmount)} = ${parts.join(' + ')}`
}

/** 서버의 order_date(로컬 YYYY-MM-DD)와 비교할 오늘 날짜. toISOString 은 UTC 라 새벽에 하루 어긋난다. */
function localToday(): string {
  const d = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** 주문 번호는 날짜마다 1부터 다시 시작하므로, 오늘 것이 아니면 날짜를 앞에 붙인다. */
function orderLabel(o: Order): string {
  if (o.orderDate === localToday()) return `#${o.orderNo}`
  const [, m, d] = o.orderDate.split('-')
  return `${Number(m)}/${Number(d)} #${o.orderNo}`
}

function OrderCard({ order: o, busy, canSms, onSms, onDone, onReopen, onCancel, onEdit }: CardProps) {
  const delivery = o.receiveType === 'DELIVERY'
  const time = o.createdAt.slice(11, 16)
  const stale = o.orderDate !== localToday()
  return (
    <div className={'order-card' + (delivery ? ' delivery' : '') + (stale ? ' stale' : '')}>
      <div className="top">
        <span className="no">{orderLabel(o)}</span>
        <span className="who">{o.customerName}{o.staffMemberName && <span className="badge-staff">사역자</span>}</span>
        <span className={'where' + (delivery ? '' : ' store')}>{delivery ? `🚶 ${o.placeName}` : '☕ 카페'}</span>
        <span className="muted" style={{ fontSize: 14 }}>{time}</span>
      </div>
      <div className="lines">
        {o.lines.map((l) => (
          <div key={l.id}>
            {l.menuName}{l.variantLabel && <span className="muted"> {l.variantLabel}</span>}
            <span className="q">×{l.quantity}</span>
            {l.staffFreeQty > 0 && <span className="staff-free"> (사역자 {l.staffFreeQty})</span>}
            {l.options.length > 0 && <span className="opt"> · {l.options.map((o) => o.name).join(', ')}</span>}
          </div>
        ))}
      </div>
      <div className="pay">{payLine(o)}</div>
      {o.memo && <div className="memo">📝 {o.memo}</div>}
      <div className="actions">
        {o.status === 'PENDING' ? (
          <>
            <button className="btn ok" disabled={busy} onClick={onDone}>완료 ✓</button>
            <button className="btn" disabled={busy} onClick={onEdit}>수정</button>
            <button className="btn danger" disabled={busy} onClick={onCancel}>취소</button>
          </>
        ) : (
          <>
            <button className="btn" disabled={busy} onClick={onReopen}>↩ 되돌리기</button>
            {o.couponId !== null && (
              <button className="btn" disabled={busy || !canSms} onClick={onSms} title={canSms ? '' : '쿠폰에 전화번호가 없음'}>📩 잔액 문자</button>
            )}
            <button className="btn danger" disabled={busy} onClick={onCancel}>취소</button>
          </>
        )}
      </div>
    </div>
  )
}
