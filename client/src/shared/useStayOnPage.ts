import { useEffect } from 'react'

/**
 * 브라우저 뒤로가기(안드로이드 back 제스처 포함)로 이 화면을 벗어나지 않게 한다.
 * 스태프 화면에서 뒤로 가면 손님용 키오스크로 넘어가 버리는 걸 막는 용도.
 * 방법: 같은 주소로 더미 기록을 하나 쌓아 두고, 뒤로가기가 그걸 소비하면 다시 쌓는다.
 * 화면 안의 '이전'·'닫기' 버튼은 그대로 쓰면 된다 (주소를 바꾸지 않는다).
 */
export function useStayOnPage() {
  useEffect(() => {
    const push = () => window.history.pushState({ stay: true }, '', window.location.href)
    push()
    window.addEventListener('popstate', push)
    return () => window.removeEventListener('popstate', push)
  }, [])
}
