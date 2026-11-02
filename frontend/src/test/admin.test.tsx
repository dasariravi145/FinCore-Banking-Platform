import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockAdminUser } from './test-utils';
import { AdminLoansPage } from '../pages/admin/AdminLoansPage';
import { AdminFraudPage } from '../pages/admin/AdminFraudPage';
import { loanApi } from '../api/loans';
import { fraudApi } from '../api/fraud';
import { LoanResponse, FraudAssessment } from '../types';

vi.mock('../api/loans');
vi.mock('../api/fraud');

const mockLoanApplied: LoanResponse = {
  id: 'loan-applied-1',
  loanNumber: 'LN-202611-APPLIED-001',
  customerNumber: 'CUST-001',
  disbursementAccountNumber: 'ACC-100000000001',
  loanType: 'PERSONAL',
  principalAmount: 50000.0,
  annualInterestRate: 10.0,
  termMonths: 6,
  monthlyEmi: 8578.74,
  totalInterest: 1472.44,
  totalPayable: 51472.44,
  outstandingPrincipal: 50000.0,
  outstandingInterest: 1472.44,
  status: 'APPLIED',
  appliedAt: '2026-11-01T11:00:00Z',
};

const mockFraudPending: FraudAssessment = {
  id: 'fraud-1111-2222-3333-444455556666',
  transactionReference: 'TX-FLAGGED-001',
  sourceAccountNumber: 'ACC-100000000001',
  destinationAccountNumber: 'ACC-100000000002',
  amount: 600000.0,
  riskScore: 75,
  riskLevel: 'HIGH',
  decision: 'BLOCK',
  status: 'PENDING_REVIEW',
  triggeredRules: ['HighValueTransferRule', 'ExcessiveBalanceDepletionRule'],
  createdAt: '2026-11-01T12:00:00Z',
};

describe('Admin Management Operations', () => {
  it('Scenario 18: allows admin to review and approve loan application', async () => {
    const user = userEvent.setup();
    vi.mocked(loanApi.getAllLoans).mockResolvedValue({
      content: [mockLoanApplied],
      totalElements: 1,
      totalPages: 1,
      size: 15,
      number: 0,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(loanApi.reviewLoan).mockResolvedValue({
      ...mockLoanApplied,
      status: 'APPROVED',
      approvedAt: '2026-11-01T12:00:00Z',
    });

    renderWithProviders(<AdminLoansPage />, {
      initialEntries: ['/admin/loans'],
      initialUser: mockAdminUser,
    });

    await waitFor(() => {
      expect(screen.getByText('LN-202611-APPLIED-001')).toBeInTheDocument();
      expect(screen.getByText('APPLIED')).toBeInTheDocument();
    });

    // Click Review App
    await user.click(screen.getByRole('button', { name: /review app/i }));
    expect(screen.getByRole('heading', { name: /review application/i })).toBeInTheDocument();

    // Select Approve & Submit
    await user.click(screen.getByRole('button', { name: /submit review decision/i }));

    await waitFor(() => {
      expect(loanApi.reviewLoan).toHaveBeenCalledWith(
        'LN-202611-APPLIED-001',
        expect.objectContaining({
          approved: true,
        })
      );
      expect(screen.getByText(/loan reviewed/i)).toBeInTheDocument();
    });
  });

  it('Scenario 19: allows compliance officer to review and decision fraud assessment', async () => {
    const user = userEvent.setup();
    vi.mocked(fraudApi.getAssessments).mockResolvedValue({
      content: [mockFraudPending],
      totalElements: 1,
      totalPages: 1,
      size: 15,
      number: 0,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(fraudApi.reviewAssessment).mockResolvedValue({
      ...mockFraudPending,
      status: 'APPROVED',
      reviewedAt: '2026-11-01T13:00:00Z',
    });

    renderWithProviders(<AdminFraudPage />, {
      initialEntries: ['/admin/fraud'],
      initialUser: mockAdminUser,
    });

    await waitFor(() => {
      expect(screen.getByText('TX-FLAGGED-001')).toBeInTheDocument();
      expect(screen.getByText('75 / 100')).toBeInTheDocument();
      expect(screen.getByText('HighValueTransferRule')).toBeInTheDocument();
    });

    // Click Review Alert
    await user.click(screen.getByRole('button', { name: /review alert/i }));
    expect(screen.getByRole('heading', { name: /fraud assessment review/i })).toBeInTheDocument();

    // Submit compliance approval
    await user.click(screen.getByRole('button', { name: /submit compliance decision/i }));

    await waitFor(() => {
      expect(fraudApi.reviewAssessment).toHaveBeenCalledWith(
        'fraud-1111-2222-3333-444455556666',
        expect.objectContaining({
          approved: true,
        })
      );
      expect(screen.getByText(/assessment reviewed/i)).toBeInTheDocument();
    });
  });
});

