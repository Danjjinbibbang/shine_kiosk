import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api, ApiError } from '../shared/api'
import type { Coupon, CouponPreview, CreateOrderRequest, MenuOption, Order, PayMethod, Place, ReceiveType } from '../shared/types'
import { MenuStep } from './MenuStep'
import { CartStep } from './CartStep'
import { PaymentStep, PlaceStep, ReceiveStep } from './ChoiceSteps'
import { CashStep, CouponStep, TransferStep } from './PaymentSteps'
import { NameStep } from './NameStep'
import { DoneStep } from './DoneStep'

export type Step = 'menu' | 'cart' | 'receive' | 'place' | 'payment' | 'transfer' | 'coupon' | 'cash' | 'name' | 'done'

export interface CartLine {
  variantId: number
  itemName: string
  category: string
  label: string | null
  /** 기본가 (옵션 제외) */
  price: number
  qty: number
  /** 붙인 옵션. 같은 메뉴라도 옵션이 다르면 다른 줄 */
  options: MenuOption[]
}

/** 옵션 가격까지 더한 한 잔 값 */
export function lineUnitPrice(l: CartLine): number {
  return l.price + l.options.reduce((s, o) => s + o.price, 0)
}

/** 같은 줄인지 판단하는 키: 메뉴 + 옵션 조합 */
export function lineKey(variantId: number, optionIds: number[]): string {
  return variantId + ':' + [...optionIds].sort((a, b) => a - b).join(',')
}

export interface Draft {
  cart: CartLine[]
  receiveType?: ReceiveType
  place?: Place
  payMethod?: PayMethod
  coupon?: Coupon
  preview?: CouponPreview
  remainderMethod?: 'CASH' | 'TRANSFER'
  memo?: string
  customerName?: string
}

const EMPTY: Draft = { cart: [] }
const IDLE_RESET_MS = 120_000

const STEP_TITLE: Record<Step, string> = {
  menu: '메뉴를 골라 주세요',
  cart: '주문 내용을 확인해 주세요',
  receive: '어디서 받으시나요?',
  place: '어디로 갖다 드릴까요?',
  payment: '어떻게 결제하시나요?',
  transfer: '계좌이체',
  coupon: '쿠폰',
  cash: '현금',
  name: '이름을 골라 주세요',
  done: '주문 완료',
}

export function KioskApp() {
  const [step, setStep] = useState<Step>('menu')
  const [draft, setDraft] = useState<Draft>(EMPTY)
  const [history, setHistory] = useState<Step[]>([])
  const [order, setOrder] = useState<Order | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const total = useMemo(() => draft.cart.reduce((s, l) => s + lineUnitPrice(l) * l.qty, 0), [draft.cart])
  const lines = useMemo(() => draft.cart.map((l) => ({
    variantId: l.variantId, quantity: l.qty, optionIds: l.options.map((o) => o.id),
  })), [draft.cart])

  const reset = useCallback(() => {
    setStep('menu')
    setDraft(EMPTY)
    setHistory([])
    setOrder(null)
    setError(null)
  }, [])

  const go = useCallback((next: Step) => {
    setError(null)
    setHistory((h) => [...h, step])
    setStep(next)
  }, [step])

  const back = useCallback(() => {
    setError(null)
    setHistory((h) => {
      const prev = h[h.length - 1]
      if (prev) setStep(prev)
      return h.slice(0, -1)
    })
  }, [])

  // 손을 뗀 채 오래 두면 처음 화면으로. 다음 분이 남의 장바구니를 보지 않게.
  const idleTimer = useRef<number | undefined>(undefined)
  useEffect(() => {
    const arm = () => {
      window.clearTimeout(idleTimer.current)
      idleTimer.current = window.setTimeout(reset, IDLE_RESET_MS)
    }
    arm()
    window.addEventListener('pointerdown', arm)
    return () => {
      window.removeEventListener('pointerdown', arm)
      window.clearTimeout(idleTimer.current)
    }
  }, [reset, step])

  const update = (patch: Partial<Draft>) => setDraft((d) => ({ ...d, ...patch }))

  const submit = useCallback(async (customerName: string, extra: Partial<Draft> = {}) => {
    const d = { ...draft, ...extra }
    if (!d.receiveType || !d.payMethod) return
    setSubmitting(true)
    setError(null)
    const body: CreateOrderRequest = {
      customerName,
      receiveType: d.receiveType,
      placeId: d.place?.id ?? null,
      payMethod: d.payMethod,
      couponId: d.coupon?.id ?? null,
      useFreeDrink: d.preview?.useFreeDrink ?? false,
      remainderMethod: d.remainderMethod ?? null,
      lines,
      memo: d.memo ?? null,
    }
    try {
      const created = await api.createOrder(body)
      setOrder(created)
      setDraft((cur) => ({ ...cur, ...extra, customerName }))
      setHistory([])
      setStep('done')
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '주문이 접수되지 않았습니다. 스태프를 불러 주세요.')
    } finally {
      setSubmitting(false)
    }
  }, [draft, lines])

  // 결제 수단별로 다음 화면이 갈린다.
  const afterPayment = (method: PayMethod) => {
    update({ payMethod: method, coupon: undefined, preview: undefined, remainderMethod: undefined, memo: undefined })
    go(method === 'TRANSFER' ? 'transfer' : method === 'COUPON' ? 'coupon' : 'cash')
  }

  const showBack = step !== 'menu' && step !== 'done' && history.length > 0

  return (
    <div className="kiosk">
      <header className="kiosk-head">
        {showBack && (
          <button className="btn ghost" onClick={back}>‹ 이전</button>
        )}
        <h1>{STEP_TITLE[step]}</h1>
        {step !== 'menu' && step !== 'done' && (
          <button className="btn ghost" onClick={reset}>처음으로</button>
        )}
      </header>

      {error && <div className="error">{error}</div>}

      <main className="kiosk-body">
        {step === 'menu' && (
          <MenuStep cart={draft.cart} total={total}
            onChange={(cart) => update({ cart })}
            onNext={() => go('cart')} />
        )}
        {step === 'cart' && (
          <CartStep cart={draft.cart} total={total}
            onChange={(cart) => update({ cart })}
            onAddMore={back}
            onNext={() => go('receive')} />
        )}
        {step === 'receive' && (
          <ReceiveStep onSelect={(t) => {
            update({ receiveType: t, place: t === 'STORE' ? undefined : draft.place })
            go(t === 'DELIVERY' ? 'place' : 'payment')
          }} />
        )}
        {step === 'place' && (
          <PlaceStep onSelect={(p) => { update({ place: p }); go('payment') }} />
        )}
        {step === 'payment' && (
          <PaymentStep total={total} onSelect={afterPayment} />
        )}
        {step === 'transfer' && (
          <TransferStep total={total} onNext={() => go('name')} />
        )}
        {step === 'coupon' && (
          <CouponStep lines={lines} total={total} submitting={submitting}
            onDone={(coupon, preview, remainderMethod) => {
              // 쿠폰 주인 이름을 이미 아니까 이름 화면은 건너뛴다.
              void submit(coupon.name, { coupon, preview, remainderMethod })
            }} />
        )}
        {step === 'cash' && (
          <CashStep total={total} onNext={(memo) => { update({ memo }); go('name') }} />
        )}
        {step === 'name' && (
          <NameStep submitting={submitting} onSelect={(name) => void submit(name)} />
        )}
        {step === 'done' && order && (
          <DoneStep order={order} onReset={reset} />
        )}
      </main>
    </div>
  )
}
