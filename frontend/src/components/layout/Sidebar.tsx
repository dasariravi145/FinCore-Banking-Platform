import React from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { useQuery } from '@tanstack/react-query';
import { notificationApi } from '../../api/notifications';
import {
  LayoutDashboard,
  Wallet,
  ArrowLeftRight,
  Landmark,
  Bell,
  User,
  ShieldCheck,
  Users,
  CreditCard,
  FileText,
  AlertOctagon,
  LogOut,
  Building2,
} from 'lucide-react';

export const Sidebar: React.FC = () => {
  const { user, isAdmin, isCustomer, logout } = useAuth();
  const navigate = useNavigate();

  // Fetch unread count if authenticated customer
  const { data: unreadData } = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => notificationApi.getUnreadCount(),
    enabled: isCustomer,
    refetchInterval: 30000,
  });

  const unreadCount = unreadData?.unreadCount || 0;

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  const navItemClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition-all ${
      isActive
        ? 'bg-royal-600 text-white shadow-sm'
        : 'text-slate-300 hover:text-white hover:bg-navy-800'
    }`;

  return (
    <aside className="w-64 bg-navy-900 border-r border-navy-850 flex flex-col h-screen text-slate-100 flex-shrink-0">
      {/* Brand Header */}
      <div className="h-16 px-6 flex items-center gap-3 border-b border-navy-800">
        <div className="w-9 h-9 rounded-lg bg-gradient-to-tr from-gold-500 to-amber-300 flex items-center justify-center text-navy-950 font-bold shadow-md">
          <Building2 className="w-5 h-5 text-navy-950" />
        </div>
        <div>
          <span className="font-bold text-base tracking-wide text-white">FinCore</span>
          <span className="text-[10px] block font-semibold text-gold-400 uppercase tracking-widest">
            Banking Core
          </span>
        </div>
      </div>

      {/* Navigation Sections */}
      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-6">
        {/* Customer Nav */}
        <div>
          <p className="px-3 text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-2">
            Banking Portal
          </p>
          <nav className="space-y-1">
            <NavLink to="/dashboard" className={navItemClass}>
              <LayoutDashboard className="w-4 h-4" />
              <span>Dashboard</span>
            </NavLink>
            <NavLink to="/accounts" className={navItemClass}>
              <Wallet className="w-4 h-4" />
              <span>Accounts</span>
            </NavLink>
            <NavLink to="/transfers" className={navItemClass}>
              <ArrowLeftRight className="w-4 h-4" />
              <span>Transfers</span>
            </NavLink>
            <NavLink to="/loans" className={navItemClass}>
              <Landmark className="w-4 h-4" />
              <span>Loans & EMI</span>
            </NavLink>
            <NavLink to="/notifications" className={navItemClass}>
              <div className="relative">
                <Bell className="w-4 h-4" />
                {unreadCount > 0 && (
                  <span className="absolute -top-1 -right-1 w-2 h-2 bg-gold-500 rounded-full animate-pulse" />
                )}
              </div>
              <span className="flex-1">Notifications</span>
              {unreadCount > 0 && (
                <span className="px-1.5 py-0.5 text-[10px] font-bold rounded-full bg-gold-500 text-navy-950">
                  {unreadCount}
                </span>
              )}
            </NavLink>
            <NavLink to="/profile" className={navItemClass}>
              <User className="w-4 h-4" />
              <span>My Profile</span>
            </NavLink>
          </nav>
        </div>

        {/* Admin Nav (Strictly conditional based on ROLE_ADMIN) */}
        {isAdmin && (
          <div className="pt-2 border-t border-navy-800">
            <div className="px-3 flex items-center justify-between mb-2">
              <p className="text-[11px] font-semibold text-gold-400 uppercase tracking-wider">
                Admin Console
              </p>
              <ShieldCheck className="w-3.5 h-3.5 text-gold-400" />
            </div>
            <nav className="space-y-1">
              <NavLink to="/admin" end className={navItemClass}>
                <LayoutDashboard className="w-4 h-4 text-gold-400" />
                <span>Admin Hub</span>
              </NavLink>
              <NavLink to="/admin/customers" className={navItemClass}>
                <Users className="w-4 h-4" />
                <span>Customers</span>
              </NavLink>
              <NavLink to="/admin/accounts" className={navItemClass}>
                <CreditCard className="w-4 h-4" />
                <span>Accounts & Controls</span>
              </NavLink>
              <NavLink to="/admin/transfers" className={navItemClass}>
                <ArrowLeftRight className="w-4 h-4" />
                <span>Operations & Reversals</span>
              </NavLink>
              <NavLink to="/admin/loans" className={navItemClass}>
                <Landmark className="w-4 h-4" />
                <span>Loan Approvals</span>
              </NavLink>
              <NavLink to="/admin/fraud" className={navItemClass}>
                <AlertOctagon className="w-4 h-4 text-rose-400" />
                <span>Fraud Compliance</span>
              </NavLink>
              <NavLink to="/admin/audit-logs" className={navItemClass}>
                <FileText className="w-4 h-4" />
                <span>Security Audit Logs</span>
              </NavLink>
            </nav>
          </div>
        )}
      </div>

      {/* User Details & Sign Out */}
      <div className="p-4 border-t border-navy-800 bg-navy-950/60">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-full bg-navy-800 border border-slate-700 flex items-center justify-center font-semibold text-gold-400 text-sm">
            {user?.firstName?.[0] || 'U'}
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-xs font-semibold text-white truncate">
              {user?.firstName} {user?.lastName}
            </p>
            <p className="text-[11px] text-slate-400 truncate">{user?.email}</p>
          </div>
          <button
            onClick={handleLogout}
            title="Sign out"
            className="p-1.5 rounded-lg text-slate-400 hover:text-rose-400 hover:bg-navy-800 transition"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </div>
    </aside>
  );
};

