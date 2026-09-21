import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import { RULES, isPrice } from '../shared/rules'
import type { AdminOption } from '../shared/types'
import { won } from '../shared/types'

/** 잔 단위 옵션 관리 (샷 추가 +500, 연하게). 카테고리가 같은 메뉴에만 붙는다. */
export function OptionAdmin({ onToast, categories }: { onToast: (msg: string) => void; categories: string[] }) {
  const [options, setOptions] = useState<AdminOption[] | null>(null)
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [category, setCategory] = useState(categories[0] ?? '커피')
  const [group, setGroup] = useState('')
  const [busy, setBusy] = useState(false)
  const [editing, setEditing] = useState<{ id: number; name: string; price: string; group: string } | null>(null)

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
    await api.createOption({ name: name.trim(), price: Number(price) || 0, category: category.trim(), group: group.trim() || null, available: true })
    setName('')
    setPrice('')
    setGroup('')
  }, `${name.trim()} 옵션 추가됨`)

  const saveEdit = (o: AdminOption) => run(async () => {
    if (!editing) return
    await api.updateOption(o.id, { name: editing.name.trim(), price: Number(editing.price) || 0, category: o.category,
      group: editing.group.trim() || null, available: o.available })
    setEditing(null)
  }, '옵션 수정됨')

  if (!options) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <div className="card">
        <div className="muted" style={{ fontSize: 14 }}>새 옵션</div>
        <div className="row">
          <input className="text-input grow" placeholder="이름 (예: 샷 추가)" value={name} maxLength={RULES.nameMax} onChange={(e) => setName(e.target.value)} />
          <input className="text-input" style={{ width: 110 }} inputMode="numeric" placeholder="+원" value={price} maxLength={6}
            onChange={(e) => setPrice(e.target.value.replace(/[^0-9]/g, ''))} aria-label="추가 금액" />
        </div>
        <input className="text-input" placeholder="그룹 (예: 농도 — 같은 그룹은 한 잔에 하나만, 비워도 됨)" value={group} maxLength={RULES.nameMax}
          onChange={(e) => setGroup(e.target.value)} aria-label="그룹" style={{ fontSize: 15, minHeight: 46 }} />
        <div className="chips">
          {categories.map((c) => (
            <button key={c} className={'btn' + (category === c ? ' selected' : '')} onClick={() => setCategory(c)}>{c}</button>
          ))}
        </div>
        {price !== '' && !isPrice(Number(price)) && <div className="error" style={{ fontSize: 13 }}>금액은 100원 단위, {won(RULES.priceMax)}까지입니다.</div>}
        <button className="btn primary" disabled={busy || !name.trim() || !isPrice(Number(price) || 0)} onClick={() => void add()}>추가</button>
      </div>

      {options.length === 0 && <div className="empty">옵션이 없습니다.</div>}
      {options.map((o) => (
        <div key={o.id} className={'card admin-item' + (o.available ? '' : ' off')}>
          <div className="row between">
            {editing?.id === o.id ? (
              <div className="stack grow" style={{ gap: 6 }}>
                <div className="row">
                  <input className="text-input grow" value={editing.name} maxLength={RULES.nameMax} onChange={(e) => setEditing({ ...editing, name: e.target.value })} aria-label="옵션 이름" style={{ minHeight: 44, fontSize: 16 }} />
                  <input className="text-input" style={{ width: 100, minHeight: 44, fontSize: 16 }} inputMode="numeric" value={editing.price} maxLength={6}
                    onChange={(e) => setEditing({ ...editing, price: e.target.value.replace(/[^0-9]/g, '') })} aria-label="옵션 금액" placeholder="+원" />
                </div>
                <div className="row">
                  <input className="text-input grow" value={editing.group} placeholder="그룹 (비우면 자유 조합)"
                    onChange={(e) => setEditing({ ...editing, group: e.target.value })} aria-label="옵션 그룹" style={{ minHeight: 44, fontSize: 15 }} />
                  <button className="btn primary" style={{ minHeight: 44, fontSize: 14 }} disabled={busy || !editing.name.trim() || !isPrice(Number(editing.price) || 0)} onClick={() => void saveEdit(o)}>저장</button>
                  <button className="btn ghost" style={{ minHeight: 44, fontSize: 14 }} onClick={() => setEditing(null)}>취소</button>
                </div>
              </div>
            ) : (
              <div>
                <div className="admin-name">{o.name} <span className="muted admin-cat">{o.category}</span></div>
                <div className="muted admin-variants">{o.price > 0 ? `+${won(o.price)}` : '추가 금액 없음'}{o.group && ` · 그룹 ${o.group}`}</div>
              </div>
            )}
            <button className={'btn toggle' + (o.available ? ' on' : '')} disabled={busy}
              onClick={() => void run(() => api.updateOption(o.id, { name: o.name, price: o.price, category: o.category, group: o.group, available: !o.available }), o.available ? `${o.name} 숨김` : `${o.name} 사용`)}>
              {o.available ? '사용중' : '숨김'}
            </button>
          </div>
          <div className="row admin-actions">
            <span className="grow" />
            <button className="btn" disabled={busy || editing?.id === o.id}
              onClick={() => setEditing({ id: o.id, name: o.name, price: o.price ? String(o.price) : '', group: o.group ?? '' })}>수정</button>
            <button className="btn danger" disabled={busy} onClick={() => {
              if (window.confirm(`"${o.name}" 옵션을 삭제할까요?`)) void run(() => api.deleteOption(o.id), `${o.name} 삭제됨`)
            }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  )
}
