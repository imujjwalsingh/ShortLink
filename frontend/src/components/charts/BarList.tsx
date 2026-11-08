import { Link } from 'react-router'
import { formatCount } from '../../lib/format'

export interface BarListItem {
  label: string
  value: number
  /** Makes the label a link (e.g. to the link's analytics page). */
  to?: string
}

interface BarListProps {
  items: BarListItem[]
  emptyText: string
  /** Spoken after each value by screen readers, e.g. "clicks". */
  unit?: string
}

/**
 * Ranked horizontal bars for one series of nominal categories: one color for
 * every bar, 12px thick, square at the baseline with a 4px rounded data end,
 * and the value printed at the tip, so no value hides behind a hover.
 */
export function BarList({ items, emptyText, unit = 'clicks' }: BarListProps) {
  if (items.length === 0) {
    return <p className="py-6 text-center text-sm text-ink-2">{emptyText}</p>
  }
  const max = Math.max(...items.map((item) => item.value), 1)

  return (
    <ul className="space-y-3">
      {items.map((item) => (
        <li key={item.label} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] items-center gap-3 text-sm">
          {item.to ? (
            <Link to={item.to} className="truncate text-accent-text hover:underline" title={item.label}>
              {item.label}
            </Link>
          ) : (
            <span className="truncate text-ink" title={item.label}>
              {item.label}
            </span>
          )}
          <span className="flex min-w-0 items-center gap-2">
            <span
              aria-hidden
              className="h-3 rounded-r-[4px] bg-series-1"
              // Scaled to 80% so the longest bar still leaves room for its value.
              style={{ width: `max(2px, ${(item.value / max) * 80}%)` }}
            />
            <span className="tabular-nums text-ink-2">
              {formatCount(item.value)}
              <span className="sr-only"> {unit}</span>
            </span>
          </span>
        </li>
      ))}
    </ul>
  )
}
