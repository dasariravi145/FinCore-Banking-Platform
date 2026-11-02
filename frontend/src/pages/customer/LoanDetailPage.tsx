import React, { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { loanApi, RepayLoanPayload } from '../../api/loans';
import { accountApi } from '../../api/accounts';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Modal } from '../../components/common/Modal';
import { ConfirmationModal } from '../../components/common/ConfirmationModal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton, TableSkeleton } from '../../components/common/Skeleton';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { LoanRepaymentReceipt } from '../../types';
import {
  ArrowLeft,
  CreditCard,
  CheckCircle2,
  Clock,
} from 'lucide-react';

const repaySchema = z.object({
  sourceAccountNumber: z.string().min(1, 'Please select a source account'),
  amount: z.number().positive('Repayment amount must be greater than 0'),
});

type RepayFormData = z.infer<typeof repaySchema>;

export const LoanDetailPage: React.FC = () => {
  const { loanNumber } = useParams<{ loanNumber: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { error: toastError, success: toastSuccess } = useToast();

  const [isRepayModalOpen, setIsRepayModalOpen] = useState(false);
  const [isConfirmModalOpen, setIsConfirmModalOpen] = useState(false);
  const [pendingRepayment, setPendingRepayment] = useState<RepayFormData | null>(null);
  const [repaymentReceipt, setRepaymentReceipt] = useState<LoanRepaymentReceipt | null>(null);

  // 1. Fetch Loan Details
  const { data: loan, isLoading: isLoanLoading } = useQuery({
    queryKey: ['loans', loanNumber],
    queryFn: () => loanApi.getLoanDetails(loanNumber!),
    enabled: Boolean(loanNumber),
  });

  // 2. Fetch Customer Accounts
  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts', 'my-accounts'],
    queryFn: () => accountApi.getMyAccounts(),
  });

  const {
    register,
    handleSubmit,
    setValue,
    watch,
    formState: { errors },
  } = useForm<RepayFormData>({
    resolver: zodResolver(repaySchema),
    defaultValues: {
      sourceAccountNumber: accounts[0]?.accountNumber || '',
      amount: loan?.monthlyEmi || 0,
    },
  });

  // Set default amount when loan loads
  React.useEffect(() => {
    if (loan) {
      setValue('amount', loan.monthlyEmi);
      if (accounts.length > 0 && !watch('sourceAccountNumber')) {
        setValue('sourceAccountNumber', accounts[0].accountNumber);
      }
    }
  }, [loan, accounts, setValue, watch]);

  // Repayment Mutation
  const repayMutation = useMutation({
    mutationFn: (data: RepayLoanPayload) => loanApi.repayLoan(loanNumber!, data),
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: ['loans'] });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      setRepaymentReceipt(receipt);
      setIsConfirmModalOpen(false);
      setIsRepayModalOpen(false);
      toastSuccess('Repayment Completed', `Ref: ${receipt.repaymentReference}`);
    },
    onError: (err) => {
      toastError('Repayment Failed', getErrorMessage(err));
    },
  });

  if (!loanNumber) {
    return <div className="p-8 text-center text-slate-500">Loan number missing.</div>;
  }

  const onInitiateRepay = (data: RepayFormData) => {
    setPendingRepayment(data);
    setIsConfirmModalOpen(true);
  };

  const onConfirmRepayment = () => {
    if (!pendingRepayment) return;
    repayMutation.mutate(pendingRepayment);
  };

  const canRepay = loan?.status === 'ACTIVE' && (loan.outstandingPrincipal > 0 || loan.outstandingInterest > 0);

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <Button
          size="sm"
          variant="outline"
          leftIcon={<ArrowLeft className="w-4 h-4" />}
          onClick={() => navigate('/loans')}
        >
          Back to Loans
        </Button>
      </div>

      {/* Main Loan Details Banner */}
      {isLoanLoading ? (
        <Skeleton className="h-44 w-full" />
      ) : loan ? (
        <div className="bg-gradient-to-r from-navy-950 via-navy-900 to-navy-850 text-white rounded-2xl p-6 sm:p-8 shadow-xl relative overflow-hidden">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-6">
            <div>
              <div className="flex items-center gap-2">
                <span className="text-xs font-semibold px-2 py-0.5 rounded bg-gold-500/20 text-gold-300 border border-gold-500/30">
                  {loan.loanType} LOAN
                </span>
                <StatusBadge status={loan.status} size="sm" />
              </div>
              <h1 className="text-2xl sm:text-3xl font-mono font-bold tracking-tight mt-2">
                {loan.loanNumber}
              </h1>
              <p className="text-xs text-slate-400 mt-1">
                Disbursement Account: {loan.disbursementAccountNumber}
              </p>
            </div>

            <div className="sm:text-right">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">
                Outstanding Principal
              </span>
              <p className="text-3xl sm:text-4xl font-bold text-white mt-0.5">
                {formatCurrency(loan.outstandingPrincipal)}
              </p>
              <p className="text-xs text-gold-400 mt-1">
                Monthly EMI: {formatCurrency(loan.monthlyEmi)}
              </p>
              {canRepay && (
                <div className="mt-3">
                  <Button
                    size="sm"
                    variant="secondary"
                    leftIcon={<CreditCard className="w-3.5 h-3.5" />}
                    onClick={() => setIsRepayModalOpen(true)}
                  >
                    Make Installment Payment
                  </Button>
                </div>
              )}
            </div>
          </div>
        </div>
      ) : null}

      {/* Status Alert if Loan not yet active */}
      {loan && loan.status === 'APPLIED' && (
        <div className="bg-sky-50 border border-sky-200 rounded-xl p-4 flex items-start gap-3">
          <Clock className="w-5 h-5 text-sky-600 flex-shrink-0 mt-0.5" />
          <div className="text-xs text-sky-900">
            <h4 className="font-semibold text-sm">Application Under Review</h4>
            <p className="mt-0.5">
              This loan application has been submitted and is currently awaiting administrator approval and subsequent disbursement.
            </p>
          </div>
        </div>
      )}

      {/* Repayment Receipt Notification */}
      {repaymentReceipt && (
        <Card
          className="border-2 border-emerald-500 bg-emerald-50/20"
          title={
            <div className="flex items-center gap-2 text-emerald-800">
              <CheckCircle2 className="w-5 h-5 text-emerald-600" />
              <span>Repayment Processed Successfully</span>
            </div>
          }
          action={
            <Button size="sm" variant="outline" onClick={() => setRepaymentReceipt(null)}>
              Dismiss
            </Button>
          }
        >
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 text-xs">
            <div>
              <span className="text-slate-500">Repayment Ref:</span>
              <p className="font-mono font-bold text-slate-900 mt-0.5">
                {repaymentReceipt.repaymentReference}
              </p>
            </div>
            <div>
              <span className="text-slate-500">Total Paid:</span>
              <p className="font-bold text-emerald-600 mt-0.5">
                {formatCurrency(repaymentReceipt.amountPaid)}
              </p>
            </div>
            <div>
              <span className="text-slate-500">Allocated to Principal:</span>
              <p className="font-bold text-slate-900 mt-0.5">
                {formatCurrency(repaymentReceipt.principalPaid)}
              </p>
            </div>
            <div>
              <span className="text-slate-500">Allocated to Interest:</span>
              <p className="font-bold text-slate-900 mt-0.5">
                {formatCurrency(repaymentReceipt.interestPaid)}
              </p>
            </div>
          </div>
        </Card>
      )}

      {/* Loan Financial Breakdown Cards */}
      {loan && (
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <Card>
            <span className="text-[11px] font-semibold text-slate-500 uppercase">
              Original Principal
            </span>
            <p className="text-lg font-bold text-slate-900 mt-1">
              {formatCurrency(loan.principalAmount)}
            </p>
          </Card>
          <Card>
            <span className="text-[11px] font-semibold text-slate-500 uppercase">
              Interest Rate
            </span>
            <p className="text-lg font-bold text-slate-900 mt-1">
              {loan.annualInterestRate}% p.a.
            </p>
          </Card>
          <Card>
            <span className="text-[11px] font-semibold text-slate-500 uppercase">
              Repayment Tenure
            </span>
            <p className="text-lg font-bold text-slate-900 mt-1">
              {loan.termMonths} Months
            </p>
          </Card>
          <Card>
            <span className="text-[11px] font-semibold text-slate-500 uppercase">
              Total Interest Payable
            </span>
            <p className="text-lg font-bold text-slate-900 mt-1">
              {formatCurrency(loan.totalInterest)}
            </p>
          </Card>
        </div>
      )}

      {/* Amortization Schedule Table */}
      <Card
        title="Amortization Repayment Schedule"
        subtitle="1..N Installment Breakdown (Monthly reducing-balance schedule)"
      >
        {isLoanLoading ? (
          <TableSkeleton rows={6} cols={6} />
        ) : !loan?.schedules || loan.schedules.length === 0 ? (
          <p className="text-xs text-slate-500 text-center py-6">
            Repayment schedule will be generated upon loan approval.
          </p>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50/70 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">#</th>
                  <th className="py-3 px-4 font-semibold">Due Date</th>
                  <th className="py-3 px-4 font-semibold text-right">Principal Due</th>
                  <th className="py-3 px-4 font-semibold text-right">Interest Due</th>
                  <th className="py-3 px-4 font-semibold text-right">Total EMI</th>
                  <th className="py-3 px-4 font-semibold text-right">Remaining Balance</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loan.schedules.map((item) => (
                  <tr key={item.installmentNumber} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-bold text-slate-800">
                      {item.installmentNumber}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-slate-600 font-mono">
                      {formatDate(item.dueDate)}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-right font-mono text-slate-800">
                      {formatCurrency(item.principalDue)}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-right font-mono text-slate-800">
                      {formatCurrency(item.interestDue)}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-right font-mono font-bold text-slate-900">
                      {formatCurrency(item.totalEmi)}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-right font-mono text-slate-600">
                      {formatCurrency(item.remainingBalance)}
                    </td>
                    <td className="py-3 px-4 whitespace-nowrap text-center">
                      <StatusBadge status={item.status} size="sm" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {/* Repay Modal */}
      <Modal
        isOpen={isRepayModalOpen}
        onClose={() => setIsRepayModalOpen(false)}
        title="Pay Loan Installment"
        description={`Make a repayment towards loan ${loanNumber}. Payment is applied first to due interest, then principal.`}
      >
        <form onSubmit={handleSubmit(onInitiateRepay)} className="space-y-4">
          <Select
            label="Payment Source Account"
            options={[
              ...accounts.map((a) => ({
                value: a.accountNumber,
                label: `${a.accountType} (${a.accountNumber}) — Bal: ${formatCurrency(a.balance)}`,
              })),
            ]}
            error={errors.sourceAccountNumber?.message}
            {...register('sourceAccountNumber')}
          />

          <Input
            label="Repayment Amount (INR ₹)"
            type="number"
            step="0.01"
            error={errors.amount?.message}
            helperText={`Recommended monthly EMI: ${formatCurrency(loan?.monthlyEmi)}`}
            {...register('amount', { valueAsNumber: true })}
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsRepayModalOpen(false)}
            >
              Cancel
            </Button>
            <Button type="submit" variant="primary">
              Review Repayment
            </Button>
          </div>
        </form>
      </Modal>

      {/* Repayment Confirmation Modal */}
      <ConfirmationModal
        isOpen={isConfirmModalOpen}
        onClose={() => setIsConfirmModalOpen(false)}
        onConfirm={onConfirmRepayment}
        title="Confirm Loan Repayment"
        description="Please confirm the repayment details. Funds will be debited from your bank account and posted against this loan."
        confirmLabel="Authorize Payment"
        isLoading={repayMutation.isPending}
      >
        {pendingRepayment && (
          <div className="space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-slate-500">Debited Account:</span>
              <span className="font-mono font-bold text-slate-900">
                {pendingRepayment.sourceAccountNumber}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Repayment Amount:</span>
              <span className="font-bold text-royal-600">
                {formatCurrency(pendingRepayment.amount)}
              </span>
            </div>
          </div>
        )}
      </ConfirmationModal>
    </div>
  );
};
