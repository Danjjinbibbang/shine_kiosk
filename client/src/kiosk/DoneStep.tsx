import { useEffect, useState } from 'react'
import type { Order } from '../shared/types'
import { PAY_LABEL, won } from '../shared/types'

const AUTO_RESET_SECONDS = 8

export function DoneStep({ order, onReset }: { order: Order; onReset: () => void }) {
  const [left, setLeft] = useState(AUTO_RESET_SECONDS)

  // 음성 안내. 지원 안 되는 브라우저면 조용히 넘어간다.
  useEffect(() => {
    try {
      const u = new SpeechSynthesisUtterance(`${order.customerName}님, ${won(order.totalAmount)} 주문이 접수되었습니다.`)
      u.lang = 'ko-KR'
      window.speechSynthesis?.speak(u)
    } catch { /* 무시 */ }
    return () => { try { window.speechSynthesis?.cancel() } catch { /* 무시 */ } }
  }, [order])

  useEffect(() => {
    if (left <= 0) {
      onReset()
      return
    }
    const t = window.setTimeout(() => setLeft((s) => s - 1), 1000)
    return () => window.clearTimeout(t)
  }, [left, onReset])

  const where = order.receiveType === 'DELIVERY' ? `${order.placeName}(으)로 갖다 드릴게요` : '완성되면 이름을 불러 드릴게요'

  return (
    <div className="hero">
      <div className="title">주문이 접수되었습니다 ✓</div>
      <div className="amount">{order.customerName}님</div>
      <div className="sub" style={{ fontSize: 30, color: 'var(--ink)' }}>
        {order.payMethod === 'NONE' ? '사역자 무료' : `${won(order.totalAmount)} · ${PAY_LABEL[order.payMethod]}`}
        {order.payMethod !== 'NONE' && order.staffFreeAmount > 0 && <small className="muted"> (사역자 무료 {won(order.staffFreeAmount)} 제외)</small>}
      </div>
      <div className="sub">{where}</div>
      <div className="sub muted" style={{ marginTop: 40 }}>{left}초 후 처음 화면으로</div>
      <div style={{ marginTop: 16 }}>
        <button className="btn big" onClick={onReset}>처음으로</button>
      </div>
    </div>
  )
}
