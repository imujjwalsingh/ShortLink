import { useEffect, useId, useRef, type ReactNode } from 'react'

interface DialogProps {
  open: boolean
  onClose: () => void
  title: string
  description?: ReactNode
  children: ReactNode
}

/**
 * A modal on the native <dialog> element, which brings focus trapping,
 * Escape-to-close and the inert background for free. Children mount only
 * while open, so a form inside starts fresh each time.
 */
export function Dialog({ open, onClose, title, description, children }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  // Close on a backdrop click, but not when a text selection that started
  // inside the dialog happens to end on the backdrop.
  const pressStartedOnBackdrop = useRef(false)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    if (open && !dialog.open) dialog.showModal()
    if (!open && dialog.open) dialog.close()
  }, [open])

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onMouseDown={(event) => {
        pressStartedOnBackdrop.current = event.target === event.currentTarget
      }}
      onClick={(event) => {
        if (pressStartedOnBackdrop.current && event.target === event.currentTarget) onClose()
      }}
      className="m-auto max-h-[calc(100dvh-2rem)] w-[min(32rem,calc(100vw-2rem))] overflow-auto rounded-xl border border-line bg-surface p-0 text-ink shadow-2xl"
    >
      {open && (
        <div className="p-6">
          <h2 id={titleId} className="text-lg font-semibold tracking-tight">
            {title}
          </h2>
          {description && <div className="mt-1.5 text-sm text-ink-2">{description}</div>}
          <div className="mt-5">{children}</div>
        </div>
      )}
    </dialog>
  )
}
