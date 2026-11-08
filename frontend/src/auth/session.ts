/**
 * The signed-in session: the JWT from auth-service and when it expires.
 *
 * A tiny external store rather than React state, so the API client can read
 * the token and end the session on a 401 without going through React, and
 * components subscribe with useSyncExternalStore. Persisted to localStorage
 * so a reload keeps you signed in until the token expires (1 hour).
 */

const STORAGE_KEY = 'shortlink.session'

/** Treat the token as expired slightly early, so it never dies mid-request. */
export const EXPIRY_SKEW_MS = 10_000

export interface Session {
  token: string
  /** Epoch millis, computed from the login response's expiresIn. */
  expiresAt: number
}

export type EndReason = 'signed-out' | 'expired'

type Listener = () => void

const listeners = new Set<Listener>()
let current: Session | null = readStored()
let lastEndReason: EndReason | null = null

function isLive(session: Session): boolean {
  return session.expiresAt - EXPIRY_SKEW_MS > Date.now()
}

function readStored(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<Session>
    if (typeof parsed.token !== 'string' || typeof parsed.expiresAt !== 'number') return null
    const session = { token: parsed.token, expiresAt: parsed.expiresAt }
    return isLive(session) ? session : null
  } catch {
    return null
  }
}

function store(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage blocked (private mode, disabled site data): the session
    // still works, it just won't survive a reload.
  }
}

function notify() {
  listeners.forEach((listener) => listener())
}

export function getSession(): Session | null {
  return current
}

export function subscribe(listener: Listener): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function startSession(token: string, expiresInSeconds: number): Session {
  current = { token, expiresAt: Date.now() + expiresInSeconds * 1000 }
  lastEndReason = null
  store(current)
  notify()
  return current
}

export function endSession(reason: EndReason): void {
  if (!current) return
  current = null
  lastEndReason = reason
  store(null)
  notify()
}

/** Why the last session ended, so the sign-in page can say "session expired". */
export function endReason(): EndReason | null {
  return lastEndReason
}

/** The bearer token for a request, or null (and the session ends) if it has expired. */
export function currentToken(): string | null {
  if (current && !isLive(current)) endSession('expired')
  return current?.token ?? null
}

// Signing in or out in another tab applies here too.
window.addEventListener('storage', (event) => {
  if (event.key !== STORAGE_KEY && event.key !== null) return
  const next = readStored()
  if (next?.token === current?.token) return
  if (!next) lastEndReason = 'signed-out'
  current = next
  notify()
})
