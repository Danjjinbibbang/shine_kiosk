import { defineConfig } from '@playwright/test'

// E2E: 빌드된 jar 를 8091 포트에 임시 DB 로 띄우고 실제 화면을 돌린다. 먼저 `../gradlew bootJar`.
// 기본은 설치된 크롬(안드로이드 태블릿/폰과 같은 엔진), kiosk-ipad 는 사파리와 같은 WebKit 으로 아이패드 크기.
//   npx playwright test                    크롬만 (기본)
//   npx playwright test --project=kiosk-ipad   아이패드(사파리 엔진)
export default defineConfig({
  testDir: './e2e',
  timeout: 30_000,
  workers: 1, // DB 를 공유하므로 직렬
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:8091',
    locale: 'ko-KR',
    screenshot: 'only-on-failure',
  },
  projects: [
    { name: 'kiosk-tablet', testMatch: /kiosk\.spec\.ts/, use: { channel: 'chrome', viewport: { width: 800, height: 1333 } } },
    { name: 'staff-phone', testMatch: /staff\.spec\.ts/, use: { channel: 'chrome', viewport: { width: 412, height: 915 } } },
    // 아이패드(사파리): 같은 키오스크 시나리오를 WebKit + iPad 10.9" 세로 크기로. 브라우저는 `npx playwright install webkit`
    {
      name: 'kiosk-ipad',
      testMatch: /kiosk\.spec\.ts/,
      use: { browserName: 'webkit', viewport: { width: 820, height: 1180 }, hasTouch: true, deviceScaleFactor: 2 },
    },
  ],
  webServer: {
    command: 'node e2e/serve.mjs',
    url: 'http://localhost:8091/api/menu',
    reuseExistingServer: false,
    timeout: 90_000,
  },
})
