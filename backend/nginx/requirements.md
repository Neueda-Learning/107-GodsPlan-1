# Payments Processing System - Frontend Requirements (Codex)

## Goal
Build a clean, modern React frontend for the Payments Processing System.

- React (JSX)
- Vite
- Tailwind CSS
- React Router
- Axios
- Recharts (dashboard charts)
- Lucide React icons

Keep the code modular, reusable and responsive.

---

# Design

Create a modern banking dashboard with a **soft pastel blue and grey** theme.

## Theme

- Background: `#F7F9FC`
- Cards: `#FFFFFF`
- Primary: `#6FAEDB`
- Primary Hover: `#5A9DCC`
- Primary Light: `#EEF6FC`
- Borders: `#DDE7F1`
- Primary Text: `#334155`
- Secondary Text: `#64748B`
- Success: `#67B68A`
- Warning: `#D8B24C`
- Error: `#D77A7A`

Style:
- Minimal
- Soft shadows
- Rounded corners (12px)
- Plenty of whitespace
- No gradients
- No glassmorphism
- No dark mode

---

# Pages

## Dashboard (/)

Display:

- Total Payments
- Completed
- Failed
- In Progress
- Status Doughnut Chart
- Recent Payments (5)

Refresh automatically after actions.

---

## Payments (/payments)

Features:

- Search by ID or Reference
- Filter by Status
- Pagination
- Click row -> Payment Details

Columns:

- ID
- Amount
- Currency
- Status
- Created At

---

## Payment Details (/payments/:id)

Display:

- Payment details
- Source & Destination Account
- Amount
- Converted Amount (if applicable)
- Exchange Rate
- Status Badge
- Status History Timeline
- Error banner for FAILED payments

---

## Create Payment

Open as a modal.

Fields:

- Source Account
- Destination Account
- Amount
- Reference

Requirements:

- Client-side validation
- Disable submit while loading
- Generate idempotency key once
- Close modal after successful creation
- Redirect to Payment Details

---

# Components

Create reusable components.

- Sidebar
- Topbar
- DashboardCard
- StatusBadge
- PaymentTable
- PaymentTimeline
- SearchBar
- FilterDropdown
- Pagination
- CreatePaymentModal
- Toast
- LoadingSkeleton
- EmptyState
- ErrorBanner

---

# Routing

- /
- /payments
- /payments/:id

---

# API

Use Axios.

Create a dedicated API layer.

Handle:

- Loading
- Errors
- Success Toasts

Do not hardcode data.

---

# Responsive Design

Desktop:
- Fixed sidebar

Tablet:
- Collapsible sidebar

Mobile:
- Drawer sidebar
- Fullscreen create-payment modal
- Tables become cards

---

# UX

- Smooth 150–250ms transitions
- Skeleton loaders
- Inline validation
- Keyboard accessible
- Visible focus states
- Color + icon for every status

---

# Folder Structure

```
src/
  api/
  components/
  layouts/
  pages/
  hooks/
  utils/
  assets/
  App.jsx
  main.jsx
```

---

# General Instructions for Codex

- Use functional React components.
- Use JSX only (no TypeScript).
- Use Tailwind CSS for all styling.
- Keep components small and reusable.
- Avoid duplicated code.
- Follow clean folder structure.
- Build production-quality UI.
- Match the supplied backend API exactly.
- Do not invent business logic or endpoints.
- Prioritize readability, responsiveness, and maintainability.
