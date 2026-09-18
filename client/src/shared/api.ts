import type {
  Coupon, CouponPreview, CreateOrderRequest, DailySummary, FloorGroup, LineRequest,
  LookupResult, MenuItem, Order, OrderStatus, UpdateOrderRequest,
} from './types'

const STAFF_TOKEN_KEY = 'shine-kiosk.staffToken'

export class ApiError extends Error {
  constructor(message: string, readonly status: number) {
    super(message)
  }
}

export function getStaffToken(): string | null {
  try {
    return localStorage.getItem(STAFF_TOKEN_KEY)
  } catch {
    return null
  }
}

export function setStaffToken(token: string | null) {
  try {
    if (token) localStorage.setItem(STAFF_TOKEN_KEY, token)
    else localStorage.removeItem(STAFF_TOKEN_KEY)
  } catch {
    /* 사생활 보호 모드 등에서 저장 불가 — 그냥 세션 동안만 쓴다 */
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' }
  const token = getStaffToken()
  if (token) headers['X-Staff-Token'] = token

  let res: Response
  try {
    res = await fetch(path, { ...init, headers: { ...headers, ...(init.headers as Record<string, string>) } })
  } catch {
    throw new ApiError('서버와 연결되지 않습니다. 와이파이를 확인해 주세요.', 0)
  }
  if (res.status === 401) {
    setStaffToken(null)
    throw new ApiError('PIN 을 다시 입력해 주세요.', 401)
  }
  if (!res.ok) {
    let message = '문제가 생겼습니다. 봉사자를 불러 주세요.'
    try {
      const body = await res.json()
      if (body?.message) message = body.message
    } catch { /* 본문 없음 */ }
    throw new ApiError(message, res.status)
  }
  if (res.status === 204 || res.headers.get('content-length') === '0') {
    return undefined as T
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

const post = <T>(path: string, body?: unknown) =>
  request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })

// ── 공개 (고객 키오스크) ──────────────────────────────────
export const api = {
  menu: () => request<MenuItem[]>('/api/menu'),
  places: () => request<FloorGroup[]>('/api/places'),
  regulars: () => request<string[]>('/api/customers/regulars'),
  paymentInfo: () => request<{ bankAccount: string }>('/api/orders/payment-info'),
  lookupCoupon: (name: string, phoneLast4?: string) =>
    post<LookupResult>('/api/coupons/lookup', { name, phoneLast4: phoneLast4 || null }),
  couponPreview: (couponId: number, useFreeDrink: boolean, lines: LineRequest[]) =>
    post<CouponPreview>('/api/orders/coupon-preview', { couponId, useFreeDrink, lines }),
  createOrder: (body: CreateOrderRequest) => post<Order>('/api/orders', body),

  // ── 스태프 (PIN 토큰 필요) ────────────────────────────
  staffLogin: (pin: string) => post<{ token: string }>('/api/staff-auth/login', { pin }),
  staffCheck: () => request<{ valid: boolean }>('/api/staff-auth/check'),
  staffLogout: () => post<void>('/api/staff-auth/logout'),
  orders: (status: OrderStatus) => request<Order[]>(`/api/staff/orders?status=${status}`),
  summary: () => request<DailySummary>('/api/staff/orders/summary'),
  updateOrder: (id: number, body: UpdateOrderRequest) =>
    request<Order>(`/api/staff/orders/${id}`, { method: 'PUT', body: JSON.stringify(body) }),
  doneOrder: (id: number) => post<void>(`/api/staff/orders/${id}/done`),
  reopenOrder: (id: number) => post<void>(`/api/staff/orders/${id}/reopen`),
  cancelOrder: (id: number) => post<void>(`/api/staff/orders/${id}/cancel`),
  registerCoupon: (name: string, phoneLast4: string | null, amount: number) =>
    post<Coupon>('/api/staff/coupons', { name, phoneLast4, amount }),
  chargeCoupon: (id: number, amount: number) => post<Coupon>(`/api/staff/coupons/${id}/charge`, { amount }),
  adjustCoupon: (id: number, balance: number, freeDrinks: number) =>
    post<Coupon>(`/api/staff/coupons/${id}/adjust`, { balance, freeDrinks }),
  deleteCoupon: (id: number) => request<void>(`/api/staff/coupons/${id}`, { method: 'DELETE' }),
  couponPreset: () => request<{ amount: number }>('/api/staff/coupons/preset'),
}

/** 서버가 ORDERS_CHANGED 를 뿌리면 onChange 를 부른다. 끊기면 알아서 다시 붙는다. */
export function subscribeOrders(onChange: () => void): () => void {
  let socket: WebSocket | null = null
  let closed = false
  let retryMs = 1000

  const connect = () => {
    if (closed) return
    const proto = location.protocol === 'https:' ? 'wss' : 'ws'
    socket = new WebSocket(`${proto}://${location.host}/ws`)
    socket.onopen = () => {
      retryMs = 1000
      onChange() // 끊겨 있던 동안 놓친 변경을 따라잡는다
    }
    socket.onmessage = (e) => {
      try {
        if (JSON.parse(e.data).type === 'ORDERS_CHANGED') onChange()
      } catch { /* 무시 */ }
    }
    socket.onclose = () => {
      if (closed) return
      setTimeout(connect, retryMs)
      retryMs = Math.min(retryMs * 2, 15000)
    }
    socket.onerror = () => socket?.close()
  }
  connect()

  return () => {
    closed = true
    socket?.close()
  }
}
