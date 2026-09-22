import type { AdminItem, AdminOption, AdminPlace, Category, ChargePreset, Coupon, CouponPreview, CouponTx, CreateOrderRequest, DailySummary, DayReport, FloorGroup, LineRequest, LookupResult, MenuItem, MenuOption, Order, OrderStatus, SaveItemRequest, UpdateOrderRequest } from './types'

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
    let message = '문제가 생겼습니다. 스태프를 불러 주세요.'
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
const put = <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) })
const del = <T>(path: string) => request<T>(path, { method: 'DELETE' })

// ── 공개 (고객 키오스크) ──────────────────────────────────
export const api = {
  menu: () => request<MenuItem[]>('/api/menu'),
  menuCategories: () => request<string[]>('/api/menu/categories'),
  menuOptions: () => request<MenuOption[]>('/api/menu/options'),
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
  updateOrder: (id: number, body: UpdateOrderRequest) => put<Order>(`/api/staff/orders/${id}`, body),
  doneOrder: (id: number) => post<void>(`/api/staff/orders/${id}/done`),
  reopenOrder: (id: number) => post<void>(`/api/staff/orders/${id}/reopen`),
  /** 돌려줄 돈: couponId 로 쿠폰에 넣기(없으면 현금). 더 받을 돈: method 로 현금/이체/쿠폰(couponId) 중 어떻게 받았는지 */
  settleOrder: (id: number, opts: { couponId?: number | null; method?: 'CASH' | 'TRANSFER' | 'COUPON' } = {}) =>
    post<void>(`/api/staff/orders/${id}/settle`, { couponId: opts.couponId ?? null, method: opts.method ?? null }),
  couponsByName: (name: string) => request<Coupon[]>(`/api/staff/coupons?name=${encodeURIComponent(name)}`),
  cancelOrder: (id: number, refundToCouponId: number | null = null) => post<void>(`/api/staff/orders/${id}/cancel`, { refundToCouponId }),
  staffLookupCoupon: (name: string, phoneLast4?: string) =>
    post<LookupResult>('/api/staff/coupons/lookup', { name, phoneLast4: phoneLast4 || null }),
  getCoupon: (id: number) => request<Coupon>(`/api/staff/coupons/${id}`),
  couponHistory: (id: number) => request<CouponTx[]>(`/api/staff/coupons/${id}/history`),
  registerCoupon: (name: string, phone: string | null, amount: number) =>
    post<Coupon>('/api/staff/coupons', { name, phone, amount }),
  updateCouponPhone: (id: number, phone: string) => put<Coupon>(`/api/staff/coupons/${id}/phone`, { phone }),
  renameCoupon: (id: number, name: string) => put<Coupon>(`/api/staff/coupons/${id}/name`, { name }),
  chargeCoupon: (id: number, amount: number) => post<Coupon>(`/api/staff/coupons/${id}/charge`, { amount }),
  adjustCoupon: (id: number, balance: number, freeDrinks: number) =>
    post<Coupon>(`/api/staff/coupons/${id}/adjust`, { balance, freeDrinks }),
  deleteCoupon: (id: number) => del<void>(`/api/staff/coupons/${id}`),
  couponPreset: () => request<ChargePreset>('/api/staff/coupons/preset'),

  // ── 스태프 설정 (메뉴 / 장소) ────────────────────────
  categories: () => request<Category[]>('/api/staff/categories'),
  createCategory: (name: string) => post<Category>('/api/staff/categories', { name }),
  renameCategory: (id: number, name: string) => put<Category>(`/api/staff/categories/${id}`, { name }),
  deleteCategory: (id: number) => del<void>(`/api/staff/categories/${id}`),
  reorderCategories: (ids: number[]) => put<Category[]>('/api/staff/categories/order', { ids }),
  adminMenu: () => request<AdminItem[]>('/api/staff/menu'),
  createMenuItem: (body: SaveItemRequest) => post<AdminItem>('/api/staff/menu', body),
  updateMenuItem: (id: number, body: SaveItemRequest) => put<AdminItem>(`/api/staff/menu/${id}`, body),
  setMenuAvailable: (id: number, available: boolean) => put<AdminItem>(`/api/staff/menu/${id}/available`, { available }),
  deleteMenuItem: (id: number) => del<void>(`/api/staff/menu/${id}`),
  reorderMenu: (ids: number[]) => put<AdminItem[]>('/api/staff/menu/order', { ids }),
  adminOptions: () => request<AdminOption[]>('/api/staff/menu/options'),
  createOption: (body: { name: string; price: number; category: string; group: string | null; available: boolean }) => post<AdminOption>('/api/staff/menu/options', body),
  updateOption: (id: number, body: { name: string; price: number; category: string; group: string | null; available: boolean }) => put<AdminOption>(`/api/staff/menu/options/${id}`, body),
  deleteOption: (id: number) => del<void>(`/api/staff/menu/options/${id}`),
  reportDays: () => request<DayReport[]>('/api/staff/reports/days'),
  reportOrders: (date: string) => request<Order[]>(`/api/staff/reports/orders?date=${date}`),
  adminPlaces: () => request<AdminPlace[]>('/api/staff/places'),
  createPlace: (body: { floor: number; name: string; active: boolean }) => post<AdminPlace>('/api/staff/places', body),
  updatePlace: (id: number, body: { floor: number; name: string; active: boolean }) => put<AdminPlace>(`/api/staff/places/${id}`, body),
  deletePlace: (id: number) => del<void>(`/api/staff/places/${id}`),
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
