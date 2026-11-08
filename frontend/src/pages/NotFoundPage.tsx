import { SearchX } from 'lucide-react'
import { Link } from 'react-router'
import { buttonClass } from '../components/ui/styles'

export function NotFoundContent({ title, message }: { title: string; message: string }) {
  return (
    <div className="flex flex-col items-center py-20 text-center">
      <span className="grid size-12 place-items-center rounded-full bg-wash text-ink-2">
        <SearchX className="size-6" aria-hidden />
      </span>
      <h1 className="mt-4 text-xl font-semibold tracking-tight">{title}</h1>
      <p className="mt-1 max-w-sm text-sm text-ink-2">{message}</p>
      <Link to="/" className={`${buttonClass('secondary', 'md')} mt-6`}>
        Back to dashboard
      </Link>
    </div>
  )
}

export function NotFoundPage() {
  return (
    <main className="px-4">
      <title>Page not found · ShortLink</title>
      <NotFoundContent title="Page not found" message="There's nothing at this address." />
    </main>
  )
}
