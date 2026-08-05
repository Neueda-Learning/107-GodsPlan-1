import { Check, X } from 'lucide-react'
import { formatDate, statusMeta } from '../utils/format'

export default function PaymentTimeline({ history = [] }) {
  return (
    <ol className="space-y-0">
      {history.map((item, index) => {
        const failed = item.toStatus === 'FAILED'
        return <li key={`${item.toStatus}-${item.createdAt}`} className="relative flex gap-4 pb-7 last:pb-0">
          {index < history.length - 1 && <span className="absolute left-[15px] top-8 h-[calc(100%-1rem)] w-px bg-line" />}
          <span className={`relative z-10 grid size-8 shrink-0 place-items-center rounded-full ${failed ? 'bg-red-100 text-danger' : 'bg-emerald-100 text-success'}`}>
            {failed ? <X className="size-4" /> : <Check className="size-4" />}
          </span>
          <div className="min-w-0 pt-0.5"><p className="font-semibold text-ink">{statusMeta[item.toStatus]?.label || item.toStatus}</p>
            <time className="mt-0.5 block text-sm text-ink-muted">{formatDate(item.createdAt, { second: '2-digit' })}</time>
            {item.errorDescription && <p className="mt-2 text-sm text-red-700">{item.errorDescription}</p>}
          </div>
        </li>
      })}
    </ol>
  )
}
