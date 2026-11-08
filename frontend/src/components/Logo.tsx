import { Link2 } from 'lucide-react'

export function Logo() {
  return (
    <span className="inline-flex items-center gap-2 font-semibold tracking-tight text-ink">
      <span className="grid size-8 place-items-center rounded-lg bg-accent text-white">
        <Link2 className="size-4.5" aria-hidden />
      </span>
      <span className="text-lg">ShortLink</span>
    </span>
  )
}
