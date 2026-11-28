import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  timeout: 180_000,
  expect: { timeout: 25_000 },
  workers: 1,
  reporter: 'list',
  use: {
    baseURL: process.env.BTT_E2E_URL || 'http://localhost:8080',
    timezoneId: 'UTC',
    actionTimeout: 20_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
})
