import { Ban, ChartColumn, Pencil } from 'lucide-react'
import { Link } from 'react-router'
import type { ShortLink } from '../../api/types'
import { CopyButton } from '../../components/CopyButton'
import { LinkStatusBadge } from '../../components/LinkStatusBadge'
import { Button } from '../../components/ui/Button'
import { iconButtonClass } from '../../components/ui/styles'
import { formatCount, formatDate, formatRelative, linkStatus, withoutScheme } from '../../lib/format'

interface LinksListProps {
  links: ShortLink[]
  clicksFor: (shortCode: string) => number | undefined
  onEdit: (link: ShortLink) => void
  onDeactivate: (link: ShortLink) => void
}

/** One row per link; stacks on narrow screens instead of scrolling sideways. */
export function LinksList({ links, clicksFor, onEdit, onDeactivate }: LinksListProps) {
  return (
    <ul className="-my-4 divide-y divide-line">
      {links.map((link) => {
        const status = linkStatus(link)
        const clicks = clicksFor(link.shortCode)
        const detailsPath = `/links/${encodeURIComponent(link.shortCode)}`
        return (
          <li key={link.shortCode} className="flex flex-col gap-3 py-4 sm:flex-row sm:items-center sm:gap-4">
            <div className="min-w-0 flex-1">
              <div className="flex min-w-0 items-center gap-2">
                <Link to={detailsPath} className="truncate font-medium text-accent-text hover:underline">
                  {withoutScheme(link.shortUrl)}
                </Link>
                <LinkStatusBadge status={status} />
              </div>
              <p className="mt-0.5 truncate text-sm text-ink-2" title={link.originalUrl}>
                {link.originalUrl}
              </p>
              <p className="mt-1 text-xs text-ink-2">
                <span title={formatDate(link.createdAt)}>Created {formatRelative(link.createdAt)}</span>
                {link.expiresAt && (
                  <span>
                    {' · '}
                    {status === 'expired' ? 'Expired' : 'Expires'} {formatDate(link.expiresAt)}
                  </span>
                )}
              </p>
            </div>

            <div className="flex items-center justify-between gap-4 sm:justify-end">
              <div className="min-w-16 sm:text-right">
                <p className="text-sm font-semibold tabular-nums">{clicks === undefined ? '–' : formatCount(clicks)}</p>
                <p className="text-xs text-ink-2">clicks</p>
              </div>
              <div className="flex items-center gap-0.5">
                <CopyButton text={link.shortUrl} label="Copy" variant="ghost" iconOnly />
                <Link
                  to={detailsPath}
                  className={iconButtonClass()}
                  aria-label={`Analytics for ${link.shortCode}`}
                  title="Analytics"
                >
                  <ChartColumn className="size-4" aria-hidden />
                </Link>
                {link.active && (
                  <>
                    <Button
                      variant="ghost"
                      size="sm"
                      iconOnly
                      onClick={() => onEdit(link)}
                      aria-label={`Edit ${link.shortCode}`}
                      title="Edit"
                    >
                      <Pencil className="size-4" aria-hidden />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      iconOnly
                      onClick={() => onDeactivate(link)}
                      aria-label={`Deactivate ${link.shortCode}`}
                      title="Deactivate"
                    >
                      <Ban className="size-4" aria-hidden />
                    </Button>
                  </>
                )}
              </div>
            </div>
          </li>
        )
      })}
    </ul>
  )
}
