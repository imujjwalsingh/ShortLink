import { useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { asApiError, type ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import { AuthLayout } from '../components/AuthLayout'
import { Alert } from '../components/ui/Alert'
import { Button } from '../components/ui/Button'
import { Field } from '../components/ui/Field'

export function RegisterPage() {
  const { register } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [mismatch, setMismatch] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState(false)

  // 409: an account with this email already exists.
  const emailError = error?.fieldErrors.email ?? (error?.status === 409 ? error.message : undefined)
  const passwordError = error?.fieldErrors.password
  const generalError = error && !emailError && !passwordError ? error.message : null

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (password !== confirm) {
      setMismatch(true)
      return
    }
    setPending(true)
    setError(null)
    try {
      await register(email.trim(), password)
      // Signed in now; RedirectIfAuthed moves on to the dashboard.
    } catch (caught) {
      setError(asApiError(caught))
      setPending(false)
    }
  }

  return (
    <AuthLayout
      title="Create your account"
      subtitle="Shorten links and see who clicks them."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" state={location.state} className="font-medium text-accent-text hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <title>Create account · ShortLink</title>
      {generalError && <Alert className="mb-5">{generalError}</Alert>}
      <form onSubmit={onSubmit} className="space-y-4">
        <Field
          label="Email"
          type="email"
          autoComplete="email"
          required
          maxLength={254}
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          error={emailError}
          autoFocus
        />
        <Field
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={72}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          hint="8 to 72 characters."
          error={passwordError}
        />
        <Field
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(event) => {
            setConfirm(event.target.value)
            setMismatch(false)
          }}
          error={mismatch ? "Passwords don't match." : undefined}
        />
        <Button type="submit" loading={pending} className="w-full">
          Create account
        </Button>
      </form>
    </AuthLayout>
  )
}
