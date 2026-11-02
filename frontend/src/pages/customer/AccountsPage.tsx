import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { accountApi } from '../../api/accounts';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Select } from '../../components/common/Select';
import { Modal } from '../../components/common/Modal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { Wallet, PlusCircle, ArrowUpRight, ShieldCheck, CheckCircle } from 'lucide-react';

const openAccountSchema = z.object({
  accountType: z.enum(['SAVINGS', 'CURRENT']),
});

type OpenAccountFormData = z.infer<typeof openAccountSchema>;

export const AccountsPage: React.FC = () => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { success: toastSuccess, error: toastError } = useToast();
  const [isModalOpen, setIsModalOpen] = useState(false);

  const { data: accounts = [], isLoading } = useQuery({
    queryKey: ['accounts', 'my-accounts'],
    queryFn: () => accountApi.getMyAccounts(),
  });

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<OpenAccountFormData>({
    resolver: zodResolver(openAccountSchema),
    defaultValues: { accountType: 'SAVINGS' },
  });

  const openAccountMutation = useMutation({
    mutationFn: (data: OpenAccountFormData) => accountApi.openAccount(data),
    onSuccess: (newAccount) => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      toastSuccess('Account Opened', `Account ${newAccount.accountNumber} opened successfully.`);
      setIsModalOpen(false);
      reset();
    },
    onError: (err) => {
      toastError('Account Opening Failed', getErrorMessage(err));
    },
  });

  const onSubmit = (data: OpenAccountFormData) => {
    openAccountMutation.mutate(data);
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Bank Accounts</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Manage your deposit accounts, verify ledger reconciliation, and view statements
          </p>
        </div>
        <Button
          variant="primary"
          leftIcon={<PlusCircle className="w-4 h-4" />}
          onClick={() => setIsModalOpen(true)}
        >
          Open New Account
        </Button>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      ) : accounts.length === 0 ? (
        <EmptyState
          icon={<Wallet className="w-8 h-8 text-slate-400" />}
          title="No Accounts Found"
          description="You do not have any open accounts yet. Click below to open a new simulated SAVINGS or CURRENT account."
          action={
            <Button
              variant="secondary"
              leftIcon={<PlusCircle className="w-4 h-4" />}
              onClick={() => setIsModalOpen(true)}
            >
              Open Account
            </Button>
          }
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {accounts.map((acc) => (
            <Card
              key={acc.id}
              className="hover:border-royal-300 hover:shadow-md transition cursor-pointer"
              title={
                <div className="flex items-center justify-between w-full">
                  <span className="font-mono text-xs text-slate-500">{acc.accountNumber}</span>
                  <StatusBadge status={acc.status} size="sm" />
                </div>
              }
              footer={
                <div className="flex items-center justify-between text-xs text-slate-500">
                  <span>Opened on {formatDate(acc.createdAt)}</span>
                  <span className="text-royal-600 font-semibold flex items-center gap-1 group-hover:underline">
                    View Details <ArrowUpRight className="w-3.5 h-3.5" />
                  </span>
                </div>
              }
            >
              <div
                onClick={() => navigate(`/accounts/${acc.accountNumber}`)}
                className="space-y-3"
              >
                <div>
                  <span className="text-xs font-semibold px-2 py-0.5 rounded bg-royal-50 text-royal-700">
                    {acc.accountType} ACCOUNT
                  </span>
                  <p className="text-2xl font-bold text-slate-900 mt-2">
                    {formatCurrency(acc.balance, acc.currency)}
                  </p>
                </div>
                <div className="flex items-center gap-1.5 text-xs text-slate-600">
                  <ShieldCheck className="w-4 h-4 text-emerald-600" />
                  <span>Category: {acc.accountCategory}</span>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}

      {/* Open Account Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title="Open a New Bank Account"
        description="Select the type of account you want to open in the FinCore simulation."
      >
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <Select
            label="Account Type"
            options={[
              { value: 'SAVINGS', label: 'SAVINGS ACCOUNT — Interest Bearing Deposit' },
              { value: 'CURRENT', label: 'CURRENT ACCOUNT — Commercial Checking' },
            ]}
            error={errors.accountType?.message}
            {...register('accountType')}
          />

          <div className="p-3 bg-slate-50 rounded-lg border border-slate-100 text-xs text-slate-600 space-y-1">
            <div className="flex items-center gap-1.5 font-semibold text-slate-800">
              <CheckCircle className="w-4 h-4 text-emerald-600" />
              <span>Double-Entry Features Included:</span>
            </div>
            <p>• Initial balance defaults to ₹0.00 (Fundable via Admin Simulation)</p>
            <p>• Immutable double-entry ledger postings for every transaction</p>
            <p>• Mathematical reconciliation verification on demand</p>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={openAccountMutation.isPending}
            >
              Confirm & Open Account
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
