import React from 'react';
import { useAuth } from '../../context/AuthContext';
import { NavLink } from 'react-router-dom';
import { Bell, Shield, User, ArrowUpRight } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { notificationApi } from '../../api/notifications';

export const Topbar: React.FC = () => {
  const { user, isAdmin, isCustomer } = useAuth();

  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationApi.getUnreadCount(),
    enabled: isCustomer,
    refetchInterval: 30000,
  });

  const unreadCount = unreadData?.unreadCount || 0;

  return (
    <header className="h-16 bg-white border-b border-slate-200 px-6 flex items-center justify-between z-10">
      <div className="flex items-center gap-3">
        <h2 className="text-base font-bold text-slate-800">FinCore Platform</h2>
        <span className="hidden sm:inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium bg-slate-100 text-slate-700">
          v1.0.0-PROD-LOCAL
        </span>
      </div>

      <div className="flex items-center gap-4">
        {/* Quick Links */}
        {isCustomer && (
          <NavLink
            to="/transfers"
            className="hidden sm:inline-flex items-center gap-1.5 text-xs font-semibold px-3 py-1.5 rounded-lg bg-navy-900 text-white hover:bg-navy-800 transition"
          >
            <span>Transfer Funds</span>
            <ArrowUpRight className="w-3.5 h-3.5" />
          </NavLink>
        )}

        {/* Notification Bell */}
        {isCustomer && (
          <NavLink
            to="/notifications"
            className="relative p-2 text-slate-500 hover:text-slate-800 hover:bg-slate-100 rounded-lg transition"
            aria-label="View notifications"
          >
            <Bell className="w-5 h-5" />
            {unreadCount > 0 && (
              <span className="absolute top-1.5 right-1.5 w-2 h-2 bg-rose-600 rounded-full" />
            )}
          </NavLink>
        )}

        {/* User Role Badge */}
        <div className="flex items-center gap-2 pl-2 border-l border-slate-200">
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
            {isAdmin ? (
              <>
                <Shield className="w-3.5 h-3.5 text-amber-600" />
                <span className="text-amber-800 font-semibold">ADMIN</span>
              </>
            ) : (
              <>
                <User className="w-3.5 h-3.5 text-royal-600" />
                <span className="text-slate-700">CUSTOMER</span>
              </>
            )}
          </div>
          <span className="text-xs font-semibold text-slate-800 hidden md:inline">
            {user?.firstName} {user?.lastName}
          </span>
        </div>
      </div>
    </header>
  );
};

