import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { asApiError, type ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type { AuthRedirectState } from '../auth/guards'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/ui/Alert'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'

export function LoginPage() {
  const { login } = useAuth()
  const location = useLocation()
  const redirect = location.state as AuthRedirectState | null
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState(false)

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setPending(true)
    setError(null)
    try {
      await login(email.trim(), password)
      // No navigate() here: RedirectIfAuthed takes over once the session exists.
    } catch (caught) {
      setError(asApiError(caught))
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      subtitle="Welcome back. Sign in to manage your links."
      footer={
        <>
          New to ShortLink?{' '}
          <Link to="/register" state={location.state} className="font-medium text-accent-text hover:underline">
            Create an account
          </Link>
        </>
      }
    >
      <title>Sign in · ShortLink</title>
      {redirect?.expired && !error && (
        <Alert tone="info" className="mb-5">
          Your session expired. Please sign in again.
        </Alert>
      )}
      {error && <Alert className="mb-5">{error.message}</Alert>}
      <form onSubmit={onSubmit} className="space-y-4">
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={error?.fieldErrors.email}
          autoFocus
        />
        <Field
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          maxLength={72}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          error={error?.fieldErrors.password}
        />
        <Button type="submit" loading={pending} className="w-full">
          Sign in
        </Button>
      </form>
    </AuthLayout>
  )
}
