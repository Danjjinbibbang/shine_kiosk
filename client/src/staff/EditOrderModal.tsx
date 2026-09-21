import { useEffect, useMemo, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { FloorGroup, MenuItem, MenuOption, Order, ReceiveType } from '../shared/types'
import { won } from '../shared/types'

interface Line {
  variantId: number
  name: string
  category: string
  /** 기본가 */
  price: number
  qty: number
  options: MenuOption[]
}

const unitPrice = (l: Line) => l.price + l.options.reduce((s, o) => s + o.price, 0)
const keyOf = (variantId: number, optionIds: number[]) => variantId + ':' + [...optionIds].sort((a, b) => a - b).join(',')

interface Props {
  order: Order
  onClose: () => void
  onSaved: () => void
}

/** 항목 수량, 이름, 받는 방법/장소를 고친다. 결제 수단은 서버가 유지하고 금액만 다시 계산한다. */
export function EditOrderModal({ order, onClose, onSaved }: Props) {
  const [menu, setMenu] = useState<MenuItem[]>([])
  const [allOptions, setAllOptions] = useState<MenuOption[]>([])
  const [floors, setFloors] = useState<FloorGroup[]>([])
  const [name, setName] = useState(order.customerName)
  const [receiveType, setReceiveType] = useState<ReceiveType>(order.receiveType)
  const [placeId, setPlaceId] = useState<number | null>(order.placeId)
  const [memo, setMemo] = useState(order.memo ?? '')
  // 기존 줄은 메뉴표를 받은 뒤 category/기본가를 채운다 (아래 useEffect)
  const [lines, setLines] = useState<Line[]>(() => order.lines
    .filter((l) => l.variantId !== null)
    .map((l) => ({
      variantId: l.variantId!,
      name: l.variantLabel ? `${l.menuName} ${l.variantLabel}` : l.menuName,
      category: '',
      price: l.unitPrice - l.options.reduce((s, o) => s + o.price, 0),
      qty: l.quantity,
      options: l.options.filter((o) => o.optionId !== null).map((o) => ({ id: o.optionId!, name: o.name, price: o.price, category: '' })),
    })))
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.menu().then((m) => {
      setMenu(m)
      // 기존 줄에 카테고리를 채워 옵션 칩이 뜨게 한다
      const catOf = new Map<number, string>()
      m.forEach((item) => item.variants.forEach((v) => catOf.set(v.id, item.category)))
      setLines((ls) => ls.map((l) => ({ ...l, category: catOf.get(l.variantId) ?? l.category })))
    }).catch(() => {})
    api.menuOptions().then(setAllOptions).catch(() => {})
    api.places().then(setFloors).catch(() => {})
  }, [])

  const total = useMemo(() => lines.reduce((s, l) => s + unitPrice(l) * l.qty, 0), [lines])
  const keyOfLine = (l: Line) => keyOf(l.variantId, l.options.map((o) => o.id))

  const setQty = (index: number, qty: number) =>
    setLines((ls) => qty <= 0 ? ls.filter((_, i) => i !== index) : ls.map((l, i) => i === index ? { ...l, qty } : l))

  const addVariant = (item: MenuItem, variantId: number, label: string | null, price: number) => {
    setLines((ls) => {
      const idx = ls.findIndex((l) => keyOfLine(l) === keyOf(variantId, []))
      if (idx >= 0) return ls.map((l, i) => i === idx ? { ...l, qty: l.qty + 1 } : l)
      return [...ls, { variantId, name: label ? `${item.name} ${label}` : item.name, category: item.category, price, qty: 1, options: [] }]
    })
  }

  const toggleOption = (index: number, opt: MenuOption) => {
    setLines((ls) => {
      const line = ls[index]
      const has = line.options.some((o) => o.id === opt.id)
      const changed: Line = { ...line, options: has ? line.options.filter((o) => o.id !== opt.id) : [...line.options, opt] }
      const mergeIdx = ls.findIndex((l, i) => i !== index && keyOfLine(l) === keyOfLine(changed))
      if (mergeIdx >= 0) {
        return ls.map((l, i) => i === mergeIdx ? { ...l, qty: l.qty + line.qty } : l).filter((_, i) => i !== index)
      }
      return ls.map((l, i) => i === index ? changed : l)
    })
  }

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      await api.updateOrder(order.id, {
        customerName: name,
        receiveType,
        placeId: receiveType === 'DELIVERY' ? placeId : null,
        lines: lines.map((l) => ({ variantId: l.variantId, quantity: l.qty, optionIds: l.options.map((o) => o.id) })),
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
          <label>메뉴</label>
          <div className="stack">
            {lines.map((l, index) => {
              const applicable = allOptions.filter((o) => o.category === l.category)
              return (
                <div key={keyOfLine(l)} className="cart-line-wrap">
                  <div className="cart-line" style={{ padding: '8px 12px' }}>
                    <div className="name" style={{ fontSize: 18 }}>
                      {l.name}
                      {l.options.length > 0 && <div className="line-options" style={{ fontSize: 14 }}>{l.options.map((o) => o.name).join(' · ')}</div>}
                    </div>
                    <div className="qty">
                      <button className="btn" style={{ minHeight: 44, minWidth: 44 }} onClick={() => setQty(index, l.qty - 1)}>−</button>
                      <span className="n" style={{ fontSize: 20 }}>{l.qty}</span>
                      <button className="btn" style={{ minHeight: 44, minWidth: 44 }} onClick={() => setQty(index, l.qty + 1)}>+</button>
                    </div>
                  </div>
                  {applicable.length > 0 && (
                    <div className="chips" style={{ padding: '0 8px' }}>
                      {applicable.map((o) => {
                        const on = l.options.some((x) => x.id === o.id)
                        return (
                          <button key={o.id} className={'btn' + (on ? ' selected' : '')} onClick={() => toggleOption(index, o)}>
                            {on ? '☑' : '☐'} {o.name}{o.price > 0 && ` +${won(o.price)}`}
                          </button>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        </div>

        <div className="field">
          <label>메뉴 추가</label>
          <div className="chips">
            {menu.flatMap((item) => item.variants.map((v) => (
              <button key={v.id} className="btn" onClick={() => addVariant(item, v.id, v.label, v.price)}>
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
          <span className="label" style={{ fontSize: 18 }}>합계</span>
          <span className="amount" style={{ fontSize: 28 }}>{won(total)}</span>
        </div>
        {order.couponId !== null && (
          <div className="muted" style={{ fontSize: 14 }}>쿠폰 주문입니다. 저장하면 새 합계 기준으로 쿠폰 차감을 다시 계산합니다.</div>
        )}

        <button className="btn big primary" disabled={busy || lines.length === 0 || !name.trim()} onClick={save}>
          {busy ? '저장 중…' : '저장'}
        </button>
      </div>
    </div>
  )
}
