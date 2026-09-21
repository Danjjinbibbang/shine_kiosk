import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import { RULES } from '../shared/rules'
import type { Category } from '../shared/types'

/** 메뉴 카테고리(커피/논커피/아이스크림/디저트…). 키오스크에 보이는 순서도 여기서. */
export function CategoryAdmin({ onToast }: { onToast: (msg: string) => void }) {
  const [categories, setCategories] = useState<Category[] | null>(null)
  const [name, setName] = useState('')
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.categories().then(setCategories).catch((e) => onToast(e.message))
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
    await api.createCategory(name.trim())
    setName('')
  }, `${name.trim()} 추가됨`)

  const saveName = (c: Category) => run(async () => {
    await api.renameCategory(c.id, draft.trim())
    setEditingId(null)
  }, '이름 변경됨 (메뉴·옵션도 같이 바뀜)')

  const move = (index: number, dir: -1 | 1) => {
    if (!categories) return
    const target = index + dir
    if (target < 0 || target >= categories.length) return
    const ids = categories.map((c) => c.id)
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    void run(() => api.reorderCategories(ids))
  }

  if (!categories) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <div className="muted" style={{ fontSize: 14 }}>키오스크 메뉴 화면은 이 순서대로 묶여서 나옵니다. 메뉴·옵션을 만들 때 여기 있는 카테고리만 고를 수 있어요.</div>
      <div className="card">
        <div className="row">
          <input className="text-input grow" placeholder="새 카테고리 (예: 디저트)" value={name} maxLength={RULES.nameMax} onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void add() }} />
          <button className="btn primary" disabled={busy || !name.trim()} onClick={() => void add()}>추가</button>
        </div>
      </div>

      {categories.map((c, idx) => (
        <div key={c.id} className="card admin-item">
          <div className="row between">
            {editingId === c.id ? (
              <div className="row grow">
                <input className="text-input grow" value={draft} maxLength={RULES.nameMax} onChange={(e) => setDraft(e.target.value)} autoFocus
                  onKeyDown={(e) => { if (e.key === 'Enter' && draft.trim()) void saveName(c) }} aria-label="카테고리 이름" style={{ minHeight: 44, fontSize: 17 }} />
                <button className="btn primary" style={{ minHeight: 44, fontSize: 14 }} disabled={busy || !draft.trim() || draft.trim() === c.name} onClick={() => void saveName(c)}>저장</button>
                <button className="btn ghost" style={{ minHeight: 44, fontSize: 14 }} onClick={() => setEditingId(null)}>취소</button>
              </div>
            ) : (
              <div className="admin-name">{c.name} <span className="muted admin-cat">메뉴 {c.itemCount}개</span></div>
            )}
          </div>
          <div className="row admin-actions">
            <button className="btn" disabled={busy || idx === 0} onClick={() => move(idx, -1)} aria-label="위로">↑</button>
            <button className="btn" disabled={busy || idx === categories.length - 1} onClick={() => move(idx, 1)} aria-label="아래로">↓</button>
            <span className="grow" />
            <button className="btn" disabled={busy || editingId === c.id} onClick={() => { setEditingId(c.id); setDraft(c.name) }}>이름 변경</button>
            <button className="btn danger" disabled={busy || c.itemCount > 0} title={c.itemCount > 0 ? '메뉴가 있으면 지울 수 없어요' : ''} onClick={() => {
              if (window.confirm(`"${c.name}" 카테고리를 삭제할까요?`)) void run(() => api.deleteCategory(c.id), `${c.name} 삭제됨`)
            }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  )
}
