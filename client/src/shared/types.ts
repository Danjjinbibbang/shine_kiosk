// 서버 DTO 와 1:1 로 맞춘 타입. 서버 쪽 OrderDtos / MenuDtos 를 바꾸면 여기도 같이 고친다.

export type ReceiveType = 'STORE' | 'DELIVERY'
export type PayMethod = 'TRANSFER' | 'COUPON' | 'CASH'
export type OrderStatus = 'PENDING' | 'DONE' | 'CANCELED'

export interface MenuVariant {
  id: number
  label: string | null
  price: number
}

export interface MenuItem {
  id: number
  name: string
  category: string
  variants: MenuVariant[]
}

export interface Place {
  id: number
  floor: number
  name: string
}

export interface FloorGroup {
  floor: number
  places: Place[]
}

// ── 스태프 설정 화면 ──────────────────────────────────

export interface AdminVariant {
  id: number | null
  label: string | null
  price: number
  available: boolean
}

export interface AdminItem {
  id: number
  name: string
  category: string
  sortOrder: number
  available: boolean
  variants: AdminVariant[]
}

export interface SaveItemRequest {
  name: string
  category: string
  available: boolean
  variants: AdminVariant[]
}

export interface AdminPlace {
  id: number
  floor: number
  name: string
  sortOrder: number
  active: boolean
}

export interface Coupon {
  id: number
  name: string
  phoneLast4: string | null
  balance: number
  /** 남은 무료 1잔 개수 */
  freeDrinks: number
}

export type LookupStatus = 'FOUND' | 'NOT_FOUND' | 'NEED_PHONE'

export interface LookupResult {
  status: LookupStatus
  coupon: Coupon | null
  candidateCount: number
}

export interface LineRequest {
  variantId: number
  quantity: number
}

export interface CouponPreview {
  total: number
  balance: number
  freeDrinks: number
  useFreeDrink: boolean
  freeAmount: number
  freeItemName: string | null
  couponAmount: number
  remainder: number
  balanceAfter: number
  freeDrinksAfter: number
}

export interface CreateOrderRequest {
  customerName: string
  receiveType: ReceiveType
  placeId?: number | null
  payMethod: PayMethod
  couponId?: number | null
  useFreeDrink?: boolean
  remainderMethod?: PayMethod | null
  lines: LineRequest[]
  memo?: string | null
}

export interface UpdateOrderRequest {
  customerName: string
  receiveType: ReceiveType
  placeId?: number | null
  lines: LineRequest[]
  memo?: string | null
}

export interface OrderLine {
  id: number
  variantId: number | null
  menuName: string
  variantLabel: string | null
  unitPrice: number
  quantity: number
}

export interface Order {
  id: number
  orderDate: string
  orderNo: number
  customerName: string
  receiveType: ReceiveType
  placeId: number | null
  placeName: string | null
  totalAmount: number
  payMethod: PayMethod
  remainderMethod: PayMethod | null
  couponId: number | null
  couponAmount: number
  freeAmount: number
  freeItemName: string | null
  cashAmount: number
  transferAmount: number
  status: OrderStatus
  memo: string | null
  createdAt: string
  completedAt: string | null
  lines: OrderLine[]
}

export interface DailySummary {
  date: string
  orderCount: number
  totalAmount: number
  cashAmount: number
  transferAmount: number
  couponAmount: number
  freeAmount: number
}

export const PAY_LABEL: Record<PayMethod, string> = {
  TRANSFER: '계좌이체',
  COUPON: '쿠폰',
  CASH: '현금',
}

export function won(n: number): string {
  return n.toLocaleString('ko-KR') + '원'
}
