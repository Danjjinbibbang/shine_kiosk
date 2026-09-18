import { won } from '../shared/types'
import type { CartLine } from './KioskApp'

interface Props {
  cart: CartLine[]
  total: number
  onChange: (cart: CartLine[]) => void
  onAddMore: () => void
  onNext: () => void
}

export function lineName(l: { itemName: string; label: string | null }) {
  return l.label ? `${l.itemName} (${l.label})` : l.itemName
}

export function CartStep({ cart, total, onChange, onAddMore, onNext }: Props) {
  const setQty = (variantId: number, qty: number) => {
    onChange(qty <= 0
      ? cart.filter((l) => l.variantId !== variantId)
      : cart.map((l) => (l.variantId === variantId ? { ...l, qty } : l)))
  }

  return (
    <div className="stack">
      {cart.length === 0 && <div className="empty">담긴 메뉴가 없습니다.</div>}
      {cart.map((l) => (
        <div key={l.variantId} className="cart-line">
          <div className="name">{lineName(l)}</div>
          <div className="qty">
            <button className="btn" onClick={() => setQty(l.variantId, l.qty - 1)}>−</button>
            <span className="n">{l.qty}</span>
            <button className="btn" onClick={() => setQty(l.variantId, l.qty + 1)}>+</button>
          </div>
          <div className="sum">{won(l.price * l.qty)}</div>
        </div>
      ))}

      <div className="total-box">
        <span className="label">총 금액</span>
        <span className="amount">{won(total)}</span>
      </div>

      <div className="kiosk-foot">
        <button className="btn big" onClick={onAddMore}>더 담기</button>
        <button className="btn big primary" disabled={cart.length === 0} onClick={onNext}>주문하기 ›</button>
      </div>
    </div>
  )
}
