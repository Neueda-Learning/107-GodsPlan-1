import { Inbox } from 'lucide-react'

export default function EmptyState({ title = 'No payments found', message = 'Try changing your search or create a new payment.' }) {
  return (
    <div className="grid min-h-64 place-items-center px-6 py-12 text-center">
      <div><span className="relative mx-auto grid size-14 place-items-center rounded-2xl border border-primary/15 bg-primary-light text-primary before:absolute before:-inset-3 before:-z-10 before:rounded-[22px] before:border before:border-primary/10"><Inbox className="size-6" /></span>
        <h3 className="mt-6 font-semibold text-ink">{title}</h3><p className="mx-auto mt-2 max-w-sm text-sm leading-6 text-ink-muted">{message}</p></div>
    </div>
  )
}
