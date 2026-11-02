import React, { ReactElement, ReactNode } from 'react';
import { render, RenderOptions } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider, useAuth } from '../context/AuthContext';
import { ToastProvider } from '../context/ToastContext';
import { UserProfile, AuthResponse } from '../types';

export const mockCustomerUser: UserProfile = {
  id: 'a1b2c3d4-1111-2222-3333-444455556666',
  email: 'customer@fincore.local',
  firstName: 'Aarav',
  lastName: 'Sharma',
  phoneNumber: '+919876543210',
  status: 'ACTIVE',
  roles: ['ROLE_CUSTOMER'],
  permissions: ['ACCOUNT_READ', 'TRANSFER_EXECUTE'],
  createdAt: '2026-11-01T12:00:00Z',
};

export const mockAdminUser: UserProfile = {
  id: 'admin-0001-2222-3333-444455556666',
  email: 'admin@fincore.local',
  firstName: 'Institutional',
  lastName: 'Administrator',
  phoneNumber: '+919876543299',
  status: 'ACTIVE',
  roles: ['ROLE_ADMIN'],
  permissions: ['ADMIN_READ', 'ADMIN_WRITE'],
  createdAt: '2026-11-01T12:00:00Z',
};

const AuthInitializer: React.FC<{ initialUser?: UserProfile; children: ReactNode }> = ({
  initialUser,
  children,
}) => {
  const { login } = useAuth();

  React.useEffect(() => {
    if (initialUser) {
      const mockAuthResponse: AuthResponse = {
        accessToken: 'mock-jwt-token-xyz',
        tokenType: 'Bearer',
        expiresIn: 900,
        user: initialUser,
      };
      login(mockAuthResponse);
    }
  }, [initialUser, login]);

  return <>{children}</>;
};

interface CustomRenderOptions extends Omit<RenderOptions, 'wrapper'> {
  initialEntries?: string[];
  initialUser?: UserProfile;
}

export function renderWithProviders(
  ui: ReactElement,
  {
    initialEntries = ['/'],
    initialUser,
    ...renderOptions
  }: CustomRenderOptions = {}
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        gcTime: 0,
      },
    },
  });

  const Wrapper: React.FC<{ children: ReactNode }> = ({ children }) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <AuthInitializer initialUser={initialUser}>
          <ToastProvider>
            <MemoryRouter initialEntries={initialEntries}>{children}</MemoryRouter>
          </ToastProvider>
        </AuthInitializer>
      </AuthProvider>
    </QueryClientProvider>
  );

  return {
    ...render(ui, { wrapper: Wrapper, ...renderOptions }),
    queryClient,
  };
}

