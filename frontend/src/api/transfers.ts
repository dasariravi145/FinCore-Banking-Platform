import { apiClient } from './client';
import { TransferReceipt, TransactionSummary, ReconciliationResult, PageResponse } from '../types';

export interface TransferPayload {
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  currency?: string;
  description?: string;
}

export interface SimulatedFundingPayload {
  destinationAccountNumber: string;
  amount: number;
  description?: string;
}

export interface ReversalPayload {
  reason: string;
}

export const transferApi = {
  executeTransfer: async (
    payload: TransferPayload,
    idempotencyKey: string
  ): Promise<TransferReceipt> => {
    const { data } = await apiClient.post<TransferReceipt>('/api/v1/transfers', payload, {
      headers: {
        'Idempotency-Key': idempotencyKey,
      },
    });
    return data;
  },

  getReceipt: async (transactionReference: string): Promise<TransferReceipt> => {
    const { data } = await apiClient.get<TransferReceipt>(`/api/v1/transfers/${transactionReference}`);
    return data;
  },

  getTransactions: async (
    accountNumber: string,
    page = 0,
    size = 20,
    from?: string,
    to?: string
  ): Promise<PageResponse<TransactionSummary>> => {
    const { data } = await apiClient.get<PageResponse<TransactionSummary>>(
      `/api/v1/accounts/${accountNumber}/transactions`,
      {
        params: { page, size, from, to },
      }
    );
    return data;
  },

  reconcileAccount: async (accountNumber: string): Promise<ReconciliationResult> => {
    const { data } = await apiClient.get<ReconciliationResult>(
      `/api/v1/accounts/${accountNumber}/reconciliation`
    );
    return data;
  },

  // Admin APIs
  fundAccountSimulated: async (payload: SimulatedFundingPayload): Promise<TransferReceipt> => {
    const { data } = await apiClient.post<TransferReceipt>(
      '/api/v1/admin/simulation/fund-account',
      payload
    );
    return data;
  },

  reverseTransaction: async (
    transactionReference: string,
    payload: ReversalPayload
  ): Promise<TransferReceipt> => {
    const { data } = await apiClient.post<TransferReceipt>(
      `/api/v1/admin/transfers/${transactionReference}/reverse`,
      payload
    );
    return data;
  },

  getAllTransactions: async (page = 0, size = 20): Promise<PageResponse<TransactionSummary>> => {
    const { data } = await apiClient.get<PageResponse<TransactionSummary>>('/api/v1/admin/transactions', {
      params: { page, size },
    });
    return data;
  },
};

