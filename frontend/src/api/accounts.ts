import { apiClient } from './client';
import { BankAccount, AccountBalanceResponse, AccountType, PageResponse } from '../types';

export interface CreateAccountPayload {
  accountType: AccountType;
}

export interface UpdateAccountStatusPayload {
  status: 'ACTIVE' | 'FROZEN' | 'CLOSED';
  reason: string;
}

export const accountApi = {
  getMyAccounts: async (): Promise<BankAccount[]> => {
    const { data } = await apiClient.get<BankAccount[]>('/api/v1/accounts/my-accounts');
    return data;
  },

  openAccount: async (payload: CreateAccountPayload): Promise<BankAccount> => {
    const { data } = await apiClient.post<BankAccount>('/api/v1/accounts', payload);
    return data;
  },

  getBalance: async (accountNumber: string): Promise<AccountBalanceResponse> => {
    const { data } = await apiClient.get<AccountBalanceResponse>(`/api/v1/accounts/${accountNumber}/balance`);
    return data;
  },

  getAccountDetails: async (accountNumber: string): Promise<BankAccount> => {
    const { data } = await apiClient.get<BankAccount>(`/api/v1/accounts/${accountNumber}`);
    return data;
  },

  // Admin APIs
  getAllAccounts: async (page = 0, size = 20): Promise<PageResponse<BankAccount>> => {
    const { data } = await apiClient.get<PageResponse<BankAccount>>('/api/v1/admin/accounts', {
      params: { page, size },
    });
    return data;
  },

  updateAccountStatus: async (
    accountNumber: string,
    payload: UpdateAccountStatusPayload
  ): Promise<BankAccount> => {
    const { data } = await apiClient.patch<BankAccount>(
      `/api/v1/admin/accounts/${accountNumber}/status`,
      payload
    );
    return data;
  },
};

