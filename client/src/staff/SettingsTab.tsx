import { useCallback, useEffect, useState } from 'react'
import { api } from '../shared/api'
import { CategoryAdmin } from './CategoryAdmin'
import { MenuAdmin } from './MenuAdmin'
import { OptionAdmin } from './OptionAdmin'
import { PlaceAdmin } from './PlaceAdmin'

type Section = 'menu' | 'options' | 'categories' | 'places'

/** 스태프 = 관리자. 카테고리·메뉴·옵션·배달 장소를 여기서 고친다. */
export function SettingsTab({ onToast }: { onToast: (msg: string) => void }) {
  const [section, setSection] = useState<Section>('menu')
  const [categories, setCategories] = useState<string[]>([])

  const loadCategories = useCallback(() => {
    api.categories().then((cs) => setCategories(cs.map((c) => c.name))).catch(() => {})
  }, [])

  useEffect(() => { loadCategories() }, [loadCategories, section])

  return (
    <div className="stack">
      <div className="tabs sub">
        <button className={'btn' + (section === 'menu' ? ' selected' : '')} onClick={() => setSection('menu')}>메뉴</button>
        <button className={'btn' + (section === 'options' ? ' selected' : '')} onClick={() => setSection('options')}>옵션</button>
        <button className={'btn' + (section === 'categories' ? ' selected' : '')} onClick={() => setSection('categories')}>카테고리</button>
        <button className={'btn' + (section === 'places' ? ' selected' : '')} onClick={() => setSection('places')}>배달 장소</button>
      </div>
      {section === 'menu' && <MenuAdmin onToast={onToast} categories={categories} />}
      {section === 'options' && <OptionAdmin onToast={onToast} categories={categories} />}
      {section === 'categories' && <CategoryAdmin onToast={onToast} />}
      {section === 'places' && <PlaceAdmin onToast={onToast} />}
    </div>
  )
}
