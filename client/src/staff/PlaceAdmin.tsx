import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { AdminPlace } from '../shared/types'

/** 배달 장소 관리. 층 + 이름. 지난 주문이 참조하는 장소는 삭제 대신 숨겨진다. */
export function PlaceAdmin({ onToast }: { onToast: (msg: string) => void }) {
  const [places, setPlaces] = useState<AdminPlace[] | null>(null)
  const [floor, setFloor] = useState('1')
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [draft, setDraft] = useState('')

  const load = useCallback(() => {
    api.adminPlaces().then(setPlaces).catch((e) => onToast(e.message))
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
    await api.createPlace({ floor: Number(floor) || 1, name: name.trim(), active: true })
    setName('')
  }, `${floor}층 ${name.trim()} 추가됨`)

  const saveName = (p: AdminPlace) => run(async () => {
    await api.updatePlace(p.id, { floor: p.floor, name: draft.trim(), active: p.active })
    setEditingId(null)
  }, '이름 변경됨')

  if (!places) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <div className="card">
        <div className="muted" style={{ fontSize: 14 }}>새 장소</div>
        <div className="row">
          <input className="text-input" style={{ width: 90 }} inputMode="numeric" value={floor}
            onChange={(e) => setFloor(e.target.value.replace(/[^0-9]/g, ''))} aria-label="층" />
          <span className="muted">층</span>
          <input className="text-input grow" placeholder="장소 이름 (예: 식당)" value={name}
            onChange={(e) => setName(e.target.value)} onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void add() }} />
          <button className="btn primary" disabled={busy || !name.trim() || !floor} onClick={() => void add()}>추가</button>
        </div>
      </div>

      {places.length === 0 && <div className="empty">배달 장소가 없습니다. 장소가 없으면 고객 화면에서 배달을 고를 수 없습니다.</div>}
      {places.map((p) => (
        <div key={p.id} className={'card admin-item' + (p.active ? '' : ' off')}>
          <div className="row between">
            {editingId === p.id ? (
              <div className="row grow">
                <span className="admin-name">{p.floor}층</span>
                <input className="text-input grow" value={draft} onChange={(e) => setDraft(e.target.value)} autoFocus
                  onKeyDown={(e) => { if (e.key === 'Enter' && draft.trim()) void saveName(p) }} aria-label="장소 이름" style={{ minHeight: 44, fontSize: 17 }} />
                <button className="btn primary" style={{ minHeight: 44, fontSize: 14 }} disabled={busy || !draft.trim() || draft.trim() === p.name} onClick={() => void saveName(p)}>저장</button>
                <button className="btn ghost" style={{ minHeight: 44, fontSize: 14 }} onClick={() => setEditingId(null)}>취소</button>
              </div>
            ) : (
              <div className="admin-name">{p.floor}층 {p.name}</div>
            )}
            <button className={'btn toggle' + (p.active ? ' on' : '')} disabled={busy}
              onClick={() => void run(() => api.updatePlace(p.id, { floor: p.floor, name: p.name, active: !p.active }), p.active ? `${p.name} 숨김` : `${p.name} 표시`)}>
              {p.active ? '표시중' : '숨김'}
            </button>
          </div>
          <div className="row admin-actions">
            <span className="grow" />
            <button className="btn" disabled={busy || editingId === p.id} onClick={() => { setEditingId(p.id); setDraft(p.name) }}>이름 변경</button>
            <button className="btn danger" disabled={busy} onClick={() => {
              if (window.confirm(`"${p.floor}층 ${p.name}" 을 삭제할까요?`)) void run(() => api.deletePlace(p.id), `${p.name} 삭제됨`)
            }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  )
}
