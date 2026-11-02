import { apiClient } from './client';
import { AuthResponse } from '../types';

export interface RegisterPayload {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  phoneNumber: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export const authApi = {
  register: async (payload: RegisterPayload): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/api/v1/auth/register', payload);
    return data;
  },

  login: async (payload: LoginPayload): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/api/v1/auth/login', payload);
    return data;
  },

  logout: async (refreshToken?: string): Promise<void> => {
    try {
      await apiClient.post('/api/v1/auth/logout', { refreshToken });
    } catch {
      // Best effort logout
    }
  },
};

