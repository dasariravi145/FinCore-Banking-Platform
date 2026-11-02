import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';
import { Routes, Route } from 'react-router-dom';
import { renderWithProviders, mockCustomerUser, mockAdminUser } from './test-utils';
import { ProtectedRoute } from '../components/layout/ProtectedRoute';
import { Sidebar } from '../components/layout/Sidebar';

describe('Navigation & Authorization Guards', () => {
  it('Scenario 3: redirects unauthenticated users away from protected route', () => {
    renderWithProviders(
      <Routes>
        <Route path="/login" element={<div>Login Page Screen</div>} />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <div>Protected Dashboard Content</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/dashboard'], initialUser: undefined }
    );

    // Unauthenticated user redirected to /login
    expect(screen.getByText('Login Page Screen')).toBeInTheDocument();
    expect(screen.queryByText('Protected Dashboard Content')).not.toBeInTheDocument();
  });

  it('Scenario 4a: customer sees customer tabs and does NOT see Admin Console', () => {
    renderWithProviders(<Sidebar />, { initialUser: mockCustomerUser });

    expect(screen.getByText('Banking Portal')).toBeInTheDocument();
    expect(screen.getByText('Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Accounts')).toBeInTheDocument();
    expect(screen.getByText('Transfers')).toBeInTheDocument();
    expect(screen.getByText('Loans & EMI')).toBeInTheDocument();

    // Admin Console must not be visible
    expect(screen.queryByText('Admin Console')).not.toBeInTheDocument();
    expect(screen.queryByText('Fraud Compliance')).not.toBeInTheDocument();
  });

  it('Scenario 4b: administrator sees Admin Console navigation tabs', () => {
    renderWithProviders(<Sidebar />, { initialUser: mockAdminUser });

    expect(screen.getByText('Admin Console')).toBeInTheDocument();
    expect(screen.getByText('Admin Hub')).toBeInTheDocument();
    expect(screen.getByText('Customers')).toBeInTheDocument();
    expect(screen.getByText('Accounts & Controls')).toBeInTheDocument();
    expect(screen.getByText('Fraud Compliance')).toBeInTheDocument();
    expect(screen.getByText('Security Audit Logs')).toBeInTheDocument();
  });

  it('Scenario 17: customer attempting to access admin route is blocked', () => {
    renderWithProviders(
      <Routes>
        <Route path="/dashboard" element={<div>Customer Dashboard Fallback</div>} />
        <Route
          path="/admin"
          element={
            <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
              <div>Restricted Admin Panel</div>
            </ProtectedRoute>
          }
        />
      </Routes>,
      { initialEntries: ['/admin'], initialUser: mockCustomerUser }
    );

    // Restricted panel not rendered, redirected to /dashboard
    expect(screen.queryByText('Restricted Admin Panel')).not.toBeInTheDocument();
    expect(screen.getByText('Customer Dashboard Fallback')).toBeInTheDocument();
  });
});

