import { ChartColumn, ChevronDown, CircleCheck, X } from 'lucide-react'
import { useId, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { API_BASE_URL } from '../../api/client'
import { useCreateLink } from '../../api/queries'
import type { CreateLinkRequest, ShortLink } from '../../api/types'
import { CopyButton } from '../../components/CopyButton'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Field } from '../../components/ui/Field'
import { buttonClass, inputClass } from '../../components/ui/styles'
import { fromLocalInput, normalizeUrl, toLocalInput, withoutScheme } from '../../lib/format'

// Short links are served by the gateway, so its host is the alias prefix.
const SHORT_HOST = `${withoutScheme(API_BASE_URL)}/`

export function CreateLinkForm() {
  const create = useCreateLink()
  const urlId = useId()
  const [url, setUrl] = useState('')
  const [alias, setAlias] = useState('')
  const [expires, setExpires] = useState('')
  const [showOptions, setShowOptions] = useState(false)
  const [created, setCreated] = useState<ShortLink | null>(null)
  // Earliest pickable expiry; the backend re-checks it's in the future anyway.
  const [minExpiry] = useState(() => toLocalInput(new Date().toISOString()))

  const error = create.error
  const urlError = error?.fieldErrors.originalUrl
  // 409 is "alias already taken"; a reserved alias comes back as a plain 400.
  const aliasError = error?.fieldErrors.customAlias ?? (error?.status === 409 ? error.message : undefined)
  const expiresError = error?.fieldErrors.expiresAt
  const generalError = error && !urlError && !aliasError && !expiresError ? error.message : null
  const optionsOpen = showOptions || Boolean(aliasError || expiresError)

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const body: CreateLinkRequest = { originalUrl: normalizeUrl(url) }
    if (alias.trim()) body.customAlias = alias.trim()
    const expiresAt = fromLocalInput(expires)
    if (expiresAt) body.expiresAt = expiresAt

    create.mutate(body, {
      onSuccess: (link) => {
        setCreated(link)
        setUrl('')
        setAlias('')
        setExpires('')
        setShowOptions(false)
      },
    })
  }

  return (
    <Card>
      <form onSubmit={onSubmit} className="space-y-4">
        <div>
          <label htmlFor={urlId} className="text-base font-semibold">
            Shorten a link
          </label>
          <p className="mt-0.5 text-sm text-ink-2">Paste a long URL to get a short one you can share.</p>
          <div className="mt-3 flex flex-col gap-2 sm:flex-row">
            <input
              id={urlId}
              value={url}
              onChange={(event) => setUrl(event.target.value)}
              placeholder="https://example.com/a/very/long/link"
              inputMode="url"
              autoComplete="url"
              spellCheck={false}
              required
              maxLength={2048}
              aria-invalid={urlError ? true : undefined}
              aria-describedby={urlError ? `${urlId}-error` : undefined}
              className={inputClass({ invalid: Boolean(urlError), size: 'lg' })}
            />
            <Button type="submit" size="lg" loading={create.isPending} className="sm:w-32">
              Shorten
            </Button>
          </div>
          {urlError && (
            <p id={`${urlId}-error`} className="mt-1.5 text-sm text-danger">
              {urlError}
            </p>
          )}
        </div>

        <button
          type="button"
          onClick={() => setShowOptions(!optionsOpen)}
          aria-expanded={optionsOpen}
          className="inline-flex cursor-pointer items-center gap-1 rounded-md text-sm font-medium text-ink-2 hover:text-ink"
        >
          <ChevronDown className={`size-4 transition-transform ${optionsOpen ? 'rotate-180' : ''}`} aria-hidden />
          Custom alias and expiry
        </button>

        {optionsOpen && (
          <div className="grid gap-4 sm:grid-cols-2">
            <Field
              label="Custom alias"
              prefix={SHORT_HOST}
              value={alias}
              onChange={(event) => setAlias(event.target.value)}
              placeholder="my-link"
              pattern="[A-Za-z0-9_\-]{3,32}"
              title="3 to 32 letters, numbers, hyphens or underscores"
              maxLength={32}
              autoComplete="off"
              spellCheck={false}
              hint="Optional. 3 to 32 letters, numbers, - or _"
              error={aliasError}
            />
            <Field
              label="Expires"
              type="datetime-local"
              value={expires}
              min={minExpiry}
              onChange={(event) => setExpires(event.target.value)}
              hint="Optional. Leave empty to never expire."
              error={expiresError}
            />
          </div>
        )}

        {generalError && <Alert>{generalError}</Alert>}
      </form>

      {created && <CreatedBanner link={created} onDismiss={() => setCreated(null)} />}
    </Card>
  )
}

function CreatedBanner({ link, onDismiss }: { link: ShortLink; onDismiss: () => void }) {
  return (
    <div role="status" className="mt-5 flex flex-col gap-3 rounded-lg bg-success-wash p-4 sm:flex-row sm:items-center">
      <CircleCheck className="hidden size-5 shrink-0 text-good sm:block" aria-hidden />
      <div className="min-w-0 flex-1">
        <p className="text-sm text-ink-2">Your short link is ready</p>
        <a
          href={link.shortUrl}
          target="_blank"
          rel="noreferrer"
          className="block truncate font-semibold text-accent-text hover:underline"
        >
          {withoutScheme(link.shortUrl)}
        </a>
      </div>
      <div className="flex items-center gap-2">
        <CopyButton text={link.shortUrl} variant="primary" />
        <Link to={`/links/${encodeURIComponent(link.shortCode)}`} className={buttonClass('secondary', 'sm')}>
          <ChartColumn className="size-4" aria-hidden />
          Analytics
        </Link>
        <Button variant="ghost" size="sm" iconOnly onClick={onDismiss} aria-label="Dismiss">
          <X className="size-4" aria-hidden />
        </Button>
      </div>
    </div>
  )
}
