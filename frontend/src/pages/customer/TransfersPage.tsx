import React, { useState, useEffect } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { accountApi } from '../../api/accounts';
import { transferApi, TransferPayload } from '../../api/transfers';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { ConfirmationModal } from '../../components/common/ConfirmationModal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { TransferReceipt } from '../../types';
import {
  ShieldCheck,
  CheckCircle2,
  Copy,
  ArrowRight,
  Wallet,
} from 'lucide-react';

const transferFormSchema = z.object({
  sourceAccountNumber: z.string().min(1, 'Please select a source account'),
  destinationAccountNumber: z
    .string()
    .min(1, 'Destination account number is required')
    .regex(/^ACC-[A-Z0-9-]+$/, 'Account number must follow ACC-XXXXXXXXXXXX format'),
  amount: z
    .number()
    .positive('Transfer amount must be greater than 0')
    .max(10000000, 'Maximum simulated single transfer limit is ₹10,000,000.00'),
  description: z.string().max(255, 'Description cannot exceed 255 characters').optional(),
});

type TransferFormData = z.infer<typeof transferFormSchema>;

export const TransfersPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { error: toastError, success: toastSuccess } = useToast();

  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [pendingTransferData, setPendingTransferData] = useState<TransferFormData | null>(null);
  const [currentIdempotencyKey, setCurrentIdempotencyKey] = useState<string>('');
  const [completedReceipt, setCompletedReceipt] = useState<TransferReceipt | null>(null);
  const [copiedKey, setCopiedKey] = useState(false);

  // Fetch customer accounts
  const { data: accounts = [], isLoading: isAccountsLoading } = useQuery({
    queryKey: ['accounts', 'my-accounts'],
    queryFn: () => accountApi.getMyAccounts(),
  });

  const {
    register,
    handleSubmit,
    watch,
    reset,
    formState: { errors },
  } = useForm<TransferFormData>({
    resolver: zodResolver(transferFormSchema),
    defaultValues: {
      sourceAccountNumber: '',
      destinationAccountNumber: '',
      description: 'Vendor payment',
    },
  });

  const selectedSourceAccount = watch('sourceAccountNumber');
  const enteredAmount = watch('amount');

  const activeSource = accounts.find((a) => a.accountNumber === selectedSourceAccount);
  const hasSufficientFunds = activeSource
    ? (enteredAmount || 0) <= (activeSource.balance || 0)
    : true;

  // Initialize or generate idempotency key
  const generateNewKey = () => {
    const key = typeof crypto !== 'undefined' && crypto.randomUUID
      ? crypto.randomUUID()
      : `idem-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`;
    setCurrentIdempotencyKey(key);
    return key;
  };

  useEffect(() => {
    generateNewKey();
  }, []);

  const transferMutation = useMutation({
    mutationFn: ({ payload, idempotencyKey }: { payload: TransferPayload; idempotencyKey: string }) =>
      transferApi.executeTransfer(payload, idempotencyKey),
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
      setCompletedReceipt(receipt);
      setIsConfirmModalOpen(false);
      toastSuccess('Transfer Completed', `Ref: ${receipt.transactionReference}`);
      reset();
      generateNewKey();
    },
    onError: (err) => {
      // Keep modal open or show error; importantly PRESERVE the current idempotency key for safe retry!
      toastError('Transfer Failed', getErrorMessage(err));
    },
  });

  const onInitiateReview = (data: TransferFormData) => {
    if (activeSource && data.amount > activeSource.balance) {
      toastError('Insufficient Funds', `Available balance is ${formatCurrency(activeSource.balance)}`);
      return;
    }
    if (data.sourceAccountNumber === data.destinationAccountNumber) {
      toastError('Invalid Transfer', 'Source and destination accounts must be different');
      return;
    }

    setPendingTransferData(data);
    setIsConfirmModalOpen(true);
  };

  const onConfirmExecution = () => {
    if (!pendingTransferData) return;

    transferMutation.mutate({
      payload: {
        sourceAccountNumber: pendingTransferData.sourceAccountNumber,
        destinationAccountNumber: pendingTransferData.destinationAccountNumber,
        amount: pendingTransferData.amount,
        currency: 'INR',
        description: pendingTransferData.description,
      },
      idempotencyKey: currentIdempotencyKey,
    });
  };

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    setCopiedKey(true);
    setTimeout(() => setCopiedKey(false), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Page Header */}
      <div>
        <h1 className="text-2xl font-bold text-slate-900">Transfer Funds</h1>
        <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
          Execute real-time double-entry internal transfers with SHA-256 idempotency protection
        </p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Transfer Form (Left 2 cols) */}
        <div className="lg:col-span-2 space-y-6">
          <Card title="Initiate Money Transfer">
            <form onSubmit={handleSubmit(onInitiateReview)} className="space-y-5">
              {/* Step 1: Select Source Account */}
              <div>
                <Select
                  label="1. Select Source Account"
                  options={[
                    { value: '', label: '-- Select Sender Account --', disabled: true },
                    ...accounts.map((a) => ({
                      value: a.accountNumber,
                      label: `${a.accountType} (${a.accountNumber}) — Available: ${formatCurrency(a.balance)}`,
                    })),
                  ]}
                  error={errors.sourceAccountNumber?.message}
                  {...register('sourceAccountNumber')}
                />
                {activeSource && (
                  <div className="mt-2 p-2.5 bg-slate-50 rounded-lg flex items-center justify-between text-xs text-slate-600">
                    <span className="flex items-center gap-1.5 font-medium">
                      <Wallet className="w-3.5 h-3.5 text-royal-600" />
                      Cleared Balance:
                    </span>
                    <span className="font-bold text-slate-900 font-mono">
                      {formatCurrency(activeSource.balance, activeSource.currency)}
                    </span>
                  </div>
                )}
              </div>

              {/* Step 2: Destination Account */}
              <Input
                label="2. Destination Account Number"
                placeholder="e.g. ACC-100000000002"
                error={errors.destinationAccountNumber?.message}
                helperText="Must start with ACC- followed by alphanumeric account identifier"
                {...register('destinationAccountNumber')}
              />

              {/* Step 3 & 4: Amount & Balance Validation */}
              <div>
                <Input
                  label="3. Transfer Amount (INR ₹)"
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  error={
                    errors.amount?.message ||
                    (!hasSufficientFunds ? 'Transfer amount exceeds available account balance' : undefined)
                  }
                  {...register('amount', { valueAsNumber: true })}
                />
                {enteredAmount && activeSource && (
                  <p
                    className={`text-xs mt-1 font-medium ${
                      hasSufficientFunds ? 'text-emerald-600' : 'text-rose-600'
                    }`}
                  >
                    Remaining balance after transfer:{' '}
                    {formatCurrency(Math.max(0, activeSource.balance - enteredAmount))}
                  </p>
                )}
              </div>

              {/* Description */}
              <Input
                label="4. Transfer Note / Description (Optional)"
                placeholder="e.g. Software milestone 1 payment"
                error={errors.description?.message}
                {...register('description')}
              />

              {/* Idempotency Key Notice */}
              <div className="p-3 bg-royal-50/60 border border-royal-200/60 rounded-xl text-xs space-y-1">
                <div className="flex items-center justify-between font-semibold text-royal-900">
                  <span className="flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4 text-royal-600" />
                    Idempotency-Key (Replay Protection):
                  </span>
                  <button
                    type="button"
                    onClick={() => copyToClipboard(currentIdempotencyKey)}
                    className="text-royal-600 hover:text-royal-800 flex items-center gap-1 text-[11px]"
                  >
                    <Copy className="w-3 h-3" />
                    {copiedKey ? 'Copied' : 'Copy Key'}
                  </button>
                </div>
                <p className="font-mono text-slate-600 break-all text-[11px]">
                  {currentIdempotencyKey}
                </p>
                <p className="text-slate-500 text-[10px]">
                  Guarantees that re-submitting this exact request will return the same receipt without double-debiting.
                </p>
              </div>

              <Button
                type="submit"
                variant="primary"
                size="lg"
                className="w-full"
                disabled={!hasSufficientFunds || isAccountsLoading}
                rightIcon={<ArrowRight className="w-4 h-4" />}
              >
                Review Transfer Details
              </Button>
            </form>
          </Card>

          {/* Completed Receipt Card */}
          {completedReceipt && (
            <Card
              className="border-2 border-emerald-500 bg-emerald-50/20"
              title={
                <div className="flex items-center gap-2 text-emerald-800">
                  <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                  <span>Transfer Successfully Executed</span>
                </div>
              }
              action={
                <Button
                  size="sm"
                  variant="outline"
                  onClick={() => setCompletedReceipt(null)}
                >
                  Dismiss
                </Button>
              }
            >
              <div className="space-y-4">
                <div className="grid grid-cols-2 gap-4 text-xs">
                  <div>
                    <span className="text-slate-500">Transaction Reference:</span>
                    <p className="font-mono font-bold text-slate-900 mt-0.5">
                      {completedReceipt.transactionReference}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-500">Execution Status:</span>
                    <div className="mt-0.5">
                      <StatusBadge status={completedReceipt.status} size="sm" />
                    </div>
                  </div>
                  <div>
                    <span className="text-slate-500">Amount Transferred:</span>
                    <p className="text-base font-bold text-emerald-600 mt-0.5">
                      {formatCurrency(completedReceipt.amount, completedReceipt.currency)}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-500">Timestamp:</span>
                    <p className="font-mono text-slate-800 mt-0.5">
                      {formatDateTime(completedReceipt.timestamp)}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-500">Sender Account:</span>
                    <p className="font-mono text-slate-800 mt-0.5">
                      {completedReceipt.sourceAccountNumber}
                    </p>
                  </div>
                  <div>
                    <span className="text-slate-500">Recipient Account:</span>
                    <p className="font-mono text-slate-800 mt-0.5">
                      {completedReceipt.destinationAccountNumber}
                    </p>
                  </div>
                </div>

                <div className="pt-3 border-t border-emerald-100 flex items-center justify-between text-xs text-slate-600">
                  <span>Double-Entry Journal: Posted atomically ($\sum D = \sum C$)</span>
                  <span className="font-mono text-[11px] text-slate-500">
                    Idempotent Key: {completedReceipt.idempotencyKey.slice(0, 8)}...
                  </span>
                </div>
              </div>
            </Card>
          )}
        </div>

        {/* Sidebar Info (Right col) */}
        <div className="space-y-6">
          <Card title="Banking Invariants">
            <div className="space-y-3 text-xs text-slate-600">
              <div className="p-3 bg-slate-50 rounded-lg space-y-1">
                <span className="font-semibold text-slate-800 block">
                  1. Double-Entry Posting
                </span>
                <p>
                  Every transfer debits the sender and credits the recipient simultaneously in an ACID transaction.
                </p>
              </div>

              <div className="p-3 bg-slate-50 rounded-lg space-y-1">
                <span className="font-semibold text-slate-800 block">
                  2. Deterministic Deadlock Locks
                </span>
                <p>
                  Row locks on accounts are acquired in lexicographical UUID order, eliminating concurrent circular-wait deadlocks.
                </p>
              </div>

              <div className="p-3 bg-slate-50 rounded-lg space-y-1">
                <span className="font-semibold text-slate-800 block">
                  3. Real-Time Fraud Guards
                </span>
                <p>
                  Transfers exceeding ₹500,000 or exhibiting rapid velocity triggers risk evaluation before balances are debited.
                </p>
              </div>
            </div>
          </Card>
        </div>
      </div>

      {/* Explicit Transfer Confirmation Modal */}
      <ConfirmationModal
        isOpen={isConfirmModalOpen}
        onClose={() => setIsConfirmModalOpen(false)}
        onConfirm={onConfirmExecution}
        title="Confirm Funds Transfer"
        description="Please carefully verify the transfer parameters before authorization. This transaction will create immutable double-entry ledger entries."
        confirmLabel="Authorize & Post Transfer"
        cancelLabel="Revise Details"
        variant="primary"
        isLoading={transferMutation.isPending}
      >
        {pendingTransferData && (
          <div className="space-y-3 text-xs">
            <div className="p-4 bg-slate-50 rounded-xl space-y-2 border border-slate-100">
              <div className="flex justify-between">
                <span className="text-slate-500">Sender Account:</span>
                <span className="font-mono font-bold text-slate-900">
                  {pendingTransferData.sourceAccountNumber}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Recipient Account:</span>
                <span className="font-mono font-bold text-slate-900">
                  {pendingTransferData.destinationAccountNumber}
                </span>
              </div>
              <div className="flex justify-between pt-2 border-t border-slate-200">
                <span className="text-slate-700 font-semibold">Transfer Amount:</span>
                <span className="font-bold text-sm text-royal-600">
                  {formatCurrency(pendingTransferData.amount)}
                </span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-500">Description:</span>
                <span className="text-slate-700">
                  {pendingTransferData.description || '—'}
                </span>
              </div>
            </div>

            <div className="text-[11px] text-slate-500 flex items-center justify-between">
              <span>Active Idempotency Key:</span>
              <span className="font-mono">{currentIdempotencyKey.slice(0, 18)}...</span>
            </div>
          </div>
        )}
      </ConfirmationModal>
    </div>
  );
};
