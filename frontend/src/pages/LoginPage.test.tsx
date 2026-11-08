import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { RouterProvider, createMemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthProvider'
import { RedirectIfAuthed, RequireAuth } from '../auth/guards'
import { endSession, startSession } from '../auth/session'
import { LoginPage } from './LoginPage'

function renderApp(initialPath: string) {
  const router = createMemoryRouter(
    [
      { element: <RedirectIfAuthed />, children: [{ path: '/login', element: <LoginPage /> }] },
      {
        path: '/',
        element: <RequireAuth />,
        children: [
          { index: true, element: <p>Dashboard page</p> },
          { path: 'links/:code', element: <p>Link page</p> },
        ],
      },
    ],
    { initialEntries: [initialPath] },
  )
  render(
    <QueryClientProvider client={new QueryClient()}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
}

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

async function signIn(password: string) {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), 'ada@example.com')
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

describe('sign-in flow', () => {
  it('sends signed-out visitors to sign in', async () => {
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it("shows the server's message for wrong credentials", async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(json(401, { status: 401, detail: 'Invalid email or password' })))
    renderApp('/login')
    await signIn('wrong-password')
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password')
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('returns to the page that required sign-in', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(json(200, { accessToken: 'jwt-token', tokenType: 'Bearer', expiresIn: 3600 }))
    vi.stubGlobal('fetch', fetchMock)
    renderApp('/links/abc')

    await signIn('correct-password')

    expect(await screen.findByText('Link page')).toBeInTheDocument()
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({ email: 'ada@example.com', password: 'correct-password' })
    expect(JSON.parse(localStorage.getItem('shortlink.session')!).token).toBe('jwt-token')
  })

  it('says so when the session expired', async () => {
    startSession('token', 3600)
    endSession('expired')
    renderApp('/')
    expect(await screen.findByText(/your session expired/i)).toBeInTheDocument()
  })
})
