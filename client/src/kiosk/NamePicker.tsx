import { useEffect, useRef, useState } from 'react'
import { api } from '../shared/api'

interface Props {
  title?: string
  confirmLabel: string
  disabled?: boolean
  onSelect: (name: string) => void
}

/**
 * 단골 명단 버튼(가나다순) + 직접 입력.
 * 어르신은 버튼 하나 누르면 끝, 처음 오신 분은 봉사자가 옆에서 대신 쳐 드리면 된다.
 */
export function NamePicker({ title, confirmLabel, disabled, onSelect }: Props) {
  const [names, setNames] = useState<string[] | null>(null) // null = 아직 불러오는 중
  const [typing, setTyping] = useState(false)
  const [text, setText] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    api.regulars().then(setNames).catch(() => setNames([]))
  }, [])

  useEffect(() => {
    if (typing) inputRef.current?.focus()
  }, [typing])

  const submitText = () => {
    const name = text.trim()
    if (name) onSelect(name)
  }

  return (
    <div className="stack">
      {title && <div className="muted center">{title}</div>}

      {typing ? (
        <div className="stack">
          <input ref={inputRef} className="text-input" placeholder="이름" value={text} maxLength={20}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') submitText() }} />
          <div className="kiosk-foot">
            {names && names.length > 0 && (
              <button className="btn big" onClick={() => setTyping(false)}>명단에서 고르기</button>
            )}
            <button className="btn big primary" disabled={disabled || !text.trim()} onClick={submitText}>
              {confirmLabel}
            </button>
          </div>
        </div>
      ) : (
        <div className="stack">
          {names === null && <div className="empty">명단을 불러오는 중…</div>}
          {names && names.length === 0 && <div className="empty">아직 명단이 없습니다. 이름을 직접 입력해 주세요.</div>}
          <div className="name-grid">
            {(names ?? []).map((n) => (
              <button key={n} className="btn" disabled={disabled} onClick={() => onSelect(n)}>{n}</button>
            ))}
          </div>
          <button className="btn big" onClick={() => setTyping(true)}>✏️ 이름 직접 입력</button>
        </div>
      )}
    </div>
  )
}
