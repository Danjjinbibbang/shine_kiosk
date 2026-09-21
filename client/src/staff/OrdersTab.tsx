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
      const ids = [...new Set(list.filter((o) => o.couponId != null).map((o) => o.couponId!))]
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
    const coupon = o.couponId != null ? coupons[o.couponId] : undefined
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
          onSettle={(couponId) => run(o.id, () => api.settleOrder(o.id, couponId), couponId != null ? '쿠폰 잔액에 넣었습니다' : '정산 표시했습니다')}
          findCoupons={(customerName) => api.couponsByName(customerName)}
          canSms={o.couponId != null && !!coupons[o.couponId]?.phone}
          onSms={() => { if (!sendBalanceSms(o)) onToast('쿠폰에 전화번호가 없어 문자를 못 보냅니다') }}
          onDone={() => {
            // 문자 앱은 탭 직후(사용자 동작 안)에 열어야 하므로 서버 호출보다 먼저
            const sent = o.couponId != null ? sendBalanceSms(o) : false
            void run(o.id, () => api.doneOrder(o.id),
              `${orderLabel(o)} ${o.customerName}님 완료` + (o.couponId != null && !sent ? ' (번호 없어 문자 생략)' : ''))
          }}
          onReopen={() => run(o.id, () => api.reopenOrder(o.id), `${orderLabel(o)} 다시 만들 것으로 이동`)}
          onCancel={(refundToCouponId) => run(o.id, () => api.cancelOrder(o.id, refundToCouponId),
            `${orderLabel(o)} 취소됨` + (refundToCouponId != null ? ' · 쿠폰 잔액에 넣음' : ''))}
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
  onSettle: (couponId: number | null) => void
  findCoupons: (customerName: string) => Promise<Coupon[]>
  canSms: boolean
  onSms: () => void
  onDone: () => void
  onReopen: () => void
  onCancel: (refundToCouponId: number | null) => void
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

function CouponCandidates({ candidates, customerName, busy, onPick }: {
  candidates: Coupon[] | 'none' | null; customerName: string; busy: boolean; onPick: (id: number) => void
}) {
  if (candidates === 'none') {
    return <span className="settle-note">{customerName}님 쿠폰이 없어요. 스태프 &gt; 쿠폰에서 먼저 등록하거나 현금으로 주세요.</span>
  }
  if (!Array.isArray(candidates)) return null
  return (
    <div className="settle-pick">
      <span className="settle-note">같은 이름이 {candidates.length}명 — 누구 쿠폰인가요?</span>
      {candidates.map((c) => (
        <button key={c.id} className="btn" disabled={busy} onClick={() => onPick(c.id)}>
          {c.name}{c.phoneLast4 && ` (${c.phoneLast4})`} · 잔액 {won(c.balance)}
        </button>
      ))}
    </div>
  )
}

/**
 * 수정 뒤 실제 받은 돈과 현재 금액의 차이.
 * 돌려줄 돈은 원래 수단이 뭐였든 현금(또는 쿠폰 잔액)으로 → 합쳐서 하나. 더 받을 돈은 수단별로.
 */
function settlement(o: Order): { refund: number; extra: string[] } {
  const cash = o.cashAmount - o.settledCash
  const transfer = o.transferAmount - o.settledTransfer
  const extra: string[] = []
  if (cash > 0) extra.push(`현금 ${won(cash)} 더 받기`)
  if (transfer > 0) extra.push(`계좌이체 ${won(transfer)} 더 받기`)
  return { refund: (cash < 0 ? -cash : 0) + (transfer < 0 ? -transfer : 0), extra }
}

function OrderCard({ order: o, busy, onSettle, findCoupons, canSms, onSms, onDone, onReopen, onCancel, onEdit }: CardProps) {
  const delivery = o.receiveType === 'DELIVERY'
  const time = o.createdAt.slice(11, 16)
  const stale = o.orderDate !== localToday()
  const { refund, extra } = o.status === 'CANCELED' ? { refund: 0, extra: [] } : settlement(o)
  // 돌려줄 돈을 쿠폰에 넣을 때: 쿠폰 주문이면 그 쿠폰, 아니면 주문자 이름으로 찾은 후보.
  // 하나면 바로, 여럿이면 고르게, 없으면 안내. settle(차액 정산)과 cancel(취소 환불) 둘 다 쓴다.
  const [candidates, setCandidates] = useState<Coupon[] | 'none' | null>(null)
  const [pickFor, setPickFor] = useState<'settle' | 'cancel'>('settle')
  const [cancelling, setCancelling] = useState(false)
  const paid = o.settledCash + o.settledTransfer

  const pickCoupon = async (purpose: 'settle' | 'cancel') => {
    const done = (id: number) => (purpose === 'settle' ? onSettle(id) : onCancel(id))
    setPickFor(purpose)
    if (o.couponId != null) {
      done(o.couponId)
      return
    }
    const found = await findCoupons(o.customerName).catch(() => [])
    if (found.length === 0) setCandidates('none')
    else if (found.length === 1) done(found[0].id)
    else setCandidates(found)
  }
  const refundToCoupon = () => pickCoupon('settle')
  return (
    <div className={'order-card' + (delivery ? ' delivery' : '') + (stale ? ' stale' : '')}>
      <div className="top">
        <span className="no">{orderLabel(o)}</span>
        <span className="who">{o.customerName}{o.staffFreeAmount > 0 && <span className="badge-staff">사역자</span>}</span>
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
      {o.editedAt && (
        <div className="edited">
          <span className="badge-edited">수정됨 {o.editedAt.slice(11, 16)}</span>
          {o.editNote && <span className="muted"> 이전: {o.editNote}</span>}
        </div>
      )}
      {refund > 0 && (
        <div className="settle">
          <span className="grow">💸 {won(refund)} 돌려주기</span>
          <button className="btn" disabled={busy} onClick={() => onSettle(null)}>현금으로 줬어요</button>
          <button className="btn" disabled={busy} onClick={() => void refundToCoupon()}>쿠폰에 넣기</button>
          {pickFor === 'settle' && <CouponCandidates candidates={candidates} customerName={o.customerName} busy={busy} onPick={onSettle} />}
        </div>
      )}
      {cancelling && (
        <div className="settle cancel-panel">
          <span className="grow">
            {orderLabel(o)} {o.customerName}님 주문을 취소합니다.
            {paid > 0 && ` 받은 ${won(paid)}은?`}
            {o.couponId != null && <small className="muted"> (쿠폰으로 낸 몫은 자동 복원)</small>}
          </span>
          {paid > 0 ? (
            <>
              <button className="btn danger" disabled={busy} onClick={() => onCancel(null)}>현금으로 돌려주고 취소</button>
              <button className="btn danger" disabled={busy} onClick={() => void pickCoupon('cancel')}>쿠폰에 넣고 취소</button>
            </>
          ) : (
            <button className="btn danger" disabled={busy} onClick={() => onCancel(null)}>취소 확정</button>
          )}
          <button className="btn ghost" onClick={() => { setCancelling(false); setCandidates(null) }}>취소 안 함</button>
          {pickFor === 'cancel' && <CouponCandidates candidates={candidates} customerName={o.customerName} busy={busy} onPick={onCancel} />}
        </div>
      )}
      {refund === 0 && extra.length > 0 && (
        <div className="settle">
          <span className="grow">💰 {extra.join(' · ')}</span>
          <button className="btn" disabled={busy} onClick={() => onSettle(null)}>받았어요</button>
        </div>
      )}
      <div className="actions">
        {o.status === 'PENDING' ? (
          <>
            <button className="btn ok" disabled={busy || refund > 0 || extra.length > 0}
              title={refund > 0 || extra.length > 0 ? '먼저 정산 버튼을 눌러 주세요' : ''} onClick={onDone}>
              {refund > 0 || extra.length > 0 ? '정산 먼저' : '완료 ✓'}
            </button>
            <button className="btn" disabled={busy} onClick={onEdit}>수정</button>
            <button className="btn danger" disabled={busy || cancelling} onClick={() => setCancelling(true)}>취소</button>
          </>
        ) : (
          <>
            <button className="btn" disabled={busy} onClick={onReopen}>↩ 되돌리기</button>
            {o.couponId != null && (
              <button className="btn" disabled={busy || !canSms} onClick={onSms} title={canSms ? '' : '쿠폰에 전화번호가 없음'}>📩 잔액 문자</button>
            )}
            <button className="btn danger" disabled={busy || cancelling} onClick={() => setCancelling(true)}>취소</button>
          </>
        )}
      </div>
    </div>
  )
}
