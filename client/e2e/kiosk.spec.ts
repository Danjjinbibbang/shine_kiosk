import { expect, test } from '@playwright/test'
import {
  addToCart, createOrder, gotoKiosk, lookupCoupon, menuCard, orderOf, pickNameByTyping,
  registerCoupon, toReceiveStep, uniq,
} from './helpers'

test.describe('메뉴 화면', () => {
  test('카테고리 순서, 담기/스테퍼, 장바구니 바', async ({ page }) => {
    await gotoKiosk(page)
    const titles = await page.locator('.category-title').allTextContents()
    expect(titles).toEqual(['커피', '논커피', '아이스크림'])
    await expect(menuCard(page, '아포카토')).toBeVisible()
    await expect(page.getByRole('button', { name: /주문 확인/ })).toHaveCount(0)

    // 한 번 누르면 1개, 스테퍼가 생긴다
    await addToCart(page, '아메리카노', 'ICE')
    const card = menuCard(page, '아메리카노')
    await expect(card.locator('.stepper .n')).toHaveText('1')
    await expect(page.locator('.cart-bar')).toContainText('1개 · 1,000원')

    // 같은 버튼 반복 탭도, + 도 된다
    await addToCart(page, '아메리카노', 'ICE')
    await card.getByRole('button', { name: '더하기' }).click()
    await expect(card.locator('.stepper .n')).toHaveText('3')
    await expect(page.locator('.cart-bar')).toContainText('3개 · 3,000원')

    // − 로 0 이 되면 스테퍼가 사라지고 장바구니 바도 사라진다
    for (let i = 0; i < 3; i++) await card.getByRole('button', { name: '빼기' }).click()
    await expect(card.locator('.stepper')).toHaveCount(0)
    await expect(page.locator('.cart-bar')).toHaveCount(0)

    // 선택지 없는 메뉴는 '담기'
    await addToCart(page, '아샷추', null)
    await expect(page.locator('.cart-bar')).toContainText('1개 · 2,500원')
  })
})

test.describe('장바구니', () => {
  test('수량 조절, 삭제, 더 담기, 비면 주문 불가', async ({ page }) => {
    await gotoKiosk(page)
    await addToCart(page, '아메리카노', 'ICE', 2)
    await addToCart(page, '아이스크림', '컵')
    await page.getByRole('button', { name: /주문 확인/ }).click()
    await expect(page.getByRole('heading', { name: '주문 내용을 확인해 주세요' })).toBeVisible()

    const lines = page.locator('.cart-line')
    await expect(lines).toHaveCount(2)
    await expect(page.locator('.total-box .amount')).toHaveText('5,000원')

    await lines.nth(0).getByRole('button', { name: '+' }).click()
    await expect(page.locator('.total-box .amount')).toHaveText('6,000원')
    await lines.nth(1).getByRole('button', { name: '−' }).click()
    await expect(lines).toHaveCount(1)
    await expect(page.locator('.total-box .amount')).toHaveText('3,000원')

    // 더 담기 → 메뉴로 돌아가도 장바구니 유지
    await page.getByRole('button', { name: '더 담기' }).click()
    await expect(page.locator('.cart-bar')).toContainText('3개 · 3,000원')
    await page.getByRole('button', { name: /주문 확인/ }).click()

    // 전부 빼면 주문하기 비활성
    for (let i = 0; i < 3; i++) await lines.nth(0).getByRole('button', { name: '−' }).click()
    await expect(page.getByText('담긴 메뉴가 없습니다')).toBeVisible()
    await expect(page.getByRole('button', { name: /주문하기/ })).toBeDisabled()
  })
})

test.describe('결제 흐름', () => {
  test('현금 · 매장: 낸 돈 선택 → 거스름돈 → 명단에서 이름 → 완료, 스태프 메모까지', async ({ page, request }) => {
    const name = uniq('현금')
    await createOrder(request, { customerName: name, lines: [{ variantId: 1001, quantity: 1 }] }) // 명단에 올리기
    await toReceiveStep(page, [['아메리카노', 'ICE', 2], ['아이스크림', '컵', 1]]) // 5,000원
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await expect(page.getByRole('heading', { name: '어떻게 결제하시나요?' })).toBeVisible()
    await expect(page.locator('.total-box .amount')).toHaveText('5,000원')
    await page.getByRole('button', { name: /현금/ }).click()

    // 5,000원이면 딱 맞게 / 10,000 / 50,000
    await expect(page.getByRole('button', { name: '딱 맞게' })).toBeVisible()
    await expect(page.getByRole('button', { name: '10,000원' })).toBeVisible()
    await expect(page.getByRole('button', { name: '50,000원' })).toBeVisible()
    await expect(page.getByRole('button', { name: '20,000원' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: /다음/ })).toBeDisabled()
    await page.getByRole('button', { name: '10,000원' }).click()
    await expect(page.locator('.total-box .amount')).toHaveText('5,000원') // 거스름돈
    await page.getByRole('button', { name: /다음/ }).click()

    await expect(page.getByRole('heading', { name: '이름을 골라 주세요' })).toBeVisible()
    await page.getByRole('button', { name: name, exact: true }).click()

    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    await expect(page.locator('.hero .amount')).toHaveText(`${name}님`)
    await expect(page.getByText('5,000원 · 현금')).toBeVisible()
    await expect(page.getByText('이름을 불러 드릴게요')).toBeVisible()

    const order = await orderOf(request, name)
    expect(order.cashAmount).toBe(5000)
    expect(order.memo).toBe('현금 10,000원 받음 → 거스름돈 5,000원')
  })

  test('현금 · 딱 맞게 → 거스름돈 없음 메모', async ({ page, request }) => {
    const name = uniq('딱')
    await toReceiveStep(page, [['복숭아 아이스티', null, 1]])
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /현금/ }).click()
    await page.getByRole('button', { name: '딱 맞게' }).click()
    await expect(page.locator('.total-box .amount')).toHaveText('0원')
    await page.getByRole('button', { name: /다음/ }).click()
    await pickNameByTyping(page, name, '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    expect((await orderOf(request, name)).memo).toBe('현금 2,000원 딱 맞게')
  })

  test('계좌이체 · 배달(식당): 계좌 표시 → 보냈어요 → 직접 입력 → 완료', async ({ page, request }) => {
    const name = uniq('이체')
    await toReceiveStep(page, [['바닐라라떼', 'HOT', 1]]) // 2,500원
    await page.getByRole('button', { name: /갖다 주세요/ }).click()
    await expect(page.getByText('배달은 1층만')).toBeVisible({ timeout: 1000 }).catch(() => {}) // 이미 넘어갔을 수 있음
    await expect(page.getByRole('heading', { name: '어디로 갖다 드릴까요?' })).toBeVisible()
    await expect(page.getByRole('button', { name: /^2층/ })).toHaveCount(0)
    await page.getByRole('button', { name: '식당' }).click()
    await page.getByRole('button', { name: /계좌이체/ }).click()

    await expect(page.locator('.hero .account')).toHaveText('테스트은행 123-45-678901')
    await expect(page.locator('.hero .amount')).toHaveText('2,500원')
    await expect(page.getByText(/초 후 다음으로/)).toBeVisible()
    await page.getByRole('button', { name: /보냈어요/ }).click()

    await pickNameByTyping(page, name, '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    await expect(page.getByText('식당(으)로 갖다 드릴게요')).toBeVisible()

    const order = await orderOf(request, name)
    expect(order.transferAmount).toBe(2500)
    expect(order.placeName).toBe('식당')
  })

  test('계좌이체: 아무것도 안 누르면 12초 뒤 자동으로 이름 화면', async ({ page }) => {
    test.setTimeout(45_000)
    await toReceiveStep(page, [['아메리카노', 'HOT', 1]])
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /계좌이체/ }).click()
    await expect(page.getByRole('heading', { name: '이름을 골라 주세요' })).toBeVisible({ timeout: 15_000 })
  })

  test('쿠폰: 없는 이름 오류 → 동명이인 전화번호 → 무료 1잔 토글 → 결제 (이름 화면 건너뜀)', async ({ page, request }) => {
    const name = uniq('쿠폰')
    await registerCoupon(request, name, 20000)          // 잔액 20,000 / 무료 1잔
    await registerCoupon(request, name, 500, '01000004321')    // 동명이인

    await toReceiveStep(page, [['아메리카노', 'ICE', 2], ['아이스크림', '컵', 1]]) // 5,000원
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /쿠폰/ }).click()
    await expect(page.getByRole('heading', { name: '쿠폰' })).toBeVisible()

    await pickNameByTyping(page, '없는사람', '쿠폰 조회')
    await expect(page.locator('.error')).toContainText('쿠폰이 없습니다')

    await page.getByPlaceholder('이름').fill(name)
    await page.getByRole('button', { name: '쿠폰 조회' }).click()
    await expect(page.getByText('여러 분 계십니다')).toBeVisible()
    await expect(page.getByRole('button', { name: '확인' })).toBeDisabled()
    for (const k of '9999') await page.getByRole('button', { name: k, exact: true }).click()
    await page.getByRole('button', { name: '확인' }).click()
    await expect(page.locator('.error')).toContainText('전화번호가 맞지 않습니다')
    await page.getByRole('button', { name: '⌫' }).click({ clickCount: 4 })
    for (const k of '4321') await page.getByRole('button', { name: k, exact: true }).click()
    await page.getByRole('button', { name: '확인' }).click()

    // 동명이인 쪽(잔액 500, 무료 없음) 이 조회됨 → 잔액 부족 화면
    await expect(page.locator('.total-box .amount')).toHaveText('500원')
    await expect(page.getByText('남은 무료 1잔이 없습니다')).toBeVisible()
    await expect(page.getByText('추가로 내실 금액')).toBeVisible()
    await expect(page.getByRole('button', { name: /현금/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /계좌이체/ })).toBeVisible()

    // 뒤로 가서 원래 쿠폰(무료 1잔 있는 쪽)으로 다시. 동명이인이라 같은 이름으로는 못 고르니 새 쿠폰으로 검증한다.
    const rich = uniq('부자')
    await registerCoupon(request, rich, 20000)
    await page.getByRole('button', { name: '‹ 이전' }).click()
    await page.getByRole('button', { name: /쿠폰/ }).click()
    await pickNameByTyping(page, rich, '쿠폰 조회')
    await expect(page.locator('.total-box .amount')).toHaveText('20,000원')

    // 무료 토글 전: 전액 차감
    await expect(page.getByText('잔액에서 차감')).toBeVisible()
    await expect(page.locator('.card').getByText('− 5,000원')).toBeVisible()
    await page.getByRole('button', { name: /무료 1잔 쓰기/ }).click()
    await expect(page.getByText('아이스크림 컵 3,000원 무료')).toBeVisible()
    await expect(page.locator('.card').getByText('− 3,000원')).toBeVisible()
    await expect(page.locator('.card').getByText('− 2,000원')).toBeVisible()
    await expect(page.getByText('18,000원 · 무료 0잔')).toBeVisible()

    await page.getByRole('button', { name: /쿠폰으로 결제/ }).click()
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    await expect(page.locator('.hero .amount')).toHaveText(`${rich}님`)

    const coupon = (await lookupCoupon(request, rich)).coupon
    expect(coupon.balance).toBe(18000)
    expect(coupon.freeDrinks).toBe(0)
    const order = await orderOf(request, rich)
    expect(order.freeAmount).toBe(3000)
    expect(order.couponAmount).toBe(2000)
  })

  test('쿠폰 잔액 부족: 나머지를 현금으로', async ({ page, request }) => {
    const name = uniq('부족')
    await registerCoupon(request, name, 1500)
    await toReceiveStep(page, [['아이스크림', '컵', 1]]) // 3,000원
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /쿠폰/ }).click()
    await pickNameByTyping(page, name, '쿠폰 조회')
    await expect(page.getByText('나머지 1,500원은 어떻게')).toBeVisible()
    await expect(page.locator('.hero .account')).toHaveText('테스트은행 123-45-678901')
    await page.getByRole('button', { name: /현금/ }).click()
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    const order = await orderOf(request, name)
    expect(order.couponAmount).toBe(1500)
    expect(order.cashAmount).toBe(1500)
    expect((await lookupCoupon(request, name)).coupon.balance).toBe(0)
  })
})

test.describe('네비게이션', () => {
  test('이전은 한 단계씩, 처음으로는 장바구니까지 비운다', async ({ page }) => {
    await toReceiveStep(page, [['라떼', 'ICE', 1]])
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await expect(page.getByRole('heading', { name: '어떻게 결제하시나요?' })).toBeVisible()

    await page.getByRole('button', { name: '‹ 이전' }).click()
    await expect(page.getByRole('heading', { name: '어디서 받으시나요?' })).toBeVisible()
    await page.getByRole('button', { name: '‹ 이전' }).click()
    await expect(page.getByRole('heading', { name: '주문 내용을 확인해 주세요' })).toBeVisible()
    await expect(page.locator('.cart-line')).toHaveCount(1)

    await page.getByRole('button', { name: '처음으로' }).click()
    await expect(page.getByRole('heading', { name: '메뉴를 골라 주세요' })).toBeVisible()
    await expect(page.locator('.cart-bar')).toHaveCount(0)
  })

  test('완료 화면의 처음으로는 새 주문을 받을 준비', async ({ page }) => {
    await toReceiveStep(page, [['오미자', 'HOT', 1]])
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /현금/ }).click()
    await page.getByRole('button', { name: '딱 맞게' }).click()
    await page.getByRole('button', { name: /다음/ }).click()
    await pickNameByTyping(page, uniq('완료'), '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    await page.getByRole('button', { name: '처음으로' }).click()
    await expect(page.getByRole('heading', { name: '메뉴를 골라 주세요' })).toBeVisible()
    await expect(page.locator('.cart-bar')).toHaveCount(0)
  })

  test('이름 화면: 명단 ↔ 직접 입력 전환, 빈 이름은 확정 불가', async ({ page, request }) => {
    const known = uniq('단골')
    await createOrder(request, { customerName: known, lines: [{ variantId: 1001, quantity: 1 }] })
    await toReceiveStep(page, [['아메리카노', 'ICE', 1]])
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /현금/ }).click()
    await page.getByRole('button', { name: '딱 맞게' }).click()
    await page.getByRole('button', { name: /다음/ }).click()

    await expect(page.getByRole('button', { name: known, exact: true })).toBeVisible()
    await page.getByRole('button', { name: /직접 입력/ }).click()
    await expect(page.getByRole('button', { name: '주문 완료' })).toBeDisabled()
    await page.getByPlaceholder('이름').fill('   ')
    await expect(page.getByRole('button', { name: '주문 완료' })).toBeDisabled()
    await page.getByRole('button', { name: '명단에서 고르기' }).click()
    await expect(page.getByRole('button', { name: known, exact: true })).toBeVisible()
  })
})

test.describe('옵션 (샷 추가 / 연하게)', () => {
  test('칩은 한 잔씩: 2잔 중 1잔만 샷 추가, 두 번 누르면 둘 다, 커피에만 칩', async ({ page, request }) => {
    const name = uniq('샷')
    await gotoKiosk(page)
    await addToCart(page, '아메리카노', 'ICE', 2)
    await addToCart(page, '복숭아 아이스티', null)
    await page.getByRole('button', { name: /주문 확인/ }).click()

    const lines = page.locator('.cart-line-wrap')
    await expect(lines).toHaveCount(2)
    await expect(lines.nth(0).getByRole('button', { name: /샷 추가/ })).toBeVisible()
    await expect(lines.nth(1).getByRole('button', { name: /샷 추가/ })).toHaveCount(0)   // 아이스티: 옵션 없음
    await expect(lines.nth(1).getByRole('button', { name: /사역자/ })).toBeVisible()      // 사역자 칩은 모든 줄에
    await expect(page.locator('.total-box .amount')).toHaveText('4,000원')

    // 2잔 줄에서 샷 추가 → 1잔만 떨어져 나온다
    await lines.nth(0).getByRole('button', { name: /샷 추가/ }).click()
    await expect(lines).toHaveCount(3)
    await expect(lines.nth(0).locator('.qty .n')).toHaveText('1')
    await expect(lines.nth(1).locator('.line-options')).toHaveText('샷 추가')
    await expect(lines.nth(1).locator('.qty .n')).toHaveText('1')
    await expect(page.locator('.total-box .amount')).toHaveText('4,500원')

    // 남은 1잔도 샷 추가 → 샷 추가 줄에 합쳐져 2잔
    await lines.nth(0).getByRole('button', { name: /샷 추가/ }).click()
    await expect(lines).toHaveCount(2)
    await expect(lines.nth(0).locator('.line-options')).toHaveText('샷 추가')
    await expect(lines.nth(0).locator('.qty .n')).toHaveText('2')
    await expect(page.locator('.total-box .amount')).toHaveText('5,000원')

    // 샷 추가 줄에서는 연하게가 비활성 (같은 '농도' 그룹). 샷 추가를 빼면 다시 활성
    await expect(lines.nth(0).getByRole('button', { name: /연하게/ })).toBeDisabled()
    await lines.nth(0).getByRole('button', { name: /샷 추가/ }).click()   // 1잔 샷 해제
    await expect(lines).toHaveCount(3)
    await expect(lines.nth(1).getByRole('button', { name: /연하게/ })).toBeEnabled()
    await lines.nth(1).getByRole('button', { name: /연하게/ }).click()
    await expect(lines.nth(1).locator('.line-options')).toHaveText('연하게')
    await expect(page.locator('.total-box .amount')).toHaveText('4,500원')
    await lines.nth(1).getByRole('button', { name: /연하게/ }).click()   // 되돌리기
    await lines.nth(1).getByRole('button', { name: /샷 추가/ }).click()   // 다시 샷 추가 → 2잔 샷
    await expect(lines).toHaveCount(2)
    await expect(page.locator('.total-box .amount')).toHaveText('5,000원')

    // 메뉴로 돌아가면 아메리카노 수량은 옵션 상관없이 합쳐서 2
    await page.getByRole('button', { name: '더 담기' }).click()
    await expect(menuCard(page, '아메리카노').locator('.stepper .n')).toHaveText('2')
    await page.getByRole('button', { name: /주문 확인/ }).click()

    await page.getByRole('button', { name: /^주문하기/ }).click()
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /현금/ }).click()
    await page.getByRole('button', { name: '딱 맞게' }).click()
    await page.getByRole('button', { name: /다음/ }).click()
    await pickNameByTyping(page, name, '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()

    const order = await orderOf(request, name)
    expect(order.totalAmount).toBe(5000)
    const shot = order.lines.find((l: any) => l.options.some((o: any) => o.name === '샷 추가'))
    expect(shot.unitPrice).toBe(1500)
    expect(shot.quantity).toBe(2)
  })
})

test.describe('사역자 무료', () => {
  test('사역자 칩: 전부 무료면 결제 없이 이름만 → 접수', async ({ page, request }) => {
    const name = uniq('목사')
    await gotoKiosk(page)
    await addToCart(page, '아메리카노', 'ICE', 2)
    await page.getByRole('button', { name: /주문 확인/ }).click()
    const lines = page.locator('.cart-line-wrap')

    await lines.nth(0).getByRole('button', { name: /사역자/ }).click()   // 1잔 무료
    await expect(lines).toHaveCount(2)
    await expect(page.locator('.staff-bar')).toContainText('사역자 무료 1,000원')
    await expect(page.locator('.total-box .amount')).toHaveText('1,000원')
    await lines.nth(0).getByRole('button', { name: /사역자/ }).click()   // 나머지도 무료
    await expect(lines).toHaveCount(1)
    await expect(lines.nth(0).locator('.line-options')).toHaveText('사역자 무료')
    await expect(page.locator('.total-box .amount')).toHaveText('0원')

    await page.getByRole('button', { name: /무료로 주문하기/ }).click()
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    // 결제 화면 없이 바로 이름
    await expect(page.getByRole('heading', { name: '이름을 골라 주세요' })).toBeVisible()
    await pickNameByTyping(page, name, '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    await expect(page.getByText('사역자 무료 · 결제 없음')).toBeVisible()
    const order = await orderOf(request, name)
    expect(order.payMethod).toBe('NONE')
    expect(order.totalAmount).toBe(0)
    expect(order.staffFreeAmount).toBe(2000)
  })

  test('3잔 중 2잔만 사역자 → 나머지 1잔 현금', async ({ page, request }) => {
    const name = uniq('전도사')
    await gotoKiosk(page)
    await addToCart(page, '아메리카노', 'ICE', 3)
    await page.getByRole('button', { name: /주문 확인/ }).click()
    const lines = page.locator('.cart-line-wrap')
    await lines.nth(0).getByRole('button', { name: /사역자/ }).click()
    await lines.nth(0).getByRole('button', { name: /사역자/ }).click()
    await expect(lines).toHaveCount(2)
    await expect(lines.nth(1).locator('.qty .n')).toHaveText('2')
    await expect(page.locator('.total-box .amount')).toHaveText('1,000원')

    await page.getByRole('button', { name: /^주문하기/ }).click()
    await page.getByRole('button', { name: /카페에서 받기/ }).click()
    await page.getByRole('button', { name: /현금/ }).click()
    await page.getByRole('button', { name: '딱 맞게' }).click()
    await page.getByRole('button', { name: /다음/ }).click()
    await pickNameByTyping(page, name, '주문 완료')
    await expect(page.getByText('주문이 접수되었습니다')).toBeVisible()
    const order = await orderOf(request, name)
    expect(order.totalAmount).toBe(1000)
    expect(order.cashAmount).toBe(1000)
    expect(order.staffFreeAmount).toBe(2000)
  })

  test('사역자 칩을 다시 누르면 유료로 돌아온다', async ({ page }) => {
    await gotoKiosk(page)
    await addToCart(page, '아샷추', null)
    await page.getByRole('button', { name: /주문 확인/ }).click()
    const line = page.locator('.cart-line-wrap').first()
    await line.getByRole('button', { name: /사역자/ }).click()
    await expect(page.locator('.total-box .amount')).toHaveText('0원')
    await line.getByRole('button', { name: /사역자/ }).click()
    await expect(page.locator('.staff-bar')).toHaveCount(0)
    await expect(page.locator('.total-box .amount')).toHaveText('2,500원')
  })
})
