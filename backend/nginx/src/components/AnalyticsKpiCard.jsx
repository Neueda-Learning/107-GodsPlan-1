import { ArrowDownRight, ArrowRight, ArrowUpRight } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Line, LineChart, ResponsiveContainer } from 'recharts'
import { formatMoney } from '../utils/format'

function useReducedMotion() {
  const [reduced, setReduced] = useState(false)
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)')
    const update = () => setReduced(media.matches)
    update(); media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  return reduced
}

function AnimatedValue({ value, formatter }) {
  const reduced = useReducedMotion()
  const [display, setDisplay] = useState(0)
  useEffect(() => {
    if (reduced) return undefined
    const started = performance.now()
    let frame
    const update = (now) => {
      const progress = Math.min((now - started) / 550, 1)
      setDisplay(value * (1 - Math.pow(1 - progress, 3)))
      if (progress < 1) frame = requestAnimationFrame(update)
    }
    frame = requestAnimationFrame(update)
    return () => cancelAnimationFrame(frame)
  }, [value, reduced])
  return formatter(reduced ? value : display)
}

export default function AnalyticsKpiCard({ kpi, baseCurrency }) {
  const reduced = useReducedMotion()
  const numeric = Number(kpi.value || 0)
  const data = useMemo(() => (kpi.sparkline || []).map((value, index) => ({ index, value: Number(value) })), [kpi.sparkline])
  const formatter = (value) => kpi.unit === 'currency' ? formatMoney(value, baseCurrency)
    : kpi.unit === 'percent' ? `${value.toFixed(1)}%` : Math.round(value).toLocaleString()
  const TrendIcon = kpi.trend === 'INCREASE' ? ArrowUpRight : kpi.trend === 'DECREASE' ? ArrowDownRight : ArrowRight
  const trendTone = kpi.trend === 'INCREASE' ? 'text-emerald-700 bg-emerald-50'
    : kpi.trend === 'DECREASE' ? 'text-red-700 bg-red-50' : 'text-slate-600 bg-slate-100'
  return <article className="card min-w-0 p-4 sm:p-5">
    <p className="truncate text-xs font-semibold uppercase tracking-wide text-ink-muted" title={kpi.label}>{kpi.label}</p>
    <div className="mt-2 flex items-end justify-between gap-2">
      <p className="truncate text-2xl font-bold tracking-tight text-ink" title={formatter(numeric)}><AnimatedValue value={numeric} formatter={formatter} /></p>
      {data.length > 1 && <div className="h-9 w-20 shrink-0" aria-hidden="true"><ResponsiveContainer width="100%" height="100%"><LineChart data={data}><Line type="monotone" dataKey="value" stroke="#2F80ED" strokeWidth={2} dot={false} isAnimationActive={!reduced} /></LineChart></ResponsiveContainer></div>}
    </div>
    <div className="mt-3 flex items-center justify-between gap-2 text-xs">
      <span className={`inline-flex items-center gap-1 rounded-full px-2 py-1 font-semibold ${trendTone}`}><TrendIcon className="size-3" />{kpi.changePercent === null ? (kpi.trend === 'NO_CHANGE' ? 'No change' : 'New activity') : `${Math.abs(Number(kpi.changePercent)).toFixed(1)}%`}</span>
      <span className="truncate text-ink-muted">vs previous period</span>
    </div>
  </article>
}
