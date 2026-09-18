import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 개발 중에는 vite dev 서버(5173)에서 API/WebSocket 을 Spring(8080)으로 넘긴다.
// 빌드 결과물은 서버 jar 안 static/ 으로 들어가므로 같은 origin 이 된다.
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true,
  },
})
