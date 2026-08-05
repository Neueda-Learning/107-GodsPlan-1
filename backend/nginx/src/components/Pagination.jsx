import { ChevronLeft, ChevronRight } from 'lucide-react'

export default function Pagination({ page, pages, onChange, total, label = 'payments' }) {
  if (pages <= 1) return null
  return (
    <div className="flex items-center justify-between border-t border-line px-4 py-3 sm:px-6">
      <p className="text-sm text-ink-muted"><span className="font-medium text-ink">{total}</span> {label}</p>
      <div className="flex items-center gap-2">
        <button className="btn-secondary size-9 p-0" disabled={page === 1} onClick={() => onChange(page - 1)} aria-label="Previous page"><ChevronLeft className="size-4" /></button>
        <span className="min-w-20 text-center text-sm text-ink-muted">{page} of {pages}</span>
        <button className="btn-secondary size-9 p-0" disabled={page === pages} onClick={() => onChange(page + 1)} aria-label="Next page"><ChevronRight className="size-4" /></button>
      </div>
    </div>
  )
}
