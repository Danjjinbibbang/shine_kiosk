import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { MenuOption } from '../shared/types'
import { blockedByGroup, won } from '../shared/types'
import { lineKeyOf, lineUnitPrice, type CartLine } from './KioskApp'

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

/**
 * 수량 조절과 잔 단위 커스텀(샷 추가 / 연하게 / 사역자).
 * 칩을 누르면 그 줄에서 **한 잔**만 떨어져 나와 바뀐다 (2잔 중 1잔만 샷 추가 같은 경우).
 * 같은 조합(메뉴+옵션+사역자)의 줄이 이미 있으면 거기에 합쳐진다.
 */
export function CartStep({ cart, total, onChange, onAddMore, onNext }: Props) {
  const [options, setOptions] = useState<MenuOption[]>([])

  useEffect(() => {
    api.menuOptions().then(setOptions).catch(() => setOptions([]))
  }, [])

  const staffFreeTotal = cart.reduce((s, l) => s + (l.staffFree ? lineUnitPrice(l) * l.qty : 0), 0)

  const setQty = (index: number, qty: number) => {
    onChange(qty <= 0 ? cart.filter((_, i) => i !== index) : cart.map((l, i) => (i === index ? { ...l, qty } : l)))
  }

  /** index 줄에서 한 잔을 떼어 transform 을 적용하고, 같은 조합 줄에 합치거나 새 줄로 넣는다. */
  const changeOneCup = (index: number, transform: (cup: CartLine) => CartLine) => {
    const line = cart[index]
    const changed = transform({ ...line, qty: 1 })
    const rest = line.qty > 1 ? { ...line, qty: line.qty - 1 } : null
    const next: CartLine[] = []
    let merged = false
    cart.forEach((l, i) => {
      if (i === index) {
        if (rest) next.push(rest)
        return
      }
      if (!merged && lineKeyOf(l) === lineKeyOf(changed)) {
        next.push({ ...l, qty: l.qty + 1 })
        merged = true
      } else {
        next.push(l)
      }
    })
    if (!merged) {
      // 원래 줄 바로 뒤에 넣어서 눈에서 안 사라지게
      const at = rest ? next.indexOf(rest) + 1 : Math.min(index, next.length)
      next.splice(at, 0, changed)
    }
    onChange(next)
  }

  const toggleOption = (index: number, opt: MenuOption) =>
    changeOneCup(index, (cup) => ({
      ...cup,
      options: cup.options.some((o) => o.id === opt.id) ? cup.options.filter((o) => o.id !== opt.id) : [...cup.options, opt],
    }))

  const toggleStaffFree = (index: number) =>
    changeOneCup(index, (cup) => ({ ...cup, staffFree: !cup.staffFree }))

  return (
    <div className="stack">
      {cart.length === 0 && <div className="empty">담긴 메뉴가 없습니다.</div>}

      {cart.map((l, index) => {
        const applicable = options.filter((o) => o.category === l.category)
        return (
          <div key={lineKeyOf(l)} className={'cart-line-wrap' + (l.staffFree ? ' staff-free' : '')}>
            <div className="cart-line">
              <div className="name">
                {lineName(l)}
                {(l.options.length > 0 || l.staffFree) && (
                  <div className="line-options">
                    {[...l.options.map((o) => o.name), ...(l.staffFree ? ['사역자 무료'] : [])].join(' · ')}
                  </div>
                )}
              </div>
              <div className="qty">
                <button className="btn" onClick={() => setQty(index, l.qty - 1)}>−</button>
                <span className="n">{l.qty}</span>
                <button className="btn" onClick={() => setQty(index, l.qty + 1)}>+</button>
              </div>
              <div className="sum">{l.staffFree ? '0원' : won(lineUnitPrice(l) * l.qty)}</div>
            </div>
            <div className="option-chips">
              {applicable.map((o) => {
                const on = l.options.some((x) => x.id === o.id)
                const blocked = !on && blockedByGroup(o, l.options)
                return (
                  <button key={o.id} className={'btn chip' + (on ? ' selected' : '')} disabled={blocked}
                    onClick={() => toggleOption(index, o)}>
                    {on ? '☑' : '☐'} {o.name}{o.price > 0 && <small> +{won(o.price)}</small>}
                  </button>
                )
              })}
              <button className={'btn chip staff' + (l.staffFree ? ' selected' : '')} onClick={() => toggleStaffFree(index)}>
                {l.staffFree ? '☑' : '☐'} 사역자
              </button>
              {l.qty > 1 && <span className="muted chip-hint">한 잔씩 바뀝니다</span>}
            </div>
          </div>
        )
      })}

      {staffFreeTotal > 0 && (
        <div className="staff-bar"><span className="grow">사역자 무료 <b>{won(staffFreeTotal)}</b></span></div>
      )}

      <div className="total-box">
        <span className="label">{staffFreeTotal > 0 ? '내실 금액' : '총 금액'}</span>
        <span className="amount">{won(total)}</span>
      </div>

      <div className="kiosk-foot">
        <button className="btn big" onClick={onAddMore}>더 담기</button>
        <button className="btn big primary" disabled={cart.length === 0} onClick={onNext}>
          {cart.length > 0 && total === 0 ? '무료로 주문하기 ›' : '주문하기 ›'}
        </button>
      </div>
    </div>
  )
}
