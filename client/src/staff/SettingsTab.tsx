import { useEffect, useState } from 'react'
import { api } from '../shared/api'
import { MenuAdmin } from './MenuAdmin'
import { OptionAdmin } from './OptionAdmin'
import { PlaceAdmin } from './PlaceAdmin'

type Section = 'menu' | 'options' | 'places'

/** 스태프 = 관리자. 메뉴·옵션·배달 장소를 여기서 고친다. */
export function SettingsTab({ onToast }: { onToast: (msg: string) => void }) {
  const [section, setSection] = useState<Section>('menu')
  const [categories, setCategories] = useState<string[]>([])

  useEffect(() => {
    if (section !== 'options') return
    api.adminMenu().then((items) => setCategories([...new Set(items.map((i) => i.category))])).catch(() => {})
  }, [section])

  return (
    <div className="stack">
      <div className="tabs sub">
        <button className={'btn' + (section === 'menu' ? ' selected' : '')} onClick={() => setSection('menu')}>메뉴</button>
        <button className={'btn' + (section === 'options' ? ' selected' : '')} onClick={() => setSection('options')}>옵션</button>
        <button className={'btn' + (section === 'places' ? ' selected' : '')} onClick={() => setSection('places')}>배달 장소</button>
      </div>
      {section === 'menu' && <MenuAdmin onToast={onToast} />}
      {section === 'options' && <OptionAdmin onToast={onToast} categories={categories} />}
      {section === 'places' && <PlaceAdmin onToast={onToast} />}
    </div>
  )
}
