import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import StatusBadge from '../components/StatusBadge'
import { formatMoney } from '../utils/format'

describe('payment presentation', () => {
  it('renders a human-readable completed status', () => {
    render(<StatusBadge status="COMPLETED" />)
    expect(screen.getByText('Completed')).toBeInTheDocument()
  })

  it('formats monetary values in their payment currency', () => {
    expect(formatMoney(125.5, 'USD')).toContain('$125.50')
  })
})
