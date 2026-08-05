import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import StatusBadge from '../components/StatusBadge'
import TransactionStatusBadge from '../components/TransactionStatusBadge'
import { formatMoney } from '../utils/format'

describe('payment presentation', () => {
  it('renders a human-readable completed status', () => {
    render(<StatusBadge status="COMPLETED" />)
    expect(screen.getByText('Completed')).toBeInTheDocument()
  })

  it('formats monetary values in their payment currency', () => {
    expect(formatMoney(125.5, 'USD')).toContain('$125.50')
  })

  it.each([
    ['SUCCESSFUL', 'Successful'],
    ['FAILED', 'Failed'],
    ['PENDING', 'Pending'],
  ])('renders the %s customer transaction outcome', (outcome, label) => {
    render(<TransactionStatusBadge outcome={outcome} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })
})
