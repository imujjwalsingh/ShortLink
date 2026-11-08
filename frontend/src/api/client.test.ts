import { describe, expect, it, vi } from 'vitest'
import { getSession, startSession } from '../auth/session'
import { ApiError, request } from './client'

function respond(status: number, body?: unknown, contentType = 'application/problem+json') {
  const fetchMock = vi.fn().mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), {
      status,
      headers: body === undefined ? {} : { 'Content-Type': contentType },
    }),
  )
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

async function caught(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    if (error instanceof ApiError) return error
    throw error
  }
  throw new Error('expected the request to fail')
}

describe('request', () => {
  it('calls the gateway with JSON and the bearer token', async () => {
    startSession('token-123', 3600)
    const fetchMock = respond(200, { ok: true }, 'application/json')

    await expect(request('/api/urls', { method: 'POST', body: { a: 1 } })).resolves.toEqual({ ok: true })

    const [url, init] = fetchMock.mock.calls[0]
    expect(url).toBe('http://localhost:8080/api/urls')
    expect(init.headers).toMatchObject({ Authorization: 'Bearer token-123', 'Content-Type': 'application/json' })
    expect(init.body).toBe('{"a":1}')
  })

  it('omits the token for auth: false', async () => {
    startSession('token-123', 3600)
    const fetchMock = respond(200, {}, 'application/json')
    await request('/api/auth/login', { method: 'POST', body: {}, auth: false })
    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })

  it('turns problem+json validation errors into field errors', async () => {
    respond(400, {
      title: 'Bad Request',
      status: 400,
      detail: 'Request validation failed',
      errors: [{ field: 'customAlias', message: 'customAlias must be 3-32 chars' }],
    })
    const error = await caught(request('/api/urls', { method: 'POST', body: {} }))
    expect(error.status).toBe(400)
    expect(error.message).toBe('Request validation failed')
    expect(error.fieldErrors).toEqual({ customAlias: 'customAlias must be 3-32 chars' })
  })

  it("explains the gateway's empty-bodied 429", async () => {
    respond(429)
    const error = await caught(request('/api/urls'))
    expect(error.status).toBe(429)
    expect(error.message).toMatch(/too many requests/i)
  })

  it('reports an unreachable server as status 0', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))
    const error = await caught(request('/api/urls'))
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/can't reach the server/i)
  })

  it('ends the session when the token is rejected', async () => {
    startSession('stale-token', 3600)
    respond(401)
    await caught(request('/api/urls'))
    expect(getSession()).toBeNull()
  })

  it('keeps the session on a failed sign-in, which sends no token', async () => {
    startSession('token-123', 3600)
    respond(401, { detail: 'Invalid email or password' })
    const error = await caught(request('/api/auth/login', { method: 'POST', body: {}, auth: false }))
    expect(error.message).toBe('Invalid email or password')
    expect(getSession()).not.toBeNull()
  })

  it('resolves 204 No Content to undefined', async () => {
    respond(204)
    await expect(request('/api/urls/abc', { method: 'DELETE' })).resolves.toBeUndefined()
  })
})
