const iso = (date) => {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000)
  return local.toISOString().slice(0, 10)
}

export function defaultAnalyticsFilters() {
  const to = new Date()
  const from = new Date(to)
  from.setDate(from.getDate() - 29)
  return { from: iso(from), to: iso(to), status: '', currency: '', paymentMethod: '', customerId: '',
    minimumAmount: '', maximumAmount: '', baseCurrency: '', grouping: 'AUTO' }
}
