import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../shared/api'
import { RULES, isPrice } from '../shared/rules'
import type { AdminItem, AdminVariant, SaveItemRequest } from '../shared/types'
import { won } from '../shared/types'
import { Switch } from '../shared/Switch'

/**
 * 메뉴 관리. 자주 쓰는 건 품절 토글이라 목록에서 바로 되게 하고,
 * 이름/가격/선택지 편집은 모달로.
 */
export function MenuAdmin({ onToast, categories }: { onToast: (msg: string) => void; categories: string[] }) {
  const [items, setItems] = useState<AdminItem[] | null>(null)
  const [editing, setEditing] = useState<AdminItem | 'new' | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api.adminMenu().then(setItems).catch((e) => onToast(e.message))
  }, [onToast])

  useEffect(() => { load() }, [load])

  const run = async (fn: () => Promise<unknown>, doneMsg?: string) => {
    setBusy(true)
    try {
      await fn()
      if (doneMsg) onToast(doneMsg)
      load()
    } catch (e) {
      onToast(e instanceof ApiError ? e.message : '처리하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const move = (index: number, dir: -1 | 1) => {
    if (!items) return
    const target = index + dir
    if (target < 0 || target >= items.length) return
    const ids = items.map((i) => i.id)
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    void run(() => api.reorderMenu(ids))
  }

  if (!items) return <div className="empty">불러오는 중…</div>

  return (
    <div className="stack">
      <button className="btn primary" onClick={() => setEditing('new')}>＋ 새 메뉴</button>
      {items.length === 0 && <div className="empty">메뉴가 없습니다. 새 메뉴를 추가해 주세요.</div>}

      {items.map((item, idx) => (
        <div key={item.id} className={'card admin-item' + (item.available ? '' : ' off')}>
          <div className="row between">
            <div>
              <div className="admin-name">{item.name} <span className="muted admin-cat">{item.category}</span></div>
              <div className="muted admin-variants">
                {item.variants.map((v) => `${v.label ?? ''} ${won(v.price)}${v.available ? '' : ' (품절)'}`.trim()).join(' · ')}
              </div>
            </div>
            <Switch on={item.available} onLabel="판매중" offLabel="품절" disabled={busy}
              onChange={(next) => void run(() => api.setMenuAvailable(item.id, next), next ? `${item.name} 판매중` : `${item.name} 품절`)} />
          </div>
          <div className="row admin-actions">
            <button className="btn" disabled={busy || idx === 0} onClick={() => move(idx, -1)} aria-label="위로">↑</button>
            <button className="btn" disabled={busy || idx === items.length - 1} onClick={() => move(idx, 1)} aria-label="아래로">↓</button>
            <span className="grow" />
            <button className="btn" disabled={busy} onClick={() => setEditing(item)}>수정</button>
            <button className="btn danger" disabled={busy} onClick={() => {
              if (window.confirm(`"${item.name}" 메뉴를 삭제할까요?\n지난 주문 기록은 남습니다.`)) {
                void run(() => api.deleteMenuItem(item.id), `${item.name} 삭제됨`)
              }
            }}>삭제</button>
          </div>
        </div>
      ))}

      {editing && (
        <MenuItemModal item={editing === 'new' ? null : editing}
          categories={categories}
          onClose={() => setEditing(null)}
          onSaved={(msg) => { setEditing(null); onToast(msg); load() }} />
      )}
    </div>
  )
}

interface ModalProps {
  item: AdminItem | null
  categories: string[]
  onClose: () => void
  onSaved: (msg: string) => void
}

const EMPTY_VARIANT: AdminVariant = { id: null, label: null, price: 0, available: true }

function MenuItemModal({ item, categories, onClose, onSaved }: ModalProps) {
  const [name, setName] = useState(item?.name ?? '')
  const [category, setCategory] = useState(item?.category ?? (categories[0] ?? '커피'))
  const [available, setAvailable] = useState(item?.available ?? true)
  const [variants, setVariants] = useState<AdminVariant[]>(item?.variants ?? [{ ...EMPTY_VARIANT }])
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const setVariant = (i: number, patch: Partial<AdminVariant>) =>
    setVariants((vs) => vs.map((v, k) => (k === i ? { ...v, ...patch } : v)))

  const useIceHot = () => {
    const price = variants[0]?.price ?? 0
    setVariants([{ ...EMPTY_VARIANT, label: 'ICE', price }, { ...EMPTY_VARIANT, label: 'HOT', price }])
  }

  const save = async () => {
    setBusy(true)
    setError(null)
    const body: SaveItemRequest = {
      name: name.trim(),
      category: category.trim(),
      available,
      variants: variants.map((v) => ({ ...v, label: v.label?.trim() || null })),
    }
    try {
      if (item) await api.updateMenuItem(item.id, body)
      else await api.createMenuItem(body)
      onSaved(item ? `${body.name} 수정됨` : `${body.name} 추가됨`)
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '저장하지 못했습니다.')
    } finally {
      setBusy(false)
    }
  }

  const valid = name.trim() && category.trim() && variants.length > 0 && variants.every((v) => isPrice(v.price))
  const badPrice = variants.some((v) => !isPrice(v.price))

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <div className="row between">
          <h2>{item ? '메뉴 수정' : '새 메뉴'}</h2>
          <button className="btn ghost" onClick={onClose}>닫기</button>
        </div>
        {error && <div className="error">{error}</div>}

        <div className="field">
          <label>이름</label>
          <input className="text-input" value={name} maxLength={RULES.menuNameMax} onChange={(e) => setName(e.target.value)} placeholder="예: 유자차" autoFocus={!item} />
        </div>

        <div className="field">
          <label>카테고리 <span className="muted">(새 카테고리는 설정 &gt; 카테고리에서)</span></label>
          <div className="chips">
            {categories.map((c) => (
              <button key={c} className={'btn' + (category === c ? ' selected' : '')} onClick={() => setCategory(c)}>{c}</button>
            ))}
          </div>
        </div>

        <div className="field">
          <label>선택지와 가격 <span className="muted">(ICE/HOT 처럼 나뉘면 여러 줄, 하나면 이름 비워두기)</span></label>
          <div className="stack">
            {variants.map((v, i) => (
              <div key={i} className="row variant-edit">
                <input className="text-input" placeholder="예: ICE" value={v.label ?? ''} maxLength={RULES.labelMax}
                  onChange={(e) => setVariant(i, { label: e.target.value })} />
                <input className="text-input price" inputMode="numeric" placeholder="가격" value={v.price || ''} maxLength={6}
                  onChange={(e) => setVariant(i, { price: Number(e.target.value.replace(/[^0-9]/g, '')) || 0 })} />
                <Switch small on={v.available} onLabel="판매" offLabel="품절" onChange={(next) => setVariant(i, { available: next })} />
                <button className="btn ghost" disabled={variants.length === 1} aria-label="선택지 삭제"
                  onClick={() => setVariants((vs) => vs.filter((_, k) => k !== i))}>✕</button>
              </div>
            ))}
          </div>
          {badPrice && <div className="error" style={{ fontSize: 13 }}>가격은 100원 단위, {won(RULES.priceMax)}까지입니다.</div>}
          <div className="chips" style={{ marginTop: 6 }}>
            <button className="btn" onClick={() => setVariants((vs) => [...vs, { ...EMPTY_VARIANT, price: vs[vs.length - 1]?.price ?? 0 }])}>＋ 선택지 추가</button>
            <button className="btn" onClick={useIceHot}>ICE / HOT 으로</button>
          </div>
        </div>

        <div className="field">
          <Switch on={available} onLabel="판매중" offLabel="품절 (고객 메뉴에서 숨김)" onChange={setAvailable} />
        </div>

        <button className="btn big primary" disabled={busy || !valid} onClick={() => void save()}>
          {busy ? '저장 중…' : '저장'}
        </button>
      </div>
    </div>
  )
}
