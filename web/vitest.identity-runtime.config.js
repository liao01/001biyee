import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  test: {
    include: ['src/modules/identity/identityRuntime.integration.js'],
    environment: 'jsdom',
    environmentOptions: { jsdom: { url: 'http://127.0.0.1:5173' } },
    testTimeout: 30000,
  },
})
