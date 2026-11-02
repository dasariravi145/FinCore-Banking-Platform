import React from 'react';

export interface StatusBadgeProps {
  status: string;
  size?: 'sm' | 'md';
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({ status, size = 'md' }) => {
  const normalized = status.toUpperCase();

  const colorMap: Record<string, string> = {
    // Account / General status
    ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    FROZEN: 'bg-amber-50 text-amber-700 border-amber-200',
    CLOSED: 'bg-slate-100 text-slate-600 border-slate-200',
    LOCKED: 'bg-rose-50 text-rose-700 border-rose-200',
    SUSPENDED: 'bg-amber-50 text-amber-700 border-amber-200',

    // Loan status
    APPLIED: 'bg-sky-50 text-sky-700 border-sky-200',
    APPROVED: 'bg-royal-50 text-royal-700 border-royal-200',
    REJECTED: 'bg-rose-50 text-rose-700 border-rose-200',
    DISBURSED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
    SCHEDULED: 'bg-slate-100 text-slate-700 border-slate-200',
    PAID: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    OVERDUE: 'bg-rose-50 text-rose-700 border-rose-200',

    // Fraud decision & risk
    ALLOW: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    REVIEW: 'bg-amber-50 text-amber-700 border-amber-200',
    BLOCK: 'bg-rose-50 text-rose-700 border-rose-200',
    LOW: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    MEDIUM: 'bg-amber-50 text-amber-700 border-amber-200',
    HIGH: 'bg-rose-50 text-rose-700 border-rose-200',
    CRITICAL: 'bg-purple-50 text-purple-700 border-purple-200',

    // Transactions
    COMPLETED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
    FAILED: 'bg-rose-50 text-rose-700 border-rose-200',
    REVERSED: 'bg-purple-50 text-purple-700 border-purple-200',
    DEBIT: 'bg-rose-50 text-rose-700 border-rose-200',
    CREDIT: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  };

  const badgeClass = colorMap[normalized] || 'bg-slate-100 text-slate-700 border-slate-200';
  const sizeClass = size === 'sm' ? 'px-2 py-0.5 text-[11px]' : 'px-2.5 py-1 text-xs';

  return (
    <span
      className={`inline-flex items-center font-medium border rounded-full uppercase tracking-wider ${sizeClass} ${badgeClass}`}
    >
      <span className="w-1.5 h-1.5 rounded-full mr-1.5 bg-current opacity-70" />
      {status}
    </span>
  );
};

