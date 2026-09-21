import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { AdminOption } from '../shared/types'
import { won } from '../shared/types'

/** 잔 단위 옵션 관리 (샷 추가 +500, 연하게). 카테고리가 같은 메뉴에만 붙는다. */
export function OptionAdmin({ onToast, categories }: { onToast: (msg: string) => void; categories: string[] }) {
  const [options, setOptions] = useState<AdminOption[] | null>(null)
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [category, setCategory] = useState(categories[0] ?? '커피')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.adminOptions().then(setOptions).catch((e) => onToast(e.message))
  }, [onToast])

  useEffect(() => { load() }, [load])

  const run = async (fn: () => Promise<unknown>, doneMsg?: string) => {
    setBusy(true)
    try {
      await fn()
      if (doneMsg) onToast(doneMsg)
      load()
    } catch (e) {
      onToast(e instanceof ApiError ? e.message : '처리하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const add = () => run(async () => {
    await api.createOption({ name: name.trim(), price: Number(price) || 0, category: category.trim(), available: true })
    setName('')
    setPrice('')
  }, `${name.trim()} 옵션 추가됨`)

  const editPrice = (o: AdminOption) => {
    const next = window.prompt(`"${o.name}" 추가 금액 (원)`, String(o.price))
    if (next === null) return
    const p = Number(next.replace(/[^0-9]/g, ''))
    if (Number.isNaN(p)) return
    void run(() => api.updateOption(o.id, { name: o.name, price: p, category: o.category, available: o.available }), '금액 변경됨')
  }

  if (!options) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <div className="card">
        <div className="muted" style={{ fontSize: 14 }}>새 옵션</div>
        <div className="row">
          <input className="text-input grow" placeholder="이름 (예: 샷 추가)" value={name} onChange={(e) => setName(e.target.value)} />
          <input className="text-input" style={{ width: 110 }} inputMode="numeric" placeholder="+원" value={price}
            onChange={(e) => setPrice(e.target.value.replace(/[^0-9]/g, ''))} aria-label="추가 금액" />
        </div>
        <div className="chips">
          {categories.map((c) => (
            <button key={c} className={'btn' + (category === c ? ' selected' : '')} onClick={() => setCategory(c)}>{c}</button>
          ))}
        </div>
        <button className="btn primary" disabled={busy || !name.trim()} onClick={() => void add()}>추가</button>
      </div>

      {options.length === 0 && <div className="empty">옵션이 없습니다.</div>}
      {options.map((o) => (
        <div key={o.id} className={'card admin-item' + (o.available ? '' : ' off')}>
          <div className="row between">
            <div>
              <div className="admin-name">{o.name} <span className="muted admin-cat">{o.category}</span></div>
              <div className="muted admin-variants">{o.price > 0 ? `+${won(o.price)}` : '추가 금액 없음'}</div>
            </div>
            <button className={'btn toggle' + (o.available ? ' on' : '')} disabled={busy}
              onClick={() => void run(() => api.updateOption(o.id, { ...o, available: !o.available }), o.available ? `${o.name} 숨김` : `${o.name} 사용`)}>
              {o.available ? '사용중' : '숨김'}
            </button>
          </div>
          <div className="row admin-actions">
            <span className="grow" />
            <button className="btn" disabled={busy} onClick={() => editPrice(o)}>금액 변경</button>
            <button className="btn danger" disabled={busy} onClick={() => {
              if (window.confirm(`"${o.name}" 옵션을 삭제할까요?`)) void run(() => api.deleteOption(o.id), `${o.name} 삭제됨`)
            }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  )
}
