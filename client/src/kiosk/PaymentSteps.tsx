import { useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { Coupon, CouponPreview, LineRequest } from '../shared/types'
import { won } from '../shared/types'
import { NamePicker } from './NamePicker'

const TRANSFER_SECONDS = 12

/** 계좌번호와 금액을 보여주고, 확인 버튼 없이 시간이 지나면 다음으로 넘어간다 (신뢰 기반). */
export function TransferStep({ total, onNext }: { total: number; onNext: () => void }) {
  const [account, setAccount] = useState<string>('')
  const [left, setLeft] = useState(TRANSFER_SECONDS)

  useEffect(() => {
    api.paymentInfo().then((r) => setAccount(r.bankAccount)).catch(() => setAccount('스태프에게 문의해 주세요'))
  }, [])

  useEffect(() => {
    if (left <= 0) {
      onNext()
      return
    }
    const t = window.setTimeout(() => setLeft((s) => s - 1), 1000)
    return () => window.clearTimeout(t)
  }, [left, onNext])

  return (
    <div className="hero">
      <div className="title">아래 계좌로 보내 주세요</div>
      <div className="account">{account || '…'}</div>
      <div className="amount">{won(total)}</div>
      <div className="sub">{left}초 후 다음으로 넘어갑니다</div>
      <div style={{ marginTop: 24 }}>
        <button className="btn big primary" onClick={onNext}>보냈어요 ›</button>
      </div>
    </div>
  )
}

interface CouponProps {
  lines: LineRequest[]
  total: number
  submitting: boolean
  onDone: (coupon: Coupon, preview: CouponPreview, remainderMethod?: 'CASH' | 'TRANSFER') => void
}

/**
 * 이름으로 쿠폰 조회 → (동명이인일 때만 전화 뒤 4자리) → 잔액/무료 1잔 보여주고 확정.
 * 무료 1잔이 남아 있으면 체크해서 이번 주문의 가장 비싼 한 잔을 무료로 뺄 수 있다.
 * 잔액이 모자라면 나머지를 현금/계좌이체 중 고른다.
 */
export function CouponStep({ lines, total, submitting, onDone }: CouponProps) {
  const [name, setName] = useState<string | null>(null)
  const [candidates, setCandidates] = useState<{ id: number; phoneLast4: string | null }[] | null>(null)
  const [coupon, setCoupon] = useState<Coupon | null>(null)
  const [preview, setPreview] = useState<CouponPreview | null>(null)
  const [useFree, setUseFree] = useState(false)
  const [account, setAccount] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const loadPreview = async (c: Coupon, free: boolean) => {
    const p = await api.couponPreview(c.id, free, lines)
    setPreview(p)
    setUseFree(p.useFreeDrink)
    if (p.remainder > 0 && !account) {
      api.paymentInfo().then((i) => setAccount(i.bankAccount)).catch(() => {})
    }
  }

  const toggleFree = async () => {
    if (!coupon) return
    setBusy(true)
    try {
      await loadPreview(coupon, !useFree)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '계산에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const lookup = async (n: string, phoneLast4?: string) => {
    setBusy(true)
    setError(null)
    try {
      const r = await api.lookupCoupon(n, phoneLast4)
      if (r.status === 'NEED_PHONE') {
        // 같은 이름이 여럿이면 번호를 치게 하지 않고, 뒤 4자리 목록에서 자기 것을 고른다
        setName(n)
        setCandidates(r.candidates ?? [])
        return
      }
      if (r.status !== 'FOUND' || !r.coupon) {
        setError(phoneLast4
          ? `전화번호 뒤 4자리(${phoneLast4})가 맞지 않습니다. 스태프에게 확인해 주세요.`
          : `"${n}" 이름의 쿠폰이 없습니다. 스태프에게 확인해 주세요.`)
        return
      }
      setName(n)
      setCoupon(r.coupon)
      await loadPreview(r.coupon, false)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '조회에 실패했습니다.')
    } finally {
      setBusy(false)
    }
  }

  if (coupon && preview) {
    return (
      <div className="stack">
        {error && <div className="error">{error}</div>}
        <div className="total-box">
          <span className="label">{coupon.name}님 쿠폰 잔액</span>
          <span className="amount">{won(preview.balance)}</span>
        </div>

        {preview.freeDrinks > 0 ? (
          <button className={'btn big free-toggle' + (useFree ? ' selected' : '')} disabled={busy} onClick={() => void toggleFree()}>
            <span className="check">{useFree ? '☑' : '☐'}</span>
            <span className="grow" style={{ textAlign: 'left' }}>
              무료 1잔 쓰기 <small className="muted">({preview.freeDrinks}잔 남음)</small>
              {useFree && preview.freeItemName && (
                <div className="free-detail">{preview.freeItemName} {won(preview.freeAmount)} 무료</div>
              )}
            </span>
          </button>
        ) : (
          <div className="muted center">남은 무료 1잔이 없습니다</div>
        )}

        <div className="card">
          <div className="row between"><span>주문 금액</span><b>{won(preview.total)}</b></div>
          {preview.useFreeDrink && (
            <div className="row between"><span>무료 1잔 ({preview.freeItemName})</span><b>− {won(preview.freeAmount)}</b></div>
          )}
          <div className="row between"><span>잔액에서 차감</span><b>− {won(preview.couponAmount)}</b></div>
          <div className="row between muted"><span>차감 후 잔액</span><span>{won(preview.balanceAfter)}{preview.useFreeDrink && ` · 무료 ${preview.freeDrinksAfter}잔`}</span></div>
          {preview.remainder > 0 && (
            <div className="row between" style={{ fontSize: 26 }}>
              <span>추가로 내실 금액</span><b>{won(preview.remainder)}</b>
            </div>
          )}
        </div>

        {preview.remainder === 0 ? (
          <button className="btn huge primary" disabled={submitting} onClick={() => onDone(coupon, preview)}>
            {submitting ? '접수 중…' : '쿠폰으로 결제 ✓'}
          </button>
        ) : (
          <div className="stack">
            <div className="muted center">나머지 {won(preview.remainder)}은 어떻게 내시나요?</div>
            {account && <div className="hero" style={{ padding: 0 }}><div className="account">{account}</div></div>}
            <div className="kiosk-foot">
              <button className="btn huge" disabled={submitting} onClick={() => onDone(coupon, preview, 'TRANSFER')}>
                🏦 계좌이체
              </button>
              <button className="btn huge" disabled={submitting} onClick={() => onDone(coupon, preview, 'CASH')}>
                💵 현금
              </button>
            </div>
          </div>
        )}
      </div>
    )
  }

  if (candidates && name) {
    return (
      <div className="stack">
        {error && <div className="error">{error}</div>}
        <div className="muted center">"{name}" 이름의 쿠폰이 {candidates.length}개 있어요. 본인 전화번호를 골라 주세요.</div>
        <div className="stack">
          {candidates.map((c) => (
            <button key={c.id} className="btn huge" disabled={busy} onClick={() => void lookup(name, c.phoneLast4 ?? '')}>
              📱 010-****-{c.phoneLast4 ?? '????'}
            </button>
          ))}
        </div>
        <div className="kiosk-foot">
          <button className="btn big" onClick={() => { setCandidates(null); setError(null) }}>‹ 다른 이름</button>
        </div>
      </div>
    )
  }

  return (
    <div className="stack">
      <div className="total-box">
        <span className="label">결제 금액</span>
        <span className="amount">{won(total)}</span>
      </div>
      {error && <div className="error">{error}</div>}
      <NamePicker title="쿠폰 주인 이름을 골라 주세요" confirmLabel="쿠폰 조회" disabled={busy}
        onSelect={(n) => void lookup(n)} />
    </div>
  )
}

/**
 * 총액을 보고 실제로 낼 법한 금액들 = 각 지폐 단위로 올린 값.
 *  예) 3,500원 → 4,000(천원권) / 5,000 / 10,000 / 50,000
 *      15,000원 → 20,000(만원 두 장) / 50,000
 *      10,000원 → 50,000 (만원 한 장이면 '딱 맞게')
 * 총액과 같은 값은 '딱 맞게' 버튼이 대신한다.
 */
export function cashOptions(total: number): number[] {
  const up = (unit: number) => Math.ceil(total / unit) * unit
  const candidates = [up(1000), up(5000), up(10000), up(50000)]
  return [...new Set(candidates)].filter((n) => n > total).sort((a, b) => a - b).slice(0, 4)
}

/** 총액을 보여주고, 낸 돈을 고른다. 거스름돈 안내는 서버가 낸 돈과 지금 금액으로 계산해 스태프 카드에 보여준다. */
export function CashStep({ total, onNext }: { total: number; onNext: (cashGiven: number) => void }) {
  const [given, setGiven] = useState<number | null>(null)
  const change = given === null ? 0 : given - total
  const options = cashOptions(total)

  return (
    <div className="stack">
      <div className="hero" style={{ padding: '10px 0 0' }}>
        <div className="title">현금은 바구니에 넣어주세요</div>
        <div className="amount">{won(total)}</div>
      </div>
      <div className="muted center">얼마를 내시나요?</div>
      <div className="grid">
        <button className={'btn big' + (given === total ? ' selected' : '')} onClick={() => setGiven(total)}>딱 맞게</button>
        {options.map((c) => (
          <button key={c} className={'btn big' + (given === c ? ' selected' : '')} onClick={() => setGiven(c)}>
            {won(c)}
          </button>
        ))}
      </div>
      {given !== null && (
        <div className="total-box">
          <span className="label">거스름돈</span>
          <span className="amount">{won(change)}</span>
        </div>
      )}
      <button className="btn huge primary" disabled={given === null}
        onClick={() => onNext(given!)}>
        다음 ›
      </button>
    </div>
  )
}

export function Keypad({ value, maxLength, onChange }: { value: string; maxLength: number; onChange: (v: string) => void }) {
  const press = (k: string) => {
    if (k === '⌫') onChange(value.slice(0, -1))
    else if (value.length < maxLength) onChange(value + k)
  }
  return (
    <div className="keypad">
      {['1', '2', '3', '4', '5', '6', '7', '8', '9', '', '0', '⌫'].map((k, i) => (
        k ? <button key={i} className="btn" onClick={() => press(k)}>{k}</button> : <span key={i} />
      ))}
    </div>
  )
}
