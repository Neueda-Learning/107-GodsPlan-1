import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api/v1',
  timeout: 10000,
  headers: { Accept: 'application/json' },
  withCredentials: true,
})

export function apiMessage(error) {
  return error.response?.data?.message || (error.code === 'ECONNABORTED'
    ? 'The service took too long to respond. Please try again.'
    : 'We could not reach the payment service. Please try again.')
}

export const paymentApi = {
  list: (params = {}) => client.get('/payments', { params }).then(({ data }) => data),
  get: (id) => client.get(`/payments/${id}`).then(({ data }) => data),
  history: (id) => client.get(`/payments/${id}/history`).then(({ data }) => data),
  create: (payment, idempotencyKey) => client.post('/payments', payment, {
    headers: { 'Idempotency-Key': idempotencyKey },
  }).then(({ data }) => data),
}

export const customerApi = {
  list: (params = {}) => client.get('/customers', { params }).then(({ data }) => data),
  paymentOptions: () => client.get('/payment-options/customers').then(({ data }) => data),
  accounts: (customerId) => client.get(`/payment-options/customers/${customerId}/accounts`).then(({ data }) => data),
  account: (customerId, accountId) => client.get(`/payment-options/customers/${customerId}/accounts/${accountId}`).then(({ data }) => data),
  transactions: (customerId, params = {}) => client.get(`/customers/${customerId}/transactions`, { params }).then(({ data }) => data),
}

export const analyticsApi = {
  overview: (params = {}) => client.get('/analytics/overview', { params }).then(({ data }) => data),
  recent: (params = {}) => client.get('/analytics/recent-transactions', { params }).then(({ data }) => data),
  exchangeRates: (params = {}) => client.get('/analytics/exchange-rates', { params }).then(({ data }) => data),
}

export const authApi = {
  me: () => client.get('/auth/me').then(({ data }) => data),
}

export const exchangeRateApi = {
  quote: (params) => client.get('/exchange-rates/quote', { params }).then(({ data }) => data),
}
