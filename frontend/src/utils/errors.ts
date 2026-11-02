import axios from 'axios';
import { ApiErrorResponse } from '../types';

export function getErrorMessage(error: unknown, fallback = 'An unexpected error occurred. Please try again.'): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as ApiErrorResponse | undefined;
    if (data?.message) {
      if (data.validationErrors && Object.keys(data.validationErrors).length > 0) {
        const details = Object.entries(data.validationErrors)
          .map(([field, msg]) => `${field}: ${msg}`)
          .join(', ');
        return `${data.message} (${details})`;
      }
      return data.message;
    }
    if (error.response?.status === 403) {
      return 'You do not have permission to perform this action.';
    }
    if (error.response?.status === 401) {
      return 'Authentication required. Please sign in again.';
    }
    if (error.response?.status === 404) {
      return 'The requested resource was not found.';
    }
    if (error.response?.status === 409) {
      return data?.message || 'A conflict occurred with an existing resource.';
    }
    if (error.response?.status === 422) {
      return data?.message || 'Transaction could not be processed due to a business rule violation.';
    }
    if (error.message) {
      return error.message;
    }
  }

  if (error instanceof Error) {
    return error.message;
  }

  return fallback;
}

