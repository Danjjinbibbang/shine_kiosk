import type { MenuOption } from './types'

/**
 * 장바구니/수정 모달이 같이 쓰는 줄 모델과 조작 규칙.
 * 한 줄 = 같은 메뉴 + 같은 옵션 + 같은 사역자 여부. 칩을 누르면 **한 잔**만 떨어져 나와 바뀐다.
 */
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
  /** 사역자 무료 잔. 한 줄은 전부 무료거나 전부 유료 */
  staffFree: boolean
}

/** 옵션 가격까지 더한 한 잔 값 */
export function lineUnitPrice(l: CartLine): number {
  return l.price + l.options.reduce((s, o) => s + o.price, 0)
}

/** 같은 줄인지 판단하는 키: 메뉴 + 옵션 조합 + 사역자 여부 */
export function lineKey(variantId: number, optionIds: number[], staffFree = false): string {
  return variantId + ':' + [...optionIds].sort((a, b) => a - b).join(',') + (staffFree ? ':staff' : '')
}

export function lineKeyOf(l: CartLine): string {
  return lineKey(l.variantId, l.options.map((o) => o.id), l.staffFree)
}

/** "아메리카노 (ICE)" */
export function lineName(l: { itemName: string; label: string | null }): string {
  return l.label ? `${l.itemName} (${l.label})` : l.itemName
}

/** 낼 금액 (사역자 무료 잔 제외) */
export function cartTotal(cart: CartLine[]): number {
  return cart.reduce((s, l) => s + (l.staffFree ? 0 : lineUnitPrice(l) * l.qty), 0)
}

export function cartStaffFree(cart: CartLine[]): number {
  return cart.reduce((s, l) => s + (l.staffFree ? lineUnitPrice(l) * l.qty : 0), 0)
}

/** 서버로 보낼 모양 */
export function toLineRequests(cart: CartLine[]) {
  return cart.map((l) => ({
    variantId: l.variantId, quantity: l.qty, optionIds: l.options.map((o) => o.id), staffFreeQty: l.staffFree ? l.qty : 0,
  }))
}

export function setLineQty(cart: CartLine[], index: number, qty: number): CartLine[] {
  return qty <= 0 ? cart.filter((_, i) => i !== index) : cart.map((l, i) => (i === index ? { ...l, qty } : l))
}

/** index 줄에서 한 잔을 떼어 transform 을 적용하고, 같은 조합 줄에 합치거나 원래 줄 뒤에 새 줄로 넣는다. */
export function changeOneCup(cart: CartLine[], index: number, transform: (cup: CartLine) => CartLine): CartLine[] {
  const line = cart[index]
  const changed = transform({ ...line, qty: 1 })
  const rest = line.qty > 1 ? { ...line, qty: line.qty - 1 } : null
  const next: CartLine[] = []
  let merged = false
  cart.forEach((l, i) => {
    if (i === index) {
      if (rest) next.push(rest)
      return
    }
    if (!merged && lineKeyOf(l) === lineKeyOf(changed)) {
      next.push({ ...l, qty: l.qty + 1 })
      merged = true
    } else {
      next.push(l)
    }
  })
  if (!merged) {
    const at = rest ? next.indexOf(rest) + 1 : Math.min(index, next.length)
    next.splice(at, 0, changed)
  }
  return next
}

export function toggleOptionOnOneCup(cart: CartLine[], index: number, opt: MenuOption): CartLine[] {
  return changeOneCup(cart, index, (cup) => ({
    ...cup,
    options: cup.options.some((o) => o.id === opt.id) ? cup.options.filter((o) => o.id !== opt.id) : [...cup.options, opt],
  }))
}

export function toggleStaffFreeOnOneCup(cart: CartLine[], index: number): CartLine[] {
  return changeOneCup(cart, index, (cup) => ({ ...cup, staffFree: !cup.staffFree }))
}

/** 옵션 없는 일반 줄에 한 잔 더하기 (메뉴 화면/수정 모달의 '추가') */
export function addPlainCup(cart: CartLine[], cup: Omit<CartLine, 'qty' | 'options' | 'staffFree'>): CartLine[] {
  const key = lineKey(cup.variantId, [])
  const idx = cart.findIndex((l) => lineKeyOf(l) === key)
  if (idx >= 0) return cart.map((l, i) => (i === idx ? { ...l, qty: l.qty + 1 } : l))
  return [...cart, { ...cup, qty: 1, options: [], staffFree: false }]
}

/** 이 줄에 이미 같은 그룹의 다른 옵션이 있어서 못 고르는지 */
export function blockedByGroup(opt: MenuOption, selected: MenuOption[]): boolean {
  return opt.group !== null && selected.some((o) => o.id !== opt.id && o.group === opt.group)
}
