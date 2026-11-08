import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, vi } from 'vitest'
import { endSession } from '../auth/session'

afterEach(() => {
  // Vitest globals are off, so Testing Library can't register this itself.
  cleanup()
  endSession('signed-out')
  localStorage.clear()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})
