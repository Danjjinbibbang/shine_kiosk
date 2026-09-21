import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { MenuOption } from '../shared/types'
import { won } from '../shared/types'
import { lineKey, lineUnitPrice, type CartLine } from './KioskApp'

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

const keyOf = (l: CartLine) => lineKey(l.variantId, l.options.map((o) => o.id))

/**
 * 수량 조절과 옵션(샷 추가/연하게). 옵션을 바꾸면 다른 줄이 되고,
 * 이미 같은 조합의 줄이 있으면 거기에 합쳐진다.
 */
export function CartStep({ cart, total, onChange, onAddMore, onNext }: Props) {
  const [options, setOptions] = useState<MenuOption[]>([])

  useEffect(() => {
    api.menuOptions().then(setOptions).catch(() => setOptions([]))
  }, [])

  const setQty = (index: number, qty: number) => {
    onChange(qty <= 0 ? cart.filter((_, i) => i !== index) : cart.map((l, i) => (i === index ? { ...l, qty } : l)))
  }

  const toggleOption = (index: number, opt: MenuOption) => {
    const line = cart[index]
    const has = line.options.some((o) => o.id === opt.id)
    const nextOptions = has ? line.options.filter((o) => o.id !== opt.id) : [...line.options, opt]
    const changed: CartLine = { ...line, options: nextOptions }
    const mergeIdx = cart.findIndex((l, i) => i !== index && keyOf(l) === keyOf(changed))
    if (mergeIdx >= 0) {
      onChange(cart
        .map((l, i) => (i === mergeIdx ? { ...l, qty: l.qty + line.qty } : l))
        .filter((_, i) => i !== index))
    } else {
      onChange(cart.map((l, i) => (i === index ? changed : l)))
    }
  }

  return (
    <div className="stack">
      {cart.length === 0 && <div className="empty">담긴 메뉴가 없습니다.</div>}
      {cart.map((l, index) => {
        const applicable = options.filter((o) => o.category === l.category)
        return (
          <div key={keyOf(l)} className="cart-line-wrap">
            <div className="cart-line">
              <div className="name">
                {lineName(l)}
                {l.options.length > 0 && <div className="line-options">{l.options.map((o) => o.name).join(' · ')}</div>}
              </div>
              <div className="qty">
                <button className="btn" onClick={() => setQty(index, l.qty - 1)}>−</button>
                <span className="n">{l.qty}</span>
                <button className="btn" onClick={() => setQty(index, l.qty + 1)}>+</button>
              </div>
              <div className="sum">{won(lineUnitPrice(l) * l.qty)}</div>
            </div>
            {applicable.length > 0 && (
              <div className="option-chips">
                {applicable.map((o) => {
                  const on = l.options.some((x) => x.id === o.id)
                  return (
                    <button key={o.id} className={'btn chip' + (on ? ' selected' : '')} onClick={() => toggleOption(index, o)}>
                      {on ? '☑' : '☐'} {o.name}{o.price > 0 && <small> +{won(o.price)}</small>}
                    </button>
                  )
                })}
              </div>
            )}
          </div>
        )
      })}

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
