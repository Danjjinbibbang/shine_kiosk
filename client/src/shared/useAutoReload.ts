import { useEffect, useRef } from 'react'

/**
 * 서버(jar)가 새 버전으로 바뀌면 열려 있던 화면을 알아서 새로고침한다.
 * 태블릿 키오스크는 몇 주씩 켜 둔 채라 업데이트 뒤에도 옛 화면이 남기 쉽다.
 * 판단은 index.html 의 번들 파일 이름(빌드마다 해시가 바뀜)을 지금 실행 중인 것과 비교해서.
 * canReload 가 참일 때만 실제로 새로고침한다 (손님이 주문 중이거나 스태프가 뭔가 하는 중엔 기다린다).
 */
export function useAutoReload(canReload: boolean, intervalMs = 60_000) {
  const stale = useRef(false)
  const can = useRef(canReload)
  can.current = canReload

  useEffect(() => {
    const running = currentBundle()
    if (!running) return
    let stopped = false
    const check = async () => {
      if (stopped) return
      try {
        const html = await fetch('/index.html', { cache: 'no-store' }).then((r) => (r.ok ? r.text() : ''))
        const latest = html.match(/assets\/index-[^"']+\.js/)?.[0]
        if (latest && latest !== running) stale.current = true
      } catch {
        // 오프라인이면 다음에
      }
      if (stale.current && can.current) {
        stopped = true
        window.location.reload()
      }
    }
    const timer = window.setInterval(check, intervalMs)
    const onVisible = () => { if (document.visibilityState === 'visible') void check() }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      stopped = true
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [intervalMs])

  // 새 버전을 알고 있는데 아직 못 바꾼 상태에서 canReload 가 참이 되면 바로
  useEffect(() => {
    if (canReload && stale.current) window.location.reload()
  }, [canReload])
}

function currentBundle(): string | null {
  const script = Array.from(document.scripts).find((s) => /assets\/index-[^/]+\.js/.test(s.src))
  return script ? (script.src.match(/assets\/index-[^/]+\.js/)?.[0] ?? null) : null
}
