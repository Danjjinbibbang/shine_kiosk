import { useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { Order, OrderStatus } from '../shared/types'
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
  const [editing, setEditing] = useState<Order | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    let alive = true
    api.orders(status).then((o) => { if (alive) setOrders(o) }).catch(() => {})
    return () => { alive = false }
  }, [status, tick])

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
          onDone={() => run(o.id, () => api.doneOrder(o.id), `#${o.orderNo} ${o.customerName}님 완료`)}
          onReopen={() => run(o.id, () => api.reopenOrder(o.id), `#${o.orderNo} 다시 만들 것으로 이동`)}
          onCancel={() => {
            if (window.confirm(`#${o.orderNo} ${o.customerName}님 주문을 취소할까요?${o.couponAmount ? '\n쿠폰 차감액은 되돌려집니다.' : ''}`)) {
              void run(o.id, () => api.cancelOrder(o.id), `#${o.orderNo} 취소됨`)
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
  onDone: () => void
  onReopen: () => void
  onCancel: () => void
  onEdit: () => void
}

/** "현금 1,000원" / "5,000원 = 무료 1잔(아메리카노 ICE) + 쿠폰 3,000원 + 현금 1,000원" */
function payLine(o: Order): string {
  const parts: string[] = []
  if (o.freeAmount) parts.push(`무료 1잔(${o.freeItemName ?? won(o.freeAmount)})`)
  if (o.couponAmount) parts.push(`쿠폰 ${won(o.couponAmount)}`)
  if (o.cashAmount) parts.push(`현금 ${won(o.cashAmount)}`)
  if (o.transferAmount) parts.push(`이체 ${won(o.transferAmount)}`)
  if (parts.length === 0) return `${PAY_LABEL[o.payMethod]} ${won(o.totalAmount)}`
  if (parts.length === 1) return parts[0]
  return `${won(o.totalAmount)} = ${parts.join(' + ')}`
}

function OrderCard({ order: o, busy, onDone, onReopen, onCancel, onEdit }: CardProps) {
  const delivery = o.receiveType === 'DELIVERY'
  const time = o.createdAt.slice(11, 16)
  return (
    <div className={'order-card' + (delivery ? ' delivery' : '')}>
      <div className="top">
        <span className="no">#{o.orderNo}</span>
        <span className="who">{o.customerName}</span>
        <span className={'where' + (delivery ? '' : ' store')}>{delivery ? `🚶 ${o.placeName}` : '☕ 카페'}</span>
        <span className="muted" style={{ fontSize: 14 }}>{time}</span>
      </div>
      <div className="lines">
        {o.lines.map((l) => (
          <div key={l.id}>
            {l.menuName}{l.variantLabel && <span className="muted"> {l.variantLabel}</span>}
            <span className="q">×{l.quantity}</span>
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
            <button className="btn danger" disabled={busy} onClick={onCancel}>취소</button>
          </>
        )}
      </div>
    </div>
  )
}
