import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

const backend = process.env.AGENTOA_BACKEND || 'http://localhost:18081'

export default defineConfig({
  plugins: [uni()],
  server: {
    port: 5174,
    proxy: {
      '/api': { target: backend, changeOrigin: true },
      '/auth': { target: backend, changeOrigin: true },
      '/ws': { target: backend, ws: true, changeOrigin: true }
    }
  }
})
