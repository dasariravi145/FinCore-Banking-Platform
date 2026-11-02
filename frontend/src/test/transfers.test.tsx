import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockCustomerUser } from './test-utils';
import { TransfersPage } from '../pages/customer/TransfersPage';
import { accountApi } from '../api/accounts';
import { transferApi } from '../api/transfers';
import { BankAccount, TransferReceipt } from '../types';

vi.mock('../api/accounts');
vi.mock('../api/transfers');

const mockAccounts: BankAccount[] = [
  {
    id: 'acc-1',
    accountNumber: 'ACC-100000000001',
    accountType: 'SAVINGS',
    accountCategory: 'LIABILITY',
    currency: 'INR',
    balance: 10000.0,
    status: 'ACTIVE',
    createdAt: '2026-11-01T12:00:00Z',
  },
];

describe('Funds Transfers & Idempotency', () => {
  it('Scenario 8: validates transfer form and rejects excessive amount', async () => {
    const user = userEvent.setup();
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);

    renderWithProviders(<TransfersPage />, {
      initialEntries: ['/transfers'],
      initialUser: mockCustomerUser,
    });

    // Wait for account options to load
    await screen.findByRole('option', { name: /ACC-100000000001/i });

    // Select source account
    await user.selectOptions(screen.getByLabelText(/1. select source account/i), 'ACC-100000000001');

    // Type destination account
    await user.type(screen.getByLabelText(/2. destination account number/i), 'ACC-100000000002');

    // Type amount greater than balance (15000 > 10000)
    await user.type(screen.getByLabelText(/3. transfer amount/i), '15000');

    // Button should be disabled or error indicated
    await waitFor(() => {
      expect(screen.getByText(/exceeds available account balance/i)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /review transfer details/i })).toBeDisabled();
    });
  });

  it('Scenario 9 & 20: displays confirmation dialog for sensitive financial action before executing', async () => {
    const user = userEvent.setup();
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);

    const mockReceipt: TransferReceipt = {
      transactionReference: 'TX-CONFIRM-TEST-001',
      sourceAccountNumber: 'ACC-100000000001',
      destinationAccountNumber: 'ACC-100000000002',
      amount: 2500.0,
      currency: 'INR',
      type: 'INTERNAL_TRANSFER',
      status: 'COMPLETED',
      timestamp: '2026-11-01T15:30:00Z',
      description: 'Vendor payment',
      idempotencyKey: '00000000-0000-0000-0000-000000000001',
    };
    vi.mocked(transferApi.executeTransfer).mockResolvedValue(mockReceipt);

    renderWithProviders(<TransfersPage />, {
      initialEntries: ['/transfers'],
      initialUser: mockCustomerUser,
    });

    // Wait for options
    await screen.findByRole('option', { name: /ACC-100000000001/i });

    // Fill valid transfer details
    await user.selectOptions(screen.getByLabelText(/1. select source account/i), 'ACC-100000000001');
    await user.type(screen.getByLabelText(/2. destination account number/i), 'ACC-100000000002');
    await user.type(screen.getByLabelText(/3. transfer amount/i), '2500');

    // Step 5: Review transfer
    await user.click(screen.getByRole('button', { name: /review transfer details/i }));

    // Step 6: Confirmation dialog is displayed
    await screen.findByRole('heading', { name: /confirm funds transfer/i });
    expect(screen.getAllByText(/ACC-100000000001/i).length).toBeGreaterThanOrEqual(1);
    expect(screen.getAllByText(/ACC-100000000002/i).length).toBeGreaterThanOrEqual(1);

    // Step 8: Authorize payment
    await user.click(screen.getByRole('button', { name: /authorize & post transfer/i }));

    await waitFor(() => {
      expect(transferApi.executeTransfer).toHaveBeenCalledWith(
        {
          sourceAccountNumber: 'ACC-100000000001',
          destinationAccountNumber: 'ACC-100000000002',
          amount: 2500,
          currency: 'INR',
          description: 'Vendor payment',
        },
        expect.any(String)
      );
      // Receipt displayed
      expect(screen.getByText('TX-CONFIRM-TEST-001')).toBeInTheDocument();
      expect(screen.getByText(/transfer successfully executed/i)).toBeInTheDocument();
    });
  });

  it('Scenario 10 & 11: preserves idempotency key on failure for safe retry and handles backend error', async () => {
    const user = userEvent.setup();
    vi.mocked(accountApi.getMyAccounts).mockResolvedValue(mockAccounts);

    const axiosError = {
      isAxiosError: true,
      response: {
        status: 422,
        data: { message: 'Transfer blocked by real-time fraud detection engine' },
      },
      message: 'Transfer blocked',
    };
    vi.mocked(transferApi.executeTransfer).mockRejectedValue(axiosError);

    renderWithProviders(<TransfersPage />, {
      initialEntries: ['/transfers'],
      initialUser: mockCustomerUser,
    });

    // Wait for options
    await screen.findByRole('option', { name: /ACC-100000000001/i });

    await user.selectOptions(screen.getByLabelText(/1. select source account/i), 'ACC-100000000001');
    await user.type(screen.getByLabelText(/2. destination account number/i), 'ACC-100000000002');
    await user.type(screen.getByLabelText(/3. transfer amount/i), '5000');

    // Review & Confirm
    await user.click(screen.getByRole('button', { name: /review transfer details/i }));
    await screen.findByRole('heading', { name: /confirm funds transfer/i });
    await user.click(screen.getByRole('button', { name: /authorize & post transfer/i }));

    await waitFor(() => {
      // Backend error toast displayed
      expect(screen.getByText(/transfer blocked by real-time fraud detection/i)).toBeInTheDocument();
      // The idempotency key remains unchanged for safe retry!
      expect(transferApi.executeTransfer).toHaveBeenCalledWith(
        expect.anything(),
        expect.any(String)
      );
    });
  });
});
