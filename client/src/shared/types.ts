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

export interface Coupon {
  id: number
  name: string
  phoneLast4: string | null
  balance: number
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
  couponAmount: number
  remainder: number
  balanceAfter: number
}

export interface CreateOrderRequest {
  customerName: string
  receiveType: ReceiveType
  placeId?: number | null
  payMethod: PayMethod
  couponId?: number | null
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
}

export const PAY_LABEL: Record<PayMethod, string> = {
  TRANSFER: '계좌이체',
  COUPON: '쿠폰',
  CASH: '현금',
}

export function won(n: number): string {
  return n.toLocaleString('ko-KR') + '원'
}
