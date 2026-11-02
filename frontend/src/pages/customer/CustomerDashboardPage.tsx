import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { customerApi } from '../../api/customers';
import { accountApi } from '../../api/accounts';
import { loanApi } from '../../api/loans';
import { notificationApi } from '../../api/notifications';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatAccountMasked } from '../../utils/formatters';
import {
  Wallet,
  ArrowLeftRight,
  Landmark,
  Bell,
  ArrowUpRight,
  PlusCircle,
  Calculator,
  ShieldCheck,
  AlertCircle,
  FileText,
} from 'lucide-react';

export const CustomerDashboardPage: React.FC = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  // 1. Fetch Customer Profile
  const { data: profile, isLoading: isProfileLoading } = useQuery({
    queryKey: ['customers', 'me'],
    queryFn: () => customerApi.getMyProfile(),
    retry: 1,
  });

  // 2. Fetch Accounts
  const { data: accounts = [], isLoading: isAccountsLoading } = useQuery({
    queryKey: ['accounts', 'my-accounts'],
    queryFn: () => accountApi.getMyAccounts(),
  });

  // 3. Fetch Loans
  const { data: loansData, isLoading: isLoansLoading } = useQuery({
    queryKey: ['loans', 'my-loans', 0, 5],
    queryFn: () => loanApi.getMyLoans(0, 5),
  });

  // 4. Fetch Notifications
  const { data: notifsData } = useQuery({
    queryKey: ['notifications', 0, 5],
    queryFn: () => notificationApi.getNotifications(0, 5, false),
  });

  const totalBalance = accounts.reduce((acc, a) => acc + (a.balance || 0), 0);
  const activeLoans = loansData?.content?.filter((l) => l.status === 'ACTIVE') || [];
  const totalLoanOutstanding = activeLoans.reduce(
    (acc, l) => acc + (l.outstandingPrincipal || 0),
    0
  );

  return (
    <div className="space-y-6">
      {/* Welcome Banner */}
      <div className="bg-gradient-to-r from-navy-900 via-navy-850 to-royal-900 rounded-2xl p-6 sm:p-8 text-white shadow-xl relative overflow-hidden">
        <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-semibold px-2.5 py-0.5 rounded-full bg-gold-500/20 text-gold-300 border border-gold-500/30">
                PORTFOLIO SIMULATION
              </span>
              <span className="text-xs text-slate-300">
                Customer ID: {profile?.customerNumber || 'Pending Onboarding'}
              </span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight mt-2">
              Good day, {user?.firstName} {user?.lastName}
            </h1>
            <p className="text-xs sm:text-sm text-slate-300 mt-1 max-w-xl">
              Welcome to your FinCore personal banking overview. Monitor cleared balances, execute double-entry transfers, and manage synthetic loans in real-time.
            </p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="secondary"
              leftIcon={<ArrowLeftRight className="w-4 h-4" />}
              onClick={() => navigate('/transfers')}
            >
              Transfer Funds
            </Button>
            <Button
              variant="outline"
              className="bg-white/10 text-white border-white/20 hover:bg-white/20"
              leftIcon={<PlusCircle className="w-4 h-4" />}
              onClick={() => navigate('/accounts')}
            >
              Open Account
            </Button>
          </div>
        </div>
      </div>

      {/* KYC Alert if Customer Profile not yet completed */}
      {!isProfileLoading && !profile && (
        <div className="bg-amber-50 border border-amber-200 rounded-xl p-4 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <AlertCircle className="w-5 h-5 text-amber-600 flex-shrink-0 mt-0.5" />
            <div>
              <h4 className="text-sm font-semibold text-amber-900">
                Complete Your Customer Banking Profile
              </h4>
              <p className="text-xs text-amber-700 mt-0.5">
                To link personal identification and enable full double-entry services, please submit your synthetic KYC attributes.
              </p>
            </div>
          </div>
          <Button
            size="sm"
            variant="secondary"
            onClick={() => navigate('/profile')}
          >
            Complete Profile
          </Button>
        </div>
      )}

      {/* Metric Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="border-l-4 border-l-royal-600">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Total Cleared Balance
            </p>
            <Wallet className="w-4 h-4 text-royal-600" />
          </div>
          <div className="mt-2">
            {isAccountsLoading ? (
              <Skeleton className="h-8 w-32" />
            ) : (
              <h3 className="text-2xl font-bold text-slate-900">
                {formatCurrency(totalBalance)}
              </h3>
            )}
            <p className="text-[11px] text-slate-500 mt-1">
              Across {accounts.length} linked account{accounts.length === 1 ? '' : 's'}
            </p>
          </div>
        </Card>

        <Card className="border-l-4 border-l-gold-500">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Outstanding Loans
            </p>
            <Landmark className="w-4 h-4 text-gold-600" />
          </div>
          <div className="mt-2">
            {isLoansLoading ? (
              <Skeleton className="h-8 w-32" />
            ) : (
              <h3 className="text-2xl font-bold text-slate-900">
                {formatCurrency(totalLoanOutstanding)}
              </h3>
            )}
            <p className="text-[11px] text-slate-500 mt-1">
              {activeLoans.length} active loan facility
            </p>
          </div>
        </Card>

        <Card className="border-l-4 border-l-emerald-600">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Account Status
            </p>
            <ShieldCheck className="w-4 h-4 text-emerald-600" />
          </div>
          <div className="mt-2">
            <div className="flex items-center gap-2">
              <StatusBadge status="ACTIVE" size="sm" />
              <span className="text-xs font-medium text-slate-700">All Systems Normal</span>
            </div>
            <p className="text-[11px] text-slate-500 mt-2">
              Double-entry ledger verified
            </p>
          </div>
        </Card>

        <Card className="border-l-4 border-l-navy-800">
          <div className="flex items-center justify-between">
            <p className="text-xs font-semibold text-slate-500 uppercase tracking-wide">
              Recent Alerts
            </p>
            <Bell className="w-4 h-4 text-navy-800" />
          </div>
          <div className="mt-2">
            <h3 className="text-2xl font-bold text-slate-900">
              {notifsData?.totalElements || 0}
            </h3>
            <p className="text-[11px] text-slate-500 mt-1">
              <Link to="/notifications" className="text-royal-600 font-medium hover:underline">
                View notification inbox
              </Link>
            </p>
          </div>
        </Card>
      </div>

      {/* Main Content Sections: Accounts & Recent Activity */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: My Accounts */}
        <div className="lg:col-span-2 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-base font-bold text-slate-900">Bank Accounts</h3>
              <p className="text-xs text-slate-500">Your deposit accounts and real-time balances</p>
            </div>
            <Button
              size="sm"
              variant="outline"
              leftIcon={<PlusCircle className="w-3.5 h-3.5" />}
              onClick={() => navigate('/accounts')}
            >
              Manage Accounts
            </Button>
          </div>

          {isAccountsLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-24 w-full" />
              <Skeleton className="h-24 w-full" />
            </div>
          ) : accounts.length === 0 ? (
            <EmptyState
              icon={<Wallet className="w-8 h-8 text-slate-400" />}
              title="No Bank Accounts Found"
              description="Open your first synthetic savings or current account to start transferring and receiving simulated funds."
              action={
                <Button size="sm" variant="secondary" onClick={() => navigate('/accounts')}>
                  Open New Account
                </Button>
              }
            />
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {accounts.map((acc) => (
                <div
                  key={acc.id}
                  onClick={() => navigate(`/accounts/${acc.accountNumber}`)}
                  className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:shadow-md hover:border-royal-300 transition cursor-pointer group"
                >
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                      {acc.accountType}
                    </span>
                    <StatusBadge status={acc.status} size="sm" />
                  </div>
                  <div className="mt-4">
                    <p className="text-xs font-mono text-slate-500">
                      {formatAccountMasked(acc.accountNumber)}
                    </p>
                    <p className="text-xl font-bold text-slate-900 mt-1">
                      {formatCurrency(acc.balance, acc.currency)}
                    </p>
                  </div>
                  <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-royal-600 font-medium group-hover:text-royal-700">
                    <span>View statement & reconciliation</span>
                    <ArrowUpRight className="w-3.5 h-3.5 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right Col: Quick Actions & Loan Status */}
        <div className="space-y-6">
          <Card title="Quick Actions">
            <div className="space-y-2">
              <button
                onClick={() => navigate('/transfers')}
                className="w-full text-left p-3 rounded-lg border border-slate-100 hover:border-royal-200 hover:bg-slate-50 transition flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-royal-50 text-royal-600">
                    <ArrowLeftRight className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-slate-900">Transfer Funds</p>
                    <p className="text-[11px] text-slate-500">Idempotent double-entry transfer</p>
                  </div>
                </div>
                <ArrowUpRight className="w-4 h-4 text-slate-400" />
              </button>

              <button
                onClick={() => navigate('/loans')}
                className="w-full text-left p-3 rounded-lg border border-slate-100 hover:border-royal-200 hover:bg-slate-50 transition flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-gold-50 text-gold-600">
                    <Calculator className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-slate-900">Calculate EMI Quote</p>
                    <p className="text-[11px] text-slate-500">Reducing-balance amortization</p>
                  </div>
                </div>
                <ArrowUpRight className="w-4 h-4 text-slate-400" />
              </button>

              <button
                onClick={() => navigate('/profile')}
                className="w-full text-left p-3 rounded-lg border border-slate-100 hover:border-royal-200 hover:bg-slate-50 transition flex items-center justify-between"
              >
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-slate-100 text-slate-700">
                    <FileText className="w-4 h-4" />
                  </div>
                  <div>
                    <p className="text-xs font-semibold text-slate-900">KYC & Customer Profile</p>
                    <p className="text-[11px] text-slate-500">Identity details & address</p>
                  </div>
                </div>
                <ArrowUpRight className="w-4 h-4 text-slate-400" />
              </button>
            </div>
          </Card>

          {/* Active Loans Preview */}
          <Card title="Loans Summary" action={<Link to="/loans" className="text-xs text-royal-600 font-semibold hover:underline">View all</Link>}>
            {isLoansLoading ? (
              <Skeleton className="h-20 w-full" />
            ) : loansData?.content && loansData.content.length > 0 ? (
              <div className="space-y-3">
                {loansData.content.slice(0, 2).map((l) => (
                  <div key={l.id} className="p-3 bg-slate-50 rounded-lg text-xs space-y-1">
                    <div className="flex justify-between items-center">
                      <span className="font-semibold text-slate-800">{l.loanNumber}</span>
                      <StatusBadge status={l.status} size="sm" />
                    </div>
                    <div className="flex justify-between text-slate-500">
                      <span>Principal:</span>
                      <span>{formatCurrency(l.principalAmount)}</span>
                    </div>
                    <div className="flex justify-between text-slate-500">
                      <span>Monthly EMI:</span>
                      <span className="font-semibold text-slate-900">{formatCurrency(l.monthlyEmi)}</span>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="text-xs text-slate-500 text-center py-4">No active or applied loans.</p>
            )}
          </Card>
        </div>
      </div>
    </div>
  );
};
