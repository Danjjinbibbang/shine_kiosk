import { useEffect, useState } from 'react'
import { api, ApiError, setStaffToken } from '../shared/api'
import { Keypad } from '../kiosk/PaymentSteps'

export function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [pin, setPin] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    if (pin.length !== 4 || busy) return
    setBusy(true)
    api.staffLogin(pin)
      .then((r) => { setStaffToken(r.token); onLogin() })
      .catch((e) => {
        setError(e instanceof ApiError ? e.message : '로그인에 실패했습니다.')
        setPin('')
      })
      .finally(() => setBusy(false))
  }, [pin, busy, onLogin])

  return (
    <div className="kiosk" style={{ maxWidth: 480, justifyContent: 'center' }}>
      <div className="hero" style={{ padding: 0 }}>
        <div className="title">스태프 화면</div>
        <div className="sub">PIN 4자리를 눌러 주세요</div>
      </div>
      <div className="pin-dots">
        {[0, 1, 2, 3].map((i) => <span key={i} className={i < pin.length ? 'on' : ''} />)}
      </div>
      {error && <div className="error center">{error}</div>}
      <Keypad value={pin} maxLength={4} onChange={(v) => { setError(null); setPin(v) }} />
    </div>
  )
}
