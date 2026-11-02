import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { UserProfile, AuthResponse } from '../types';
import { setAuthToken } from '../api/client';
import { authApi } from '../api/auth';

interface AuthContextType {
  user: UserProfile | null;
  accessToken: string | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  isCustomer: boolean;
  isLoading: boolean;
  login: (authResponse: AuthResponse) => void;
  logout: () => Promise<void>;
  updateUser: (user: UserProfile) => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  // In-memory access token & profile state (Never persisted to localStorage/sessionStorage)
  const [accessToken, setAccessTokenState] = useState<string | null>(null);
  const [user, setUser] = useState<UserProfile | null>(null);
  const [isLoading] = useState<boolean>(false);
  const [refreshToken, setRefreshToken] = useState<string | null>(null);

  const login = (authResponse: AuthResponse) => {
    setAccessTokenState(authResponse.accessToken);
    setAuthToken(authResponse.accessToken);
    setUser(authResponse.user);
    if (authResponse.refreshToken) {
      setRefreshToken(authResponse.refreshToken);
    }
  };

  const logout = async () => {
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken);
      }
    } finally {
      setAccessTokenState(null);
      setAuthToken(null);
      setUser(null);
      setRefreshToken(null);
    }
  };

  const updateUser = (updatedUser: UserProfile) => {
    setUser(updatedUser);
  };

  // Listen to 401 Unauthorized events from API client interceptor
  useEffect(() => {
    const handleUnauthorized = () => {
      setAccessTokenState(null);
      setAuthToken(null);
      setUser(null);
      setRefreshToken(null);
    };

    window.addEventListener('fincore:unauthorized', handleUnauthorized);
    return () => {
      window.removeEventListener('fincore:unauthorized', handleUnauthorized);
    };
  }, []);

  const roles = user?.roles || [];
  const isAdmin = roles.includes('ROLE_ADMIN');
  const isCustomer = roles.includes('ROLE_CUSTOMER');
  const isAuthenticated = !!accessToken && !!user;

  return (
    <AuthContext.Provider
      value={{
        user,
        accessToken,
        isAuthenticated,
        isAdmin,
        isCustomer,
        isLoading,
        login,
        logout,
        updateUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
