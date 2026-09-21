import { useState } from 'react'
import { MenuAdmin } from './MenuAdmin'
import { PlaceAdmin } from './PlaceAdmin'

type Section = 'menu' | 'places'

/** 스태프 = 관리자. 메뉴와 배달 장소를 여기서 고친다. */
export function SettingsTab({ onToast }: { onToast: (msg: string) => void }) {
  const [section, setSection] = useState<Section>('menu')
  return (
    <div className="stack">
      <div className="tabs sub">
        <button className={'btn' + (section === 'menu' ? ' selected' : '')} onClick={() => setSection('menu')}>메뉴</button>
        <button className={'btn' + (section === 'places' ? ' selected' : '')} onClick={() => setSection('places')}>배달 장소</button>
      </div>
      {section === 'menu' && <MenuAdmin onToast={onToast} />}
      {section === 'places' && <PlaceAdmin onToast={onToast} />}
    </div>
  )
}
