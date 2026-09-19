import { defineConfig } from '@playwright/test'

// E2E: 빌드된 jar 를 8091 포트에 임시 DB 로 띄우고, 설치된 크롬으로 실제 화면을 돌린다.
// 먼저 `../gradlew bootJar` 로 jar 가 있어야 한다.
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  workers: 1, // DB 를 공유하므로 직렬
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:8091',
    locale: 'ko-KR',
    channel: 'chrome',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'kiosk-tablet', testMatch: /kiosk\.spec\.ts/, use: { viewport: { width: 800, height: 1333 } } },
    { name: 'staff-phone', testMatch: /staff\.spec\.ts/, use: { viewport: { width: 412, height: 915 } } },
  ],
  webServer: {
    command: 'node e2e/serve.mjs',
    url: 'http://localhost:8091/api/menu',
    reuseExistingServer: false,
    timeout: 90_000,
  },
})
