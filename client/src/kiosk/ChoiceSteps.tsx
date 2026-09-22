import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { FloorGroup, PayMethod, Place, ReceiveType } from '../shared/types'
import { won } from '../shared/types'

export function ReceiveStep({ onSelect }: { onSelect: (t: ReceiveType) => void }) {
  return (
    <div className="stack">
      <button className="btn huge" onClick={() => onSelect('STORE')}>☕ 카페에서 받기</button>
      <button className="btn huge" onClick={() => onSelect('DELIVERY')}>
        <span>🚶 배달</span>
      </button>
    </div>
  )
}

/**
 * 배달 가능한 층이 하나면 층 선택을 건너뛰고 바로 장소를 고른다.
 * (현재는 1층만. 나중에 층이 늘면 층 → 세부 장소 2단계가 자동으로 살아난다)
 */
export function PlaceStep({ onSelect }: { onSelect: (p: Place) => void }) {
  const [floors, setFloors] = useState<FloorGroup[] | null>(null)
  const [floor, setFloor] = useState<FloorGroup | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.places().then((f) => {
      setFloors(f)
      if (f.length === 1) setFloor(f[0])
    }).catch((e) => setError(e.message))
  }, [])

  if (error) return <div className="error">{error}</div>
  if (!floors) return <div className="empty">장소를 불러오는 중…</div>
  if (floors.length === 0) return <div className="empty">지금은 배달을 하지 않습니다. 카페에서 받아 주세요.</div>

  const pickFloor = (f: FloorGroup) => {
    if (f.places.length === 1) onSelect(f.places[0])
    else setFloor(f)
  }

  if (!floor) {
    return (
      <div className="stack">
        <div className="muted center">몇 층으로 갖다 드릴까요?</div>
        <div className="grid wide">
          {floors.map((f) => (
            <button key={f.floor} className="btn huge" onClick={() => pickFloor(f)}>
              {f.floor}층{f.places.length === 1 ? ` · ${f.places[0].name}` : ''}
            </button>
          ))}
        </div>
      </div>
    )
  }

  return (
    <div className="stack">
      {floors.length > 1 && (
        <div className="row">
          <button className="btn ghost" onClick={() => setFloor(null)}>‹ 다른 층</button>
          <div className="muted">{floor.floor}층 어디로 갖다 드릴까요?</div>
        </div>
      )}
      <div className="grid wide">
        {floor.places.map((p) => (
          <button key={p.id} className="btn huge" onClick={() => onSelect(p)}>{p.name}</button>
        ))}
      </div>
    </div>
  )
}

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
