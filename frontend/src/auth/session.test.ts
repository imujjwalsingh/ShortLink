import { describe, expect, it, vi } from 'vitest'
import { EXPIRY_SKEW_MS, currentToken, endReason, getSession, startSession } from './session'

describe('session', () => {
  it('persists across reloads', async () => {
    startSession('token-abc', 3600)
    vi.resetModules()
    const reloaded = await import('./session')
    expect(reloaded.getSession()?.token).toBe('token-abc')
  })

  it("doesn't restore an expired session from storage", async () => {
    localStorage.setItem('shortlink.session', JSON.stringify({ token: 'old', expiresAt: Date.now() - 1000 }))
    vi.resetModules()
    const reloaded = await import('./session')
    expect(reloaded.getSession()).toBeNull()
  })

  it('ignores malformed storage', async () => {
    localStorage.setItem('shortlink.session', '{not json')
    vi.resetModules()
    const reloaded = await import('./session')
    expect(reloaded.getSession()).toBeNull()
  })

  it('ends the session, as expired, once the token is about to run out', () => {
    vi.useFakeTimers()
    startSession('token-abc', 60)
    expect(currentToken()).toBe('token-abc')

    vi.advanceTimersByTime(60_000 - EXPIRY_SKEW_MS)
    expect(currentToken()).toBeNull()
    expect(getSession()).toBeNull()
    expect(endReason()).toBe('expired')
  })
})
