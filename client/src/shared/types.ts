// 서버 DTO 와 1:1 로 맞춘 타입. 서버 쪽 OrderDtos / MenuDtos 를 바꾸면 여기도 같이 고친다.
// 주의: 서버는 null 인 필드를 JSON 에서 아예 뺀다 (non_null). 그래서 `x | null` 필드는 실제로 undefined 로 올 수 있으니
// 비교는 항상 `== null` / `!= null` 로 한다.

export type ReceiveType = 'STORE' | 'DELIVERY'
/** NONE = 사역자 무료로 낼 금액이 0 이라 결제 없음 */
export type PayMethod = 'TRANSFER' | 'COUPON' | 'CASH' | 'NONE'
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

/** 잔 단위 옵션 (샷 추가 / 연하게). category 가 같은 메뉴에만 붙는다. */
export interface MenuOption {
  id: number
  name: string
  price: number
  category: string
  /** 같은 그룹은 한 잔에 하나만 (샷 추가/연하게 = '농도'). null 이면 자유 조합 */
  group: string | null
}


export interface AdminOption extends MenuOption {
  sortOrder: number
  available: boolean
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

/** 메뉴 카테고리. 키오스크 순서 = 이 순서 */
export interface Category {
  id: number
  name: string
  sortOrder: number
  itemCount: number
}

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
  /** 전체 번호 (숫자만). 스태프 API 에서만 내려온다 */
  phone?: string | null
  phoneLast4: string | null
  balance: number
  /** 남은 무료 1잔 개수 */
  freeDrinks: number
}

/** 쿠폰 잔액 변동 이력 한 줄 */
export interface CouponTx {
  id: number
  createdAt: string
  reason: 'CHARGE' | 'USE' | 'REFUND' | 'ADJUST' | string
  delta: number
  freeDelta: number
  balanceAfter: number
  orderId: number | null
  orderNo: number | null
  orderDate: string | null
}

export type LookupStatus = 'FOUND' | 'NOT_FOUND' | 'NEED_PHONE'

export interface LookupResult {
  status: LookupStatus
  coupon: Coupon | null
  candidateCount: number
  /** 동명이인일 때 고를 후보 (뒤 4자리만) */
  candidates?: { id: number; phoneLast4: string | null }[]
}

export interface LineRequest {
  variantId: number
  quantity: number
  optionIds?: number[]
  /** 이 줄에서 사역자 무료로 처리할 잔 수 */
  staffFreeQty?: number
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
  /** 손님이 낸 현금. 거스름돈 안내(payNote)는 서버가 지금 금액 기준으로 계산한다 */
  cashGiven?: number | null
  /** 키오스크가 만든 요청 번호. 와이파이가 끊겨 다시 보내도 한 번만 접수된다 */
  clientRequestId?: string
}

export interface UpdateOrderRequest {
  customerName: string
  receiveType: ReceiveType
  placeId?: number | null
  lines: LineRequest[]
  memo?: string | null
}

export interface OrderLineOption {
  optionId: number | null
  name: string
  price: number
}

export interface OrderLine {
  id: number
  variantId: number | null
  menuName: string
  variantLabel: string | null
  /** 옵션 가격까지 더한 한 잔 값 */
  unitPrice: number
  quantity: number
  /** 이 중 사역자 무료 잔 수 */
  staffFreeQty: number
  options: OrderLineOption[]
}

export interface Order {
  id: number
  orderDate: string
  orderNo: number
  customerName: string
  receiveType: ReceiveType
  placeId: number | null
  placeName: string | null
  /** 사역자 무료를 뺀 실제로 받을 금액 */
  totalAmount: number
  staffFreeAmount: number
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
  /** 스태프가 고친 시각/이전 내용. 안 고쳤으면 null */
  editedAt: string | null
  editNote: string | null
  /** 실제로 받은 현금/이체. cashAmount - settledCash 가 양수면 더 받을 돈, 음수면 돌려줄 돈 */
  settledCash: number
  settledTransfer: number
  /** 손님이 낸 현금 (없으면 생략) */
  cashGiven?: number | null
  /** 거스름돈 중 쿠폰에 넣은 금액 */
  changeCredited: number
  /** 아직 안 준 거스름돈 */
  changeDue: number
  /** "현금 5,000원 받음 → 거스름돈 1,000원" — 수정 뒤에도 지금 금액 기준 (없으면 생략) */
  payNote?: string | null
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
  staffFreeAmount: number
}

/** 기록 화면의 하루 집계. totalAmount 는 주문 금액 합, couponChargeAmount 는 그날 쿠폰 충전 입금 */
export interface DayReport {
  date: string
  orderCount: number
  totalAmount: number
  cashAmount: number
  transferAmount: number
  couponAmount: number
  freeAmount: number
  staffFreeAmount: number
  couponChargeAmount: number
}

export const PAY_LABEL: Record<PayMethod, string> = {
  TRANSFER: '계좌이체',
  COUPON: '쿠폰',
  CASH: '현금',
  NONE: '결제 없음',
}

/** "아메리카노 ICE · 샷 추가" 처럼 한 줄 이름. */
export function lineTitle(menuName: string, label: string | null, optionNames: string[]): string {
  const base = label ? `${menuName} ${label}` : menuName
  return optionNames.length ? `${base} · ${optionNames.join(', ')}` : base
}

export function won(n: number): string {
  return n.toLocaleString('ko-KR') + '원'
}

/** 충전 버튼 금액과 무료잔, 한 번 충전 상한 (/api/staff/coupons/preset) */
export interface ChargePreset {
  tiers: { amount: number; freeDrinks: number }[]
  max: number
}
