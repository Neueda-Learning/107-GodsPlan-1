import { Inbox } from 'lucide-react'

export default function EmptyState({ title = 'No payments found', message = 'Try changing your search or create a new payment.' }) {
  return (
    <div className="grid min-h-64 place-items-center px-6 py-12 text-center">
      <div><span className="mx-auto grid size-12 place-items-center rounded-full bg-primary-light text-primary"><Inbox className="size-6" /></span>
        <h3 className="mt-4 font-semibold text-ink">{title}</h3><p className="mt-1 max-w-sm text-sm text-ink-muted">{message}</p></div>
    </div>
  )
}

