import { TriangleAlert } from 'lucide-react'
import { useRouteError } from 'react-router'

/** Last-resort screen when a page throws while rendering. */
export function RouteError() {
  const error = useRouteError()
  if (import.meta.env.DEV) console.error(error)
  return (
    <main className="flex min-h-dvh flex-col items-center justify-center px-4 text-center">
      <title>Something went wrong · ShortLink</title>
      <span className="grid size-12 place-items-center rounded-full bg-danger-wash text-danger">
        <TriangleAlert className="size-6" aria-hidden />
      </span>
      <h1 className="mt-4 text-xl font-semibold tracking-tight">Something went wrong</h1>
      <p className="mt-1 max-w-sm text-sm text-ink-2">An unexpected error stopped this page from loading.</p>
      <button
        type="button"
        onClick={() => window.location.reload()}
        className="mt-6 h-10 cursor-pointer rounded-lg border border-line bg-surface px-4 text-sm font-medium hover:bg-wash"
      >
        Reload the page
      </button>
    </main>
  )
}
