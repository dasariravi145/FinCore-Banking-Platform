import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { accountApi } from '../../api/accounts';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Modal } from '../../components/common/Modal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { BankAccount, AccountStatus } from '../../types';
import { CreditCard, Snowflake, Unlock } from 'lucide-react';

export const AdminAccountsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();
  const [page, setPage] = useState(0);
  const size = 15;

  const [selectedAccount, setSelectedAccount] = useState<BankAccount | null>(null);
  const [targetStatus, setTargetStatus] = useState<AccountStatus>('FROZEN');
  const [auditReason, setAuditReason] = useState('');

  const { data: accountsPage, isLoading } = useQuery({
    queryKey: ['admin', 'accounts', page, size],
    queryFn: () => accountApi.getAllAccounts(page, size),
  });

  const statusMutation = useMutation({
    mutationFn: ({
      accountNumber,
      status,
      reason,
    }: {
      accountNumber: string;
      status: AccountStatus;
      reason: string;
    }) =>
      accountApi.updateAccountStatus(accountNumber, {
        status,
        reason,
      }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'accounts'] });
      toastSuccess('Account Status Updated', `Account ${updated.accountNumber} is now ${updated.status}.`);
      setSelectedAccount(null);
      setAuditReason('');
    },
    onError: (err) => {
      toastError('Update Failed', getErrorMessage(err));
    },
  });

  const openStatusModal = (account: BankAccount, newStatus: AccountStatus) => {
    setSelectedAccount(account);
    setTargetStatus(newStatus);
    setAuditReason(
      newStatus === 'FROZEN'
        ? 'Suspicious activity reported - preliminary operational freeze'
        : newStatus === 'ACTIVE'
        ? 'Compliance review completed - account unfreezed'
        : 'Customer requested account closure'
    );
  };

  const accounts = accountsPage?.content || [];

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Account Monitoring & Controls</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
          Real-time oversight of customer deposit accounts and administrative freeze/unfreeze controls
        </p>
      </div>

      <Card title="Platform Bank Accounts">
        {isLoading ? (
          <TableSkeleton rows={6} cols={6} />
        ) : accounts.length === 0 ? (
          <EmptyState
            icon={<CreditCard className="w-8 h-8 text-slate-400" />}
            title="No Accounts Found"
            description="No bank accounts exist in the simulation database."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Account Number</th>
                  <th className="py-3 px-4 font-semibold">Account Type</th>
                  <th className="py-3 px-4 font-semibold">Category</th>
                  <th className="py-3 px-4 font-semibold text-right">Balance</th>
                  <th className="py-3 px-4 font-semibold">Created Date</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                  <th className="py-3 px-4 font-semibold text-center">Admin Controls</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {accounts.map((acc) => (
                  <tr key={acc.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">
                      {acc.accountNumber}
                    </td>
                    <td className="py-3 px-4">
                      <span className="font-semibold text-slate-800">{acc.accountType}</span>
                    </td>
                    <td className="py-3 px-4 text-slate-600 font-mono text-[11px]">
                      {acc.accountCategory}
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-slate-900">
                      {formatCurrency(acc.balance, acc.currency)}
                    </td>
                    <td className="py-3 px-4 text-slate-500">{formatDate(acc.createdAt)}</td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={acc.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        {acc.status === 'ACTIVE' ? (
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-amber-700 hover:bg-amber-50 border-amber-200"
                            leftIcon={<Snowflake className="w-3 h-3 text-amber-600" />}
                            onClick={() => openStatusModal(acc, 'FROZEN')}
                          >
                            Freeze
                          </Button>
                        ) : acc.status === 'FROZEN' ? (
                          <Button
                            size="sm"
                            variant="outline"
                            className="text-emerald-700 hover:bg-emerald-50 border-emerald-200"
                            leftIcon={<Unlock className="w-3 h-3 text-emerald-600" />}
                            onClick={() => openStatusModal(acc, 'ACTIVE')}
                          >
                            Unfreeze
                          </Button>
                        ) : null}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {accountsPage && accountsPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {accountsPage.number + 1} of {accountsPage.totalPages} ({accountsPage.totalElements} accounts)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={accountsPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={accountsPage.last}
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

      {/* Status Modification Modal */}
      <Modal
        isOpen={!!selectedAccount}
        onClose={() => setSelectedAccount(null)}
        title={`Change Account Status to ${targetStatus}`}
        description={`Modify operational status for ${selectedAccount?.accountNumber}. An audit event will be logged.`}
      >
        <div className="space-y-4">
          <Select
            label="Target Account Status"
            value={targetStatus}
            onChange={(e) => setTargetStatus(e.target.value as AccountStatus)}
            options={[
              { value: 'ACTIVE', label: 'ACTIVE — Unrestricted operational status' },
              { value: 'FROZEN', label: 'FROZEN — Disallow all inflows and outflows' },
              { value: 'CLOSED', label: 'CLOSED — Permanently terminate account' },
            ]}
          />

          <Input
            label="Mandatory Audit Reason"
            value={auditReason}
            onChange={(e) => setAuditReason(e.target.value)}
            helperText="Reason is recorded permanently in the institutional audit log"
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button variant="outline" onClick={() => setSelectedAccount(null)}>
              Cancel
            </Button>
            <Button
              variant={targetStatus === 'FROZEN' ? 'danger' : 'primary'}
              disabled={!auditReason.trim()}
              isLoading={statusMutation.isPending}
              onClick={() => {
                if (selectedAccount) {
                  statusMutation.mutate({
                    accountNumber: selectedAccount.accountNumber,
                    status: targetStatus,
                    reason: auditReason,
                  });
                }
              }}
            >
              Confirm Status Change
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
