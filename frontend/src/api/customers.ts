import { apiClient } from './client';
import { CustomerProfile, PageResponse } from '../types';

export interface CreateCustomerPayload {
  firstName: string;
  lastName: string;
  phoneNumber: string;
  dateOfBirth: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  nationalId: string;
}

export interface UpdateCustomerPayload {
  phoneNumber: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
}

export const customerApi = {
  getMyProfile: async (): Promise<CustomerProfile> => {
    const { data } = await apiClient.get<CustomerProfile>('/api/v1/customers/me');
    return data;
  },

  createProfile: async (payload: CreateCustomerPayload): Promise<CustomerProfile> => {
    const { data } = await apiClient.post<CustomerProfile>('/api/v1/customers', payload);
    return data;
  },

  updateMyProfile: async (payload: UpdateCustomerPayload): Promise<CustomerProfile> => {
    const { data } = await apiClient.put<CustomerProfile>('/api/v1/customers/me', payload);
    return data;
  },

  // Admin APIs
  getAllCustomers: async (page = 0, size = 20): Promise<PageResponse<CustomerProfile>> => {
    const { data } = await apiClient.get<PageResponse<CustomerProfile>>('/api/v1/admin/customers', {
      params: { page, size },
    });
    return data;
  },

  getCustomerById: async (id: string): Promise<CustomerProfile> => {
    const { data } = await apiClient.get<CustomerProfile>(`/api/v1/admin/customers/${id}`);
    return data;
  },
};

