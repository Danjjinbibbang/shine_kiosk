import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { StaffMember } from '../shared/types'

/** 사역자 명단. 여기 있는 이름만 키오스크에서 "사역자 주문" 으로 고를 수 있다. */
export function MemberAdmin({ onToast }: { onToast: (msg: string) => void }) {
  const [members, setMembers] = useState<StaffMember[] | null>(null)
  const [name, setName] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.adminMembers().then(setMembers).catch((e) => onToast(e.message))
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
    await api.createMember(name.trim())
    setName('')
  }, `${name.trim()} 등록됨`)

  const rename = (m: StaffMember) => {
    const next = window.prompt('사역자 이름', m.name)
    if (next === null || !next.trim() || next.trim() === m.name) return
    void run(() => api.updateMember(m.id, { name: next.trim(), active: m.active }), '이름 변경됨')
  }

  if (!members) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <div className="muted" style={{ fontSize: 14 }}>사역자는 주문 시 잔 단위로 무료 처리할 수 있습니다. 키오스크 장바구니의 "사역자 주문이에요" 에 이 명단이 뜹니다.</div>
      <div className="card">
        <div className="row">
          <input className="text-input grow" placeholder="사역자 이름" value={name} onChange={(e) => setName(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void add() }} />
          <button className="btn primary" disabled={busy || !name.trim()} onClick={() => void add()}>등록</button>
        </div>
      </div>

      {members.length === 0 && <div className="empty">등록된 사역자가 없습니다.</div>}
      {members.map((m) => (
        <div key={m.id} className={'card admin-item' + (m.active ? '' : ' off')}>
          <div className="row between">
            <div className="admin-name">{m.name}</div>
            <button className={'btn toggle' + (m.active ? ' on' : '')} disabled={busy}
              onClick={() => void run(() => api.updateMember(m.id, { name: m.name, active: !m.active }), m.active ? `${m.name} 제외` : `${m.name} 포함`)}>
              {m.active ? '사역중' : '제외'}
            </button>
          </div>
          <div className="row admin-actions">
            <span className="grow" />
            <button className="btn" disabled={busy} onClick={() => rename(m)}>이름 변경</button>
            <button className="btn danger" disabled={busy} onClick={() => {
              if (window.confirm(`"${m.name}" 을 명단에서 삭제할까요?`)) void run(() => api.deleteMember(m.id), `${m.name} 삭제됨`)
            }}>삭제</button>
          </div>
        </div>
      ))}
    </div>
  )
}
