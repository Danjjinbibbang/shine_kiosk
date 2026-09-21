import { useEffect, useMemo, useState } from 'react'
import { api, ApiError } from '../shared/api'
import {
  addPlainCup, blockedByGroup, cartStaffFree, cartTotal, lineKeyOf, lineName, lineUnitPrice, setLineQty,
  toLineRequests, toggleOptionOnOneCup, toggleStaffFreeOnOneCup, type CartLine,
} from '../shared/cart'
import type { FloorGroup, MenuItem, MenuOption, Order, ReceiveType } from '../shared/types'
import { won } from '../shared/types'

interface Props {
  order: Order
  onClose: () => void
  onSaved: () => void
}

/**
 * 항목/이름/장소/메모를 고친다. 결제 수단은 서버가 유지하고 금액만 다시 계산한다.
 * 칩(옵션/사역자)은 키오스크와 똑같이 한 번에 한 잔만 바뀐다.
 */
export function EditOrderModal({ order, onClose, onSaved }: Props) {
  const [menu, setMenu] = useState<MenuItem[]>([])
  const [allOptions, setAllOptions] = useState<MenuOption[]>([])
  const [floors, setFloors] = useState<FloorGroup[]>([])
  const [name, setName] = useState(order.customerName)
  const [receiveType, setReceiveType] = useState<ReceiveType>(order.receiveType)
  const [placeId, setPlaceId] = useState<number | null>(order.placeId)
  const [memo, setMemo] = useState(order.memo ?? '')
  const [lines, setLines] = useState<CartLine[]>([])
  const [ready, setReady] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // 주문 줄을 장바구니 줄로 바꾸려면 메뉴표(카테고리/기본가)와 옵션표(그룹)가 필요하다.
  useEffect(() => {
    Promise.all([api.menu(), api.menuOptions(), api.places()]).then(([m, opts, f]) => {
      setMenu(m)
      setAllOptions(opts)
      setFloors(f)
      const variantOf = new Map<number, { item: MenuItem; label: string | null; price: number }>()
      m.forEach((item) => item.variants.forEach((v) => variantOf.set(v.id, { item, label: v.label, price: v.price })))
      const optionOf = new Map(opts.map((o) => [o.id, o]))
      const converted: CartLine[] = []
      for (const l of order.lines) {
        if (l.variantId == null) continue
        const v = variantOf.get(l.variantId)
        const options = l.options.map((o) => (o.optionId != null && optionOf.get(o.optionId))
          || { id: o.optionId ?? -1, name: o.name, price: o.price, category: '', group: null })
        const base = {
          variantId: l.variantId,
          itemName: v?.item.name ?? l.menuName,
          category: v?.item.category ?? '',
          label: v?.label ?? l.variantLabel,
          price: l.unitPrice - l.options.reduce((s, o) => s + o.price, 0),
          options,
        }
        // 한 줄에 사역자 잔과 유료 잔이 섞여 있었으면 두 줄로 나눈다
        const free = l.staffFreeQty
        const paid = l.quantity - free
        if (paid > 0) converted.push({ ...base, qty: paid, staffFree: false })
        if (free > 0) converted.push({ ...base, qty: free, staffFree: true })
      }
      setLines(converted)
      setReady(true)
    }).catch(() => setError('메뉴를 불러오지 못했습니다.'))
  }, [order])

  const total = useMemo(() => cartTotal(lines), [lines])
  const staffFree = useMemo(() => cartStaffFree(lines), [lines])

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      await api.updateOrder(order.id, {
        customerName: name,
        receiveType,
        placeId: receiveType === 'DELIVERY' ? placeId : null,
        lines: toLineRequests(lines),
        memo: memo || null,
      })
      onSaved()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="row between">
          <h2>#{order.orderNo} 주문 수정</h2>
          <button className="btn ghost" onClick={onClose}>닫기</button>
        </div>
        {error && <div className="error">{error}</div>}

        <div className="field">
          <label>이름</label>
          <input className="text-input" value={name} onChange={(e) => setName(e.target.value)} />
        </div>

        <div className="field">
          <label>받는 방법</label>
          <div className="chips">
            <button className={'btn' + (receiveType === 'STORE' ? ' selected' : '')} onClick={() => setReceiveType('STORE')}>카페</button>
            <button className={'btn' + (receiveType === 'DELIVERY' ? ' selected' : '')} onClick={() => setReceiveType('DELIVERY')}>배달</button>
          </div>
        </div>

        {receiveType === 'DELIVERY' && (
          <div className="field">
            <label>장소</label>
            <div className="chips">
              {floors.flatMap((f) => f.places).map((p) => (
                <button key={p.id} className={'btn' + (placeId === p.id ? ' selected' : '')} onClick={() => setPlaceId(p.id)}>
                  {p.floor}층 {p.name}
                </button>
              ))}
            </div>
          </div>
        )}

        <div className="field">
          <label>메뉴 <span className="muted">(칩은 한 잔씩 바뀝니다)</span></label>
          {!ready && <div className="muted">불러오는 중…</div>}
          <div className="stack">
            {lines.map((l, index) => {
              const applicable = allOptions.filter((o) => o.category === l.category)
              return (
                <div key={lineKeyOf(l)} className={'cart-line-wrap' + (l.staffFree ? ' staff-free' : '')}>
                  <div className="cart-line" style={{ padding: '8px 12px' }}>
                    <div className="name" style={{ fontSize: 18 }}>
                      {lineName(l)}
                      {(l.options.length > 0 || l.staffFree) && (
                        <div className="line-options" style={{ fontSize: 14 }}>
                          {[...l.options.map((o) => o.name), ...(l.staffFree ? ['사역자 무료'] : [])].join(' · ')}
                        </div>
                      )}
                    </div>
                    <div className="qty">
                      <button className="btn" style={{ minHeight: 44, minWidth: 44 }} onClick={() => setLines(setLineQty(lines, index, l.qty - 1))}>−</button>
                      <span className="n" style={{ fontSize: 20 }}>{l.qty}</span>
                      <button className="btn" style={{ minHeight: 44, minWidth: 44 }} onClick={() => setLines(setLineQty(lines, index, l.qty + 1))}>+</button>
                    </div>
                    <div className="sum" style={{ fontSize: 16, minWidth: 80 }}>{l.staffFree ? '0원' : won(lineUnitPrice(l) * l.qty)}</div>
                  </div>
                  <div className="chips" style={{ padding: '0 8px' }}>
                    {applicable.map((o) => {
                      const on = l.options.some((x) => x.id === o.id)
                      const blocked = !on && blockedByGroup(o, l.options)
                      return (
                        <button key={o.id} className={'btn' + (on ? ' selected' : '')} disabled={blocked}
                          onClick={() => setLines(toggleOptionOnOneCup(lines, index, o))}>
                          {on ? '☑' : '☐'} {o.name}{o.price > 0 && ` +${won(o.price)}`}
                        </button>
                      )
                    })}
                    <button className={'btn' + (l.staffFree ? ' selected' : '')} onClick={() => setLines(toggleStaffFreeOnOneCup(lines, index))}>
                      {l.staffFree ? '☑' : '☐'} 사역자
                    </button>
                  </div>
                </div>
              )
            })}
          </div>
        </div>

        <div className="field">
          <label>메뉴 추가</label>
          <div className="chips">
            {menu.flatMap((item) => item.variants.map((v) => (
              <button key={v.id} className="btn"
                onClick={() => setLines(addPlainCup(lines, { variantId: v.id, itemName: item.name, category: item.category, label: v.label, price: v.price }))}>
                {item.name}{v.label ? ` ${v.label}` : ''} <span className="muted">{won(v.price)}</span>
              </button>
            )))}
          </div>
        </div>

        <div className="field">
          <label>메모</label>
          <input className="text-input" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder="예: 얼음 적게" />
        </div>

        <div className="total-box" style={{ padding: 14 }}>
          <span className="label" style={{ fontSize: 18 }}>받을 금액{staffFree > 0 && <small className="muted"> (사역자 무료 {won(staffFree)} 제외)</small>}</span>
          <span className="amount" style={{ fontSize: 28 }}>{won(total)}</span>
        </div>
        {order.couponId != null && (
          <div className="muted" style={{ fontSize: 14 }}>쿠폰 주문입니다. 저장하면 새 합계 기준으로 쿠폰 차감을 다시 계산합니다.</div>
        )}
        {(order.settledCash > 0 || order.settledTransfer > 0) && (
          <div className="muted" style={{ fontSize: 14 }}>
            받은 돈: {[order.settledCash > 0 ? `현금 ${won(order.settledCash)}` : '', order.settledTransfer > 0 ? `이체 ${won(order.settledTransfer)}` : ''].filter(Boolean).join(' · ')}
            {' '}— 저장 후 차액이 있으면 카드에 표시됩니다.
          </div>
        )}

        <button className="btn big primary" disabled={busy || !ready || lines.length === 0 || !name.trim()} onClick={save}>
          {busy ? '저장 중…' : '저장'}
        </button>
      </div>
    </div>
  )
}
