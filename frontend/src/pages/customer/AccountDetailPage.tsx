import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { accountApi } from '../../api/accounts';
import { transferApi } from '../../api/transfers';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton, TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDateTime, formatDate, formatAccountMasked } from '../../utils/formatters';
import {
  ArrowLeft,
  RefreshCw,
  CheckCircle2,
  AlertTriangle,
  ArrowUpRight,
  ArrowDownLeft,
  ShieldCheck,
  Receipt,
} from 'lucide-react';

export const AccountDetailPage: React.FC = () => {
  const { accountNumber } = useParams<{ accountNumber: string }>();
  const navigate = useNavigate();
  const [page, setPage] = useState(0);
  const size = 15;

  // 1. Account Details
  const { data: account, isLoading: isAccountLoading } = useQuery({
    queryKey: ['accounts', accountNumber],
    queryFn: () => accountApi.getAccountDetails(accountNumber!),
    enabled: Boolean(accountNumber),
  });

  // 2. Reconciliation Query
  const {
    data: recon,
    isLoading: isReconLoading,
    refetch: refetchRecon,
    isFetching: isReconFetching,
  } = useQuery({
    queryKey: ['reconciliation', accountNumber],
    queryFn: () => transferApi.reconcileAccount(accountNumber!),
    enabled: Boolean(accountNumber),
  });

  // 3. Transactions History
  const { data: txPage, isLoading: isTxLoading } = useQuery({
    queryKey: ['transactions', accountNumber, page, size],
    queryFn: () => transferApi.getTransactions(accountNumber!, page, size),
    enabled: Boolean(accountNumber),
  });

  if (!accountNumber) {
    return <div className="p-8 text-center text-slate-500">Account number missing.</div>;
  }

  const transactions = txPage?.content || [];

  return (
    <div className="space-y-6">
      {/* Header with Back button */}
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant="outline"
          leftIcon={<ArrowLeft className="w-4 h-4" />}
          onClick={() => navigate('/accounts')}
        >
          Back to Accounts
        </Button>
      </div>

      {/* Account Overview Card */}
      {isAccountLoading ? (
        <Skeleton className="h-44 w-full" />
      ) : account ? (
        <div className="bg-gradient-to-r from-navy-950 via-navy-900 to-navy-850 text-white rounded-2xl p-6 sm:p-8 shadow-xl relative overflow-hidden">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-royal-600/30 text-royal-200 border border-royal-500/30">
                  {account.accountType}
                </span>
                <span className="text-xs text-slate-400">
                  Category: {account.accountCategory}
                </span>
                <StatusBadge status={account.status} size="sm" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-mono font-bold tracking-tight mt-2">
                {account.accountNumber}
              </h1>
              <p className="text-xs text-slate-400 mt-1">
                Opened on {formatDate(account.createdAt)}
              </p>
            </div>

            <div className="sm:text-right">
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                Available Cleared Balance
              </p>
              <h2 className="text-3xl sm:text-4xl font-bold text-white mt-1">
                {formatCurrency(account.balance, account.currency)}
              </h2>
              <div className="mt-3 flex items-center gap-2 sm:justify-end">
                <Button
                  size="sm"
                  variant="secondary"
                  leftIcon={<ArrowUpRight className="w-3.5 h-3.5" />}
                  onClick={() => navigate('/transfers')}
                >
                  Transfer from this Account
                </Button>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {/* Real-time Double-Entry Ledger Reconciliation Card */}
      <Card
        title={
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-royal-600" />
            <span>Double-Entry Ledger Reconciliation</span>
          </div>
        }
        action={
          <Button
            size="sm"
            variant="outline"
            leftIcon={<RefreshCw className={`w-3.5 h-3.5 ${isReconFetching ? 'animate-spin' : ''}`} />}
            onClick={() => refetchRecon()}
            disabled={isReconFetching}
          >
            Re-Verify Ledger
          </Button>
        }
      >
        {isReconLoading ? (
          <Skeleton className="h-16 w-full" />
        ) : recon ? (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-1">
            <div className="p-4 bg-slate-50 rounded-xl border border-slate-100">
              <span className="text-xs text-slate-500 uppercase font-semibold">
                Account Table Balance
              </span>
              <p className="text-xl font-bold text-slate-900 mt-1">
                {formatCurrency(recon.accountBalance)}
              </p>
            </div>

            <div className="p-4 bg-slate-50 rounded-xl border border-slate-100">
              <span className="text-xs text-slate-500 uppercase font-semibold">
                Ledger Sum Balance (Σ Credits - Σ Debits)
              </span>
              <p className="text-xl font-bold text-slate-900 mt-1">
                {formatCurrency(recon.ledgerBalance)}
              </p>
            </div>

            <div
              className={`p-4 rounded-xl border flex items-center justify-between ${
                recon.reconciled
                  ? 'bg-emerald-50/70 border-emerald-200 text-emerald-900'
                  : 'bg-rose-50 border-rose-200 text-rose-900'
              }`}
            >
              <div>
                <span className="text-xs font-semibold uppercase tracking-wider block">
                  Reconciliation Status
                </span>
                <p className="text-base font-bold mt-0.5">
                  {recon.reconciled ? 'BALANCED & VERIFIED' : 'DISCREPANCY DETECTED'}
                </p>
                <p className="text-xs opacity-80">Difference: {formatCurrency(recon.difference)}</p>
              </div>
              {recon.reconciled ? (
                <CheckCircle2 className="w-8 h-8 text-emerald-600 flex-shrink-0" />
              ) : (
                <AlertTriangle className="w-8 h-8 text-rose-600 flex-shrink-0" />
              )}
            </div>
          </div>
        ) : null}
      </Card>

      {/* Transaction Statement Table */}
      <Card
        title="Transaction History Statement"
        subtitle={`Showing transactions for ${accountNumber}`}
      >
        {isTxLoading ? (
          <TableSkeleton rows={5} cols={5} />
        ) : transactions.length === 0 ? (
          <EmptyState
            icon={<Receipt className="w-8 h-8 text-slate-400" />}
            title="No Transactions Recorded"
            description="There are no completed debits or credits on this account yet. Perform a transfer or receive funds to see transactions."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/70 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Timestamp</th>
                  <th className="py-3 px-4 font-semibold">Reference</th>
                  <th className="py-3 px-4 font-semibold">Counterparty</th>
                  <th className="py-3 px-4 font-semibold">Description</th>
                  <th className="py-3 px-4 font-semibold">Type</th>
                  <th className="py-3 px-4 font-semibold text-right">Amount</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.map((tx) => {
                  const isCredit = tx.destinationAccountNumber === accountNumber;
                  return (
                    <tr key={tx.id} className="hover:bg-slate-50/60 transition">
                      <td className="py-3 px-4 whitespace-nowrap text-slate-600 font-mono">
                        {formatDateTime(tx.timestamp)}
                      </td>
                      <td className="py-3 px-4 whitespace-nowrap font-mono font-medium text-slate-900">
                        {tx.transactionReference}
                      </td>
                      <td className="py-3 px-4 whitespace-nowrap font-mono text-slate-600">
                        {isCredit
                          ? formatAccountMasked(tx.sourceAccountNumber)
                          : formatAccountMasked(tx.destinationAccountNumber)}
                      </td>
                      <td className="py-3 px-4 text-slate-700 max-w-xs truncate">
                        {tx.description || 'Internal Bank Transfer'}
                      </td>
                      <td className="py-3 px-4 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center gap-1 font-semibold px-2 py-0.5 rounded text-[11px] ${
                            isCredit
                              ? 'bg-emerald-50 text-emerald-700'
                              : 'bg-rose-50 text-rose-700'
                          }`}
                        >
                          {isCredit ? (
                            <>
                              <ArrowDownLeft className="w-3 h-3" /> CREDIT
                            </>
                          ) : (
                            <>
                              <ArrowUpRight className="w-3 h-3" /> DEBIT
                            </>
                          )}
                        </span>
                      </td>
                      <td
                        className={`py-3 px-4 whitespace-nowrap text-right font-bold text-sm ${
                          isCredit ? 'text-emerald-600' : 'text-slate-900'
                        }`}
                      >
                        {isCredit ? '+' : '-'}
                        {formatCurrency(tx.amount, tx.currency)}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>

            {/* Pagination Controls */}
            {txPage && txPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {txPage.number + 1} of {txPage.totalPages} ({txPage.totalElements} total entries)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={txPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={txPage.last}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Next
                  </Button>
                </div>
              </div>
            )}
          </div>
        )}
      </Card>
    </div>
  );
};
