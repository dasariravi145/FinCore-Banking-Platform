import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockCustomerUser } from './test-utils';
import { CustomerDashboardPage } from '../pages/customer/CustomerDashboardPage';
import { AccountsPage } from '../pages/customer/AccountsPage';
import { AccountDetailPage } from '../pages/customer/AccountDetailPage';
import { customerApi } from '../api/customers';
import { accountApi } from '../api/accounts';
import { loanApi } from '../api/loans';
import { notificationApi } from '../api/notifications';
import { transferApi } from '../api/transfers';
import { BankAccount } from '../types';
import { Routes, Route } from 'react-router-dom';

vi.mock('../api/customers');
vi.mock('../api/accounts');
vi.mock('../api/loans');
vi.mock('../api/notifications');
vi.mock('../api/transfers');

const mockAccounts: BankAccount[] = [
  {
    id: 'acc-1',
    accountNumber: 'ACC-100000000001',
    accountType: 'SAVINGS',
    accountCategory: 'LIABILITY',
    currency: 'INR',
    balance: 50000.0,
    status: 'ACTIVE',
    createdAt: '2026-11-01T12:00:00Z',
  },
  {
    id: 'acc-2',
    accountNumber: 'ACC-100000000002',
    accountType: 'CURRENT',
    accountCategory: 'LIABILITY',
    currency: 'INR',
    balance: 15000.0,
    status: 'ACTIVE',
    createdAt: '2026-11-01T14:00:00Z',
  },
];

describe('Customer Portal Features', () => {
  it('Scenario 5: renders customer dashboard with accounts and portfolio balance', async () => {
    vi.mocked(customerApi.getMyProfile).mockResolvedValue({
      id: 'cust-1',
      userId: mockCustomerUser.id,
      customerNumber: 'CUST-202611-001',
      firstName: 'Aarav',
      lastName: 'Sharma',
      email: 'customer@fincore.local',
      phoneNumber: '+919876543210',
      dateOfBirth: '1990-05-15',
      addressLine1: '123 Marine Drive',
      city: 'Mumbai',
      state: 'Maharashtra',
      postalCode: '400020',
      country: 'India',
      nationalId: 'SYNTH-PAN-12345',
      status: 'ACTIVE',
      createdAt: '2026-11-01T12:00:00Z',
    });
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);
    vi.mocked(loanApi.getMyLoans).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 5,
      number: 0,
      first: true,
      last: true,
      empty: true,
    });
    vi.mocked(notificationApi.getNotifications).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 5,
      number: 0,
      first: true,
      last: true,
      empty: true,
    });

    renderWithProviders(<CustomerDashboardPage />, {
      initialEntries: ['/dashboard'],
      initialUser: mockCustomerUser,
    });

    // Check greeting
    await waitFor(() => {
      expect(screen.getByText(/Good day, Aarav Sharma/i)).toBeInTheDocument();
      // Total balance (50,000 + 15,000 = ₹65,000.00)
      expect(screen.getByText(/₹65,000.00/i)).toBeInTheDocument();
      expect(screen.getByText('CUST-202611-001', { exact: false })).toBeInTheDocument();
    });
  });

  it('Scenario 6: lists all customer bank accounts with balances', async () => {
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);

    renderWithProviders(<AccountsPage />, {
      initialEntries: ['/accounts'],
      initialUser: mockCustomerUser,
    });

    await waitFor(() => {
      expect(screen.getByText('ACC-100000000001')).toBeInTheDocument();
      expect(screen.getByText('ACC-100000000002')).toBeInTheDocument();
      expect(screen.getByText(/₹50,000.00/i)).toBeInTheDocument();
      expect(screen.getByText(/₹15,000.00/i)).toBeInTheDocument();
    });
  });

  it('Scenario 7: opens modal and creates a new bank account', async () => {
    const user = userEvent.setup();
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);
    vi.mocked(accountApi.openAccount).mockResolvedValue({
      id: 'acc-3',
      accountNumber: 'ACC-100000000003',
      accountType: 'SAVINGS',
      accountCategory: 'LIABILITY',
      currency: 'INR',
      balance: 0.0,
      status: 'ACTIVE',
      createdAt: '2026-11-01T16:00:00Z',
    });

    renderWithProviders(<AccountsPage />, {
      initialEntries: ['/accounts'],
      initialUser: mockCustomerUser,
    });

    // Click Open New Account
    await user.click(screen.getByRole('button', { name: /open new account/i }));
    expect(screen.getByRole('heading', { name: /open a new bank account/i })).toBeInTheDocument();

    // Submit form
    await user.click(screen.getByRole('button', { name: /confirm & open account/i }));

    await waitFor(() => {
      expect(accountApi.openAccount).toHaveBeenCalledWith({ accountType: 'SAVINGS' });
      expect(screen.getByText(/account opened/i)).toBeInTheDocument();
    });
  });

  it('Scenario 12: displays account transactions and double-entry reconciliation', async () => {
    vi.mocked(accountApi.getAccountDetails).mockResolvedValue(mockAccounts[0]);
    vi.mocked(transferApi.reconcileAccount).mockResolvedValue({
      accountNumber: 'ACC-100000000001',
      accountBalance: 50000.0,
      ledgerBalance: 50000.0,
      reconciled: true,
      difference: 0.0,
    });
    vi.mocked(transferApi.getTransactions).mockResolvedValue({
      content: [
        {
          id: 'tx-1',
          transactionReference: 'TX-TEST-RECEIPT-001',
          sourceAccountNumber: 'ACC-100000000001',
          destinationAccountNumber: 'ACC-100000000002',
          amount: 5000.0,
          currency: 'INR',
          type: 'INTERNAL_TRANSFER',
          status: 'COMPLETED',
          timestamp: '2026-11-01T15:00:00Z',
          description: 'Payment for groceries',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 15,
      number: 0,
      first: true,
      last: true,
      empty: false,
    });

    renderWithProviders(
      <Routes>
        <Route path="/accounts/:accountNumber" element={<AccountDetailPage />} />
      </Routes>,
      { initialEntries: ['/accounts/ACC-100000000001'], initialUser: mockCustomerUser }
    );

    await waitFor(() => {
      expect(screen.getByText('BALANCED & VERIFIED')).toBeInTheDocument();
      expect(screen.getByText('TX-TEST-RECEIPT-001')).toBeInTheDocument();
      expect(screen.getByText('Payment for groceries')).toBeInTheDocument();
      expect(screen.getAllByText(/DEBIT/i).length).toBeGreaterThanOrEqual(1);
    });
  });
});
