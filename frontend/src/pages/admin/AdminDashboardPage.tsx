import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api/customers';
import { accountApi } from '../../api/accounts';
import { loanApi } from '../../api/loans';
import { fraudApi } from '../../api/fraud';
import { transferApi, SimulatedFundingPayload, ReversalPayload } from '../../api/transfers';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton } from '../../components/common/Skeleton';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import {
  Users,
  CreditCard,
  Landmark,
  AlertOctagon,
  PlusCircle,
  RotateCcw,
} from 'lucide-react';

export const AdminDashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();

  const [isFundingModalOpen, setIsFundingModalOpen] = useState(false);
  const [isReversalModalOpen, setIsReversalModalOpen] = useState(false);

  const [fundDestAccount, setFundDestAccount] = useState('');
  const [fundAmount, setFundAmount] = useState<number>(50000);
  const [fundDesc, setFundDesc] = useState('Initial synthetic test capital');

  const [reverseTxRef, setReverseTxRef] = useState('');
  const [reverseReason, setReverseReason] = useState('Customer dispute resolved - chargeback approved');

  // Summary queries
  const { data: customersPage } = useQuery({
    queryKey: ['admin', 'customers', 0, 1],
    queryFn: () => customerApi.getAllCustomers(0, 1),
  });

  const { data: accountsPage } = useQuery({
    queryKey: ['admin', 'accounts', 0, 1],
    queryFn: () => accountApi.getAllAccounts(0, 1),
  });

  const { data: pendingLoansPage } = useQuery({
    queryKey: ['admin', 'loans', 'APPLIED', 0, 1],
    queryFn: () => loanApi.getAllLoans(0, 1, 'APPLIED'),
  });

  const { data: flaggedFraudPage } = useQuery({
    queryKey: ['admin', 'fraud', 'PENDING_REVIEW', 0, 1],
    queryFn: () => fraudApi.getAssessments(0, 1, undefined, 'PENDING_REVIEW'),
  });

  // Recent transactions
  const { data: recentTxPage, isLoading: isTxLoading } = useQuery({
    queryKey: ['admin', 'transactions', 0, 5],
    queryFn: () => transferApi.getAllTransactions(0, 5),
  });

  // Funding Mutation
  const fundMutation = useMutation({
    mutationFn: (data: SimulatedFundingPayload) => transferApi.fundAccountSimulated(data),
    onSuccess: (receipt) => {
      queryClient.invalidateQueries();
      toastSuccess('Account Funded', `Ref: ${receipt.transactionReference}`);
      setIsFundingModalOpen(false);
      setFundDestAccount('');
    },
    onError: (err) => {
      toastError('Funding Failed', getErrorMessage(err));
    },
  });

  // Reversal Mutation
  const reverseMutation = useMutation({
    mutationFn: ({ ref, data }: { ref: string; data: ReversalPayload }) =>
      transferApi.reverseTransaction(ref, data),
    onSuccess: (receipt) => {
      queryClient.invalidateQueries();
      toastSuccess('Transaction Reversed', `Reversal Ref: ${receipt.transactionReference}`);
      setIsReversalModalOpen(false);
      setReverseTxRef('');
    },
    onError: (err) => {
      toastError('Reversal Failed', getErrorMessage(err));
    },
  });

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-navy-950 via-navy-900 to-navy-850 p-6 sm:p-8 rounded-2xl text-white shadow-xl flex flex-col md:flex-row md:items-center justify-between gap-6">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-semibold px-2 py-0.5 rounded bg-gold-500/20 text-gold-300 border border-gold-500/30">
              ROLE_ADMIN ACCESS
            </span>
            <span className="text-xs text-slate-400">Institutional Governance Console</span>
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold tracking-tight mt-2">
            Bank Administration Hub
          </h1>
          <p className="text-xs sm:text-sm text-slate-300 mt-1 max-w-xl">
            Supervise platform accounts, approve/disburse loans, review fraud alerts, and perform controlled synthetic funding and reversals.
          </p>
        </div>

        <div className="flex flex-wrap gap-3">
          <Button
            variant="secondary"
            leftIcon={<PlusCircle className="w-4 h-4" />}
            onClick={() => setIsFundingModalOpen(true)}
          >
            Simulated Account Funding
          </Button>
          <Button
            variant="outline"
            className="bg-white/10 text-white border-white/20 hover:bg-white/20"
            leftIcon={<RotateCcw className="w-4 h-4" />}
            onClick={() => setIsReversalModalOpen(true)}
          >
            Reverse Transaction
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card
          className="border-l-4 border-l-royal-600 cursor-pointer hover:shadow-md transition"
          onClick={() => navigate('/admin/customers')}
        >
          <div className="flex justify-between items-center text-xs text-slate-500 font-semibold uppercase">
            <span>Customer Profiles</span>
            <Users className="w-4 h-4 text-royal-600" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-2">
            {customersPage?.totalElements ?? '—'}
          </p>
          <p className="text-[11px] text-royal-600 font-medium mt-1">View registry &rarr;</p>
        </Card>

        <Card
          className="border-l-4 border-l-navy-800 cursor-pointer hover:shadow-md transition"
          onClick={() => navigate('/admin/accounts')}
        >
          <div className="flex justify-between items-center text-xs text-slate-500 font-semibold uppercase">
            <span>Bank Accounts</span>
            <CreditCard className="w-4 h-4 text-navy-800" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-2">
            {accountsPage?.totalElements ?? '—'}
          </p>
          <p className="text-[11px] text-royal-600 font-medium mt-1">Manage statuses &rarr;</p>
        </Card>

        <Card
          className="border-l-4 border-l-gold-500 cursor-pointer hover:shadow-md transition"
          onClick={() => navigate('/admin/loans')}
        >
          <div className="flex justify-between items-center text-xs text-slate-500 font-semibold uppercase">
            <span>Pending Loan Reviews</span>
            <Landmark className="w-4 h-4 text-gold-600" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-2">
            {pendingLoansPage?.totalElements ?? '0'}
          </p>
          <p className="text-[11px] text-gold-700 font-medium mt-1">Review applications &rarr;</p>
        </Card>

        <Card
          className="border-l-4 border-l-rose-600 cursor-pointer hover:shadow-md transition"
          onClick={() => navigate('/admin/fraud')}
        >
          <div className="flex justify-between items-center text-xs text-slate-500 font-semibold uppercase">
            <span>Flagged Fraud Alerts</span>
            <AlertOctagon className="w-4 h-4 text-rose-600" />
          </div>
          <p className="text-2xl font-bold text-slate-900 mt-2">
            {flaggedFraudPage?.totalElements ?? '0'}
          </p>
          <p className="text-[11px] text-rose-600 font-medium mt-1">Compliance review &rarr;</p>
        </Card>
      </div>

      {/* Recent Platform Transactions */}
      <Card
        title="Recent Platform Financial Transactions"
        action={
          <Button
            size="sm"
            variant="outline"
            onClick={() => navigate('/admin/transfers')}
          >
            All Transactions
          </Button>
        }
      >
        {isTxLoading ? (
          <Skeleton className="h-40 w-full" />
        ) : recentTxPage?.content && recentTxPage.content.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-2.5 px-3 font-semibold">Timestamp</th>
                  <th className="py-2.5 px-3 font-semibold">Reference</th>
                  <th className="py-2.5 px-3 font-semibold">Source Account</th>
                  <th className="py-2.5 px-3 font-semibold">Destination Account</th>
                  <th className="py-2.5 px-3 font-semibold text-right">Amount</th>
                  <th className="py-2.5 px-3 font-semibold text-center">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {recentTxPage.content.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-2.5 px-3 font-mono text-slate-600">
                      {formatDateTime(tx.timestamp)}
                    </td>
                    <td className="py-2.5 px-3 font-mono font-medium text-slate-900">
                      {tx.transactionReference}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-slate-700">
                      {tx.sourceAccountNumber}
                    </td>
                    <td className="py-2.5 px-3 font-mono text-slate-700">
                      {tx.destinationAccountNumber}
                    </td>
                    <td className="py-2.5 px-3 text-right font-bold text-slate-900">
                      {formatCurrency(tx.amount, tx.currency)}
                    </td>
                    <td className="py-2.5 px-3 text-center">
                      <StatusBadge status={tx.status} size="sm" />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <p className="text-xs text-slate-500 py-6 text-center">No platform transactions found.</p>
        )}
      </Card>

      {/* Simulated Account Funding Modal */}
      <Modal
        isOpen={isFundingModalOpen}
        onClose={() => setIsFundingModalOpen(false)}
        title="Simulated Account Funding"
        description="Debits Central Simulation Reserve (ACC-SYSTEM-RESERVE-001) and credits customer account with balanced double-entry ledger entries."
      >
        <div className="space-y-4">
          <Input
            label="Destination Account Number"
            placeholder="e.g. ACC-100000000001"
            value={fundDestAccount}
            onChange={(e) => setFundDestAccount(e.target.value)}
          />

          <Input
            label="Funding Amount (INR ₹)"
            type="number"
            step="1000"
            value={fundAmount}
            onChange={(e) => setFundAmount(parseFloat(e.target.value) || 0)}
          />

          <Input
            label="Audit Description / Reason"
            value={fundDesc}
            onChange={(e) => setFundDesc(e.target.value)}
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button
              variant="outline"
              onClick={() => setIsFundingModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              variant="primary"
              disabled={!fundDestAccount || fundAmount <= 0}
              isLoading={fundMutation.isPending}
              onClick={() =>
                fundMutation.mutate({
                  destinationAccountNumber: fundDestAccount,
                  amount: fundAmount,
                  description: fundDesc,
                })
              }
            >
              Post Funding Journal
            </Button>
          </div>
        </div>
      </Modal>

      {/* Reversal Modal */}
      <Modal
        isOpen={isReversalModalOpen}
        onClose={() => setIsReversalModalOpen(false)}
        title="Authorized Transaction Reversal"
        description="Creates balanced compensating double-entry ledger postings that debit original recipient and credit original sender."
      >
        <div className="space-y-4">
          <Input
            label="Original Transaction Reference"
            placeholder="e.g. TX-4A8E9B12C3D5E7F0"
            value={reverseTxRef}
            onChange={(e) => setReverseTxRef(e.target.value)}
          />

          <Input
            label="Reversal Reason (Mandatory)"
            value={reverseReason}
            onChange={(e) => setReverseReason(e.target.value)}
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button
              variant="outline"
              onClick={() => setIsReversalModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              variant="danger"
              disabled={!reverseTxRef || !reverseReason}
              isLoading={reverseMutation.isPending}
              onClick={() =>
                reverseMutation.mutate({
                  ref: reverseTxRef,
                  data: { reason: reverseReason },
                })
              }
            >
              Authorize Reversal
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
