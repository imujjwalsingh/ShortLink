import { ArrowLeft, Ban, ExternalLink, Pencil, RefreshCw } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { useLink, useLinkStats } from '../api/queries'
import type { CountBucket, LinkStats } from '../api/types'
import { BarList } from '../components/charts/BarList'
import { ClicksChart, ClicksTable } from '../components/charts/ClicksChart'
import { CopyButton } from '../components/CopyButton'
import { LinkStatusBadge } from '../components/LinkStatusBadge'
import { StatTile } from '../components/StatTile'
import { Alert } from '../components/ui/Alert'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { PageSpinner } from '../components/ui/Spinner'
import { buttonClass } from '../components/ui/styles'
import { DeactivateDialog } from '../features/links/DeactivateDialog'
import { EditLinkDialog } from '../features/links/EditLinkDialog'
import {
  bucketLabel,
  formatCount,
  formatDate,
  formatDateTime,
  formatRelative,
  linkStatus,
  referrerLabel,
  withoutScheme,
} from '../lib/format'
import { NotFoundContent } from './NotFoundPage'

const RANGES = [7, 30, 90] as const
const DEFAULT_RANGE = 30

export function LinkPage() {
  const { code = '' } = useParams()
  const [params, setParams] = useSearchParams()
  const requested = Number(params.get('days'))
  const days = (RANGES as readonly number[]).includes(requested) ? requested : DEFAULT_RANGE
  const link = useLink(code)
  const stats = useLinkStats(code, days)
  const [editing, setEditing] = useState(false)
  const [deactivating, setDeactivating] = useState(false)

  if (link.isPending) return <PageSpinner label="Loading link" />
  if (link.isError) {
    return link.error.status === 404 ? (
      <NotFoundContent title="Link not found" message="This link doesn't exist, or it belongs to another account." />
    ) : (
      <Alert>{link.error.message}</Alert>
    )
  }

  const data = link.data
  const status = linkStatus(data)

  return (
    <div className="space-y-6">
      <title>{`/${data.shortCode} · ShortLink`}</title>
      <Link to="/" className="inline-flex items-center gap-1 rounded-md text-sm text-ink-2 hover:text-ink">
        <ArrowLeft className="size-4" aria-hidden />
        All links
      </Link>

      <header className="flex flex-col gap-4 md:flex-row md:items-start md:justify-between">
        <div className="min-w-0">
          <div className="flex min-w-0 items-center gap-3">
            <h1 className="truncate text-2xl font-semibold tracking-tight">{withoutScheme(data.shortUrl)}</h1>
            <LinkStatusBadge status={status} />
          </div>
          <a
            href={data.originalUrl}
            target="_blank"
            rel="noreferrer"
            className="mt-1 flex min-w-0 items-center gap-1 text-sm text-ink-2 hover:text-ink"
            title={data.originalUrl}
          >
            <span className="truncate">{data.originalUrl}</span>
            <ExternalLink className="size-3.5 shrink-0" aria-hidden />
          </a>
          <p className="mt-2 text-xs text-ink-2">
            Created {formatDateTime(data.createdAt)}
            {data.updatedAt && data.updatedAt !== data.createdAt && <> · Updated {formatRelative(data.updatedAt)}</>}
            {' · '}
            {data.expiresAt
              ? `${status === 'expired' ? 'Expired' : 'Expires'} ${formatDate(data.expiresAt)}`
              : 'Never expires'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <CopyButton text={data.shortUrl} label="Copy link" variant="primary" size="md" />
          <a href={data.shortUrl} target="_blank" rel="noreferrer" className={buttonClass('secondary', 'md')}>
            <ExternalLink className="size-4" aria-hidden />
            Open
          </a>
          {data.active && (
            <>
              <Button variant="secondary" onClick={() => setEditing(true)}>
                <Pencil className="size-4" aria-hidden />
                Edit
              </Button>
              <Button variant="secondary" onClick={() => setDeactivating(true)}>
                <Ban className="size-4" aria-hidden />
                Deactivate
              </Button>
            </>
          )}
        </div>
      </header>

      <section aria-labelledby="analytics-heading" className="space-y-4">
        {/* One filter row scopes everything below it. */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="analytics-heading" className="text-lg font-semibold tracking-tight">
            Analytics
          </h2>
          <div className="flex items-center gap-2">
            <RangePicker value={days} onChange={(next) => setParams(next === DEFAULT_RANGE ? {} : { days: String(next) }, { replace: true })} />
            <Button
              variant="ghost"
              size="sm"
              iconOnly
              onClick={() => stats.refetch()}
              disabled={stats.isFetching}
              aria-label="Refresh analytics"
              title="Refresh"
            >
              <RefreshCw className={`size-4 ${stats.isFetching ? 'animate-spin' : ''}`} aria-hidden />
            </Button>
          </div>
        </div>

        {stats.isError ? (
          <Alert>Analytics are unavailable right now. {stats.error.message}</Alert>
        ) : stats.data ? (
          <StatsView stats={stats.data} days={days} stale={stats.isPlaceholderData} />
        ) : (
          <PageSpinner label="Loading analytics" />
        )}
      </section>

      <EditLinkDialog link={editing ? data : null} onClose={() => setEditing(false)} />
      <DeactivateDialog link={deactivating ? data : null} onClose={() => setDeactivating(false)} />
    </div>
  )
}

function RangePicker({ value, onChange }: { value: number; onChange: (days: number) => void }) {
  return (
    <div role="group" aria-label="Time range" className="inline-flex rounded-lg border border-line bg-surface p-0.5">
      {RANGES.map((range) => {
        const selected = range === value
        return (
          <button
            key={range}
            type="button"
            aria-pressed={selected}
            aria-label={`Last ${range} days`}
            onClick={() => onChange(range)}
            className={`h-7 cursor-pointer rounded-md px-3 text-sm transition-colors ${
              selected ? 'bg-wash font-medium text-ink' : 'text-ink-2 hover:text-ink'
            }`}
          >
            {range}d
          </button>
        )
      })}
    </div>
  )
}

function StatsView({ stats, days, stale }: { stats: LinkStats; days: number; stale: boolean }) {
  const [asTable, setAsTable] = useState(false)
  const inRange = stats.clicksPerDay.reduce((sum, day) => sum + day.clicks, 0)
  const items = (buckets: CountBucket[], label: (value: string) => string = bucketLabel) =>
    buckets.map((bucket) => ({ label: label(bucket.value), value: bucket.count }))

  return (
    // While a new range loads, the previous one stays on screen, dimmed.
    <div className={`space-y-4 transition-opacity ${stale ? 'opacity-60' : ''}`} aria-busy={stale || undefined}>
      <div className="grid grid-cols-2 gap-4 md:grid-cols-3">
        <StatTile label="Total clicks" value={formatCount(stats.totalClicks)} hint="All time" />
        <StatTile label="Unique visitors" value={formatCount(stats.uniqueVisitors)} hint="All time, approximate" />
        <StatTile
          label={`Clicks, last ${days} days`}
          value={formatCount(inRange)}
          hint="Selected range"
          className="col-span-2 md:col-span-1"
        />
      </div>

      <Card
        title="Clicks per day"
        description={`Last ${days} days, in UTC`}
        actions={
          <Button variant="ghost" size="sm" onClick={() => setAsTable(!asTable)} aria-pressed={asTable}>
            {asTable ? 'Show chart' : 'Show table'}
          </Button>
        }
      >
        {asTable ? <ClicksTable data={stats.clicksPerDay} /> : <ClicksChart data={stats.clicksPerDay} />}
      </Card>

      <div className="grid gap-4 md:grid-cols-2">
        <Card title="Top referrers" description="Where clicks came from">
          <BarList items={items(stats.topReferrers, referrerLabel)} emptyText="No clicks yet." />
        </Card>
        <Card title="Browsers">
          <BarList items={items(stats.browsers)} emptyText="No clicks yet." />
        </Card>
        <Card title="Operating systems">
          <BarList items={items(stats.operatingSystems)} emptyText="No clicks yet." />
        </Card>
        <Card title="Devices">
          <BarList items={items(stats.devices)} emptyText="No clicks yet." />
        </Card>
      </div>

      <p className="text-xs text-ink-2">
        Clicks are processed in the background and usually show up within a few seconds. Crawlers and link previews
        are counted, and appear as "Bot".
      </p>
    </div>
  )
}
