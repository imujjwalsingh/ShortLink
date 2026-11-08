import { useState, type FormEvent } from 'react'
import { useUpdateLink } from '../../api/queries'
import type { ShortLink } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { Field } from '../../components/ui/Field'
import { fromLocalInput, normalizeUrl, toLocalInput, withoutScheme } from '../../lib/format'

interface EditLinkDialogProps {
  /** The link being edited; null keeps the dialog closed. */
  link: ShortLink | null
  onClose: () => void
}

export function EditLinkDialog({ link, onClose }: EditLinkDialogProps) {
  return (
    <Dialog
      open={link !== null}
      onClose={onClose}
      title="Edit link"
      description={
        link && (
          <>
            Changes to <span className="font-medium text-ink">{withoutScheme(link.shortUrl)}</span> apply immediately.
            The short code itself can't change.
          </>
        )
      }
    >
      {link && <EditLinkForm link={link} onDone={onClose} />}
    </Dialog>
  )
}

function EditLinkForm({ link, onDone }: { link: ShortLink; onDone: () => void }) {
  const update = useUpdateLink(link.shortCode)
  const [url, setUrl] = useState(link.originalUrl)
  const [expires, setExpires] = useState(toLocalInput(link.expiresAt))
  const [minExpiry] = useState(() => toLocalInput(new Date().toISOString()))
  const error = update.error
  const urlError = error?.fieldErrors.originalUrl
  const expiresError = error?.fieldErrors.expiresAt
  const generalError = error && !urlError && !expiresError ? error.message : null

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    // PUT replaces both fields: an empty expiry removes the existing one.
    update.mutate({ originalUrl: normalizeUrl(url), expiresAt: fromLocalInput(expires) }, { onSuccess: onDone })
  }

  return (
    <form onSubmit={onSubmit} className="space-y-4">
      <Field
        label="Destination URL"
        value={url}
        onChange={(event) => setUrl(event.target.value)}
        inputMode="url"
        spellCheck={false}
        required
        maxLength={2048}
        error={urlError}
        autoFocus
      />
      <Field
        label="Expires"
        type="datetime-local"
        value={expires}
        min={minExpiry}
        onChange={(event) => setExpires(event.target.value)}
        hint="Leave empty to never expire."
        error={expiresError}
        trailing={
          expires && (
            <Button variant="ghost" size="sm" onClick={() => setExpires('')}>
              Clear
            </Button>
          )
        }
      />
      {generalError && <Alert>{generalError}</Alert>}
      <div className="flex justify-end gap-2 pt-1">
        <Button variant="secondary" onClick={onDone}>
          Cancel
        </Button>
        <Button type="submit" loading={update.isPending}>
          Save changes
        </Button>
      </div>
    </form>
  )
}
