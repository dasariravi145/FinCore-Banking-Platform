import { apiClient } from './client';
import { NotificationItem, UnreadCountResponse, PageResponse } from '../types';

export const notificationApi = {
  getNotifications: async (
    page = 0,
    size = 20,
    unreadOnly = false
  ): Promise<PageResponse<NotificationItem>> => {
    const { data } = await apiClient.get<PageResponse<NotificationItem>>('/api/v1/notifications', {
      params: { page, size, unreadOnly },
    });
    return data;
  },

  getUnreadCount: async (): Promise<UnreadCountResponse> => {
    const { data } = await apiClient.get<UnreadCountResponse>('/api/v1/notifications/unread-count');
    return data;
  },

  markAsRead: async (id: string): Promise<NotificationItem> => {
    const { data } = await apiClient.patch<NotificationItem>(`/api/v1/notifications/${id}/read`);
    return data;
  },
};

