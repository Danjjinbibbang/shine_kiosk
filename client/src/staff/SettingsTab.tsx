import { useCallback, useEffect, useState } from 'react'
import { api } from '../shared/api'
import type { Category } from '../shared/types'
import { CategoryAdmin } from './CategoryAdmin'
import { MenuAdmin } from './MenuAdmin'
import { OptionAdmin } from './OptionAdmin'
import { PlaceAdmin } from './PlaceAdmin'

type Section = 'menu' | 'options' | 'categories' | 'places'

const SECTIONS: { key: Section; icon: string; title: string; hint: string }[] = [
  { key: 'menu', icon: '☕', title: '메뉴', hint: '이름 · 가격 · ICE/HOT · 품절' },
  { key: 'options', icon: '➕', title: '옵션', hint: '샷 추가 · 연하게 같은 잔 단위 선택' },
  { key: 'categories', icon: '🗂️', title: '카테고리', hint: '키오스크 상단 탭. 순서 · 이름' },
  { key: 'places', icon: '🚶', title: '배달 장소', hint: '배달 받을 수 있는 곳' },
]

/**
 * 스태프 = 관리자. 카테고리·메뉴·옵션·배달 장소를 여기서 고친다.
 * 폰 설정 앱처럼 목록에서 하나를 고르면 그 화면만 보이고, 상단의 ‹ 로 돌아온다 (탭 아래 탭이 겹치지 않게).
 */
export function SettingsTab({ onToast }: { onToast: (msg: string) => void }) {
  const [section, setSection] = useState<Section | null>(null)
  const [categories, setCategories] = useState<Category[]>([])

  const loadCategories = useCallback(() => {
    api.categories().then(setCategories).catch(() => {})
  }, [])

  useEffect(() => { loadCategories() }, [loadCategories, section])

  const names = categories.map((c) => c.name)
  const itemCount = categories.reduce((s, c) => s + c.itemCount, 0)
  const countOf = (key: Section) =>
    key === 'menu' ? `${itemCount}개` : key === 'categories' ? `${categories.length}개` : ''

  if (section === null) {
    return (
      <div className="settings-home">
        {SECTIONS.map((s) => (
          <button key={s.key} className="settings-row" aria-label={s.title} onClick={() => setSection(s.key)}>
            <span className="icon">{s.icon}</span>
            <span className="grow">
              <span className="title">{s.title}</span>
              <span className="hint">{s.hint}</span>
            </span>
            <span className="muted count">{countOf(s.key)}</span>
            <span className="muted">›</span>
          </button>
        ))}
      </div>
    )
  }

  const current = SECTIONS.find((s) => s.key === section)!
  return (
    <div className="stack">
      <div className="section-head">
        <button className="btn ghost" onClick={() => setSection(null)} aria-label="설정으로">‹ 설정</button>
        <b>{current.icon} {current.title}</b>
      </div>
      {section === 'menu' && <MenuAdmin onToast={onToast} categories={names} />}
      {section === 'options' && <OptionAdmin onToast={onToast} categories={names} />}
      {section === 'categories' && <CategoryAdmin onToast={onToast} />}
      {section === 'places' && <PlaceAdmin onToast={onToast} />}
    </div>
  )
}
