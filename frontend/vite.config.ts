/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react(), tailwindcss()],
  // The browser calls the API gateway directly (http://localhost:8080), and
  // the gateway's CORS policy only allows http://localhost:3000
  // (CORS_ALLOWED_ORIGINS in docker-compose.yml). strictPort makes Vite fail
  // loudly if 3000 is taken, instead of moving to 3001 where every API call
  // would be blocked by CORS.
  server: { port: 3000, strictPort: true },
  preview: { port: 3000, strictPort: true },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    // Run in a zone behind UTC: that's where formatting a UTC day in local
    // time would label it with the previous date.
    env: { TZ: 'America/Los_Angeles' },
  },
})
