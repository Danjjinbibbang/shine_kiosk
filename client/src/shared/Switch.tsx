/**
 * 켜짐/꺼짐 스위치. 접근성 이름은 현재 상태 글자(예: 판매중 / 품절)라 E2E 에서 그 이름으로 찾는다.
 */
export function Switch({ on, onLabel, offLabel, disabled, onChange, small }: {
  on: boolean
  onLabel: string
  offLabel: string
  disabled?: boolean
  small?: boolean
  onChange: (next: boolean) => void
}) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={on ? onLabel : offLabel}
      className={'switch' + (on ? ' on' : '') + (small ? ' small' : '')} disabled={disabled}
      onClick={() => onChange(!on)}>
      <span className="switch-track"><span className="switch-knob" /></span>
      <span className="switch-text">{on ? onLabel : offLabel}</span>
    </button>
  )
}
