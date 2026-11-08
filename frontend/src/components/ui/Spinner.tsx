import { LoaderCircle } from 'lucide-react'

export function PageSpinner({ label = 'Loading' }: { label?: string }) {
  return (
    <div role="status" className="flex justify-center py-24 text-ink-2">
      <LoaderCircle className="size-6 animate-spin" aria-hidden />
      <span className="sr-only">{label}</span>
    </div>
  )
}
