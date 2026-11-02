import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockCustomerUser } from './test-utils';
import { LoansPage } from '../pages/customer/LoansPage';
import { LoanDetailPage } from '../pages/customer/LoanDetailPage';
import { loanApi } from '../api/loans';
import { accountApi } from '../api/accounts';
import { LoanResponse, BankAccount } from '../types';
import { Routes, Route } from 'react-router-dom';

vi.mock('../api/loans');
vi.mock('../api/accounts');

const mockLoan: LoanResponse = {
  id: 'loan-1',
  loanNumber: 'LN-202611-000001',
  customerNumber: 'CUST-001',
  disbursementAccountNumber: 'ACC-100000000001',
  loanType: 'PERSONAL',
  principalAmount: 100000.0,
  annualInterestRate: 12.0,
  termMonths: 12,
  monthlyEmi: 8884.88,
  totalInterest: 6618.55,
  totalPayable: 106618.55,
  outstandingPrincipal: 100000.0,
  outstandingInterest: 6618.55,
  status: 'ACTIVE',
  appliedAt: '2026-11-01T10:00:00Z',
  schedules: [
    {
      installmentNumber: 1,
      dueDate: '2026-12-01T00:00:00Z',
      principalDue: 7884.88,
      interestDue: 1000.0,
      totalEmi: 8884.88,
      remainingBalance: 92115.12,
      status: 'SCHEDULED',
    },
  ],
};

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
];

describe('Loan Engine & Repayments', () => {
  it('Scenario 13: calculates EMI quotation quote via public calculator API', async () => {
    const user = userEvent.setup();
    vi.mocked(loanApi.getMyLoans).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      first: true,
      last: true,
      empty: true,
    });
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);
    vi.mocked(loanApi.calculateEmi).mockResolvedValue({
      principal: 100000.0,
      annualInterestRate: 12.0,
      termMonths: 12,
      monthlyEmi: 8884.88,
      totalInterest: 6618.55,
      totalPayable: 106618.55,
    });

    renderWithProviders(<LoansPage />, {
      initialEntries: ['/loans'],
      initialUser: mockCustomerUser,
    });

    // Submit EMI calculator
    await user.click(screen.getByRole('button', { name: /calculate monthly emi/i }));

    await waitFor(() => {
      expect(loanApi.calculateEmi).toHaveBeenCalledWith({
        principal: 100000,
        annualInterestRate: 12,
        termMonths: 12,
      });
      // Summary displayed
      expect(screen.getByText(/Amortization Summary Quote/i)).toBeInTheDocument();
      expect(screen.getByText(/₹8,884.88/i)).toBeInTheDocument();
      expect(screen.getByText(/₹6,618.55/i)).toBeInTheDocument();
    });
  });

  it('Scenario 14: submits loan application and redirects to loan details', async () => {
    const user = userEvent.setup();
    vi.mocked(loanApi.getMyLoans).mockResolvedValue({
      content: [],
      totalElements: 0,
      totalPages: 0,
      size: 10,
      number: 0,
      first: true,
      last: true,
      empty: true,
    });
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);
    vi.mocked(loanApi.applyForLoan).mockResolvedValue(mockLoan);

    renderWithProviders(<LoansPage />, {
      initialEntries: ['/loans'],
      initialUser: mockCustomerUser,
    });

    // Open Apply modal
    await user.click(screen.getByRole('button', { name: /apply for a loan/i }));
    expect(screen.getByRole('heading', { name: /submit loan application/i })).toBeInTheDocument();

    // Select account
    await user.selectOptions(
      screen.getByLabelText(/disbursement account/i),
      'ACC-100000000001'
    );

    // Submit
    await user.click(screen.getByRole('button', { name: /submit application/i }));

    await waitFor(() => {
      expect(loanApi.applyForLoan).toHaveBeenCalledWith({
        loanType: 'PERSONAL',
        principal: 100000,
        annualInterestRate: 12,
        termMonths: 12,
        disbursementAccountNumber: 'ACC-100000000001',
      });
    });
  });

  it('Scenario 15: displays loan amortization schedule and executes repayment confirmation', async () => {
    const user = userEvent.setup();
    vi.mocked(loanApi.getLoanDetails).mockResolvedValue(mockLoan);
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);
    vi.mocked(loanApi.repayLoan).mockResolvedValue({
      repaymentReference: 'RP-202611-000001',
      loanNumber: 'LN-202611-000001',
      transactionReference: 'TX-REPAY-001',
      amountPaid: 8884.88,
      principalPaid: 7884.88,
      interestPaid: 1000.0,
      remainingPrincipal: 92115.12,
      loanStatus: 'ACTIVE',
      timestamp: '2026-11-01T16:00:00Z',
    });

    renderWithProviders(
      <Routes>
        <Route path="/loans/:loanNumber" element={<LoanDetailPage />} />
      </Routes>,
      { initialEntries: ['/loans/LN-202611-000001'], initialUser: mockCustomerUser }
    );

    // Verify schedule table
    await waitFor(() => {
      expect(screen.getByText('LN-202611-000001')).toBeInTheDocument();
      expect(screen.getByText('Amortization Repayment Schedule')).toBeInTheDocument();
      expect(screen.getByText('SCHEDULED')).toBeInTheDocument();
    });

    // Click Make Payment
    await user.click(screen.getByRole('button', { name: /make installment payment/i }));
    expect(screen.getByRole('heading', { name: /pay loan installment/i })).toBeInTheDocument();

    // Review & Confirm
    await user.click(screen.getByRole('button', { name: /review repayment/i }));
    expect(screen.getByRole('heading', { name: /confirm loan repayment/i })).toBeInTheDocument();

    // Authorize
    await user.click(screen.getByRole('button', { name: /authorize payment/i }));

    await waitFor(() => {
      expect(loanApi.repayLoan).toHaveBeenCalledWith(
        'LN-202611-000001',
        {
          sourceAccountNumber: 'ACC-100000000001',
          amount: 8884.88,
        }
      );
      expect(screen.getByText('RP-202611-000001')).toBeInTheDocument();
      expect(screen.getByText(/repayment processed successfully/i)).toBeInTheDocument();
    });
  });
});

