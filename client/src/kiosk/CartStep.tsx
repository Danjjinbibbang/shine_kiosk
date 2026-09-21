import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { MenuOption, StaffMember } from '../shared/types'
import { won } from '../shared/types'
import { lineKey, lineUnitPrice, type CartLine } from './KioskApp'

interface Props {
  cart: CartLine[]
  total: number
  staffMember?: StaffMember
  onChange: (cart: CartLine[]) => void
  onStaffMember: (m: StaffMember | undefined) => void
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
export function CartStep({ cart, total, staffMember, onChange, onStaffMember, onAddMore, onNext }: Props) {
  const [options, setOptions] = useState<MenuOption[]>([])
  const [members, setMembers] = useState<StaffMember[]>([])
  const [pickingStaff, setPickingStaff] = useState(false)

  useEffect(() => {
    api.menuOptions().then(setOptions).catch(() => setOptions([]))
    api.staffMembers().then(setMembers).catch(() => setMembers([]))
  }, [])

  const staffFreeTotal = cart.reduce((s, l) => s + lineUnitPrice(l) * l.staffFreeQty, 0)

  const setQty = (index: number, qty: number) => {
    onChange(qty <= 0 ? cart.filter((_, i) => i !== index)
      : cart.map((l, i) => (i === index ? { ...l, qty, staffFreeQty: Math.min(l.staffFreeQty, qty) } : l)))
  }

  const setStaffFree = (index: number, n: number) => {
    onChange(cart.map((l, i) => (i === index ? { ...l, staffFreeQty: Math.max(0, Math.min(l.qty, n)) } : l)))
  }

  const toggleOption = (index: number, opt: MenuOption) => {
    const line = cart[index]
    const has = line.options.some((o) => o.id === opt.id)
    const nextOptions = has ? line.options.filter((o) => o.id !== opt.id) : [...line.options, opt]
    const changed: CartLine = { ...line, options: nextOptions }
    const mergeIdx = cart.findIndex((l, i) => i !== index && keyOf(l) === keyOf(changed))
    if (mergeIdx >= 0) {
      onChange(cart
        .map((l, i) => (i === mergeIdx ? { ...l, qty: l.qty + line.qty, staffFreeQty: l.staffFreeQty + line.staffFreeQty } : l))
        .filter((_, i) => i !== index))
    } else {
      onChange(cart.map((l, i) => (i === index ? changed : l)))
    }
  }

  if (pickingStaff) {
    return (
      <div className="stack">
        <div className="muted center">사역자 이름을 골라 주세요</div>
        {members.length === 0 && <div className="empty">등록된 사역자가 없습니다. 스태프 화면 &gt; 설정에서 등록해 주세요.</div>}
        <div className="name-grid">
          {members.map((m) => (
            <button key={m.id} className="btn" onClick={() => { onStaffMember(m); setPickingStaff(false) }}>{m.name}</button>
          ))}
        </div>
        <button className="btn big" onClick={() => setPickingStaff(false)}>‹ 돌아가기</button>
      </div>
    )
  }

  return (
    <div className="stack">
      {cart.length === 0 && <div className="empty">담긴 메뉴가 없습니다.</div>}

      {members.length > 0 && cart.length > 0 && (
        staffMember ? (
          <div className="staff-bar">
            <span className="grow"><b>{staffMember.name}</b> 사역자 주문 · 무료 {won(staffFreeTotal)}</span>
            <button className="btn ghost" onClick={() => onStaffMember(undefined)}>해제</button>
          </div>
        ) : (
          <button className="btn" onClick={() => setPickingStaff(true)}>🙋 사역자 주문이에요</button>
        )
      )}

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
              <div className="sum">{won(lineUnitPrice(l) * (l.qty - l.staffFreeQty))}</div>
            </div>
            {staffMember && (
              <div className="staff-line">
                <span className="muted">사역자 잔</span>
                <button className="btn" onClick={() => setStaffFree(index, l.staffFreeQty - 1)} aria-label="사역자 잔 빼기">−</button>
                <b>{l.staffFreeQty} / {l.qty}</b>
                <button className="btn" onClick={() => setStaffFree(index, l.staffFreeQty + 1)} aria-label="사역자 잔 더하기">+</button>
                {l.staffFreeQty === l.qty && <span className="muted">전부 무료</span>}
              </div>
            )}
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
        <span className="label">{staffMember ? '내실 금액' : '총 금액'}</span>
        <span className="amount">{won(total)}</span>
      </div>

      <div className="kiosk-foot">
        <button className="btn big" onClick={onAddMore}>더 담기</button>
        <button className="btn big primary" disabled={cart.length === 0} onClick={onNext}>
          {staffMember && total === 0 ? '무료로 주문하기 ›' : '주문하기 ›'}
        </button>
      </div>
    </div>
  )
}
