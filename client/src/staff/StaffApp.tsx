import { useAutoReload } from '../shared/useAutoReload'
import { useCallback, useEffect, useState } from 'react'
import { api, ApiError, getStaffToken, setStaffToken, subscribeOrders } from '../shared/api'
import type { DailySummary } from '../shared/types'
import { won } from '../shared/types'
import { LoginPage } from './LoginPage'
import { OrdersTab } from './OrdersTab'
import { CouponTab } from './CouponTab'
import { ReportTab } from './ReportTab'
import { SettingsTab } from './SettingsTab'

type Tab = 'pending' | 'done' | 'coupon' | 'report' | 'settings'

export function StaffApp() {
  const [authed, setAuthed] = useState<boolean | null>(null)
  const [tab, setTab] = useState<Tab>('pending')
  const [summary, setSummary] = useState<DailySummary | null>(null)
  const [tick, setTick] = useState(0) // 주문 변경 신호. 자식 탭이 이걸 보고 다시 불러온다.
  const [online, setOnline] = useState(true)
  const [toast, setToast] = useState<string | null>(null)
  // 새 버전이 나오면 새로고침 (로그인은 토큰이 폰에 저장돼 있어 유지된다)
  useAutoReload(true)

  useEffect(() => {
    if (!getStaffToken()) {
      setAuthed(false)
      return
    }
    api.staffCheck().then((r) => setAuthed(r.valid)).catch(() => setAuthed(false))
  }, [])

  const refresh = useCallback(() => {
    setTick((t) => t + 1)
    api.summary().then((s) => { setSummary(s); setOnline(true) })
      .catch((e) => {
        if (e instanceof ApiError && e.status === 401) setAuthed(false)
        else setOnline(false)
      })
  }, [])

  useEffect(() => {
    if (!authed) return
    refresh()
    const unsubscribe = subscribeOrders(refresh)
    const poll = window.setInterval(refresh, 20_000) // 웹소켓이 조용히 죽었을 때 대비
    return () => {
      unsubscribe()
      window.clearInterval(poll)
    }
  }, [authed, refresh])

  const showToast = useCallback((msg: string) => {
    setToast(msg)
    window.setTimeout(() => setToast(null), 2500)
  }, [])

  const logout = async () => {
    try { await api.staffLogout() } catch { /* 무시 */ }
    setStaffToken(null)
    setAuthed(false)
  }

  if (authed === null) return <div className="empty">확인 중…</div>
  if (!authed) return <LoginPage onLogin={() => setAuthed(true)} />

  return (
    <div className="staff">
      {!online && <div className="offline">서버와 연결이 끊겼습니다. 와이파이를 확인해 주세요.</div>}
      <header className="staff-head">
        <div className="row between">
          <b style={{ fontSize: 20 }}>열린 카페 · 스태프</b>
          <button className="btn ghost" onClick={logout}>나가기</button>
        </div>
        {summary && (
          <div className="summary">
            <span>오늘 <b>{summary.orderCount}건</b></span>
            <span>합계 <b>{won(summary.totalAmount)}</b></span>
            <span>현금 {won(summary.cashAmount)}</span>
            <span>이체 {won(summary.transferAmount)}</span>
            <span>쿠폰 {won(summary.couponAmount)}</span>
            {summary.freeAmount > 0 && <span>무료잔 {won(summary.freeAmount)}</span>}
            {summary.staffFreeAmount > 0 && <span>사역자 {won(summary.staffFreeAmount)}</span>}
          </div>
        )}
        <div className="tabs">
          <button className={'btn' + (tab === 'pending' ? ' selected' : '')} onClick={() => setTab('pending')}>만들 것</button>
          <button className={'btn' + (tab === 'done' ? ' selected' : '')} onClick={() => setTab('done')}>완료</button>
          <button className={'btn' + (tab === 'coupon' ? ' selected' : '')} onClick={() => setTab('coupon')}>쿠폰</button>
          <button className={'btn' + (tab === 'report' ? ' selected' : '')} onClick={() => setTab('report')}>매출</button>
          <button className={'btn' + (tab === 'settings' ? ' selected' : '')} onClick={() => setTab('settings')}>설정</button>
        </div>
      </header>

      <main className="staff-body">
        {tab === 'pending' && <OrdersTab status="PENDING" tick={tick} onChanged={refresh} onToast={showToast} />}
        {tab === 'done' && <OrdersTab status="DONE" tick={tick} onChanged={refresh} onToast={showToast} />}
        {tab === 'coupon' && <CouponTab onToast={showToast} />}
        {tab === 'report' && <ReportTab onToast={showToast} />}
        {tab === 'settings' && <SettingsTab onToast={showToast} />}
      </main>

      {toast && <div className="toast">{toast}</div>}
    </div>
  )
}
