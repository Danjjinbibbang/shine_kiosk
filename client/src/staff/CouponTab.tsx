import { useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { Coupon, LookupResult } from '../shared/types'
import { won } from '../shared/types'

/**
 * 쿠폰 조회/등록/충전. 이름을 넣어 조회하면
 *  - 있으면: 잔액 보여주고 충전
 *  - 없으면: 신규 등록 (동명이인이면 전화 뒤 4자리 필수)
 */
export function CouponTab({ onToast }: { onToast: (msg: string) => void }) {
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [result, setResult] = useState<LookupResult | null>(null)
  const [preset, setPreset] = useState(20000)
  const [amount, setAmount] = useState('')
  const [adjusting, setAdjusting] = useState(false)
  const [newBalance, setNewBalance] = useState('')
  const [newFree, setNewFree] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.couponPreset().then((r) => setPreset(r.amount)).catch(() => {})
  }, [])

  const wrap = async (fn: () => Promise<void>) => {
    setBusy(true)
    setError(null)
    try {
      await fn()
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const lookup = () => wrap(async () => {
    setResult(await api.lookupCoupon(name.trim(), phone || undefined))
  })

  const showCoupon = (c: Coupon, msg: string) => {
    setResult({ status: 'FOUND', coupon: c, candidateCount: 1 })
    setAmount('')
    onToast(msg)
  }

  const register = (amt: number) => wrap(async () => {
    const c = await api.registerCoupon(name.trim(), phone || null, amt)
    showCoupon(c, `${c.name}님 쿠폰 등록 · 잔액 ${won(c.balance)}`)
  })

  const charge = (c: Coupon, amt: number) => wrap(async () => {
    const updated = await api.chargeCoupon(c.id, amt)
    showCoupon(updated, `${updated.name}님 ${won(amt)} 충전 · 잔액 ${won(updated.balance)}`)
  })

  const adjust = (c: Coupon) => wrap(async () => {
    const balance = newBalance === '' ? c.balance : Number(newBalance)
    const free = newFree === '' ? c.freeDrinks : Number(newFree)
    if (!window.confirm(`${c.name}님
잔액 ${won(c.balance)} → ${won(balance)}
무료잔 ${c.freeDrinks}잔 → ${free}잔
이렇게 바꿀까요?`)) return
    const updated = await api.adjustCoupon(c.id, balance, free)
    setAdjusting(false)
    setNewBalance('')
    setNewFree('')
    showCoupon(updated, `${updated.name}님 정정 · ${won(updated.balance)} / 무료 ${updated.freeDrinks}잔`)
  })

  const remove = (c: Coupon) => wrap(async () => {
    if (!window.confirm(`${c.name}님 쿠폰(잔액 ${won(c.balance)})을 삭제할까요?
되돌릴 수 없습니다.`)) return
    await api.deleteCoupon(c.id)
    onToast(`${c.name}님 쿠폰 삭제됨`)
    reset()
  })

  const customAmount = Number(amount.replace(/[^0-9]/g, '')) || 0
  const reset = () => { setName(''); setPhone(''); setResult(null); setAmount(''); setAdjusting(false); setNewBalance(''); setNewFree(''); setError(null) }

  return (
    <div className="stack">
      <div className="card">
        <div className="field">
          <label>이름</label>
          <input className="text-input" value={name} onChange={(e) => { setName(e.target.value); setResult(null) }}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void lookup() }} placeholder="쿠폰 주인 이름" />
        </div>
        {(result?.status === 'NEED_PHONE' || phone) && (
          <div className="field">
            <label>전화번호 뒤 4자리 {result?.status === 'NEED_PHONE' && `(같은 이름 ${result.candidateCount}명)`}</label>
            <input className="text-input" inputMode="numeric" maxLength={4} value={phone}
              onChange={(e) => setPhone(e.target.value.replace(/[^0-9]/g, ''))} />
          </div>
        )}
        <div className="row">
          <button className="btn primary grow" disabled={busy || !name.trim()} onClick={() => void lookup()}>조회</button>
          <button className="btn ghost" onClick={reset}>지우기</button>
        </div>
        {error && <div className="error">{error}</div>}
      </div>

      {result?.status === 'FOUND' && result.coupon && (
        <div className="card">
          <div className="row between">
            <b style={{ fontSize: 20 }}>{result.coupon.name}{result.coupon.phoneLast4 && <span className="muted"> ({result.coupon.phoneLast4})</span>}</b>
            <b style={{ fontSize: 26, color: 'var(--primary)' }}>{won(result.coupon.balance)}</b>
          </div>
          <div className="row between" style={{ fontSize: 16 }}>
            <span className="muted">무료 1잔</span>
            <b>{result.coupon.freeDrinks}잔 남음</b>
          </div>
          <div className="muted" style={{ fontSize: 14 }}>충전 ({won(preset)}마다 무료 1잔 적립)</div>
          <AmountPicker preset={preset} amount={amount} onAmount={setAmount} busy={busy}
            label={(n) => `${won(n)} 충전`} onSubmit={(n) => void charge(result.coupon!, n)} custom={customAmount} />

          {adjusting ? (
            <div className="stack" style={{ borderTop: '1px solid var(--line)', paddingTop: 10 }}>
              <div className="muted" style={{ fontSize: 14 }}>잘못 충전했을 때 직접 고칩니다. 비워 두면 그대로, 차액은 이력에 남습니다.</div>
              <div className="row">
                <input className="text-input grow" inputMode="numeric" placeholder={`잔액 (지금 ${won(result.coupon.balance)})`} value={newBalance}
                  onChange={(e) => setNewBalance(e.target.value.replace(/[^0-9]/g, ''))} autoFocus />
                <input className="text-input" style={{ width: 130 }} inputMode="numeric" placeholder={`무료 ${result.coupon.freeDrinks}잔`} value={newFree}
                  onChange={(e) => setNewFree(e.target.value.replace(/[^0-9]/g, ''))} />
              </div>
              <div className="row">
                <button className="btn grow" disabled={busy || (newBalance === '' && newFree === '')} onClick={() => void adjust(result.coupon!)}>정정</button>
                <button className="btn ghost" onClick={() => { setAdjusting(false); setNewBalance(''); setNewFree('') }}>취소</button>
              </div>
            </div>
          ) : (
            <div className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
              <button className="btn ghost" style={{ minHeight: 40, fontSize: 15 }} onClick={() => setAdjusting(true)}>잔액 정정</button>
              <button className="btn ghost" style={{ minHeight: 40, fontSize: 15, color: 'var(--danger)' }} disabled={busy}
                onClick={() => void remove(result.coupon!)}>쿠폰 삭제</button>
            </div>
          )}
        </div>
      )}

      {result?.status === 'NOT_FOUND' && (
        <div className="card">
          <b style={{ fontSize: 18 }}>"{name.trim()}" 쿠폰이 없습니다. 새로 등록할까요?</b>
          {!phone && (
            <div className="muted" style={{ fontSize: 14 }}>
              같은 이름이 나중에 또 생길 수 있으면 전화번호 뒤 4자리를 함께 넣어 두세요.
              <button className="btn ghost" style={{ minHeight: 32, fontSize: 14 }} onClick={() => setPhone(' ')}>번호 넣기</button>
            </div>
          )}
          <AmountPicker preset={preset} amount={amount} onAmount={setAmount} busy={busy}
            label={(n) => `${won(n)} 등록`} onSubmit={(n) => void register(n)} custom={customAmount} />
        </div>
      )}

      {result?.status === 'NEED_PHONE' && (
        <div className="muted center">같은 이름이 {result.candidateCount}명 있습니다. 전화번호 뒤 4자리를 넣고 다시 조회해 주세요.</div>
      )}
    </div>
  )
}

interface PickerProps {
  preset: number
  amount: string
  custom: number
  busy: boolean
  label: (n: number) => string
  onAmount: (v: string) => void
  onSubmit: (n: number) => void
}

function AmountPicker({ preset, amount, custom, busy, label, onAmount, onSubmit }: PickerProps) {
  return (
    <div className="stack">
      <button className="btn big primary" disabled={busy} onClick={() => onSubmit(preset)}>{label(preset)}</button>
      <div className="row">
        <input className="text-input grow" inputMode="numeric" placeholder="다른 금액" value={amount}
          onChange={(e) => onAmount(e.target.value.replace(/[^0-9]/g, ''))} />
        <button className="btn" disabled={busy || custom <= 0} onClick={() => onSubmit(custom)}>{custom > 0 ? label(custom) : '확인'}</button>
      </div>
    </div>
  )
}
