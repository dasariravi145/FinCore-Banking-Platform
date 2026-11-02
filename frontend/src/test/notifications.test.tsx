import { describe, it, expect, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders, mockCustomerUser } from './test-utils';
import { NotificationsPage } from '../pages/customer/NotificationsPage';
import { notificationApi } from '../api/notifications';

vi.mock('../api/notifications');

describe('Notification Center', () => {
  it('Scenario 16: renders notification inbox and allows marking alerts as read', async () => {
    const user = userEvent.setup();
    vi.mocked(notificationApi.getNotifications).mockResolvedValue({
      content: [
        {
          id: 'notif-1',
          userId: mockCustomerUser.id,
          type: 'TRANSFER_RECEIVED',
          title: 'Inward Credit Received',
          message: 'Received ₹15,000.00 from ACC-SYSTEM-RESERVE-001',
          isRead: false,
          createdAt: '2026-11-01T14:20:00Z',
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 15,
      number: 0,
      first: true,
      last: true,
      empty: false,
    });
    vi.mocked(notificationApi.markAsRead).mockResolvedValue({
      id: 'notif-1',
      userId: mockCustomerUser.id,
      type: 'TRANSFER_RECEIVED',
      title: 'Inward Credit Received',
      message: 'Received ₹15,000.00 from ACC-SYSTEM-RESERVE-001',
      isRead: true,
      createdAt: '2026-11-01T14:20:00Z',
    });

    renderWithProviders(<NotificationsPage />, {
      initialEntries: ['/notifications'],
      initialUser: mockCustomerUser,
    });

    await waitFor(() => {
      expect(screen.getByText('Inward Credit Received')).toBeInTheDocument();
      expect(screen.getByText(/Received ₹15,000.00/i)).toBeInTheDocument();
    });

    // Click Mark as Read
    await user.click(screen.getByRole('button', { name: /mark as read/i }));

    await waitFor(() => {
      expect(notificationApi.markAsRead).toHaveBeenCalledWith('notif-1');
      expect(screen.getByText(/notification marked as read/i)).toBeInTheDocument();
    });
  });
});

