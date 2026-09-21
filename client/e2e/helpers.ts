import { expect, type APIRequestContext, type Page } from '@playwright/test'

export const PIN = '1234'

/** 테스트끼리 이름이 겹치지 않게. */
export function uniq(prefix: string) {
  return `${prefix}${Math.random().toString(36).slice(2, 6)}`
}

// ── API 로 준비 데이터 만들기 ─────────────────────────────

export async function staffToken(request: APIRequestContext) {
  const r = await request.post('/api/staff-auth/login', { data: { pin: PIN } })
  return (await r.json()).token as string
}

let phoneSeq = 1000
/** 전화번호는 필수. 안 주면 테스트용으로 매번 다른 번호를 만든다. */
export async function registerCoupon(request: APIRequestContext, name: string, amount: number, phone?: string) {
  const token = await staffToken(request)
  const r = await request.post('/api/staff/coupons', { headers: { 'X-Staff-Token': token }, data: { name, amount, phone: phone ?? `0105${String(phoneSeq++).padStart(7, '0')}` } })
  expect(r.ok(), await r.text()).toBeTruthy()
  return await r.json()
}

export async function lookupCoupon(request: APIRequestContext, name: string, phoneLast4?: string) {
  const r = await request.post('/api/coupons/lookup', { data: { name, phoneLast4: phoneLast4 ?? null } })
  return await r.json()
}

export async function createOrder(request: APIRequestContext, body: Record<string, unknown>) {
  const r = await request.post('/api/orders', { data: { receiveType: 'STORE', payMethod: 'CASH', ...body } })
  expect(r.ok(), await r.text()).toBeTruthy()
  return await r.json()
}

export async function pendingOrders(request: APIRequestContext) {
  const token = await staffToken(request)
  const r = await request.get('/api/staff/orders?status=PENDING', { headers: { 'X-Staff-Token': token } })
  return (await r.json()) as Array<Record<string, any>>
}

export async function orderOf(request: APIRequestContext, customerName: string, status = 'PENDING') {
  const token = await staffToken(request)
  const r = await request.get(`/api/staff/orders?status=${status}`, { headers: { 'X-Staff-Token': token } })
  const list = (await r.json()) as Array<Record<string, any>>
  const found = list.filter((o) => o.customerName === customerName).pop()
  expect(found, `${customerName} 주문이 ${status} 목록에 없음`).toBeTruthy()
  return found!
}

// ── 키오스크 화면 조작 ────────────────────────────────────

export function menuCard(page: Page, itemName: string) {
  return page.locator('.menu-card').filter({ has: page.locator('.name', { hasText: new RegExp(`^${itemName}$`) }) })
}

/** 메뉴는 카테고리 탭 안에 있으므로, 카드가 안 보이면 탭을 차례로 눌러 찾는다. */
export async function showMenuCard(page: Page, itemName: string) {
  const card = menuCard(page, itemName)
  await page.locator('.category-tabs').waitFor()   // 메뉴가 아직 로딩 중일 수 있다
  if (await card.count() > 0) return card
  const tabs = page.locator('.category-tabs .btn.tab')
  const n = await tabs.count()
  for (let i = 0; i < n; i++) {
    await tabs.nth(i).click()
    if (await card.count() > 0) return card
  }
  throw new Error(`메뉴 카드를 찾지 못함: ${itemName}`)
}

/** 메뉴 화면에서 담기. label 이 없으면 '담기' 버튼. */
export async function addToCart(page: Page, itemName: string, label: string | null, times = 1) {
  const card = await showMenuCard(page, itemName)
  const btn = card.getByRole('button', { name: label ? new RegExp(`^${label}`) : /^담기/ })
  for (let i = 0; i < times; i++) await btn.click()
}

export async function gotoKiosk(page: Page) {
  await page.goto('/kiosk')
  await expect(page.getByText('아메리카노')).toBeVisible()
}

/** 메뉴 → 장바구니 → 받는 방법까지 진행. */
export async function toReceiveStep(page: Page, items: Array<[string, string | null, number]>) {
  await gotoKiosk(page)
  for (const [name, label, qty] of items) await addToCart(page, name, label, qty)
  await page.getByRole('button', { name: /주문 확인/ }).click()
  await page.getByRole('button', { name: /주문하기/ }).click()
  await expect(page.getByRole('heading', { name: '어디서 받으시나요?' })).toBeVisible()
}

export async function pickNameByTyping(page: Page, name: string, confirm: RegExp | string) {
  await page.getByRole('button', { name: /직접 입력/ }).click()
  await page.getByPlaceholder('이름').fill(name)
  await page.getByRole('button', { name: confirm }).click()
}

/** 키오스크 쿠폰 화면: 이름 직접 입력 → 조회. 동명이인이면 뒤 4자리 키패드 → 확인 */
export async function kioskCouponLookup(page: Page, name: string, last4?: string) {
  // 직전 조회가 실패했으면 이미 입력 상태라 '직접 입력' 버튼이 없다
  const typingBtn = page.getByRole('button', { name: /직접 입력/ })
  if (await typingBtn.count() > 0) await typingBtn.click()
  await page.getByPlaceholder('이름').fill(name)
  await page.getByRole('button', { name: '쿠폰 조회' }).click()
  if (last4) {
    await expect(page.getByText('전화번호 뒤 4자리를 눌러 주세요')).toBeVisible()
    for (const k of last4) await page.getByRole('button', { name: k, exact: true }).click()
    await page.getByRole('button', { name: '확인' }).click()
  }
}

// ── 스태프 화면 조작 ──────────────────────────────────────

export async function staffLogin(page: Page) {
  await page.goto('/staff')
  await expect(page.getByText('PIN 4자리')).toBeVisible()
  for (const k of PIN) await page.getByRole('button', { name: k, exact: true }).click()
  await expect(page.getByRole('button', { name: '만들 것' })).toBeVisible()
}

export function orderCard(page: Page, customerName: string) {
  return page.locator('.order-card').filter({ has: page.locator('.who', { hasText: customerName }) })
}
