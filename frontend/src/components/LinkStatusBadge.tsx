import { CircleCheck, CircleSlash, Clock } from 'lucide-react'
import type { LinkStatus } from '../lib/format'

// Status colors are reserved for state and always paired with an icon and a
// label, so the color never carries the meaning on its own.
const statuses: Record<LinkStatus, { label: string; Icon: typeof Clock; iconClass: string }> = {
  active: { label: 'Active', Icon: CircleCheck, iconClass: 'text-good' },
  expired: { label: 'Expired', Icon: Clock, iconClass: 'text-serious' },
  inactive: { label: 'Inactive', Icon: CircleSlash, iconClass: 'text-muted' },
}

export function LinkStatusBadge({ status }: { status: LinkStatus }) {
  const { label, Icon, iconClass } = statuses[status]
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full border border-line px-2 py-0.5 text-xs font-medium text-ink-2">
      <Icon className={`size-3.5 ${iconClass}`} aria-hidden />
      {label}
    </span>
  )
}
