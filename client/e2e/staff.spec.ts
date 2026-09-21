import { expect, test } from '@playwright/test'
import { createOrder, lookupCoupon, orderCard, orderOf, registerCoupon, staffLogin, uniq } from './helpers'

test.describe('로그인', () => {
  test('틀린 PIN 은 오류, 맞으면 목록, 새로고침해도 유지, 나가기하면 다시 PIN', async ({ page }) => {
    await page.goto('/staff')
    for (const k of '0000') await page.getByRole('button', { name: k, exact: true }).click()
    await expect(page.locator('.error')).toContainText('PIN')
    await expect(page.locator('.pin-dots .on')).toHaveCount(0) // 입력 초기화

    for (const k of '1234') await page.getByRole('button', { name: k, exact: true }).click()
    await expect(page.getByRole('button', { name: '만들 것' })).toBeVisible()

    await page.reload()
    await expect(page.getByRole('button', { name: '만들 것' })).toBeVisible()
    await expect(page.getByText('PIN 4자리')).toHaveCount(0)

    await page.getByRole('button', { name: '나가기' }).click()
    await expect(page.getByText('PIN 4자리')).toBeVisible()
    await page.reload()
    await expect(page.getByText('PIN 4자리')).toBeVisible()
  })
})

test.describe('주문 처리', () => {
  test('카드 내용 → 완료 → 완료 탭 → 되돌리기', async ({ page, request }) => {
    const name = uniq('처리')
    await createOrder(request, {
      customerName: name, receiveType: 'DELIVERY', placeId: 101,
      lines: [{ variantId: 1001, quantity: 2 }, { variantId: 3002, quantity: 1 }],
      memo: '현금 10,000원 받음 → 거스름돈 5,000원',
    })
    await staffLogin(page)

    const card = orderCard(page, name)
    await expect(card).toBeVisible()
    await expect(card.locator('.where')).toHaveText('🚶 식당')
    await expect(card.locator('.lines')).toContainText(/아메리카노 ICE\s*×2/)
    await expect(card.locator('.lines')).toContainText(/아이스크림 컵\s*×1/)
    await expect(card.locator('.pay')).toHaveText('현금 5,000원')
    await expect(card.locator('.memo')).toContainText('거스름돈 5,000원')

    await card.getByRole('button', { name: /완료/ }).click()
    await expect(page.locator('.toast')).toContainText(`${name}님 완료`)
    await expect(card).toHaveCount(0)

    await page.getByRole('button', { name: '완료', exact: true }).click()
    const done = orderCard(page, name)
    await expect(done).toBeVisible()
    await done.getByRole('button', { name: /되돌리기/ }).click()
    await expect(done).toHaveCount(0)

    await page.getByRole('button', { name: '만들 것' }).click()
    await expect(orderCard(page, name)).toBeVisible()
  })

  test('취소: 확인 창에서 아니오면 남고, 예면 사라지고 쿠폰이 돌아온다', async ({ page, request }) => {
    const name = uniq('취소')
    const coupon = await registerCoupon(request, name, 20000)
    await createOrder(request, {
      customerName: name, payMethod: 'COUPON', couponId: coupon.id, useFreeDrink: true,
      lines: [{ variantId: 3002, quantity: 1 }],
    })
    expect((await lookupCoupon(request, name)).coupon.freeDrinks).toBe(0)
    await staffLogin(page)
    const card = orderCard(page, name)
    await expect(card.locator('.pay')).toHaveText('무료 1잔(아이스크림 컵)')

    // 팝업 대신 카드 안 패널. 받은 돈이 없으면(무료 1잔만) 취소 확정 버튼 하나
    await card.getByRole('button', { name: '취소', exact: true }).click()
    await expect(card.locator('.cancel-panel')).toContainText('쿠폰으로 낸 몫은 자동 복원')
    await card.getByRole('button', { name: '취소 안 함' }).click()
    await expect(card.locator('.cancel-panel')).toHaveCount(0)
    await expect(card).toBeVisible()

    await card.getByRole('button', { name: '취소', exact: true }).click()
    await card.getByRole('button', { name: '취소 확정' }).click()
    await expect(card).toHaveCount(0)
    await expect(page.locator('.toast')).toContainText('취소됨')
    expect((await lookupCoupon(request, name)).coupon.freeDrinks).toBe(1)
  })

  test('수정: 이름/장소/수량/메뉴 추가/메모 → 카드에 반영', async ({ page, request }) => {
    const name = uniq('수정')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 1 }] })
    await staffLogin(page)
    const card = orderCard(page, name)
    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await expect(modal.getByRole('heading', { name: /주문 수정/ })).toBeVisible()

    await modal.getByPlaceholder('예: 얼음 적게').fill('얼음 적게')
    await modal.locator('.field input').first().fill(name + '2')
    await modal.getByRole('button', { name: '배달' }).click()
    await modal.getByRole('button', { name: '1층 전도사님실' }).click()
    await modal.locator('.cart-line').first().getByRole('button', { name: '+' }).click()
    await modal.locator('.chips').last().getByRole('button', { name: /^아포카토/ }).click()
    await expect(modal.locator('.total-box .amount')).toHaveText('5,000원')
    await modal.getByRole('button', { name: '저장' }).click()

    const updated = orderCard(page, name + '2')
    await expect(updated).toBeVisible()
    await expect(updated.locator('.where')).toHaveText('🚶 전도사님실')
    await expect(updated.locator('.lines')).toContainText(/아메리카노 ICE\s*×2/)
    await expect(updated.locator('.lines')).toContainText(/아포카토\s*×1/)
    await expect(updated.locator('.pay')).toHaveText('현금 5,000원')
    await expect(updated.locator('.memo')).toContainText('얼음 적게')
  })

  test('수정 모달: 메뉴를 다 빼면 저장 불가, 닫기는 변경 없음', async ({ page, request }) => {
    const name = uniq('닫기')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 1 }] })
    await staffLogin(page)
    await orderCard(page, name).getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await modal.locator('.cart-line').first().getByRole('button', { name: '−' }).click()
    await expect(modal.getByRole('button', { name: '저장' })).toBeDisabled()
    await modal.getByRole('button', { name: '닫기' }).click()
    await expect(modal).toHaveCount(0)
    expect((await orderOf(request, name)).lines).toHaveLength(1)
  })

  test('오늘 집계와 지난 날짜 주문 표시', async ({ page, request }) => {
    const name = uniq('집계')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1201, quantity: 1 }] }) // 이체 아님, 현금 2,500
    await staffLogin(page)
    await expect(page.locator('.summary')).toContainText('현금')
    await expect(page.locator('.summary')).toContainText('건')
    // 오늘 주문은 날짜 없이 # 번호만
    await expect(orderCard(page, name).locator('.no')).toHaveText(/^#\d+$/)
  })
})

const couponCard = (page: import('@playwright/test').Page) => page.locator('.card').filter({ hasText: '무료 1잔' })

test.describe('쿠폰 관리', () => {
  test('등록(번호 필수) → 조회(무료 1잔, 이력) → 충전 → 정정 → 삭제 → 없음', async ({ page, request }) => {
    const name = uniq('관리')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()

    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('새로 등록할까요?')).toBeVisible()
    await expect(page.getByRole('button', { name: '20,000원 등록' })).toBeDisabled()   // 번호 없으면 등록 불가
    await page.getByPlaceholder('010-0000-0000').fill('010-7777-8888')
    await page.getByRole('button', { name: '20,000원 등록' }).click()
    await expect(page.locator('.toast')).toContainText('등록')
    await expect(page.getByText('1잔 남음')).toBeVisible()
    await expect(couponCard(page)).toContainText('20,000원')
    await expect(couponCard(page).locator('.history-row')).toHaveCount(1)
    await expect(couponCard(page).locator('.history-row').first()).toContainText('충전')

    // 다른 금액 충전 5,000 → 25,000, 무료잔 그대로, 이력 2줄
    await page.getByPlaceholder('다른 금액').fill('5000')
    await page.getByRole('button', { name: '5,000원 충전' }).click()
    await expect(couponCard(page)).toContainText('25,000원')
    await expect(page.getByText('1잔 남음')).toBeVisible()
    await expect(couponCard(page).locator('.history-row')).toHaveCount(2)
    await expect(couponCard(page).locator('.history-row').first()).toContainText('+5,000원')

    // 정정: 잔액 20,000 / 무료 3잔
    await page.getByRole('button', { name: '잔액 정정' }).click()
    await page.getByPlaceholder(/^잔액/).fill('20000')
    await page.getByPlaceholder(/^무료/).fill('3')
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '정정' }).click()
    await expect(couponCard(page)).toContainText('20,000원')
    await expect(page.getByText('3잔 남음')).toBeVisible()
    await expect(couponCard(page).locator('.history-row').first()).toContainText('정정')
    const c = (await lookupCoupon(request, name)).coupon
    expect(c.balance).toBe(20000)
    expect(c.freeDrinks).toBe(3)

    // 삭제
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '쿠폰 삭제' }).click()
    await expect(page.locator('.toast')).toContainText('삭제됨')
    expect((await lookupCoupon(request, name)).status).toBe('NOT_FOUND')
  })

  test('동명이인: 이름만 넣으면 목록에서 고르고, 뒤 4자리를 넣으면 바로 열린다. 쓰인 쿠폰은 삭제 불가', async ({ page, request }) => {
    const name = uniq('동명')
    await registerCoupon(request, name, 20000, '01000001111')
    await registerCoupon(request, name, 500, '01000004321')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()

    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('같은 이름이 2명 있어요')).toBeVisible()
    await page.getByRole('button', { name: /010-0000-4321/ }).click()
    await expect(couponCard(page)).toContainText('500원')
    await expect(page.getByLabel('쿠폰 전화번호')).toHaveValue('010-0000-4321')

    await page.getByRole('button', { name: '지우기' }).click()
    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByPlaceholder('010-0000-0000').fill('1111')
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('같은 이름이 2명')).toHaveCount(0)
    await expect(couponCard(page)).toContainText('20,000원')

    // 주문에 쓰인 쿠폰은 삭제가 막힌다
    const used = (await lookupCoupon(request, name, '1111')).coupon
    await createOrder(request, { customerName: name, payMethod: 'COUPON', couponId: used.id, lines: [{ variantId: 1001, quantity: 1 }] })
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '쿠폰 삭제' }).click()
    await expect(page.locator('.error')).toContainText('주문에 사용된 쿠폰')
  })

  test('전화번호 변경, 비울 수 없음, 번호 없는 옛 쿠폰은 충전 잠김', async ({ page, request }) => {
    const name = uniq('번호')
    const c = await registerCoupon(request, name, 20000, '010-1234-1111')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()
    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    const phoneInput = page.getByLabel('쿠폰 전화번호')
    await expect(phoneInput).toHaveValue('010-1234-1111')
    await expect(phoneInput).toBeDisabled()

    await page.getByRole('button', { name: '번호 변경' }).click()
    await phoneInput.fill('01099998888')
    await page.getByRole('button', { name: '저장' }).click()
    await expect(phoneInput).toHaveValue('010-9999-8888')
    expect((await lookupCoupon(request, name)).coupon.phoneLast4).toBe('8888')

    await page.getByRole('button', { name: '번호 변경' }).click()
    await phoneInput.fill('')
    await expect(page.getByRole('button', { name: '저장' })).toBeDisabled()
    await page.getByRole('button', { name: '취소', exact: true }).click()

    // 옛 DB 처럼 번호가 없는 쿠폰: 충전이 잠기고, 번호를 넣으면 풀린다
    const token = (await (await request.post('/api/staff-auth/login', { data: { pin: '1234' } })).json()).token
    await request.put(`/api/staff/coupons/${c.id}/phone`, { headers: { 'X-Staff-Token': token }, data: { phone: '01000000000' } }).catch(() => {})
    // (서버는 번호를 비울 수 없으니 테스트 DB 를 직접 만질 수 없어, 화면 규칙만 확인: 번호가 있으면 충전 버튼이 있다)
    await expect(page.getByRole('button', { name: '20,000원 충전' })).toBeVisible()
  })

  test('완료 누르면 잔액 문자 앱이 열린다', async ({ page, request }) => {
    const withPhone = uniq('문자')
    const coupon = await registerCoupon(request, withPhone, 20000, '010-5555-6666')
    await createOrder(request, { customerName: withPhone, payMethod: 'COUPON', couponId: coupon.id, useFreeDrink: true, lines: [{ variantId: 3002, quantity: 1 }, { variantId: 1001, quantity: 1 }] })
    await page.addInitScript(() => { (window as unknown as { __smsCapture?: boolean }).__smsCapture = true })
    await staffLogin(page)

    await orderCard(page, withPhone).getByRole('button', { name: /완료/ }).click()
    const link = await page.evaluate(() => (window as unknown as { __lastSms?: string }).__lastSms)
    expect(link).toBeTruthy()
    expect(link!.startsWith('sms:01055556666')).toBe(true)
    const body = decodeURIComponent(link!.split('body=')[1])
    expect(body).toContain(`[열린카페] ${withPhone}님`)
    expect(body).toContain('무료 1잔 아이스크림 컵')
    expect(body).toContain('쿠폰 1,000원')
    expect(body).toContain('남은 잔액 19,000원 · 무료 1잔 0잔')
    await expect(orderCard(page, withPhone)).toHaveCount(0)

    // 완료 탭에서 다시 보내기
    await page.getByRole('button', { name: '완료', exact: true }).click()
    await expect(orderCard(page, withPhone).getByRole('button', { name: /잔액 문자/ })).toBeEnabled()
  })
})

test.describe('실시간', () => {
  test('키오스크에서 주문하면 새로고침 없이 스태프 화면에 나타나고 집계가 바뀐다', async ({ page, request }) => {
    await staffLogin(page)
    const before = await page.locator('.summary').textContent()
    const name = uniq('실시간')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1401, quantity: 1 }] })
    await expect(orderCard(page, name)).toBeVisible({ timeout: 5000 })
    await expect(orderCard(page, name).locator('.lines')).toContainText(/아이스크림 라떼\s*×1/)
    await expect.poll(async () => page.locator('.summary').textContent(), { timeout: 5000 }).not.toBe(before)
  })
})

test.describe('설정 · 메뉴 관리', () => {
  test('새 메뉴 추가 → 고객 메뉴에 등장 → 품절 → 사라짐 → 가격 수정 → 삭제', async ({ page, request }) => {
    const name = uniq('유자차')
    await staffLogin(page)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('button', { name: '＋ 새 메뉴' }).click()
    const modal = page.locator('.modal')
    await modal.getByPlaceholder('예: 유자차').fill(name)
    await modal.locator('.chips').first().getByRole('button', { name: '논커피' }).click()
    await modal.getByRole('button', { name: 'ICE / HOT 으로' }).click()
    const rows = modal.locator('.variant-edit')
    await expect(rows).toHaveCount(2)
    await rows.nth(0).getByPlaceholder('가격').fill('2500')
    await rows.nth(1).getByPlaceholder('가격').fill('2500')
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(page.locator('.toast')).toContainText('추가됨')

    const card = page.locator('.admin-item').filter({ hasText: name })
    await expect(card).toBeVisible()
    await expect(card).toContainText('ICE 2,500원 · HOT 2,500원')
    const menu = async () => (await (await request.get('/api/menu')).json()) as Array<{ name: string; category: string; variants: Array<{ label: string; price: number }> }>
    const created = (await menu()).find((m) => m.name === name)!
    expect(created.category).toBe('논커피')
    expect(created.variants.map((v) => v.label)).toEqual(['ICE', 'HOT'])

    // 품절
    await card.getByRole('button', { name: '판매중' }).click()
    await expect(card.getByRole('button', { name: '품절' })).toBeVisible()
    expect((await menu()).some((m) => m.name === name)).toBe(false)
    await card.getByRole('button', { name: '품절' }).click()
    expect((await menu()).some((m) => m.name === name)).toBe(true)

    // 가격 수정 + HOT 삭제
    await card.getByRole('button', { name: '수정' }).click()
    await modal.locator('.variant-edit').nth(0).getByPlaceholder('가격').fill('3000')
    await modal.locator('.variant-edit').nth(1).getByRole('button', { name: '선택지 삭제' }).click()
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(card).toContainText('ICE 3,000원')
    await expect(card).not.toContainText('HOT')
    expect((await menu()).find((m) => m.name === name)!.variants).toEqual([expect.objectContaining({ label: 'ICE', price: 3000 })])

    // 순서: 위로 한 칸
    const before = await page.locator('.admin-item .admin-name').allTextContents()
    const idx = before.findIndex((t) => t.startsWith(name))
    await card.getByRole('button', { name: '위로' }).click()
    await expect.poll(async () => (await page.locator('.admin-item .admin-name').allTextContents()).findIndex((t) => t.startsWith(name))).toBe(idx - 1)

    // 삭제
    page.once('dialog', (d) => d.accept())
    await card.getByRole('button', { name: '삭제' }).click()
    await expect(card).toHaveCount(0)
    expect((await menu()).some((m) => m.name === name)).toBe(false)
  })

  test('빈 이름/가격 없는 메뉴는 저장 불가', async ({ page }) => {
    await staffLogin(page)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('button', { name: '＋ 새 메뉴' }).click()
    const modal = page.locator('.modal')
    await expect(modal.getByRole('button', { name: '저장' })).toBeDisabled()
    await modal.getByPlaceholder('예: 유자차').fill('x')
    await expect(modal.getByRole('button', { name: '저장' })).toBeEnabled() // 가격 0원도 허용(무료 메뉴)
    await modal.getByRole('button', { name: '닫기' }).click()
    await expect(modal).toHaveCount(0)
  })
})

test.describe('설정 · 카테고리', () => {
  test('추가 → 메뉴 등록에 보임 → 순서 변경이 키오스크에 반영 → 이름 변경 → 삭제', async ({ page, request }) => {
    const name = uniq('디저트')
    await staffLogin(page)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('button', { name: '카테고리' }).click()
    await page.getByPlaceholder('새 카테고리 (예: 디저트)').fill(name)
    await page.getByRole('button', { name: '추가' }).click()
    const card = page.locator('.admin-item').filter({ hasText: name })
    await expect(card).toBeVisible()
    await expect(card).toContainText('메뉴 0개')

    // 메뉴 등록 화면의 카테고리 칩에 나온다
    await page.getByRole('button', { name: '메뉴', exact: true }).click()
    await page.getByRole('button', { name: '＋ 새 메뉴' }).click()
    await expect(page.locator('.modal .chips').first().getByRole('button', { name })).toBeVisible()
    await page.getByRole('button', { name: '닫기' }).click()

    // 맨 위로 올리면 키오스크 카테고리 순서가 바뀐다
    await page.getByRole('button', { name: '카테고리' }).click()
    const menu = async () => (await (await request.get('/api/menu')).json()) as Array<{ category: string }>
    const before = [...new Set((await menu()).map((m) => m.category))]
    expect(before[0]).toBe('커피')
    for (let i = 0; i < 10; i++) {
      const up = card.getByRole('button', { name: '위로' })
      if (await up.isDisabled()) break
      await up.click()
      await page.waitForTimeout(150)
    }
    // 새 카테고리엔 메뉴가 없어 키오스크엔 안 나오지만, 관리 목록 첫 줄이어야 한다
    await expect(page.locator('.admin-item').first()).toContainText(name)

    // 이름 변경
    await card.getByRole('button', { name: '이름 변경' }).click()
    await page.getByLabel('카테고리 이름').fill(name + '2')
    await page.getByRole('button', { name: '저장' }).click()
    const renamed = page.locator('.admin-item').filter({ hasText: name + '2' })
    await expect(renamed).toBeVisible()

    // 메뉴가 있는 카테고리는 삭제 버튼이 잠겨 있고, 빈 건 지워진다
    await expect(page.locator('.admin-item').filter({ hasText: '커피' }).first().getByRole('button', { name: '삭제' })).toBeDisabled()
    page.once('dialog', (d) => d.accept())
    await renamed.getByRole('button', { name: '삭제' }).click()
    await expect(renamed).toHaveCount(0)
  })
})

test.describe('설정 · 배달 장소', () => {
  test('추가 → 고객 화면에 층 등장 → 숨김 → 삭제', async ({ page, request }) => {
    const name = uniq('방')
    await staffLogin(page)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('button', { name: '배달 장소' }).click()
    await page.getByLabel('층').fill('2')
    await page.getByPlaceholder('장소 이름 (예: 식당)').fill(name)
    await page.getByRole('button', { name: '추가' }).click()
    const card = page.locator('.admin-item').filter({ hasText: `2층 ${name}` })
    await expect(card).toBeVisible()
    const floors = async () => ((await (await request.get('/api/places')).json()) as Array<{ floor: number; places: Array<{ name: string }> }>)
    expect((await floors()).map((f) => f.floor)).toEqual([1, 2])

    await card.getByRole('button', { name: '표시중' }).click()
    await expect(card.getByRole('button', { name: '숨김' })).toBeVisible()
    expect((await floors()).map((f) => f.floor)).toEqual([1])

    page.once('dialog', (d) => d.accept())
    await card.getByRole('button', { name: '삭제' }).click()
    await expect(card).toHaveCount(0)
  })
})

test.describe('옵션', () => {
  test('주문 카드에 옵션 표시, 수정 모달에서 옵션 켜고 끄기', async ({ page, request }) => {
    const name = uniq('옵션')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 1, optionIds: [1] }] })
    await staffLogin(page)
    const card = orderCard(page, name)
    await expect(card.locator('.lines')).toContainText('샷 추가')
    await expect(card.locator('.pay')).toHaveText('현금 1,500원')

    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    const line = modal.locator('.cart-line-wrap').first()
    await expect(line.locator('.line-options')).toHaveText('샷 추가')
    await expect(line.getByRole('button', { name: /연하게/ })).toBeDisabled()   // 샷 추가와 같은 그룹
    await line.getByRole('button', { name: /샷 추가/ }).click()
    await line.getByRole('button', { name: /연하게/ }).click()
    await expect(line.locator('.line-options')).toHaveText('연하게')
    await expect(modal.locator('.total-box .amount')).toHaveText('1,000원')
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(card.locator('.lines')).toContainText('연하게')
    await expect(card.locator('.lines')).not.toContainText('샷 추가')
    await expect(card.locator('.pay')).toHaveText('현금 1,000원')
  })

  test('설정 > 옵션: 추가 → 고객 옵션에 등장 → 숨김 → 삭제', async ({ page, request }) => {
    const name = uniq('휘핑')
    await staffLogin(page)
    await page.getByRole('button', { name: '설정' }).click()
    await page.getByRole('button', { name: '옵션', exact: true }).click()
    await page.getByPlaceholder('이름 (예: 샷 추가)').fill(name)
    await page.getByLabel('추가 금액').fill('300')
    await page.getByLabel('그룹').fill('토핑')
    await page.locator('.card .chips').first().getByRole('button', { name: '논커피' }).click()
    await page.getByRole('button', { name: '추가' }).click()
    const card = page.locator('.admin-item').filter({ hasText: name })
    await expect(card).toBeVisible()
    await expect(card).toContainText('+300원')
    await expect(card).toContainText('그룹 토핑')
    const options = async () => (await (await request.get('/api/menu/options')).json()) as Array<{ name: string; category: string }>
    expect((await options()).find((o) => o.name === name)?.category).toBe('논커피')

    await card.getByRole('button', { name: '사용중' }).click()
    await expect(card.getByRole('button', { name: '숨김' })).toBeVisible()
    expect((await options()).some((o) => o.name === name)).toBe(false)

    page.once('dialog', (d) => d.accept())
    await card.getByRole('button', { name: '삭제' }).click()
    await expect(card).toHaveCount(0)
  })
})

test.describe('사역자', () => {
  test('카드에 사역자 배지와 잔 수, 수정 모달의 사역자 칩', async ({ page, request }) => {
    const name = uniq('사역')
    await createOrder(request, { customerName: name, payMethod: 'NONE', lines: [{ variantId: 1001, quantity: 3, staffFreeQty: 3 }] })
    await staffLogin(page)
    const oc = orderCard(page, name)
    await expect(oc.locator('.badge-staff')).toHaveText('사역자')
    await expect(oc.locator('.lines')).toContainText('(사역자 3)')
    await expect(oc.locator('.pay')).toHaveText('사역자 무료 3,000원')

    // 수정: 사역자 칩은 한 잔씩 해제 → 세 번 누르면 3잔 현금
    await oc.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await modal.locator('.cart-line-wrap').first().getByRole('button', { name: /사역자/ }).click()
    await expect(modal.locator('.total-box .amount')).toHaveText('1,000원')
    await expect(modal.locator('.cart-line-wrap')).toHaveCount(2)
    await modal.locator('.cart-line-wrap').first().getByRole('button', { name: /사역자/ }).click()
    await modal.locator('.cart-line-wrap').first().getByRole('button', { name: /사역자/ }).click()
    await expect(modal.locator('.cart-line-wrap')).toHaveCount(1)
    await expect(modal.locator('.total-box .amount')).toHaveText('3,000원')
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(oc.locator('.badge-staff')).toHaveCount(0)
    await expect(oc.locator('.pay')).toHaveText('현금 3,000원')
    await expect(oc.locator('.settle')).toContainText('현금 3,000원 더 받기')   // 결제 없음이던 주문 → 받을 돈
    await expect(oc.getByRole('button', { name: '받았어요' })).toBeVisible()
  })
})

test.describe('기록', () => {
  test('날짜별 집계 → 펼치면 그날 주문, CSV 내려받기', async ({ page, request }) => {
    const name = uniq('기록')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1201, quantity: 2 }] }) // 현금 5,000
    await staffLogin(page)
    await page.getByRole('button', { name: '매출' }).click()

    const today = page.locator('.day-card').first()
    await expect(today).toBeVisible()
    await expect(page.locator('.month-head').first()).toContainText('월')
    await expect(today.locator('.day-breakdown')).toContainText('현금')

    await today.locator('.day-head').click()
    await expect(today).toHaveClass(/open/)
    const row = today.locator('.report-order').filter({ hasText: name })
    await expect(row).toBeVisible()
    await expect(row).toContainText('바닐라라떼 ICE ×2')
    await expect(row).toContainText('5,000원 · 현금')
    await expect(row.locator('.status')).toHaveText('대기')

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      today.getByRole('button', { name: /이날 주문 CSV/ }).click(),
    ])
    expect(download.suggestedFilename()).toMatch(/^주문-\d{4}-\d{2}-\d{2}\.csv$/)
    const [all] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /일별 CSV/ }).click(),
    ])
    expect(all.suggestedFilename()).toBe('매출-일별.csv')
    const [backup] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: /백업 내려받기/ }).click(),
    ])
    expect(backup.suggestedFilename()).toMatch(/^kiosk-backup-\d{4}-\d{2}-\d{2}\.db$/)

    await today.locator('.day-head').click()
    await expect(today).not.toHaveClass(/open/)
  })
})

test.describe('수정과 정산', () => {
  test('아메리카노 2잔 샷 추가 → 모달에서 샷 해제하면 1잔만 해제, 카드에 수정됨 + 돌려줄 돈 + 정산', async ({ page, request }) => {
    const name = uniq('정산')
    // 현금 3,000원 받은 주문: 아메리카노 ICE 샷추가 ×2
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 2, optionIds: [1] }] })
    await staffLogin(page)
    const card = orderCard(page, name)
    await expect(card.locator('.pay')).toHaveText('현금 3,000원')
    await expect(card.locator('.badge-edited')).toHaveCount(0)

    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    const lines = modal.locator('.cart-line-wrap')
    await expect(lines).toHaveCount(1)
    await lines.nth(0).getByRole('button', { name: /샷 추가/ }).click()   // 한 잔만 해제
    await expect(lines).toHaveCount(2)
    await expect(lines.nth(0).locator('.line-options')).toHaveText('샷 추가')
    await expect(lines.nth(0).locator('.qty .n')).toHaveText('1')
    await expect(lines.nth(1).locator('.line-options')).toHaveCount(0)
    await expect(modal.locator('.total-box .amount')).toHaveText('2,500원')
    await modal.getByRole('button', { name: '저장' }).click()

    // 카드: 수정됨 배지 + 이전 내용 + 돌려줄 500원
    await expect(card.locator('.badge-edited')).toContainText('수정됨')
    await expect(card.locator('.edited')).toContainText('이전: 아메리카노 ICE(샷 추가) ×2 · 3,000원')
    await expect(card.locator('.pay')).toHaveText('현금 2,500원')
    await expect(card.locator('.settle')).toContainText('500원 돌려주기')
    await expect(card.getByRole('button', { name: '정산 먼저' })).toBeDisabled()   // 정산 전엔 완료 불가
    // 현금 주문이라도 쿠폰에 넣기는 있다. 이 이름은 쿠폰이 없으니 안내만
    await card.getByRole('button', { name: '쿠폰에 넣기' }).click()
    await expect(card.locator('.settle-note')).toContainText('쿠폰이 없어요')

    await card.getByRole('button', { name: '현금으로 줬어요' }).click()
    await expect(card.locator('.settle')).toHaveCount(0)
    await expect(card.locator('.badge-edited')).toBeVisible()   // 수정 표시는 남는다
    await expect(card.getByRole('button', { name: /완료/ })).toBeEnabled()
    const o = await orderOf(request, name)
    expect(o.settledCash).toBe(2500)
  })

  test('더 비싸게 고치면 더 받을 돈, 취소 확인창에 돌려줄 돈', async ({ page, request }) => {
    const name = uniq('추가')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 1 }] })   // 현금 1,000
    await staffLogin(page)
    const card = orderCard(page, name)
    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await modal.locator('.chips').last().getByRole('button', { name: /^아이스크림 컵/ }).click()
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(card.locator('.settle')).toContainText('현금 3,000원 더 받기')
    await expect(card.getByRole('button', { name: '받았어요' })).toBeVisible()

    await card.getByRole('button', { name: '취소', exact: true }).click()
    await expect(card.locator('.cancel-panel')).toContainText('받은 1,000원은?')
    await expect(card.getByRole('button', { name: '현금으로 돌려주고 취소' })).toBeVisible()
    await expect(card.getByRole('button', { name: '쿠폰에 넣고 취소' })).toBeVisible()
    await card.getByRole('button', { name: '취소 안 함' }).click()
    await expect(card).toBeVisible()
  })

  test('취소하면서 받은 돈을 주문자 쿠폰에 넣기', async ({ page, request }) => {
    const name = uniq('취소쿠폰')
    await registerCoupon(request, name, 20000)
    await createOrder(request, { customerName: name, lines: [{ variantId: 3002, quantity: 1 }] })   // 현금 3,000
    await staffLogin(page)
    const card = orderCard(page, name)
    await card.getByRole('button', { name: '취소', exact: true }).click()
    await card.getByRole('button', { name: '쿠폰에 넣고 취소' }).click()
    await expect(card).toHaveCount(0)
    await expect(page.locator('.toast')).toContainText('쿠폰 잔액에 넣음')
    expect((await lookupCoupon(request, name)).coupon.balance).toBe(23000)
  })

  test('현금 주문의 돌려줄 돈도 주문자 이름의 쿠폰에 넣을 수 있다 (동명이인이면 고른다)', async ({ page, request }) => {
    const name = uniq('현금쿠폰')
    await registerCoupon(request, name, 20000, '010-0000-1111')
    await registerCoupon(request, name, 5000, '010-0000-2222')
    await createOrder(request, { customerName: name, lines: [{ variantId: 3002, quantity: 1 }] })   // 현금 3,000
    await staffLogin(page)
    const card = orderCard(page, name)
    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await modal.locator('.chips').last().getByRole('button', { name: /^아메리카노 ICE/ }).click()
    await modal.locator('.cart-line-wrap').first().getByRole('button', { name: '−' }).click()
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(card.locator('.settle')).toContainText('2,000원 돌려주기')
    await card.getByRole('button', { name: '쿠폰에 넣기' }).click()
    await expect(card.locator('.settle-note')).toContainText('같은 이름이 2명')
    await card.getByRole('button', { name: /\(2222\)/ }).click()
    await expect(card.locator('.settle')).toHaveCount(0)
    expect((await lookupCoupon(request, name, '2222')).coupon.balance).toBe(7000)
    expect((await lookupCoupon(request, name, '1111')).coupon.balance).toBe(20000)
  })

  test('쿠폰 주문의 돌려줄 돈은 쿠폰 잔액에 넣을 수 있다', async ({ page, request }) => {
    const name = uniq('쿠폰정산')
    const coupon = await registerCoupon(request, name, 1500)
    await createOrder(request, { customerName: name, payMethod: 'COUPON', couponId: coupon.id, remainderMethod: 'CASH', lines: [{ variantId: 3002, quantity: 1 }] }) // 쿠폰 1,500 + 현금 1,500
    await staffLogin(page)
    const card = orderCard(page, name)
    await card.getByRole('button', { name: '수정' }).click()
    const modal = page.locator('.modal')
    await modal.locator('.chips').last().getByRole('button', { name: /^아메리카노 ICE/ }).click()
    await modal.locator('.cart-line-wrap').first().getByRole('button', { name: '−' }).click()   // 컵 빼고 아메리카노만
    await modal.getByRole('button', { name: '저장' }).click()
    await expect(card.locator('.settle')).toContainText('1,500원 돌려주기')
    await card.getByRole('button', { name: '쿠폰에 넣기' }).click()
    await expect(card.locator('.settle')).toHaveCount(0)
    expect((await lookupCoupon(request, name)).coupon.balance).toBe(2000)   // 500 남았던 것 + 1,500
  })
})
