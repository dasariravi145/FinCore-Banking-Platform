import axios from 'axios';

const baseURL = import.meta.env.VITE_API_BASE_URL || '';

export const apiClient = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
  timeout: 15000,
});

// In-memory token store (Never written to localStorage/sessionStorage)
let inMemoryAccessToken: string | null = null;

export function setAuthToken(token: string | null): void {
  inMemoryAccessToken = token;
}

export function getAuthToken(): string | null {
  return inMemoryAccessToken;
}

// Request Interceptor: Inject in-memory Bearer token & MDC correlation ID
apiClient.interceptors.request.use(
  (config) => {
    if (inMemoryAccessToken) {
      config.headers.Authorization = `Bearer ${inMemoryAccessToken}`;
    }

    // Add unique trace correlation ID for each outgoing request
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      config.headers['X-Correlation-ID'] = crypto.randomUUID();
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Intercept 401 Unauthorized for graceful session expiration
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Clear token in memory
      inMemoryAccessToken = null;
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new CustomEvent('fincore:unauthorized'));
      }
    }
    return Promise.reject(error);
  }
);

