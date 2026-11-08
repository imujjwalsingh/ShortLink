import { createContext, use } from 'react'
import type { Session } from './session'

export interface AuthValue {
  session: Session | null
  login(email: string, password: string): Promise<void>
  /** Creates the account, then signs straight in. */
  register(email: string, password: string): Promise<void>
  logout(): void
}

export const AuthContext = createContext<AuthValue | null>(null)

export function useAuth(): AuthValue {
  const value = use(AuthContext)
  if (!value) throw new Error('useAuth must be used inside <AuthProvider>')
  return value
}
