import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './ui/Button'

interface PaginationProps {
  /** Zero-based, like the API. */
  page: number
  totalPages: number
  onChange: (page: number) => void
}

export function Pagination({ page, totalPages, onChange }: PaginationProps) {
  if (totalPages <= 1) return null
  return (
    <nav aria-label="Pagination" className="mt-2 flex items-center justify-between gap-3 border-t border-line pt-4">
      <Button variant="secondary" size="sm" onClick={() => onChange(page - 1)} disabled={page <= 0}>
        <ChevronLeft className="size-4" aria-hidden />
        Previous
      </Button>
      <span className="text-sm text-ink-2">
        Page {page + 1} of {totalPages}
      </span>
      <Button variant="secondary" size="sm" onClick={() => onChange(page + 1)} disabled={page >= totalPages - 1}>
        Next
        <ChevronRight className="size-4" aria-hidden />
      </Button>
    </nav>
  )
}
