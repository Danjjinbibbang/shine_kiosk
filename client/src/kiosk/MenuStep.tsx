import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { MenuItem, MenuVariant } from '../shared/types'
import { won } from '../shared/types'
import type { CartLine } from './KioskApp'

interface Props {
  cart: CartLine[]
  total: number
  onChange: (cart: CartLine[]) => void
  onNext: () => void
}

export function MenuStep({ cart, total, onChange, onNext }: Props) {
  const [menu, setMenu] = useState<MenuItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.menu().then(setMenu).catch((e) => setError(e.message))
  }, [])

  const add = (item: MenuItem, v: MenuVariant) => {
    const idx = cart.findIndex((l) => l.variantId === v.id)
    if (idx >= 0) {
      const next = cart.slice()
      next[idx] = { ...next[idx], qty: next[idx].qty + 1 }
      onChange(next)
    } else {
      onChange([...cart, { variantId: v.id, itemName: item.name, label: v.label, price: v.price, qty: 1 }])
    }
  }

  const qtyOf = (variantId: number) => cart.find((l) => l.variantId === variantId)?.qty ?? 0
  const count = cart.reduce((s, l) => s + l.qty, 0)

  if (error) return <div className="error">{error}</div>
  if (!menu) return <div className="empty">메뉴를 불러오는 중…</div>

  const categories = [...new Set(menu.map((m) => m.category))]

  return (
    <div className="stack">
      {categories.map((cat) => (
        <section key={cat}>
          <div className="category-title">{cat}</div>
          <div className="grid">
            {menu.filter((m) => m.category === cat).map((item) => (
              <div key={item.id} className="menu-card">
                <div className="name">{item.name}</div>
                {item.variants.length === 1 && item.variants[0].label === null ? (
                  <button className={'btn' + (qtyOf(item.variants[0].id) ? ' selected' : '')}
                    onClick={() => add(item, item.variants[0])}>
                    {won(item.variants[0].price)}
                    {qtyOf(item.variants[0].id) > 0 && <span> · {qtyOf(item.variants[0].id)}개</span>}
                  </button>
                ) : (
                  <div className="variants">
                    {item.variants.map((v) => (
                      <button key={v.id} className={'btn' + (qtyOf(v.id) ? ' selected' : '')}
                        onClick={() => add(item, v)}>
                        {v.label}<br />
                        <small>{won(v.price)}{qtyOf(v.id) > 0 && ` · ${qtyOf(v.id)}개`}</small>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </div>
        </section>
      ))}

      {count > 0 && (
        <div className="cart-bar">
          <span className="grow">{count}개 · {won(total)}</span>
          <button className="btn big" onClick={onNext}>주문 확인 ›</button>
        </div>
      )}
    </div>
  )
}
