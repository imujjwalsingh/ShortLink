import { useDeactivateLink } from '../../api/queries'
import type { ShortLink } from '../../api/types'
import { Alert } from '../../components/ui/Alert'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { withoutScheme } from '../../lib/format'

interface DeactivateDialogProps {
  /** The link to deactivate; null keeps the dialog closed. */
  link: ShortLink | null
  onClose: () => void
}

export function DeactivateDialog({ link, onClose }: DeactivateDialogProps) {
  return (
    <Dialog
      open={link !== null}
      onClose={onClose}
      title="Deactivate this link?"
      description={
        link && (
          <>
            <span className="font-medium text-ink">{withoutScheme(link.shortUrl)}</span> will stop redirecting, and
            anyone who opens it will get a "not found" page. Its analytics stay available. This can't be undone.
          </>
        )
      }
    >
      {link && <Confirm shortCode={link.shortCode} onDone={onClose} />}
    </Dialog>
  )
}

function Confirm({ shortCode, onDone }: { shortCode: string; onDone: () => void }) {
  const deactivate = useDeactivateLink()
  return (
    <div className="space-y-4">
      {deactivate.error && <Alert>{deactivate.error.message}</Alert>}
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onDone} autoFocus>
          Cancel
        </Button>
        <Button variant="danger" loading={deactivate.isPending} onClick={() => deactivate.mutate(shortCode, { onSuccess: onDone })}>
          Deactivate
        </Button>
      </div>
    </div>
  )
}
