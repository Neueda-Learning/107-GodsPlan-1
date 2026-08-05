import { Loader2, X } from 'lucide-react'
import { useEffect, useState } from 'react'
import { paymentApi, apiMessage } from '../api/client'
import { useToast } from '../hooks/useToast'

const empty = { sourceAccountId: '', destinationAccountId: '', amount: '', currency: 'USD', reference: '' }

export default function CreatePaymentModal({ onClose, onCreated }) {
  const [form, setForm] = useState(empty)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const notify = useToast()

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
      onCreated(payment)
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

  return (
    <div className="fixed inset-0 z-[60] grid items-end bg-slate-900/30 sm:place-items-center sm:p-5" onMouseDown={(e) => e.target === e.currentTarget && !submitting && onClose()}>
      <section className="max-h-screen w-full overflow-y-auto bg-white p-5 shadow-2xl sm:max-w-xl sm:rounded-card sm:p-6" role="dialog" aria-modal="true" aria-labelledby="create-title">
        <div className="flex items-start justify-between"><div><h2 id="create-title" className="text-xl font-bold text-ink">Create a payment</h2><p className="mt-1 text-sm text-ink-muted">Payments are validated and processed immediately.</p></div>
          <button className="rounded-lg p-2 text-ink-muted hover:bg-slate-100" onClick={onClose} disabled={submitting} aria-label="Close"><X className="size-5" /></button></div>
        <form className="mt-6 space-y-5" onSubmit={submit} noValidate>
          {errors.form && <p className="rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">{errors.form}</p>}
          <div className="grid gap-4 sm:grid-cols-2">{field('sourceAccountId', 'Source account ID', 'number', 'e.g. 1')}{field('destinationAccountId', 'Destination account ID', 'number', 'e.g. 2')}</div>
          <div className="grid gap-4 sm:grid-cols-[1fr_130px]">{field('amount', 'Amount', 'text', '0.00')}{field('currency', 'Currency', 'text', 'USD')}</div>
          {field('reference', 'Reference (optional)', 'text', 'e.g. Invoice 4471')}
          <p className="text-xs text-ink-muted">For the included demo data, account IDs 1 and 2 are USD; 3 is EUR; 4 is INR.</p>
          <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-end"><button type="button" className="btn-secondary" onClick={onClose} disabled={submitting}>Cancel</button><button type="submit" className="btn-primary" disabled={submitting}>{submitting && <Loader2 className="size-4 animate-spin" />} {submitting ? 'Processing…' : 'Create payment'}</button></div>
        </form>
      </section>
    </div>
  )
}
