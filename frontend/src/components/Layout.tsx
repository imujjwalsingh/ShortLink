import { LogOut } from 'lucide-react'
import { Link, Outlet } from 'react-router'
import { useMe } from '../api/queries'
import { useAuth } from '../auth/AuthContext'
import { Logo } from './Logo'
import { ThemeToggle } from './ThemeToggle'
import { Button } from './ui/Button'

/** Shell for signed-in pages: header with account controls, then the page. */
export function Layout() {
  const { logout } = useAuth()
  // Also validates a token restored from storage: a 401 here ends the session.
  const me = useMe()

  return (
    <div className="min-h-dvh">
      <header className="sticky top-0 z-10 border-b border-line bg-surface/85 backdrop-blur-sm">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
          <Link to="/" aria-label="ShortLink, go to dashboard" className="rounded-lg">
            <Logo />
          </Link>
          <div className="flex min-w-0 items-center gap-1 sm:gap-2">
            {me.data && (
              <span className="hidden max-w-60 truncate text-sm text-ink-2 sm:inline" title={me.data.email}>
                {me.data.email}
              </span>
            )}
            <ThemeToggle />
            <Button variant="ghost" size="sm" onClick={logout} aria-label="Sign out">
              <LogOut className="size-4" aria-hidden />
              <span className="hidden sm:inline">Sign out</span>
            </Button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  )
}
