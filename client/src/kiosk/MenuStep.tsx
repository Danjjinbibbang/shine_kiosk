import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { MenuItem, MenuVariant } from '../shared/types'
import { won } from '../shared/types'
import { addPlainCup, type CartLine } from '../shared/cart'

interface Props {
  cart: CartLine[]
  total: number
  onChange: (cart: CartLine[]) => void
  onNext: () => void
}

/**
 * 메뉴 한 줄 = 버튼 하나. 누르면 1개 담기고, 담긴 뒤에는 옆에 − 수량 + 가 나타난다.
 * 어르신은 같은 버튼을 여러 번 눌러도 되고, 익숙한 분은 +/- 를 쓰면 된다.
 */
export function MenuStep({ cart, total, onChange, onNext }: Props) {
  const [menu, setMenu] = useState<MenuItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [tab, setTab] = useState<string | null>(null) // 선택한 카테고리. 서버가 준 순서의 첫 카테고리가 기본

  useEffect(() => {
    api.menu().then(setMenu).catch((e) => setError(e.message))
  }, [])

  // 옵션이 다른 줄까지 합친 수량. (샷 추가 같은 옵션은 장바구니 화면에서 붙인다)
  const qtyOf = (variantId: number) => cart.filter((l) => l.variantId === variantId).reduce((s, l) => s + l.qty, 0)

  /** + 는 옵션 없는 줄에 더하고, − 는 그 메뉴의 마지막 줄에서 뺀다. */
  const setQty = (item: MenuItem, v: MenuVariant, qty: number) => {
    const current = qtyOf(v.id)
    if (qty > current) {
      onChange(addPlainCup(cart, { variantId: v.id, itemName: item.name, category: item.category, label: v.label, price: v.price }))
      return
    }
    const idx = cart.map((l) => l.variantId).lastIndexOf(v.id)
    if (idx < 0) return
    const target = cart[idx]
    onChange(target.qty <= 1 ? cart.filter((_, i) => i !== idx) : cart.map((l, i) => (i === idx ? { ...l, qty: l.qty - 1 } : l)))
  }

  const count = cart.reduce((s, l) => s + l.qty, 0)

  if (error) return <div className="error">{error}</div>
  if (!menu) return <div className="empty">메뉴를 불러오는 중…</div>

  // 카테고리 탭은 서버(설정 > 카테고리) 순서를 그대로 따른다. 설정에서 늘리면 탭도 늘어난다.
  const categories = [...new Set(menu.map((m) => m.category))]
  const active = tab && categories.includes(tab) ? tab : categories[0]
  const inCart = (cat: string) => cart.filter((l) => l.category === cat).reduce((s, l) => s + l.qty, 0)

  return (
    <div className="stack">
      <div className="category-tabs">
        {categories.map((cat) => (
          <button key={cat} className={'btn tab' + (cat === active ? ' selected' : '')} onClick={() => setTab(cat)}>
            {cat}{inCart(cat) > 0 && <span className="tab-badge">{inCart(cat)}</span>}
          </button>
        ))}
      </div>
      {categories.filter((cat) => cat === active).map((cat) => (
        <section key={cat}>
          <div className="menu-grid">
            {menu.filter((m) => m.category === cat).map((item) => (
              <div key={item.id} className="menu-card">
                <div className="name">{item.name}</div>
                {item.variants.map((v) => {
                  const qty = qtyOf(v.id)
                  return (
                    <div key={v.id} className="variant-row">
                      <button className={'btn variant' + (qty ? ' selected' : '')}
                        onClick={() => setQty(item, v, qty + 1)}>
                        <span>{v.label ?? '담기'}</span>
                        <span className="price">{won(v.price)}</span>
                      </button>
                      {qty > 0 && (
                        <div className="stepper">
                          <button className="btn" aria-label="빼기" onClick={() => setQty(item, v, qty - 1)}>−</button>
                          <span className="n">{qty}</span>
                          <button className="btn" aria-label="더하기" onClick={() => setQty(item, v, qty + 1)}>+</button>
                        </div>
                      )}
                    </div>
                  )
                })}
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
