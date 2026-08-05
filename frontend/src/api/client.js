import axios from 'axios'

const client = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api/v1',
  timeout: 10000,
  headers: { Accept: 'application/json' },
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

