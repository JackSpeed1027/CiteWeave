import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

const api = { '/api': 'http://127.0.0.1:8087' }

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: api },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true, proxy: api },
  build: { rollupOptions: { input: { workspace: 'index.html', prototype: 'prototype.html' } } },
})
