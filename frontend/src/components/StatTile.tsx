interface StatTileProps {
  label: string
  /** Already formatted (formatCount), or undefined while loading. */
  value: string | undefined
  hint?: string
  className?: string
}

export function StatTile({ label, value, hint, className = '' }: StatTileProps) {
  return (
    <div className={`rounded-xl border border-line bg-surface p-4 ${className}`}>
      <p className="text-sm text-ink-2">{label}</p>
      <p className="mt-1 text-2xl font-semibold tracking-tight">
        {value ?? <span className="inline-block h-7 w-14 animate-pulse rounded-md bg-wash align-middle" aria-label="Loading" />}
      </p>
      {hint && <p className="mt-0.5 text-xs text-ink-2">{hint}</p>}
    </div>
  )
}
