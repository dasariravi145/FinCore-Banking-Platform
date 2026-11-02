import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { transferApi, SimulatedFundingPayload, ReversalPayload } from '../../api/transfers';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Modal } from '../../components/common/Modal';
import { ConfirmationModal } from '../../components/common/ConfirmationModal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { TransactionSummary } from '../../types';
import { RotateCcw, PlusCircle, Receipt } from 'lucide-react';

export const AdminTransfersPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();
  const [page, setPage] = useState(0);
  const size = 20;

  // Modals state
  const [isFundingModalOpen, setIsFundingModalOpen] = useState(false);
  const [fundDestAccount, setFundDestAccount] = useState('');
  const [fundAmount, setFundAmount] = useState<number>(50000);
  const [fundDesc, setFundDesc] = useState('Initial synthetic test capital');

  const [selectedTxForReversal, setSelectedTxForReversal] = useState<TransactionSummary | null>(null);
  const [reversalReason, setReversalReason] = useState('Customer dispute resolved - chargeback approved');

  const { data: txPage, isLoading } = useQuery({
    queryKey: ['admin', 'transactions', page, size],
    queryFn: () => transferApi.getAllTransactions(page, size),
  });

  // Funding Mutation
  const fundMutation = useMutation({
    mutationFn: (data: SimulatedFundingPayload) => transferApi.fundAccountSimulated(data),
    onSuccess: (receipt) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'transactions'] });
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
      queryClient.invalidateQueries({ queryKey: ['admin', 'transactions'] });
      toastSuccess('Transaction Reversed', `Reversal Ref: ${receipt.transactionReference}`);
      setSelectedTxForReversal(null);
    },
    onError: (err) => {
      toastError('Reversal Failed', getErrorMessage(err));
    },
  });

  const transactions = txPage?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Platform Financial Transactions</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Monitor real-time ledger journals, execute simulated reserve funding, and reverse disputed transactions
          </p>
        </div>

        <div className="flex gap-3">
          <Button
            variant="secondary"
            leftIcon={<PlusCircle className="w-4 h-4" />}
            onClick={() => setIsFundingModalOpen(true)}
          >
            Fund Synthetic Account
          </Button>
        </div>
      </div>

      <Card title="Global Transaction Statement Ledger">
        {isLoading ? (
          <TableSkeleton rows={8} cols={7} />
        ) : transactions.length === 0 ? (
          <EmptyState
            icon={<Receipt className="w-8 h-8 text-slate-400" />}
            title="No Transactions Found"
            description="The platform journal does not contain any posted transactions yet."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Timestamp</th>
                  <th className="py-3 px-4 font-semibold">Reference</th>
                  <th className="py-3 px-4 font-semibold">Source Account</th>
                  <th className="py-3 px-4 font-semibold">Destination Account</th>
                  <th className="py-3 px-4 font-semibold">Description</th>
                  <th className="py-3 px-4 font-semibold text-right">Amount</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                  <th className="py-3 px-4 font-semibold text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {transactions.map((tx) => (
                  <tr key={tx.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono text-slate-600">
                      {formatDateTime(tx.timestamp)}
                    </td>
                    <td className="py-3 px-4 font-mono font-medium text-slate-900">
                      {tx.transactionReference}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-700">
                      {tx.sourceAccountNumber}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-700">
                      {tx.destinationAccountNumber}
                    </td>
                    <td className="py-3 px-4 text-slate-600 max-w-xs truncate">
                      {tx.description || '—'}
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-slate-900">
                      {formatCurrency(tx.amount, tx.currency)}
                    </td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={tx.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-center">
                      {tx.status === 'COMPLETED' && (
                        <Button
                          size="sm"
                          variant="outline"
                          className="text-rose-700 hover:bg-rose-50 border-rose-200"
                          leftIcon={<RotateCcw className="w-3 h-3 text-rose-600" />}
                          onClick={() => setSelectedTxForReversal(tx)}
                        >
                          Reverse
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {txPage && txPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {txPage.number + 1} of {txPage.totalPages} ({txPage.totalElements} records)
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

      {/* Simulated Funding Modal */}
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
            <Button variant="outline" onClick={() => setIsFundingModalOpen(false)}>
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

      {/* Reversal Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!selectedTxForReversal}
        onClose={() => setSelectedTxForReversal(null)}
        onConfirm={() => {
          if (selectedTxForReversal) {
            reverseMutation.mutate({
              ref: selectedTxForReversal.transactionReference,
              data: { reason: reversalReason },
            });
          }
        }}
        title="Confirm Transaction Reversal"
        description={`Are you sure you want to reverse transaction ${selectedTxForReversal?.transactionReference}? Compensating ledger entries will debit ${selectedTxForReversal?.destinationAccountNumber} and credit ${selectedTxForReversal?.sourceAccountNumber}.`}
        confirmLabel="Confirm Reversal"
        variant="danger"
        isLoading={reverseMutation.isPending}
      >
        <div className="space-y-3">
          <Input
            label="Mandatory Reversal Audit Reason"
            value={reversalReason}
            onChange={(e) => setReversalReason(e.target.value)}
          />
        </div>
      </ConfirmationModal>
    </div>
  );
};
