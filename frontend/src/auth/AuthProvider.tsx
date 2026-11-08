import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useSyncExternalStore, type ReactNode } from 'react'
import { api } from '../api/endpoints'
import { AuthContext, type AuthValue } from './AuthContext'
import { EXPIRY_SKEW_MS, endSession, getSession, startSession, subscribe } from './session'

export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useSyncExternalStore(subscribe, getSession)
  const queryClient = useQueryClient()

  // End the session on time even if the tab sits idle and makes no requests.
  useEffect(() => {
    if (!session) return
    const delay = Math.max(0, session.expiresAt - EXPIRY_SKEW_MS - Date.now())
    const timer = window.setTimeout(() => endSession('expired'), delay)
    return () => window.clearTimeout(timer)
  }, [session])

  // Never show one account's cached links to whoever signs in next.
  useEffect(() => {
    if (!session) queryClient.clear()
  }, [session, queryClient])

  const value = useMemo<AuthValue>(
    () => ({
      session,
      async login(email, password) {
        const token = await api.login(email, password)
        startSession(token.accessToken, token.expiresIn)
      },
      async register(email, password) {
        await api.register(email, password)
        const token = await api.login(email, password)
        startSession(token.accessToken, token.expiresIn)
      },
      logout() {
        endSession('signed-out')
      },
    }),
    [session],
  )

  return <AuthContext value={value}>{children}</AuthContext>
}
