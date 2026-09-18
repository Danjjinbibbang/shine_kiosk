import { NamePicker } from './NamePicker'

export function NameStep({ submitting, onSelect }: { submitting: boolean; onSelect: (name: string) => void }) {
  return (
    <NamePicker title="완성되면 이 이름으로 불러 드립니다" confirmLabel={submitting ? '접수 중…' : '주문 완료'}
      disabled={submitting} onSelect={onSelect} />
  )
}
