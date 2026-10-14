import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { viteSingleFile } from 'vite-plugin-singlefile'

// "npm run build:demo" gera um único HTML com dados mockados (para demonstração).
export default defineConfig(({ mode }) => ({
  plugins: mode === 'demo' ? [react(), viteSingleFile()] : [react()],
  build: { outDir: mode === 'demo' ? 'dist-demo' : 'dist' },
  server: { port: 5173 },
}))
