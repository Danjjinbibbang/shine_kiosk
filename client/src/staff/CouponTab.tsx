import { useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import { RULES, formatPhoneInput, isChargeAmount, isMobile } from '../shared/rules'
import type { ChargePreset, Coupon, CouponTx } from '../shared/types'
import { won } from '../shared/types'

/**
 * 쿠폰 조회/등록/충전/이력.
 * 이름(+전화번호)으로 찾으면 후보가 목록으로 뜨고 골라서 연다. 없으면 등록 (전화번호 필수).
 */
export function CouponTab({ onToast }: { onToast: (msg: string) => void }) {
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [candidates, setCandidates] = useState<Coupon[] | null>(null)
  const [coupon, setCoupon] = useState<Coupon | null>(null)
  const [history, setHistory] = useState<CouponTx[] | null>(null)
  const [preset, setPreset] = useState<ChargePreset>({ tiers: [{ amount: 20000, freeDrinks: 1 }, { amount: 30000, freeDrinks: 2 }], max: RULES.chargeMax })
  const [amount, setAmount] = useState('')
  const [adjusting, setAdjusting] = useState(false)
  const [newBalance, setNewBalance] = useState('')
  const [newFree, setNewFree] = useState('')
  const [phoneEditing, setPhoneEditing] = useState(false)
  const [phoneDraft, setPhoneDraft] = useState('')
  const [nameEditing, setNameEditing] = useState(false)
  const [nameDraft, setNameDraft] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api.couponPreset().then(setPreset).catch(() => {})
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

  const digits = phone.replace(/[^0-9]/g, '')
  const last4 = digits.slice(-4)
  const phoneValid = isMobile(phone)

  const open = async (c: Coupon) => {
    setCoupon(c)
    setAmount('')
    setAdjusting(false)
    setPhoneEditing(false)
    setPhoneDraft(formatPhoneInput(c.phone ?? ''))
    setNameEditing(false)
    setNameDraft(c.name)
    setHistory(null)
    setHistory(await api.couponHistory(c.id))
  }

  const saveName = (c: Coupon) => wrap(async () => {
    await refresh(await api.renameCoupon(c.id, nameDraft.trim()), '이름 변경됨')
  })

  /** 이름으로 후보를 찾고, 번호가 있으면 뒤 4자리로 좁힌다. 하나면 바로 연다. */
  const lookup = () => wrap(async () => {
    setCoupon(null)
    setHistory(null)
    let found = await api.couponsByName(name.trim())
    if (last4.length === 4) found = found.filter((c) => c.phoneLast4 === last4)
    setCandidates(found)
    if (found.length === 1) await open(found[0])
  })

  const refresh = async (c: Coupon, msg: string) => {
    setCandidates((cs) => cs?.map((x) => (x.id === c.id ? c : x)) ?? null)
    await open(c)
    onToast(msg)
  }

  const register = (amt: number) => wrap(async () => {
    const c = await api.registerCoupon(name.trim(), phone.trim(), amt)
    setCandidates([c])
    await refresh(c, `${c.name}님 쿠폰 등록 · 잔액 ${won(c.balance)}`)
  })

  const charge = (c: Coupon, amt: number) => wrap(async () => {
    await refresh(await api.chargeCoupon(c.id, amt), `${c.name}님 ${won(amt)} 충전`)
  })

  const savePhone = (c: Coupon) => wrap(async () => {
    await refresh(await api.updateCouponPhone(c.id, phoneDraft.trim()), '전화번호 저장됨')
  })

  const adjust = (c: Coupon) => wrap(async () => {
    const balance = newBalance === '' ? c.balance : Number(newBalance)
    const free = newFree === '' ? c.freeDrinks : Number(newFree)
    if (!window.confirm(`${c.name}님\n잔액 ${won(c.balance)} → ${won(balance)}\n무료잔 ${c.freeDrinks}잔 → ${free}잔\n이렇게 바꿀까요?`)) return
    const updated = await api.adjustCoupon(c.id, balance, free)
    setNewBalance('')
    setNewFree('')
    await refresh(updated, `${updated.name}님 정정 · ${won(updated.balance)} / 무료 ${updated.freeDrinks}잔`)
  })

  const remove = (c: Coupon) => wrap(async () => {
    if (!window.confirm(`${c.name}님 쿠폰(잔액 ${won(c.balance)})을 삭제할까요?\n되돌릴 수 없습니다.`)) return
    await api.deleteCoupon(c.id)
    onToast(`${c.name}님 쿠폰 삭제됨`)
    reset()
  })

  const customAmount = Number(amount.replace(/[^0-9]/g, '')) || 0
  const reset = () => {
    setName(''); setPhone(''); setCandidates(null); setCoupon(null); setHistory(null)
    setAmount(''); setAdjusting(false); setNewBalance(''); setNewFree(''); setError(null)
  }

  return (
    <div className="stack">
      <div className="card">
        <div className="field">
          <label>이름</label>
          <input className="text-input" value={name} maxLength={RULES.nameMax} onChange={(e) => { setName(e.target.value); setPhone(''); setCandidates(null); setCoupon(null) }}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void lookup() }} placeholder="쿠폰 주인 이름" />
        </div>
        <div className="field">
          <label>전화번호 <span className="muted">(조회는 뒤 4자리만으로도 됨 · 등록엔 전체 번호 필수)</span></label>
          <input className="text-input" inputMode="tel" placeholder="010-0000-0000" value={phone} maxLength={13}
            onChange={(e) => setPhone(formatPhoneInput(e.target.value))}
            onKeyDown={(e) => { if (e.key === 'Enter' && name.trim()) void lookup() }} />
          {digits.length > 4 && !phoneValid && <div className="error" style={{ fontSize: 13 }}>휴대폰 번호 13자리를 다 넣어 주세요 (010-1234-5678) · 지금 {digits.length}/11 숫자</div>}
        </div>
        <div className="row">
          <button className="btn primary grow" disabled={busy || !name.trim()} onClick={() => void lookup()}>조회</button>
          <button className="btn ghost" onClick={reset}>지우기</button>
        </div>
        {error && <div className="error">{error}</div>}
      </div>

      {candidates && candidates.length > 1 && !coupon && (
        <div className="card">
          <b style={{ fontSize: 16 }}>같은 이름이 {candidates.length}명 있어요. 누구인가요?</b>
          <div className="stack" style={{ gap: 6 }}>
            {candidates.map((c) => (
              <button key={c.id} className="btn" style={{ justifyContent: 'space-between' }} onClick={() => void open(c)}>
                <span>{c.name} {c.phone ? formatPhone(c.phone) : <span className="muted">(번호 없음)</span>}</span>
                <b>{won(c.balance)}</b>
              </button>
            ))}
          </div>
        </div>
      )}

      {coupon && (
        <div className="card">
          <div className="row between">
            {nameEditing ? (
              <div className="row grow">
                <input className="text-input grow" value={nameDraft} maxLength={RULES.nameMax} aria-label="쿠폰 이름"
                  onChange={(e) => setNameDraft(e.target.value)} autoFocus style={{ minHeight: 44, fontSize: 18 }} />
                <button className="btn primary" style={{ minHeight: 44, fontSize: 14 }} disabled={busy || !nameDraft.trim() || nameDraft.trim() === coupon.name}
                  onClick={() => void saveName(coupon)}>저장</button>
                <button className="btn ghost" style={{ minHeight: 44, fontSize: 14 }} onClick={() => setNameEditing(false)}>취소</button>
              </div>
            ) : (
              <span className="row" style={{ gap: 6 }}>
                <b style={{ fontSize: 20 }}>{coupon.name}</b>
                <button className="btn ghost" style={{ minHeight: 32, fontSize: 13 }} onClick={() => { setNameEditing(true); setNameDraft(coupon.name) }}>이름 변경</button>
              </span>
            )}
            <b style={{ fontSize: 26, color: 'var(--primary)' }}>{won(coupon.balance)}</b>
          </div>
          <div className="row between" style={{ fontSize: 16 }}>
            <span className="muted">무료 1잔</span>
            <b>{coupon.freeDrinks}잔 남음</b>
          </div>

          {/* 번호가 있으면 잠겨 있고 '번호 변경' 을 눌러야 고칠 수 있다. 없으면 바로 입력. */}
          <div className="row" style={{ fontSize: 15 }}>
            <span className="muted">📱</span>
            <input className="text-input grow" inputMode="tel" placeholder="전화번호 없음 — 입력해 주세요" maxLength={13}
              value={phoneEditing || !coupon.phone ? phoneDraft : formatPhone(coupon.phone)}
              disabled={!!coupon.phone && !phoneEditing}
              onChange={(e) => setPhoneDraft(formatPhoneInput(e.target.value))}
              aria-label="쿠폰 전화번호" style={{ minHeight: 44, fontSize: 16 }} />
            {coupon.phone && !phoneEditing ? (
              <button className="btn" style={{ minHeight: 44, fontSize: 14 }} onClick={() => { setPhoneEditing(true); setPhoneDraft(coupon.phone ?? '') }}>번호 변경</button>
            ) : (
              <>
                <button className="btn primary" style={{ minHeight: 44, fontSize: 14 }}
                  disabled={busy || phoneDraft.replace(/[^0-9]/g, '') === (coupon.phone ?? '') || !isMobile(phoneDraft)}
                  onClick={() => void savePhone(coupon)}>저장</button>
                {coupon.phone && <button className="btn ghost" style={{ minHeight: 44, fontSize: 14 }} onClick={() => setPhoneEditing(false)}>취소</button>}
              </>
            )}
          </div>

          {coupon.phone ? (
            <>
              <div className="muted" style={{ fontSize: 14 }}>충전 ({tierHint(preset)})</div>
              <AmountPicker preset={preset} amount={amount} onAmount={setAmount} busy={busy}
                label={(n) => `${won(n)} 충전`} onSubmit={(n) => void charge(coupon, n)} custom={customAmount} />
            </>
          ) : (
            <div className="error" style={{ fontSize: 14 }}>전화번호가 없어 충전할 수 없습니다. 위에 번호를 넣고 저장해 주세요.</div>
          )}

          {adjusting ? (
            <div className="stack" style={{ borderTop: '1px solid var(--line)', paddingTop: 10 }}>
              <div className="muted" style={{ fontSize: 14 }}>잘못 충전했을 때 직접 고칩니다. 비워 두면 그대로, 차액은 이력에 남습니다.</div>
              <div className="row">
                <input className="text-input grow" inputMode="numeric" placeholder={`잔액 (지금 ${won(coupon.balance)})`} value={newBalance} maxLength={7}
                  onChange={(e) => setNewBalance(e.target.value.replace(/[^0-9]/g, ''))} autoFocus />
                <input className="text-input" style={{ width: 130 }} inputMode="numeric" placeholder={`무료 ${coupon.freeDrinks}잔`} value={newFree} maxLength={3}
                  onChange={(e) => setNewFree(e.target.value.replace(/[^0-9]/g, ''))} />
              </div>
              <div className="row">
                <button className="btn grow" disabled={busy || (newBalance === '' && newFree === '')} onClick={() => void adjust(coupon)}>정정</button>
                <button className="btn ghost" onClick={() => { setAdjusting(false); setNewBalance(''); setNewFree('') }}>취소</button>
              </div>
            </div>
          ) : (
            <div className="row" style={{ justifyContent: 'flex-end', gap: 6 }}>
              <button className="btn ghost" style={{ minHeight: 40, fontSize: 15 }} onClick={() => setAdjusting(true)}>잔액 정정</button>
              <button className="btn ghost" style={{ minHeight: 40, fontSize: 15, color: 'var(--danger)' }} disabled={busy}
                onClick={() => void remove(coupon)}>쿠폰 삭제</button>
            </div>
          )}

          <div className="history">
            <div className="muted" style={{ fontSize: 14 }}>최근 한 달 이력</div>
            {history === null && <div className="muted" style={{ fontSize: 14 }}>불러오는 중…</div>}
            {history?.length === 0 && <div className="muted" style={{ fontSize: 14 }}>한 달 안에 변동이 없습니다.</div>}
            {history?.map((t) => (
              <div key={t.id} className="history-row">
                <span className="muted when">{t.createdAt.slice(5, 10).replace('-', '/')} {t.createdAt.slice(11, 16)}</span>
                <span className="grow">
                  {t.reason === 'CHARGE' && t.orderNo != null ? '잔돈 충전' : (REASON[t.reason] ?? t.reason)}
                  {t.orderNo != null && <span className="muted"> · 주문 #{t.orderNo}</span>}
                  {t.freeDelta !== 0 && <span className="muted"> · 무료잔 {t.freeDelta > 0 ? '+' : ''}{t.freeDelta}</span>}
                </span>
                <b className={t.delta > 0 ? 'plus' : t.delta < 0 ? 'minus' : ''}>{t.delta === 0 ? '' : (t.delta > 0 ? '+' : '') + won(t.delta)}</b>
                <span className="muted after">{won(t.balanceAfter)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {candidates && candidates.length === 0 && (
        <div className="card">
          <b style={{ fontSize: 18 }}>"{name.trim()}"{last4.length === 4 && ` (${last4})`} 쿠폰이 없습니다. 새로 등록할까요?</b>
          {!phoneValid && (
            <div className="error" style={{ fontSize: 14 }}>위 전화번호 칸에 휴대폰 번호 13자리(010-1234-5678)를 다 넣어야 등록할 수 있어요. 문자를 보내려면 번호가 정확해야 해요.</div>
          )}
          <div className="muted" style={{ fontSize: 14 }}>{tierHint(preset)}</div>
          <AmountPicker preset={preset} amount={amount} onAmount={setAmount} busy={busy || !phoneValid}
            label={(n) => `${won(n)} 등록`} onSubmit={(n) => void register(n)} custom={customAmount} />
        </div>
      )}
    </div>
  )
}

const REASON: Record<string, string> = { CHARGE: '충전', USE: '사용', REFUND: '환불', ADJUST: '정정' }

function formatPhone(digits: string): string {
  if (digits.length === 11) return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
  if (digits.length === 10) return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
  return digits
}

/** "20,000원 → 무료 1잔 · 30,000원 → 무료 2잔" */
function tierHint(p: ChargePreset): string {
  return p.tiers.map((t) => `${won(t.amount)} → 무료 ${t.freeDrinks}잔`).join(' · ')
}

/** 이 금액을 충전하면 적립되는 무료잔 (서버 ChargePolicy 와 같은 규칙) */
function freeDrinksFor(p: ChargePreset, amount: number): number {
  let free = 0
  for (const t of p.tiers) if (amount >= t.amount) free = t.freeDrinks
  return free
}

interface PickerProps {
  preset: ChargePreset
  amount: string
  custom: number
  busy: boolean
  label: (n: number) => string
  onAmount: (v: string) => void
  onSubmit: (n: number) => void
}

function AmountPicker({ preset, amount, custom, busy, label, onAmount, onSubmit }: PickerProps) {
  const ok = isChargeAmount(custom, preset.max)
  const customFree = freeDrinksFor(preset, custom)
  return (
    <div className="stack">
      <div className="row">
        {preset.tiers.map((t) => (
          <button key={t.amount} className="btn big primary grow" disabled={busy} onClick={() => onSubmit(t.amount)}>{label(t.amount)}</button>
        ))}
      </div>
      <div className="row">
        <input className="text-input grow" inputMode="numeric" placeholder="다른 금액" value={amount} maxLength={6}
          onChange={(e) => onAmount(e.target.value.replace(/[^0-9]/g, ''))} />
        <button className="btn" disabled={busy || !ok} onClick={() => onSubmit(custom)}>{custom > 0 ? label(custom) : '확인'}</button>
      </div>
      {custom > 0 && !ok && <div className="error" style={{ fontSize: 13 }}>한 번에 {won(preset.max)}까지 충전할 수 있어요.</div>}
      {custom > 0 && ok && <div className="muted" style={{ fontSize: 13 }}>{customFree > 0 ? `무료 ${customFree}잔 적립` : '무료잔 적립 없음'}</div>}
    </div>
  )
}
