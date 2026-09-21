/**
 * 입력 규칙. 서버 Validation.java 와 같은 값. 화면에서 먼저 막고, 최종 판단은 서버가 한다.
 */
export const RULES = {
  nameMax: 20,        // 손님/쿠폰/옵션/카테고리/장소 이름
  menuNameMax: 30,
  labelMax: 10,       // ICE/HOT 같은 선택지
  memoMax: 200,
  priceMax: 100_000,
  priceUnit: 100,
  chargeMax: 1_000_000,
  chargeUnit: 1_000,
  freeDrinksMax: 100,
  qtyMax: 99,
  floorMax: 99,
}

/** 숫자만 남긴 휴대폰 번호가 형식에 맞는지 (010/011/016/017/018/019 + 7~8자리) */
export function isMobile(raw: string): boolean {
  return /^01[016789][0-9]{7,8}$/.test(raw.replace(/[^0-9]/g, ''))
}

/** 타이핑하는 대로 010-1234-5678 모양으로. 숫자 11자리(하이픈 포함 13자)까지만 */
export function formatPhoneInput(raw: string): string {
  const d = raw.replace(/[^0-9]/g, '').slice(0, 11)
  if (d.length <= 3) return d
  if (d.length <= 7) return `${d.slice(0, 3)}-${d.slice(3)}`
  if (d.length <= 10) return `${d.slice(0, 3)}-${d.slice(3, 6)}-${d.slice(6)}`
  return `${d.slice(0, 3)}-${d.slice(3, 7)}-${d.slice(7)}`
}

export function isChargeAmount(n: number): boolean {
  return n > 0 && n <= RULES.chargeMax && n % RULES.chargeUnit === 0
}

export function isPrice(n: number): boolean {
  return n >= 0 && n <= RULES.priceMax && n % RULES.priceUnit === 0
}
