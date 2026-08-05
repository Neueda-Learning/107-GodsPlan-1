import { ArrowUpRight } from 'lucide-react'

export default function DashboardCard({ label, value, icon: Icon, tone = 'blue', helper }) {
  const tones = {
    blue: 'bg-primary-light text-primary-hover', green: 'bg-emerald-50 text-success',
    red: 'bg-red-50 text-danger', amber: 'bg-amber-50 text-warning',
  }
  return (
    <article className="card p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-ink-muted">{label}</p>
          <p className="mt-2 text-3xl font-bold tracking-tight text-ink">{value}</p>
        </div>
        <span className={`grid size-11 place-items-center rounded-xl ${tones[tone]}`}><Icon className="size-5" /></span>
      </div>
      {helper && <p className="mt-4 flex items-center gap-1 text-xs text-ink-muted"><ArrowUpRight className="size-3.5" />{helper}</p>}
    </article>
  )
}

