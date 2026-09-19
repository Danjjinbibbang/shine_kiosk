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

    page.once('dialog', (d) => d.dismiss())
    await card.getByRole('button', { name: '취소' }).click()
    await expect(card).toBeVisible()

    page.once('dialog', (d) => {
      expect(d.message()).toContain('쿠폰 차감액(무료 1잔 포함)은 되돌려집니다')
      d.accept()
    })
    await card.getByRole('button', { name: '취소' }).click()
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

test.describe('쿠폰 관리', () => {
  test('등록 → 조회(무료 1잔) → 충전 → 정정 → 삭제 → 없음', async ({ page, request }) => {
    const name = uniq('관리')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()

    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('새로 등록할까요?')).toBeVisible()
    await page.getByRole('button', { name: '20,000원 등록' }).click()
    await expect(page.locator('.toast')).toContainText('등록')
    await expect(page.getByText('1잔 남음')).toBeVisible()
    await expect(page.locator('.card').nth(1)).toContainText('20,000원')

    // 다른 금액 충전 5,000 → 25,000, 무료잔 그대로
    await page.getByPlaceholder('다른 금액').fill('5000')
    await page.getByRole('button', { name: '5,000원 충전' }).click()
    await expect(page.locator('.card').nth(1)).toContainText('25,000원')
    await expect(page.getByText('1잔 남음')).toBeVisible()

    // 정정: 잔액 20,000 / 무료 3잔
    await page.getByRole('button', { name: '잔액 정정' }).click()
    await page.getByPlaceholder(/^잔액/).fill('20000')
    await page.getByPlaceholder(/^무료/).fill('3')
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '정정' }).click()
    await expect(page.locator('.card').nth(1)).toContainText('20,000원')
    await expect(page.getByText('3잔 남음')).toBeVisible()
    const c = (await lookupCoupon(request, name)).coupon
    expect(c.balance).toBe(20000)
    expect(c.freeDrinks).toBe(3)

    // 삭제
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '쿠폰 삭제' }).click()
    await expect(page.locator('.toast')).toContainText('삭제됨')
    expect((await lookupCoupon(request, name)).status).toBe('NOT_FOUND')
  })

  test('동명이인: 조회하면 전화번호를 요구하고, 번호로 구분한다. 쓰인 쿠폰은 삭제 불가', async ({ page, request }) => {
    const name = uniq('동명')
    await registerCoupon(request, name, 20000)
    await registerCoupon(request, name, 500, '4321')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()

    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('같은 이름이 2명')).toBeVisible()
    await page.locator('input[maxlength="4"]').fill('4321')
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.locator('.card').nth(1)).toContainText('500원')
    await expect(page.locator('.card').nth(1)).toContainText('(4321)')

    // 주문에 쓰인 쿠폰은 삭제가 막힌다
    const used = (await lookupCoupon(request, name, '4321')).coupon
    await createOrder(request, { customerName: name, payMethod: 'COUPON', couponId: used.id, remainderMethod: 'CASH', lines: [{ variantId: 1001, quantity: 1 }] })
    page.once('dialog', (d) => d.accept())
    await page.getByRole('button', { name: '쿠폰 삭제' }).click()
    await expect(page.locator('.error')).toContainText('주문에 사용된 쿠폰')
  })

  test('없는 이름 등록 화면에서 번호 넣기 → 번호와 함께 등록', async ({ page, request }) => {
    const name = uniq('신규')
    await staffLogin(page)
    await page.getByRole('button', { name: '쿠폰' }).click()
    await page.getByPlaceholder('쿠폰 주인 이름').fill(name)
    await page.getByRole('button', { name: '조회' }).click()
    await expect(page.getByText('새로 등록할까요?')).toBeVisible()
    await page.getByRole('button', { name: '번호 넣기' }).click()
    await page.locator('input[maxlength="4"]').fill('1111')
    await page.getByRole('button', { name: '20,000원 등록' }).click()
    await expect(page.locator('.card').nth(1)).toContainText('(1111)')
    expect((await lookupCoupon(request, name)).coupon.phoneLast4).toBe('1111')
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
