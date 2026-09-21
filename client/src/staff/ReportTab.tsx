import { useEffect, useMemo, useState } from 'react'
import { api, ApiError, getStaffToken } from '../shared/api'
import type { DayReport, Order } from '../shared/types'
import { PAY_LABEL, won } from '../shared/types'

const WEEKDAY = ['일', '월', '화', '수', '목', '금', '토']

function labelOf(date: string): string {
  const [y, m, d] = date.split('-').map(Number)
  const wd = WEEKDAY[new Date(y, m - 1, d).getDay()]
  return `${m}/${d} (${wd})`
}

/** 매출 기록. 날짜별 집계 → 날짜 탭하면 그날 주문. 월 합계와 CSV 내보내기. */
export function ReportTab({ onToast }: { onToast: (msg: string) => void }) {
  const [days, setDays] = useState<DayReport[] | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [orders, setOrders] = useState<Order[] | null>(null)

  useEffect(() => {
    api.reportDays().then(setDays).catch((e) => onToast(e.message))
  }, [onToast])

  useEffect(() => {
    if (!open) { setOrders(null); return }
    setOrders(null)
    api.reportOrders(open).then(setOrders).catch((e) => onToast(e.message))
  }, [open, onToast])

  // 월별로 묶는다 (최근 달부터)
  const months = useMemo(() => {
    const map = new Map<string, DayReport[]>()
    for (const d of days ?? []) {
      const key = d.date.slice(0, 7)
      if (!map.has(key)) map.set(key, [])
      map.get(key)!.push(d)
    }
    return [...map.entries()]
  }, [days])

  if (!days) return <div className="empty">불러오는 중…</div>
  if (days.length === 0) return <div className="empty">아직 기록이 없습니다.</div>

  return (
    <div className="stack">
      <div className="row" style={{ justifyContent: 'flex-end' }}>
        <button className="btn" style={{ minHeight: 40, fontSize: 14 }} onClick={() => void downloadCsv('/api/staff/reports/days.csv', '매출-일별.csv', onToast)}>
          ⬇ 일별 CSV
        </button>
      </div>

      {months.map(([month, list]) => {
        const sum = (f: (d: DayReport) => number) => list.reduce((s, d) => s + f(d), 0)
        return (
          <section key={month} className="stack">
            <div className="month-head">
              <b>{Number(month.slice(5))}월</b>
              <span className="muted">{sum((d) => d.orderCount)}건 · 주문 {won(sum((d) => d.totalAmount))} · 충전 {won(sum((d) => d.couponChargeAmount))}</span>
            </div>
            {list.map((d) => (
              <div key={d.date} className={'card day-card' + (open === d.date ? ' open' : '')}>
                <button className="day-head" onClick={() => setOpen(open === d.date ? null : d.date)}>
                  <span className="day-label">{labelOf(d.date)}</span>
                  <span className="grow muted">{d.orderCount}건</span>
                  <b>{won(d.totalAmount)}</b>
                  <span className="muted">{open === d.date ? '▲' : '▼'}</span>
                </button>
                <div className="day-breakdown muted">
                  현금 {won(d.cashAmount)} · 이체 {won(d.transferAmount)} · 쿠폰 {won(d.couponAmount)}
                  {d.freeAmount > 0 && ` · 무료잔 ${won(d.freeAmount)}`}
                  {d.staffFreeAmount > 0 && ` · 사역자 ${won(d.staffFreeAmount)}`}
                  {d.couponChargeAmount > 0 && <span className="charge"> · 쿠폰 충전 입금 {won(d.couponChargeAmount)}</span>}
                </div>

                {open === d.date && (
                  <div className="stack day-orders">
                    <div className="row" style={{ justifyContent: 'flex-end' }}>
                      <button className="btn" style={{ minHeight: 36, fontSize: 13 }}
                        onClick={() => void downloadCsv(`/api/staff/reports/orders.csv?date=${d.date}`, `주문-${d.date}.csv`, onToast)}>
                        ⬇ 이날 주문 CSV
                      </button>
                    </div>
                    {orders === null && <div className="muted">불러오는 중…</div>}
                    {orders?.length === 0 && <div className="muted">주문 없음 (충전만 있던 날)</div>}
                    {orders?.map((o) => (
                      <div key={o.id} className={'report-order' + (o.status === 'CANCELED' ? ' canceled' : '')}>
                        <div className="row">
                          <span className="no">#{o.orderNo}</span>
                          <b className="grow">{o.customerName}</b>
                          <span className="muted">{o.createdAt.slice(11, 16)}</span>
                          <span className={'status ' + o.status.toLowerCase()}>{o.status === 'DONE' ? '완료' : o.status === 'CANCELED' ? '취소' : '대기'}</span>
                        </div>
                        <div className="muted lines">
                          {o.lines.map((l) => `${l.menuName}${l.variantLabel ? ' ' + l.variantLabel : ''}${l.options.length ? ' (' + l.options.map((x) => x.name).join(', ') + ')' : ''} ×${l.quantity}${l.staffFreeQty ? ` (사역자 ${l.staffFreeQty})` : ''}`).join(' / ')}
                        </div>
                        <div className="muted">{won(o.totalAmount)} · {PAY_LABEL[o.payMethod]}{o.receiveType === 'DELIVERY' && ` · 🚶 ${o.placeName}`}</div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </section>
        )
      })}
    </div>
  )
}

/** 토큰 헤더가 필요해서 링크 대신 받아서 저장한다. */
async function downloadCsv(path: string, filename: string, onToast: (m: string) => void) {
  try {
    const res = await fetch(path, { headers: { 'X-Staff-Token': getStaffToken() ?? '' } })
    if (!res.ok) throw new ApiError('내려받지 못했습니다.', res.status)
    const blob = await res.blob()
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    a.remove()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
    onToast(`${filename} 저장됨`)
  } catch (e) {
    onToast(e instanceof ApiError ? e.message : '내려받지 못했습니다.')
  }
}
