import { Link2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { clickCounter, useLinks, useTopLinks } from '../api/queries'
import type { ShortLink } from '../api/types'
import { BarList } from '../components/charts/BarList'
import { Pagination } from '../components/Pagination'
import { StatTile } from '../components/StatTile'
import { Alert } from '../components/ui/Alert'
import { Card } from '../components/ui/Card'
import { PageSpinner } from '../components/ui/Spinner'
import { CreateLinkForm } from '../features/links/CreateLinkForm'
import { DeactivateDialog } from '../features/links/DeactivateDialog'
import { EditLinkDialog } from '../features/links/EditLinkDialog'
import { LinksList } from '../features/links/LinksList'
import { formatCount } from '../lib/format'

export function DashboardPage() {
  const [params, setParams] = useSearchParams()
  // ?page= is 1-based for people; the API is 0-based.
  const page = Math.max(0, (Number.parseInt(params.get('page') ?? '1', 10) || 1) - 1)
  const links = useLinks(page)
  // Analytics is optional here: if analytics-service is down, the dashboard
  // still works and click counts show as "–".
  const top = useTopLinks()
  const clicksFor = useMemo(() => clickCounter(top.data), [top.data])
  const [editing, setEditing] = useState<ShortLink | null>(null)
  const [deactivating, setDeactivating] = useState<ShortLink | null>(null)

  const totalPages = links.data?.totalPages ?? 0
  const goToPage = (next: number) => setParams(next > 0 ? { page: String(next + 1) } : {})

  // A ?page= past the end (bookmarked, or edited by hand): go to the last page.
  useEffect(() => {
    if (links.data && !links.isPlaceholderData && totalPages > 0 && page >= totalPages) {
      setParams({ page: String(totalPages) }, { replace: true })
    }
  }, [links.data, links.isPlaceholderData, page, totalPages, setParams])

  const topItems = (top.data?.links ?? [])
    .filter((link) => link.clicks > 0)
    .slice(0, 5)
    .map((link) => ({ label: `/${link.shortCode}`, value: link.clicks, to: `/links/${encodeURIComponent(link.shortCode)}` }))

  return (
    <div className="space-y-6">
      <title>Dashboard · ShortLink</title>
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="mt-1 text-sm text-ink-2">Shorten a link, share it, and watch the clicks come in.</p>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <CreateLinkForm />

          <Card
            title="Your links"
            description={links.data ? `${formatCount(links.data.totalElements)} in total, newest first` : undefined}
          >
            {links.isPending ? (
              <PageSpinner label="Loading links" />
            ) : links.isError ? (
              <Alert>{links.error.message}</Alert>
            ) : links.data.totalElements === 0 ? (
              <EmptyLinks />
            ) : (
              <div className={`transition-opacity ${links.isPlaceholderData ? 'opacity-60' : ''}`}>
                <LinksList
                  links={links.data.content}
                  clicksFor={clicksFor}
                  onEdit={setEditing}
                  onDeactivate={setDeactivating}
                />
                <div className="mt-4">
                  <Pagination page={page} totalPages={totalPages} onChange={goToPage} />
                </div>
              </div>
            )}
          </Card>
        </div>

        <aside className="space-y-6" aria-label="Summary">
          <div className="grid grid-cols-2 gap-4">
            <StatTile
              label="Links"
              value={links.data ? formatCount(links.data.totalElements) : undefined}
              hint="Including inactive"
            />
            <StatTile
              label="Total clicks"
              value={top.data ? formatCount(top.data.totalClicks) : top.isError ? '–' : undefined}
              hint="All links, all time"
            />
          </div>
          <Card title="Most clicked" description="All time">
            {top.isError ? (
              <p className="py-6 text-center text-sm text-ink-2">Analytics are unavailable right now.</p>
            ) : top.isPending ? (
              <PageSpinner label="Loading top links" />
            ) : (
              <BarList items={topItems} emptyText="No clicks yet. Share a link to see it here." />
            )}
          </Card>
        </aside>
      </div>

      <EditLinkDialog link={editing} onClose={() => setEditing(null)} />
      <DeactivateDialog link={deactivating} onClose={() => setDeactivating(null)} />
    </div>
  )
}

function EmptyLinks() {
  return (
    <div className="flex flex-col items-center py-10 text-center">
      <span className="grid size-11 place-items-center rounded-full bg-wash text-ink-2">
        <Link2 className="size-5" aria-hidden />
      </span>
      <p className="mt-3 font-medium">No links yet</p>
      <p className="mt-1 text-sm text-ink-2">Paste a long URL above to create your first short link.</p>
    </div>
  )
}
