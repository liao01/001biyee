import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  base: mode === 'production' ? '/travel/' : '/',
  plugins: [vue()],
  test: {
    environment: 'jsdom',
    clearMocks: true,
    restoreMocks: true,
  },
}))
