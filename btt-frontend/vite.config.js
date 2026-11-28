import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv } from 'vite'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'BTT_')
  const proxy = { '/api': env.BTT_API_TARGET || 'http://127.0.0.1:8080' }
  return { plugins: [react()], server: { proxy }, preview: { proxy } }
})
