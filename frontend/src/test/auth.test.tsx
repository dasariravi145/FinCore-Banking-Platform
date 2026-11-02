import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockCustomerUser } from './test-utils';
import { LoginPage } from '../pages/auth/LoginPage';
import { RegisterPage } from '../pages/auth/RegisterPage';
import { authApi } from '../api/auth';
import { getAuthToken } from '../api/client';

vi.mock('../api/auth', () => ({
  authApi: {
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
  },
}));

describe('Authentication Flows', () => {
  it('Scenario 1: validates and submits registration form', async () => {
    const user = userEvent.setup();
    const mockRegisterResponse = {
      accessToken: 'new-reg-jwt-token',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockCustomerUser,
    };
    vi.mocked(authApi.register).mockResolvedValue(mockRegisterResponse);

    renderWithProviders(<RegisterPage />, { initialEntries: ['/register'] });

    expect(screen.getByRole('heading', { name: /open a customer account/i })).toBeInTheDocument();

    // Fill form
    await user.type(screen.getByLabelText(/first name/i), 'Aarav');
    await user.type(screen.getByLabelText(/last name/i), 'Sharma');
    await user.type(screen.getByLabelText(/email address/i), 'aarav.sharma@example.com');
    await user.type(screen.getByLabelText(/password/i), 'SecurePassword123!');

    // Submit
    await user.click(screen.getByRole('button', { name: /register & continue/i }));

    await waitFor(() => {
      expect(authApi.register).toHaveBeenCalledWith({
        firstName: 'Aarav',
        lastName: 'Sharma',
        email: 'aarav.sharma@example.com',
        phoneNumber: '+919876543210',
        password: 'SecurePassword123!',
      });
    });
  });

  it('Scenario 2a: handles login success and sets in-memory token', async () => {
    const user = userEvent.setup();
    const mockLoginResponse = {
      accessToken: 'auth-jwt-token-777',
      tokenType: 'Bearer',
      expiresIn: 900,
      user: mockCustomerUser,
    };
    vi.mocked(authApi.login).mockResolvedValue(mockLoginResponse);

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] });

    await user.type(screen.getByLabelText(/email address/i), 'customer@fincore.local');
    await user.type(screen.getByLabelText(/password/i), 'CustomerSecret2026!');
    await user.click(screen.getByRole('button', { name: /sign in to banking portal/i }));

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith({
        email: 'customer@fincore.local',
        password: 'CustomerSecret2026!',
      });
      // In-memory token store verification
      expect(getAuthToken()).toBe('auth-jwt-token-777');
    });
  });

  it('Scenario 2b: handles login failure and displays error toast message', async () => {
    const user = userEvent.setup();
    const axiosError = {
      isAxiosError: true,
      response: {
        status: 401,
        data: { message: 'Invalid credentials provided' },
      },
      message: 'Invalid credentials provided',
    };
    vi.mocked(authApi.login).mockRejectedValue(axiosError);

    renderWithProviders(<LoginPage />, { initialEntries: ['/login'] });

    await user.type(screen.getByLabelText(/email address/i), 'wrong@fincore.local');
    await user.type(screen.getByLabelText(/password/i), 'WrongPassword!');
    await user.click(screen.getByRole('button', { name: /sign in to banking portal/i }));

    await waitFor(() => {
      expect(screen.getByText(/authentication failed/i)).toBeInTheDocument();
      expect(screen.getByText(/invalid credentials provided/i)).toBeInTheDocument();
    });
  });
});

