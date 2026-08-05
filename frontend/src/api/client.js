import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
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

async function csrfHeaders() {
  const { data } = await client.get('/auth/csrf')
  return { [data.headerName]: data.token }
}

export const authApi = {
  me: () => client.get('/auth/me').then(({ data }) => data),
  login: async (email, password) => {
    const headers = await csrfHeaders()
    const body = new URLSearchParams({ username: email, password })
    return client.post('/auth/login', body, {
      headers: { ...headers, 'Content-Type': 'application/x-www-form-urlencoded' },
    }).then(({ data }) => data)
  },
  logout: async () => client.post('/auth/logout', null, { headers: await csrfHeaders() }),
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
