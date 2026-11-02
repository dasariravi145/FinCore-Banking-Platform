import { apiClient } from './client';
import { SecurityAuditEvent, UserProfile, PageResponse } from '../types';

export const auditApi = {
  getAuditLogs: async (page = 0, size = 20): Promise<PageResponse<SecurityAuditEvent>> => {
    const { data } = await apiClient.get<PageResponse<SecurityAuditEvent>>('/api/v1/admin/audit-logs', {
      params: { page, size },
    });
    return data;
  },

  getAllUsers: async (page = 0, size = 20): Promise<PageResponse<UserProfile>> => {
    const { data } = await apiClient.get<PageResponse<UserProfile>>('/api/v1/admin/users', {
      params: { page, size },
    });
    return data;
  },
};

