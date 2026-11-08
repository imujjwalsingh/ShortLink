import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './AuthContext'
import { endReason } from './session'

export interface AuthRedirectState {
  /** Where to return after signing in. */
  from?: string
  expired?: boolean
}

/** Pages that need a session; everyone else is sent to sign in. */
export function RequireAuth() {
  const { session } = useAuth()
  const location = useLocation()
  if (!session) {
    const state: AuthRedirectState = {
      from: location.pathname + location.search,
      expired: endReason() === 'expired',
    }
    return <Navigate to="/login" replace state={state} />
  }
  return <Outlet />
}

/**
 * Sign-in and register pages. Once a session exists this sends the user on
 * to where they were headed, which is also how a successful sign-in
 * navigates: the pages themselves never redirect.
 */
export function RedirectIfAuthed() {
  const { session } = useAuth()
  const location = useLocation()
  if (session) {
    const from = (location.state as AuthRedirectState | null)?.from
    // Only same-app paths: never "//other.site" or an absolute URL.
    const safe = from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
    return <Navigate to={safe} replace />
  }
  return <Outlet />
}
