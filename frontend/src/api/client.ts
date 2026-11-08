import { currentToken, endSession } from '../auth/session'

/**
 * Every request goes to the API gateway; the services themselves are not
 * reachable from the browser. Baked in at build time (Vite env vars).
 */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, '')

/**
 * A failed request, normalized: `message` is always something a user can
 * read, and `fieldErrors` maps a request field to its validation message.
 * status 0 means the request never got a response (network down, backend
 * not running, CORS rejection).
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors
  }
}

/** Narrows anything thrown into an ApiError, for code that catches. */
export function asApiError(error: unknown): ApiError {
  if (error instanceof ApiError) return error
  return new ApiError(0, error instanceof Error ? error.message : 'Something went wrong.')
}

/** RFC 9457 problem+json, as every backend service returns errors. */
interface ProblemDetail {
  title?: string
  detail?: string
  errors?: { field: string; message: string }[]
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  /** Attach the session's bearer token (default true). */
  auth?: boolean
  signal?: AbortSignal
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', body, auth = true, signal } = options
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth ? currentToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError(0, "Can't reach the server. Check that the backend is running and try again.")
  }

  // The token was rejected (expired early, signing key rotated, account
  // removed): drop the session so the app sends the user back to sign in.
  if (response.status === 401 && token) endSession('expired')

  if (!response.ok) throw await toApiError(response)
  if (response.status === 204) return undefined as T
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

async function toApiError(response: Response): Promise<ApiError> {
  let problem: ProblemDetail = {}
  try {
    problem = (await response.json()) as ProblemDetail
  } catch {
    // Empty or non-JSON body, e.g. the gateway's 429.
  }
  const fieldErrors = Object.fromEntries((problem.errors ?? []).map((e) => [e.field, e.message]))
  return new ApiError(response.status, messageFor(response.status, problem), fieldErrors)
}

function messageFor(status: number, problem: ProblemDetail): string {
  if (status === 429) return 'Too many requests. Wait a few seconds and try again.'
  if (problem.detail) return problem.detail
  if (status === 401) return 'Your session has expired. Please sign in again.'
  if (status === 404) return 'Not found.'
  if (status >= 500) return 'The server ran into a problem. Try again in a moment.'
  return problem.title ?? `Request failed (${status}).`
}
