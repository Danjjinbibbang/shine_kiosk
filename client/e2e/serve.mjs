// 테스트용 서버. 매번 새 DB 로 시작한다.
import { spawn } from 'node:child_process'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'

const jar = resolve('../server/build/libs/kiosk-server.jar')
const dir = mkdtempSync(join(tmpdir(), 'kiosk-e2e-'))
const child = spawn('java', ['-jar', jar], {
  stdio: 'inherit',
  env: {
    ...process.env,
    KIOSK_PORT: '8091',
    KIOSK_DB: join(dir, 'kiosk.db'),
    KIOSK_STAFF_PIN: '1234',
    KIOSK_BANK: '테스트은행 123-45-678901',
  },
})
process.on('SIGTERM', () => child.kill())
process.on('SIGINT', () => child.kill())
child.on('exit', (code) => process.exit(code ?? 0))
