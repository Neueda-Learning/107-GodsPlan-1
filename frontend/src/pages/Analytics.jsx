import { BarChart3, RefreshCw, ShieldCheck } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import { analyticsApi, apiMessage } from '../api/client'
import AnalyticsFilters from '../components/AnalyticsFilters'
import AnalyticsKpiCard from '../components/AnalyticsKpiCard'
import AnalyticsRecentTransactions from '../components/AnalyticsRecentTransactions'
import {
  ActivityHeatmap, CurrencyChart, CustomerGrowthChart, ExchangeRateChart, FailureReasonsChart,
  PaymentMethodsChart, RatesChart, StatusDistribution, TopCustomers, TransactionsChart, VolumeChart,
} from '../components/AnalyticsVisualizations'
import LoadingSkeleton from '../components/LoadingSkeleton'
import { formatDate } from '../utils/format'
import { defaultAnalyticsFilters } from '../utils/analytics'

const paramsFor = (filters) => Object.fromEntries(Object.entries(filters).filter(([, value]) => value !== '' && value !== null && value !== undefined))

export default function Analytics() {
  const [filters, setFilters] = useState(defaultAnalyticsFilters)
  const [overview, setOverview] = useState(null); const [recent, setRecent] = useState(null)
  const [loading, setLoading] = useState(true); const [recentLoading, setRecentLoading] = useState(true)
  const [error, setError] = useState(''); const [recentError, setRecentError] = useState('')
  const [exchange, setExchange] = useState(null); const [exchangeLoading, setExchangeLoading] = useState(false)
  const [exchangeError, setExchangeError] = useState(''); const [pair, setPair] = useState({ source: '', target: '' })
  const currencies = overview?.filterOptions?.currencies || []
  const resolvedSource = pair.source || (currencies.includes('EUR') ? 'EUR' : currencies[0]) || ''
  const resolvedTarget = pair.target || currencies.find((item) => item !== resolvedSource) || ''

  const loadDashboard = useCallback(async (nextFilters) => {
    const params = paramsFor(nextFilters); setLoading(true); setRecentLoading(true); setError(''); setRecentError('')
    const [summaryResult, recentResult] = await Promise.allSettled([
      analyticsApi.overview(params), analyticsApi.recent({ ...params, page: 0, size: 10 }),
    ])
    if (summaryResult.status === 'fulfilled') setOverview(summaryResult.value)
    else setError(apiMessage(summaryResult.reason))
    if (recentResult.status === 'fulfilled') setRecent(recentResult.value)
    else setRecentError(apiMessage(recentResult.reason))
    setLoading(false); setRecentLoading(false)
  }, [])

  useEffect(() => {
    const timer = setTimeout(() => loadDashboard(filters), 0)
    return () => clearTimeout(timer)
  }, [filters, loadDashboard])

  const loadExchange = useCallback(async (source, target, nextFilters) => {
    if (!source || !target || source === target) { setExchange(null); return }
    setExchangeLoading(true); setExchangeError('')
    try { setExchange(await analyticsApi.exchangeRates({ sourceCurrency: source, targetCurrency: target, from: nextFilters.from, to: nextFilters.to })) }
    catch (err) { setExchangeError(apiMessage(err)) } finally { setExchangeLoading(false) }
  }, [])

  useEffect(() => {
    if (!resolvedSource || !resolvedTarget) return undefined
    const timer = setTimeout(() => loadExchange(resolvedSource, resolvedTarget, filters), 0)
    return () => clearTimeout(timer)
  }, [resolvedSource, resolvedTarget, filters, loadExchange])

  const applyFilters = (next) => setFilters(next)
  const changeRecentPage = async (page) => {
    setRecentLoading(true); setRecentError('')
    try { setRecent(await analyticsApi.recent({ ...paramsFor(filters), page, size: 10 })) }
    catch (err) { setRecentError(apiMessage(err)) } finally { setRecentLoading(false) }
  }
  const changePair = (source, target) => {
    const safeTarget = source === target ? currencies.find((item) => item !== source) || '' : target
    setPair({ source, target: safeTarget })
  }
  const totalTransactions = Number(overview?.kpis?.find((item) => item.key === 'totalTransactions')?.value || 0)
  const options = overview?.filterOptions || { statuses: [], currencies: [], paymentMethods: [], customers: [], auditScopes: ['ALL', 'PAYMENTS_ONLY', 'INSUFFICIENT_ONLY'] }
  const lastUpdated = overview?.generatedAt ? formatDate(overview.generatedAt) : '—'

  return <div className="mx-auto max-w-[1600px] space-y-6">
    <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end"><div><p className="text-sm font-medium text-primary-hover">Database intelligence</p><h2 className="mt-1 flex items-center gap-2 text-2xl font-bold tracking-tight text-ink"><BarChart3 className="size-6 text-primary" />Payment analytics</h2><p className="mt-1 text-sm text-ink-muted">Every metric is aggregated from stored backend records.</p></div><div className="flex flex-wrap items-center gap-3"><div className="text-right"><p className="text-xs text-ink-muted">Last updated</p><p className="text-sm font-semibold text-ink">{lastUpdated}</p></div><button className="btn-secondary" disabled={loading} onClick={() => { loadDashboard(filters); loadExchange(resolvedSource, resolvedTarget, filters) }}><RefreshCw className={`size-4 ${loading ? 'animate-spin' : ''}`} />Refresh</button></div></div>
    <AnalyticsFilters value={filters} options={options} onApply={applyFilters} busy={loading} />
    {error && <div className="rounded-card border border-red-200 bg-red-50 p-4 text-sm text-red-700" role="alert">{error}</div>}
    {overview?.unconvertedTransactions > 0 && <div className="flex items-center gap-2 rounded-card border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800"><ShieldCheck className="size-5 shrink-0" />{overview.unconvertedTransactions} transaction(s) were excluded from base-currency volume totals because no stored historical conversion rate was available.</div>}
    {loading && !overview ? <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4"><div className="card"><LoadingSkeleton rows={3}/></div><div className="card"><LoadingSkeleton rows={3}/></div><div className="card"><LoadingSkeleton rows={3}/></div><div className="card"><LoadingSkeleton rows={3}/></div></div> : overview && <>
      {totalTransactions === 0 && <div className="rounded-card border border-blue-200 bg-primary-light p-5 text-sm text-blue-900"><strong>No matching analytics records.</strong> Adjust the filters or, in a development environment, run the documented backend analytics seed command. No placeholder values are being shown.</div>}
      <section className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-5" aria-label="Analytics summary">{overview.kpis.map((kpi) => <AnalyticsKpiCard key={kpi.key} kpi={kpi} baseCurrency={overview.baseCurrency} />)}</section>
      <div className="grid gap-6 xl:grid-cols-2"><StatusDistribution data={overview.paymentStatus}/><RatesChart data={overview.paymentRates}/><TransactionsChart data={overview.transactionsOverTime}/><PaymentMethodsChart data={overview.paymentMethods} baseCurrency={overview.baseCurrency}/><VolumeChart data={overview.paymentVolume} baseCurrency={overview.baseCurrency}/><CurrencyChart data={overview.currencies}/><ActivityHeatmap data={overview.activityHeatmap}/><CustomerGrowthChart data={overview.customerGrowth}/><TopCustomers data={overview.topCustomers} baseCurrency={overview.baseCurrency}/><FailureReasonsChart data={overview.failureReasons}/><ExchangeRateChart data={exchange} currencies={options.currencies} source={resolvedSource} target={resolvedTarget} onPairChange={changePair} loading={exchangeLoading} error={exchangeError}/><AnalyticsRecentTransactions data={recent} loading={recentLoading} error={recentError} onPage={changeRecentPage}/></div>
    </>}
  </div>
}
