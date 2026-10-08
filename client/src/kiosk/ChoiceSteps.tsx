import type { PayMethod } from '../shared/types'
import { won } from '../shared/types'

export function PaymentStep({ total, onSelect }: { total: number; onSelect: (m: PayMethod) => void }) {
  return (
    <div className="stack">
      <div className="total-box">
        <span className="label">결제 금액</span>
        <span className="amount">{won(total)}</span>
      </div>
      <button className="btn huge" onClick={() => onSelect('TRANSFER')}>🏦 계좌이체</button>
      <button className="btn huge" onClick={() => onSelect('COUPON')}>🎫 쿠폰</button>
      <button className="btn huge" onClick={() => onSelect('CASH')}>💵 현금</button>
    </div>
  )
}
