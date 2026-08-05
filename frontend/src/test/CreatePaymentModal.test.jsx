import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CreatePaymentModal from '../components/CreatePaymentModal'

const api = vi.hoisted(() => ({
  paymentOptions: vi.fn(),
  accounts: vi.fn(),
  create: vi.fn(),
  notify: vi.fn(),
}))

vi.mock('../api/client', () => ({
  apiMessage: (error) => error.message,
  customerApi: { paymentOptions: api.paymentOptions, accounts: api.accounts },
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
      ? [{ id: 12, label: 'Checking Account · XXXX 1234 · USD', currency: 'USD' }]
      : [{ id: 13, label: 'Savings Account · XXXX 5678 · EUR', currency: 'EUR' }])
    api.create.mockResolvedValue({ id: 44, status: 'COMPLETED' })
  })

  it('uses dependent customer/account dropdowns and submits IDs internally', async () => {
    const user = userEvent.setup()
    const created = vi.fn()
    render(<CreatePaymentModal onClose={() => {}} onCreated={created} />)

    await user.selectOptions(await screen.findByLabelText('Sender'), '2')
    await user.selectOptions(await screen.findByLabelText('Sender account'), '12')
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
})
