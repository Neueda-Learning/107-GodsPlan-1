import { Loader2, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { apiMessage, customerApi, paymentApi } from '../api/client'
import { useToast } from '../hooks/useToast'
import { generateId } from '../utils/generateId'

const money = (amount, currency) => `${currency} ${new Intl.NumberFormat('en-US', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
}).format(Number(amount || 0))}`

const empty = {
  senderCustomerId: '',
  sourceAccountId: '',
  receiverCustomerId: '',
  destinationAccountId: '',
  amount: '',
  intermediaryBank: '',
  reference: '',
}

export default function CreatePaymentModal({ onClose, onCreated }) {
  const [form, setForm] = useState(empty)
  const [customers, setCustomers] = useState([])
  const [sourceAccounts, setSourceAccounts] = useState([])
  const [destinationAccounts, setDestinationAccounts] = useState([])
  const [customersLoading, setCustomersLoading] = useState(true)
  const [sourceLoading, setSourceLoading] = useState(false)
  const [destinationLoading, setDestinationLoading] = useState(false)
  const [insufficientPopup, setInsufficientPopup] = useState(false)
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [idempotencyKey] = useState(() => generateId())
  const notify = useToast()

  useEffect(() => {
    let active = true
    customerApi.paymentOptions()
      .then((data) => { if (active) setCustomers(data) })
      .catch((error) => { if (active) setErrors({ form: apiMessage(error) }) })
      .finally(() => { if (active) setCustomersLoading(false) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!form.senderCustomerId) return undefined
    let active = true
    customerApi.accounts(form.senderCustomerId)
      .then((data) => { if (active) setSourceAccounts(data) })
      .catch((error) => {
        if (active) {
          setSourceAccounts([])
          setErrors((current) => ({ ...current, sourceAccounts: apiMessage(error) }))
        }
      })
      .finally(() => { if (active) setSourceLoading(false) })
    return () => { active = false }
  }, [form.senderCustomerId])

  useEffect(() => {
    if (!form.receiverCustomerId) return undefined
    let active = true
    customerApi.accounts(form.receiverCustomerId)
      .then((data) => { if (active) setDestinationAccounts(data) })
      .catch((error) => {
        if (active) {
          setDestinationAccounts([])
          setErrors((current) => ({ ...current, destinationAccounts: apiMessage(error) }))
        }
      })
      .finally(() => { if (active) setDestinationLoading(false) })
    return () => { active = false }
  }, [form.receiverCustomerId])

  useEffect(() => {
    const close = (event) => event.key === 'Escape' && !submitting && onClose()
    document.addEventListener('keydown', close)
    document.body.style.overflow = 'hidden'
    return () => {
      document.removeEventListener('keydown', close)
      document.body.style.overflow = ''
    }
  }, [onClose, submitting])

  const receiverCustomers = useMemo(
    () => customers.filter((customer) => String(customer.id) !== form.senderCustomerId),
    [customers, form.senderCustomerId],
  )
  const sourceAccount = sourceAccounts.find((account) => String(account.id) === form.sourceAccountId)
  const amountIsValid = /^\d+(\.\d{1,2})?$/.test(form.amount) && Number(form.amount) > 0
  const feeAmount = amountIsValid ? Math.round(Number(form.amount) * 0.02 * 100) / 100 : 0
  const totalDeducted = amountIsValid ? Number(form.amount) + feeAmount : 0
  const insufficient = amountIsValid && sourceAccount
    && totalDeducted > Number(sourceAccount.availableBalance)
  const insufficientMessage = insufficient
    ? `Insufficient funds. Your available balance is ${money(sourceAccount.availableBalance, sourceAccount.currency)} but this transfer requires ${money(totalDeducted.toFixed(2), sourceAccount.currency)} (amount + 2% fee).`
    : ''

  const change = (field) => (event) => {
    const value = event.target.value
    if (field === 'senderCustomerId') {
      setSourceAccounts([])
      setSourceLoading(Boolean(value))
      if (value === form.receiverCustomerId) {
        setDestinationAccounts([])
        setDestinationLoading(false)
      }
    }
    if (field === 'receiverCustomerId') {
      setDestinationAccounts([])
      setDestinationLoading(Boolean(value))
    }
    setForm((current) => {
      if (field === 'senderCustomerId') {
        const receiverWasSender = value && value === current.receiverCustomerId
        return {
          ...current,
          senderCustomerId: value,
          sourceAccountId: '',
          receiverCustomerId: receiverWasSender ? '' : current.receiverCustomerId,
          destinationAccountId: receiverWasSender ? '' : current.destinationAccountId,
        }
      }
      if (field === 'receiverCustomerId') {
        return { ...current, receiverCustomerId: value, destinationAccountId: '' }
      }
      return { ...current, [field]: value }
    })
    setErrors((current) => ({
      ...current,
      [field]: '',
      form: '',
      ...(field === 'senderCustomerId' ? { sourceAccounts: '' } : {}),
      ...(field === 'receiverCustomerId' ? { destinationAccounts: '' } : {}),
    }))
  }

  const validationErrors = () => {
    const next = {}
    if (!form.senderCustomerId) next.senderCustomerId = 'Select a sender.'
    if (!form.sourceAccountId) next.sourceAccountId = 'Select a sender account.'
    if (!form.receiverCustomerId) next.receiverCustomerId = 'Select a receiver.'
    if (!form.destinationAccountId) next.destinationAccountId = 'Select a receiver account.'
    if (form.senderCustomerId && form.senderCustomerId === form.receiverCustomerId) {
      next.receiverCustomerId = 'Sender and receiver must be different.'
    }
    if (form.sourceAccountId && form.sourceAccountId === form.destinationAccountId) {
      next.destinationAccountId = 'Source and destination accounts must be different.'
    }
    if (!amountIsValid) {
      next.amount = 'Enter an amount greater than 0, less than 1000000, with up to 2 decimals.'
    }
    if (form.intermediaryBank.length > 120) next.intermediaryBank = 'Use 120 characters or fewer.'
    if (form.reference.length > 200) next.reference = 'Use 200 characters or fewer.'
    if (!sourceAccount?.currency) next.sourceAccountId = next.sourceAccountId || 'Select a valid sender account.'
    return next
  }

  const valid = Object.keys(validationErrors()).length === 0

  const submit = async (event) => {
    event.preventDefault()
    const next = validationErrors()
    if (Object.keys(next).length) {
      setErrors(next)
      return
    }
    setSubmitting(true)
    setErrors((current) => ({ ...current, form: '' }))
    try {
      const latestAccount = await customerApi.account(form.senderCustomerId, form.sourceAccountId)
      const latestFee = Math.round(Number(form.amount) * 0.02 * 100) / 100
      const latestTotal = Number(form.amount) + latestFee
      if (latestTotal > Number(latestAccount.availableBalance)) {
        setInsufficientPopup(true)
        setErrors((current) => ({ ...current, amount: `Insufficient funds. Your available balance is ${money(latestAccount.availableBalance, latestAccount.currency)} but this transfer requires ${money(latestTotal.toFixed(2), latestAccount.currency)} (amount + 2% fee).` }))
        return
      }
      const payment = await paymentApi.create({
        senderCustomerId: Number(form.senderCustomerId),
        sourceAccountId: Number(form.sourceAccountId),
        receiverCustomerId: Number(form.receiverCustomerId),
        destinationAccountId: Number(form.destinationAccountId),
        amount: Number(form.amount),
        currency: sourceAccount.currency,
        intermediaryBank: form.intermediaryBank.trim() || null,
        reference: form.reference.trim() || null,
      }, idempotencyKey)
      const failed = payment.status === 'FAILED'
      notify(failed ? 'Payment created, but processing failed.' : 'Payment completed successfully.', failed ? 'error' : 'success')
      onCreated(payment)
    } catch (error) {
      const message = apiMessage(error)
      if (error.response?.data?.code === 'INSUFFICIENT_FUNDS') setInsufficientPopup(true)
      notify(message, 'error')
      setErrors((current) => ({ ...current, form: message }))
    } finally {
      setSubmitting(false)
    }
  }

  const selectField = ({ name, label, disabled, loading, options, placeholder, emptyMessage }) => (
    <label className="block">
      <span className="label">{label}</span>
      <div className="relative">
        <select
          className={`input appearance-none pr-10 ${errors[name] ? 'border-danger' : ''}`}
          value={form[name]}
          onChange={change(name)}
          disabled={disabled || loading}
          aria-invalid={Boolean(errors[name])}
          aria-busy={loading}
        >
          <option value="">{loading ? 'Loading…' : options.length ? placeholder : emptyMessage}</option>
          {options.map((option) => <option key={option.id} value={option.id}>{option.label}</option>)}
        </select>
        {loading && <Loader2 className="pointer-events-none absolute right-3 top-3.5 size-4 animate-spin text-primary" />}
      </div>
      {errors[name] && <span className="mt-1.5 block text-xs text-red-600">{errors[name]}</span>}
    </label>
  )

  return (
    <div className="fixed inset-0 z-[60] grid items-end bg-slate-900/30 sm:place-items-center sm:p-5" onMouseDown={(event) => event.target === event.currentTarget && !submitting && onClose()}>
      <section className="max-h-screen w-full overflow-y-auto bg-white p-5 shadow-2xl transition sm:max-w-5xl sm:rounded-card sm:p-7 lg:p-8" role="dialog" aria-modal="true" aria-labelledby="create-title">
        <div className="flex items-start justify-between gap-6">
          <div>
            <h2 id="create-title" className="text-2xl font-bold text-ink">Create a payment</h2>
            <p className="mt-1 text-sm text-ink-muted">Choose the customers and their accounts to create a secure transfer.</p>
          </div>
          <button type="button" className="rounded-lg p-2 text-ink-muted transition hover:bg-slate-100" onClick={onClose} disabled={submitting} aria-label="Close">
            <X className="size-5" />
          </button>
        </div>

        <form className="mt-7" onSubmit={submit} noValidate>
          {errors.form && <p className="mb-5 rounded-lg bg-red-50 p-3 text-sm text-red-700" role="alert">{errors.form}</p>}
          {customersLoading && <div className="mb-5 flex items-center gap-2 rounded-lg bg-primary-light p-3 text-sm text-primary-hover"><Loader2 className="size-4 animate-spin" />Loading customers…</div>}
          {!customersLoading && !errors.form && customers.length === 0 && <p className="mb-5 rounded-lg bg-slate-50 p-3 text-sm text-ink-muted">No customers are available for a transfer.</p>}

          <div className="grid gap-6 md:grid-cols-2 md:gap-8">
            <div className="space-y-5">
              {selectField({
                name: 'senderCustomerId',
                label: 'Sender',
                loading: customersLoading,
                disabled: customers.length === 0,
                options: customers.map((customer) => ({ ...customer, label: customer.fullName })),
                placeholder: 'Select sender',
                emptyMessage: 'No customers available',
              })}
              {selectField({
                name: 'receiverCustomerId',
                label: 'Receiver',
                loading: customersLoading,
                disabled: !form.senderCustomerId || receiverCustomers.length === 0,
                options: receiverCustomers.map((customer) => ({ ...customer, label: customer.fullName })),
                placeholder: 'Select receiver',
                emptyMessage: form.senderCustomerId ? 'No other customers available' : 'Select a sender first',
              })}
              <label className="block">
                <span className="label">Amount</span>
                <input
                  className={`input h-14 text-2xl font-bold ${errors.amount ? 'border-danger' : ''}`}
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  value={form.amount}
                  onChange={change('amount')}
                  placeholder="0.00"
                  aria-label="Amount"
                  aria-invalid={Boolean(errors.amount)}
                />
                <span className="mt-2 block text-xs text-ink-muted">Source currency: <strong className="text-ink">{sourceAccount?.currency || 'Select a sender account'}</strong></span>
                {amountIsValid && sourceAccount?.currency && (
                  <span className="mt-2 block text-xs text-ink-muted">
                    Transaction fee (2%): <strong className="text-ink">{money(feeAmount.toFixed(2), sourceAccount.currency)}</strong>
                    {' '}— Total deducted: <strong className="text-ink">{money(totalDeducted.toFixed(2), sourceAccount.currency)}</strong>
                  </span>
                )}
                {(insufficientMessage || errors.amount) && <span className="mt-1.5 block text-sm font-semibold text-red-600" role="alert">{insufficientMessage || errors.amount}</span>}
              </label>
              <label className="block">
                <span className="label">Intermediary bank <span className="font-normal text-ink-muted">(optional)</span></span>
                <input className={`input ${errors.intermediaryBank ? 'border-danger' : ''}`} value={form.intermediaryBank} onChange={change('intermediaryBank')} placeholder="Correspondent bank" maxLength={121} />
                {errors.intermediaryBank && <span className="mt-1.5 block text-xs text-red-600">{errors.intermediaryBank}</span>}
              </label>
            </div>

            <div className="space-y-5">
              {selectField({
                name: 'sourceAccountId',
                label: 'Sender account',
                loading: sourceLoading,
                disabled: !form.senderCustomerId,
                options: sourceAccounts,
                placeholder: 'Select sender account',
                emptyMessage: form.senderCustomerId ? 'This customer has no active accounts' : 'Select a sender first',
              })}
              {errors.sourceAccounts && <p className="-mt-3 text-xs text-red-600">{errors.sourceAccounts}</p>}
              {selectField({
                name: 'destinationAccountId',
                label: 'Receiver account',
                loading: destinationLoading,
                disabled: !form.receiverCustomerId,
                options: destinationAccounts,
                placeholder: 'Select receiver account',
                emptyMessage: form.receiverCustomerId ? 'This customer has no active accounts' : 'Select a receiver first',
              })}
              {errors.destinationAccounts && <p className="-mt-3 text-xs text-red-600">{errors.destinationAccounts}</p>}
              <label className="block">
                <span className="label">Payment reference <span className="font-normal text-ink-muted">(optional)</span></span>
                <input className={`input ${errors.reference ? 'border-danger' : ''}`} value={form.reference} onChange={change('reference')} placeholder="Invoice or purpose" maxLength={201} />
                {errors.reference && <span className="mt-1.5 block text-xs text-red-600">{errors.reference}</span>}
              </label>
            </div>
          </div>

          <div className="mt-8 flex flex-col-reverse gap-3 border-t border-line pt-5 sm:flex-row sm:justify-end">
            <button type="button" className="btn-secondary sm:min-w-28" onClick={onClose} disabled={submitting}>Cancel</button>
            <button type="submit" className="btn-primary sm:min-w-40" disabled={!valid || submitting || customersLoading || sourceLoading || destinationLoading}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? 'Processing…' : 'Confirm transfer'}
            </button>
          </div>
        </form>
      </section>
      {insufficientPopup && <div className="fixed inset-0 z-[70] grid place-items-center bg-slate-900/40 p-5" role="dialog" aria-modal="true" aria-labelledby="insufficient-title">
        <section className="w-full max-w-md rounded-card bg-white p-6 shadow-2xl">
          <h3 id="insufficient-title" className="text-xl font-bold text-ink">Insufficient Funds</h3>
          <p className="mt-4 text-sm leading-6 text-ink-muted">You do not have sufficient funds in the selected sender account to complete this transaction.</p>
          <p className="mt-2 text-sm leading-6 text-ink-muted">Please enter a smaller amount or choose another account.</p>
          <div className="mt-6 flex justify-end"><button type="button" className="btn-primary min-w-24" onClick={() => setInsufficientPopup(false)}>OK</button></div>
        </section>
      </div>}
    </div>
  )
}
