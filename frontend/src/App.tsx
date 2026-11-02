import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { ToastProvider } from './context/ToastContext';
import { AppLayout } from './components/layout/AppLayout';
import { ProtectedRoute } from './components/layout/ProtectedRoute';

// Auth Pages
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';

// Customer Pages
import { CustomerDashboardPage } from './pages/customer/CustomerDashboardPage';
import { AccountsPage } from './pages/customer/AccountsPage';
import { AccountDetailPage } from './pages/customer/AccountDetailPage';
import { TransfersPage } from './pages/customer/TransfersPage';
import { LoansPage } from './pages/customer/LoansPage';
import { LoanDetailPage } from './pages/customer/LoanDetailPage';
import { NotificationsPage } from './pages/customer/NotificationsPage';
import { ProfilePage } from './pages/customer/ProfilePage';

// Admin Pages
import { AdminDashboardPage } from './pages/admin/AdminDashboardPage';
import { AdminCustomersPage } from './pages/admin/AdminCustomersPage';
import { AdminAccountsPage } from './pages/admin/AdminAccountsPage';
import { AdminTransfersPage } from './pages/admin/AdminTransfersPage';
import { AdminLoansPage } from './pages/admin/AdminLoansPage';
import { AdminFraudPage } from './pages/admin/AdminFraudPage';
import { AdminAuditLogsPage } from './pages/admin/AdminAuditLogsPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 30, // 30 seconds
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
});

export const App: React.FC = () => {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <ToastProvider>
          <BrowserRouter>
            <Routes>
              {/* Public Authentication Routes */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />

              {/* Protected Application Routes */}
              <Route
                element={
                  <ProtectedRoute>
                    <AppLayout />
                  </ProtectedRoute>
                }
              >
                {/* Customer Routes */}
                <Route path="/dashboard" element={<CustomerDashboardPage />} />
                <Route path="/accounts" element={<AccountsPage />} />
                <Route path="/accounts/:accountNumber" element={<AccountDetailPage />} />
                <Route path="/transfers" element={<TransfersPage />} />
                <Route path="/loans" element={<LoansPage />} />
                <Route path="/loans/:loanNumber" element={<LoanDetailPage />} />
                <Route path="/notifications" element={<NotificationsPage />} />
                <Route path="/profile" element={<ProfilePage />} />

                {/* Role-Restricted Admin Routes */}
                <Route
                  path="/admin"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminDashboardPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/customers"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminCustomersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/accounts"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminAccountsPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/transfers"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminTransfersPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/loans"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminLoansPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/fraud"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminFraudPage />
                    </ProtectedRoute>
                  }
                />
                <Route
                  path="/admin/audit-logs"
                  element={
                    <ProtectedRoute allowedRoles={['ROLE_ADMIN']}>
                      <AdminAuditLogsPage />
                    </ProtectedRoute>
                  }
                />
              </Route>

              {/* Default Fallback Redirect */}
              <Route path="/" element={<Navigate to="/dashboard" replace />} />
              <Route path="*" element={<Navigate to="/dashboard" replace />} />
            </Routes>
          </BrowserRouter>
        </ToastProvider>
      </AuthProvider>
    </QueryClientProvider>
  );
};

export default App;
