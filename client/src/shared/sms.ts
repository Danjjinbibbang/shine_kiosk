import type { Coupon, Order } from './types'
import { won } from './types'

/**
 * 스태프 폰의 문자 앱을 내용이 채워진 채로 연다. 실제 발송은 스태프가 보내기를 눌러야 한다.
 * 안드로이드는 ?body=, iOS 는 &body= 를 쓴다.
 */
export function smsLink(phone: string, body: string): string {
  // 아이패드 사파리는 기본이 데스크톱 모드라 userAgent 에 Macintosh 로 나온다 → 터치 지원 여부로 같이 판단
  const ua = navigator.userAgent
  const ios = /iPhone|iPad|iPod/i.test(ua) || (/Mac/i.test(ua) && navigator.maxTouchPoints > 1)
  return `sms:${phone}${ios ? '&' : '?'}body=${encodeURIComponent(body)}`
}

/** 쿠폰 결제 주문의 잔액 안내 문자. 잔액은 이미 차감된 현재 값이다. */
export function couponBalanceMessage(order: Order, coupon: Coupon): string {
  const [, m, d] = order.orderDate.split('-')
  const used: string[] = []
  if (order.freeAmount) used.push(`무료 1잔 ${order.freeItemName ?? ''}`.trim())
  if (order.couponAmount) used.push(`쿠폰 ${won(order.couponAmount)}`)
  const lines = [
    `[열린카페] ${coupon.name}님 ${Number(m)}/${Number(d)} 주문`,
    used.length ? `사용: ${used.join(' + ')}` : '',
    `남은 잔액 ${won(coupon.balance)} · 무료 1잔 ${coupon.freeDrinks}잔`,
  ]
  return lines.filter(Boolean).join('\n')
}

interface SmsTestHooks { __lastSms?: string; __smsCapture?: boolean }

export function openSms(phone: string, body: string) {
  const link = smsLink(phone, body)
  const hooks = window as unknown as SmsTestHooks
  hooks.__lastSms = link // E2E 테스트가 확인할 수 있게
  if (hooks.__smsCapture) return // 테스트에선 실제로 문자 앱을 열지 않는다 (헤드리스 브라우저가 멈춤)
  window.location.href = link
}
