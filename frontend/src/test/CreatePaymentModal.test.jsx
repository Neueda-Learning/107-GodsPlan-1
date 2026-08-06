import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CreatePaymentModal from '../components/CreatePaymentModal'

const api = vi.hoisted(() => ({
  paymentOptions: vi.fn(),
  accounts: vi.fn(),
  account: vi.fn(),
  quote: vi.fn(),
  create: vi.fn(),
  notify: vi.fn(),
}))

vi.mock('../api/client', () => ({
  apiMessage: (error) => error.message,
  customerApi: { paymentOptions: api.paymentOptions, accounts: api.accounts, account: api.account },
  exchangeRateApi: { quote: api.quote },
  paymentApi: { create: api.create },
}))

vi.mock('../hooks/useToast', () => ({ useToast: () => api.notify }))

describe('CreatePaymentModal', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    api.paymentOptions.mockResolvedValue([
      { id: 2, fullName: 'Nihal Yadav', country: 'India' },
      { id: 3, fullName: 'Sriya Patel', country: 'India' },
    ])
    api.accounts.mockImplementation(async (customerId) => String(customerId) === '2'
      ? [{ id: 12, label: 'Checking Account · XXXX 1234 · USD', currency: 'USD', availableBalance: 1000 }]
      : [{ id: 13, label: 'Savings Account · XXXX 5678 · EUR', currency: 'EUR', availableBalance: 500 }])
    api.account.mockResolvedValue({ id: 12, label: 'Checking Account · XXXX 1234 · USD', currency: 'USD', availableBalance: 1000 })
    api.quote.mockImplementation(async ({ sourceCurrency, destinationCurrency, amount }) => ({
      sourceCurrency,
      destinationCurrency,
      sourceAmount: Number(amount),
      exchangeRate: destinationCurrency === sourceCurrency ? 1 : 0.92,
      destinationAmount: Number(amount) * (destinationCurrency === sourceCurrency ? 1 : 0.92),
      source: 'test-provider',
    }))
    api.create.mockResolvedValue({ id: 44, status: 'COMPLETED' })
  })

  it('uses dependent customer/account dropdowns and submits IDs internally', async () => {
    const user = userEvent.setup()
    const created = vi.fn()
    render(<CreatePaymentModal onClose={() => {}} onCreated={created} />)

    await user.selectOptions(await screen.findByLabelText('Sender'), '2')
    await user.selectOptions(await screen.findByLabelText('Sender account'), '12')
    expect(await screen.findByText(/Available Balance:/)).toHaveTextContent('USD 1,000.00')
    const receiver = screen.getByLabelText('Receiver')
    expect(receiver).not.toHaveTextContent('Nihal Yadav')
    await user.selectOptions(receiver, '3')
    await user.selectOptions(await screen.findByLabelText('Receiver account'), '13')
    await user.type(screen.getByLabelText('Amount'), '25.50')
    await user.type(screen.getByLabelText(/Payment reference/), 'Invoice 42')

    const confirm = screen.getByRole('button', { name: 'Confirm transfer' })
    await waitFor(() => expect(confirm).toBeEnabled())
    await user.click(confirm)

    await waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({
      senderCustomerId: 2,
      sourceAccountId: 12,
      receiverCustomerId: 3,
      destinationAccountId: 13,
      amount: 25.5,
      currency: 'USD',
      reference: 'Invoice 42',
    }), expect.any(String)))
    expect(created).toHaveBeenCalledWith({ id: 44, status: 'COMPLETED' })
  })

  it('disables transfer and shows the insufficient-funds validation', async () => {
    const user = userEvent.setup()
    render(<CreatePaymentModal onClose={() => {}} onCreated={() => {}} />)
    await user.selectOptions(await screen.findByLabelText('Sender'), '2')
    await user.selectOptions(await screen.findByLabelText('Sender account'), '12')
    await screen.findByText(/Available Balance:/)
    await user.selectOptions(screen.getByLabelText('Receiver'), '3')
    await user.selectOptions(await screen.findByLabelText('Receiver account'), '13')
    await user.type(screen.getByLabelText('Amount'), '1000.01')

    expect(await screen.findByText(/Insufficient funds\. Your available balance is USD 1,000.00/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Confirm transfer' })).toBeDisabled()
    expect(api.create).not.toHaveBeenCalled()
  })

  it('shows the insufficient-funds popup when the backend rejects the final check', async () => {
    const user = userEvent.setup()
    const error = Object.assign(new Error('The selected account does not have sufficient funds to complete this transaction.'), {
      response: { data: { code: 'INSUFFICIENT_FUNDS' } },
    })
    api.create.mockRejectedValueOnce(error)
    render(<CreatePaymentModal onClose={() => {}} onCreated={() => {}} />)
    await user.selectOptions(await screen.findByLabelText('Sender'), '2')
    await user.selectOptions(await screen.findByLabelText('Sender account'), '12')
    await screen.findByText(/Available Balance:/)
    await user.selectOptions(screen.getByLabelText('Receiver'), '3')
    await user.selectOptions(await screen.findByLabelText('Receiver account'), '13')
    await user.type(screen.getByLabelText('Amount'), '100.00')
    const confirm = screen.getByRole('button', { name: 'Confirm transfer' })
    await waitFor(() => expect(confirm).toBeEnabled())
    await user.click(confirm)

    expect(await screen.findByRole('heading', { name: 'Insufficient Funds' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'OK' })).toBeInTheDocument()
  })
})
