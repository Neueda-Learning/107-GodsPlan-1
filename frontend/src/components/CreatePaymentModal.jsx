import { Loader2, X, CheckCircle2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { paymentApi, apiMessage } from '../api/client'
import { useToast } from '../hooks/useToast'

function calcFee(amountStr) {
  const n = parseFloat(amountStr)
  if (!amountStr || isNaN(n) || n <= 0) return null
  const fee = Math.round(n * 0.02 * 100) / 100
  return { amount: n, fee, total: Math.round((n + fee) * 100) / 100 }
}

const empty = { sourceAccountId: '', destinationAccountId: '', amount: '', currency: 'USD', reference: '' }
const supportedCurrencies = [
  { code: 'USD', name: 'US Dollar' },
  { code: 'EUR', name: 'Euro' },
  { code: 'GBP', name: 'British Pound' },
  { code: 'INR', name: 'Indian Rupee' },
  { code: 'JPY', name: 'Japanese Yen' },
]

export default function CreatePaymentModal({ onClose, onCreated }) {
  const [form, setForm] = useState(empty)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const [confirmedPayment, setConfirmedPayment] = useState(null)
  const notify = useToast()

  const preview = calcFee(form.amount)

  useEffect(() => {
    const close = (event) => event.key === 'Escape' && !submitting && onClose()
    document.addEventListener('keydown', close)
    document.body.style.overflow = 'hidden'
    return () => { document.removeEventListener('keydown', close); document.body.style.overflow = '' }
  }, [onClose, submitting])

  const change = (field) => (event) => {
    setForm((current) => ({ ...current, [field]: event.target.value }))
    setErrors((current) => ({ ...current, [field]: '' }))
  }

  const validate = () => {
    const next = {}
    if (!form.sourceAccountId || Number(form.sourceAccountId) < 1) next.sourceAccountId = 'Enter a valid source account ID.'
    if (!form.destinationAccountId || Number(form.destinationAccountId) < 1) next.destinationAccountId = 'Enter a valid destination account ID.'
    if (form.sourceAccountId && form.sourceAccountId === form.destinationAccountId) next.destinationAccountId = 'Destination must be different from source.'
    if (!/^\d+(\.\d{1,2})?$/.test(form.amount) || Number(form.amount) <= 0) next.amount = 'Enter an amount greater than 0 with up to 2 decimals.'
    if (!/^[A-Za-z]{3}$/.test(form.currency)) next.currency = 'Enter a 3-letter currency code.'
    if (form.reference.length > 200) next.reference = 'Reference must be 200 characters or fewer.'
    setErrors(next)
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event.preventDefault()
    if (!validate()) return
    setSubmitting(true)
    try {
      const payment = await paymentApi.create({
        amount: Number(form.amount), currency: form.currency.toUpperCase(),
        sourceAccountId: Number(form.sourceAccountId), destinationAccountId: Number(form.destinationAccountId),
        reference: form.reference.trim() || null,
      }, idempotencyKey)
      notify(payment.status === 'FAILED' ? 'Payment created, but processing failed.' : 'Payment completed successfully.', payment.status === 'FAILED' ? 'error' : 'success')
      setConfirmedPayment(payment)
    } catch (error) {
      notify(apiMessage(error), 'error')
      setErrors({ form: apiMessage(error) })
    } finally { setSubmitting(false) }
  }

  const field = (name, label, type = 'text', placeholder = '') => (
    <label className="block"><span className="label">{label}</span>
      <input className={`input ${errors[name] ? 'border-danger' : ''}`} type={type} inputMode={type === 'number' ? 'decimal' : undefined} value={form[name]} onChange={change(name)} placeholder={placeholder} aria-invalid={Boolean(errors[name])} />
      {errors[name] && <span className="mt-1.5 block text-xs text-red-600">{errors[name]}</span>}
    </label>
  )

  const fmt = (n) => n?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })

  if (confirmedPayment) {
    const cur = confirmedPayment.currency
    return (
      <div className="fixed inset-0 z-[60] grid items-end bg-slate-900/30 sm:place-items-center sm:p-5">
        <section className="max-h-screen w-full overflow-y-auto bg-white p-5 shadow-2xl sm:max-w-xl sm:rounded-card sm:p-6" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="mt-0.5 size-6 shrink-0 text-green-500" />
            <div>
              <h2 id="confirm-title" className="text-xl font-bold text-ink">Payment #{confirmedPayment.id} created</h2>
              <p className="mt-1 text-sm text-ink-muted">Here is the full fee breakdown for this transaction.</p>
            </div>
          </div>
          <div className="mt-6 rounded-xl border border-line divide-y divide-line text-sm">
            <div className="flex justify-between px-4 py-3">
              <span className="text-ink-muted">Amount</span>
              <span className="font-semibold text-ink">{fmt(confirmedPayment.amount)} {cur}</span>
            </div>
            <div className="flex justify-between px-4 py-3">
              <span className="text-ink-muted">Platform fee <span className="rounded bg-amber-100 px-1 text-xs font-semibold text-amber-700">2%</span></span>
              <span className="font-semibold text-amber-700">+ {fmt(confirmedPayment.feeAmount)} {cur}</span>
            </div>
            <div className="flex justify-between bg-slate-50 px-4 py-3 rounded-b-xl">
              <span className="font-bold text-ink">Total deducted from sender</span>
              <span className="font-bold text-ink">{fmt(confirmedPayment.totalDebitAmount)} {cur}</span>
            </div>
          </div>
          <div className="mt-5 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
            <button className="btn-primary" onClick={() => onCreated(confirmedPayment)}>Done</button>
          </div>
        </section>
      </div>
    )
  }

  return (
    <div className="fixed inset-0 z-[60] grid items-end bg-slate-900/30 sm:place-items-center sm:p-5" onMouseDown={(e) => e.target === e.currentTarget && !submitting && onClose()}>
      <section className="max-h-screen w-full overflow-y-auto bg-white p-5 shadow-2xl sm:max-w-xl sm:rounded-card sm:p-6" role="dialog" aria-modal="true" aria-labelledby="create-title">
        <div className="flex items-start justify-between"><div><h2 id="create-title" className="text-xl font-bold text-ink">Create a payment</h2><p className="mt-1 text-sm text-ink-muted">Payments are validated and processed immediately.</p></div>
          <button className="rounded-lg p-2 text-ink-muted hover:bg-slate-100" onClick={onClose} disabled={submitting} aria-label="Close"><X className="size-5" /></button></div>
        <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
          {errors.form && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">{errors.form}</p>}
          <div className="grid gap-4 sm:grid-cols-2">{field('sourceAccountId', 'Source account ID', 'number', 'e.g. 1')}{field('destinationAccountId', 'Destination account ID', 'number', 'e.g. 2')}</div>
          <div className="grid gap-4 sm:grid-cols-[1fr_220px]">
            {field('amount', 'Amount', 'text', '0.00')}
            <label className="block"><span className="label">Currency</span>
              <select className={`input ${errors.currency ? 'border-danger' : ''}`} value={form.currency} onChange={change('currency')} aria-invalid={Boolean(errors.currency)}>
                {supportedCurrencies.map((currency) => <option key={currency.code} value={currency.code}>{currency.code} — {currency.name}</option>)}
              </select>
              {errors.currency && <span className="mt-1.5 block text-xs text-red-600">{errors.currency}</span>}
            </label>
          </div>
          {field('reference', 'Reference (optional)', 'text', 'e.g. Invoice 4471')}

          {preview && (
            <div className="rounded-xl border border-amber-200 bg-amber-50 divide-y divide-amber-200 text-sm">
              <div className="flex justify-between px-4 py-2.5">
                <span className="text-ink-muted">Amount</span>
                <span className="font-semibold text-ink">{fmt(preview.amount)} {form.currency.toUpperCase()}</span>
              </div>
              <div className="flex justify-between px-4 py-2.5">
                <span className="text-ink-muted">Platform fee <span className="rounded bg-amber-200 px-1 text-xs font-semibold text-amber-800">2%</span></span>
                <span className="font-semibold text-amber-700">+ {fmt(preview.fee)} {form.currency.toUpperCase()}</span>
              </div>
              <div className="flex justify-between px-4 py-2.5 font-bold">
                <span className="text-ink">Total deducted from sender</span>
                <span className="text-ink">{fmt(preview.total)} {form.currency.toUpperCase()}</span>
              </div>
            </div>
          )}

          <p className="text-xs text-ink-muted">For the included demo data, account IDs 1 and 2 are USD; 3 is EUR; 4 is INR.</p>
          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end"><button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>Cancel</button><button type="submit" className="btn-primary" disabled={submitting}>{submitting && <Loader2 className="size-4 animate-spin" />} {submitting ? 'Processing…' : 'Create payment'}</button></div>
        </form>
      </section>
    </div>
  )
}
