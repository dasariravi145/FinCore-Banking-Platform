import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationApi } from '../../api/notifications';
import { useToast } from '../../context/ToastContext';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Skeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatDateTime } from '../../utils/formatters';
import { Bell, CheckCheck, Clock } from 'lucide-react';

export const NotificationsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: toastSuccess } = useToast();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const size = 15;

  const { data: notifPage, isLoading } = useQuery({
    queryKey: ['notifications', page, size, unreadOnly],
    queryFn: () => notificationApi.getNotifications(page, size, unreadOnly),
  });

  const markReadMutation = useMutation({
    mutationFn: (id: string) => notificationApi.markAsRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
      toastSuccess('Notification marked as read');
    },
  });

  const notifications = notifPage?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Notification Center</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Audit alerts, transfer notifications, and loan lifecycle updates
          </p>
        </div>

        {/* Filter Toggle */}
        <div className="flex items-center gap-2">
          <Button
            size="sm"
            variant={unreadOnly ? 'primary' : 'outline'}
            onClick={() => {
              setUnreadOnly(!unreadOnly);
              setPage(0);
            }}
          >
            {unreadOnly ? 'Showing Unread Only' : 'Show All Notifications'}
          </Button>
        </div>
      </div>

      <Card>
        {isLoading ? (
          <div className="space-y-3">
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : notifications.length === 0 ? (
          <EmptyState
            icon={<Bell className="w-8 h-8 text-slate-400" />}
            title="Inbox Zero"
            description={
              unreadOnly
                ? 'You have caught up with all notifications. No unread alerts.'
                : 'No notifications recorded yet.'
            }
          />
        ) : (
          <div className="divide-y divide-slate-100">
            {notifications.map((notif) => (
              <div
                key={notif.id}
                className={`py-4 px-2 flex items-start justify-between gap-4 transition ${
                  notif.isRead ? 'opacity-80' : 'bg-royal-50/30'
                }`}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`p-2 rounded-full mt-0.5 flex-shrink-0 ${
                      notif.isRead ? 'bg-slate-100 text-slate-500' : 'bg-royal-100 text-royal-700'
                    }`}
                  >
                    <Bell className="w-4 h-4" />
                  </div>
                  <div>
                    <div className="flex items-center gap-2">
                      <h4
                        className={`text-sm ${
                          notif.isRead ? 'font-medium text-slate-800' : 'font-bold text-slate-900'
                        }`}
                      >
                        {notif.title}
                      </h4>
                      {!notif.isRead && (
                        <span className="w-2 h-2 rounded-full bg-royal-600 flex-shrink-0" />
                      )}
                    </div>
                    <p className="text-xs text-slate-600 mt-1">{notif.message}</p>
                    <p className="text-[11px] text-slate-400 mt-1.5 flex items-center gap-1 font-mono">
                      <Clock className="w-3 h-3" />
                      {formatDateTime(notif.createdAt)}
                    </p>
                  </div>
                </div>

                {!notif.isRead && (
                  <Button
                    size="sm"
                    variant="ghost"
                    leftIcon={<CheckCheck className="w-3.5 h-3.5" />}
                    onClick={() => markReadMutation.mutate(notif.id)}
                    isLoading={markReadMutation.isPending}
                  >
                    Mark as Read
                  </Button>
                )}
              </div>
            ))}
          </div>
        )}

        {/* Pagination */}
        {notifPage && notifPage.totalPages > 1 && (
          <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
            <span>
              Page {notifPage.number + 1} of {notifPage.totalPages}
            </span>
            <div className="flex gap-2">
              <Button
                size="sm"
                variant="outline"
                disabled={notifPage.first}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
              >
                Previous
              </Button>
              <Button
                size="sm"
                variant="outline"
                disabled={notifPage.last}
                onClick={() => setPage((p) => p + 1)}
              >
                Next
              </Button>
            </div>
          </div>
        )}
      </Card>
    </div>
  );
};
