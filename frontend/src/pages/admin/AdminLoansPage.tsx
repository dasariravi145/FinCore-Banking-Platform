import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { loanApi, ReviewLoanPayload } from '../../api/loans';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Modal } from '../../components/common/Modal';
import { ConfirmationModal } from '../../components/common/ConfirmationModal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { TableSkeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { LoanResponse, LoanStatus } from '../../types';
import { Landmark, Check, X, DollarSign } from 'lucide-react';

export const AdminLoansPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();
  const [page, setPage] = useState(0);
  const size = 15;
  const [selectedStatus, setSelectedStatus] = useState<LoanStatus | ''>('');

  // Review modal state
  const [reviewLoanTarget, setReviewLoanTarget] = useState<LoanResponse | null>(null);
  const [reviewApproved, setReviewApproved] = useState(true);
  const [reviewNotes, setReviewNotes] = useState('');

  // Disburse modal state
  const [disburseLoanTarget, setDisburseLoanTarget] = useState<LoanResponse | null>(null);

  const { data: loansPage, isLoading } = useQuery({
    queryKey: ['admin', 'loans', page, size, selectedStatus],
    queryFn: () => loanApi.getAllLoans(page, size, selectedStatus || undefined),
  });

  const reviewMutation = useMutation({
    mutationFn: ({ loanNumber, payload }: { loanNumber: string; payload: ReviewLoanPayload }) =>
      loanApi.reviewLoan(loanNumber, payload),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'loans'] });
      toastSuccess(
        'Loan Reviewed',
        `Loan ${updated.loanNumber} is now ${updated.status}.`
      );
      setReviewLoanTarget(null);
      setReviewNotes('');
    },
    onError: (err) => {
      toastError('Review Failed', getErrorMessage(err));
    },
  });

  const disburseMutation = useMutation({
    mutationFn: (loanNumber: string) => loanApi.disburseLoan(loanNumber),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'loans'] });
      toastSuccess(
        'Loan Disbursed',
        `Loan ${updated.loanNumber} disbursed to account ${updated.disbursementAccountNumber}.`
      );
      setDisburseLoanTarget(null);
    },
    onError: (err) => {
      toastError('Disbursement Failed', getErrorMessage(err));
    },
  });

  const loans = loansPage?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Loan Portfolio Management</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Review loan applications, approve/reject credit facilities, and disburse approved funds
          </p>
        </div>

        {/* Status Filter */}
        <div className="w-48">
          <Select
            options={[
              { value: '', label: 'All Loan Statuses' },
              { value: 'APPLIED', label: 'APPLIED (Pending Review)' },
              { value: 'APPROVED', label: 'APPROVED (Pending Disbursement)' },
              { value: 'ACTIVE', label: 'ACTIVE (Disbursed)' },
              { value: 'CLOSED', label: 'CLOSED (Fully Repaid)' },
              { value: 'REJECTED', label: 'REJECTED' },
            ]}
            value={selectedStatus}
            onChange={(e) => {
              setSelectedStatus(e.target.value as LoanStatus | '');
              setPage(0);
            }}
          />
        </div>
      </div>

      <Card title="Institutional Loans Register">
        {isLoading ? (
          <TableSkeleton rows={6} cols={7} />
        ) : loans.length === 0 ? (
          <EmptyState
            icon={<Landmark className="w-8 h-8 text-slate-400" />}
            title="No Loans Found"
            description="No loan records matching the active filter criteria."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Loan Number</th>
                  <th className="py-3 px-4 font-semibold">Type</th>
                  <th className="py-3 px-4 font-semibold">Disbursement Acc</th>
                  <th className="py-3 px-4 font-semibold text-right">Principal</th>
                  <th className="py-3 px-4 font-semibold text-right">Monthly EMI</th>
                  <th className="py-3 px-4 font-semibold">Applied Date</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                  <th className="py-3 px-4 font-semibold text-center">Admin Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {loans.map((loan) => (
                  <tr key={loan.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono font-bold text-slate-900">
                      {loan.loanNumber}
                    </td>
                    <td className="py-3 px-4 font-semibold text-slate-800">
                      {loan.loanType}
                    </td>
                    <td className="py-3 px-4 font-mono text-slate-600">
                      {loan.disbursementAccountNumber}
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-slate-900">
                      {formatCurrency(loan.principalAmount)}
                    </td>
                    <td className="py-3 px-4 text-right font-mono font-semibold text-royal-600">
                      {formatCurrency(loan.monthlyEmi)}
                    </td>
                    <td className="py-3 px-4 text-slate-500">{formatDate(loan.appliedAt)}</td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={loan.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-center">
                      <div className="flex items-center justify-center gap-1.5">
                        {loan.status === 'APPLIED' && (
                          <Button
                            size="sm"
                            variant="secondary"
                            onClick={() => {
                              setReviewLoanTarget(loan);
                              setReviewApproved(true);
                              setReviewNotes('Approved based on credit evaluation');
                            }}
                          >
                            Review App
                          </Button>
                        )}
                        {loan.status === 'APPROVED' && (
                          <Button
                            size="sm"
                            variant="primary"
                            className="bg-emerald-600 hover:bg-emerald-700"
                            leftIcon={<DollarSign className="w-3.5 h-3.5" />}
                            onClick={() => setDisburseLoanTarget(loan)}
                          >
                            Disburse Funds
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {loansPage && loansPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {loansPage.number + 1} of {loansPage.totalPages} ({loansPage.totalElements} records)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={loansPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={loansPage.last}
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

      {/* Review Modal */}
      <Modal
        isOpen={!!reviewLoanTarget}
        onClose={() => setReviewLoanTarget(null)}
        title={`Review Application ${reviewLoanTarget?.loanNumber}`}
        description={`Principal: ${formatCurrency(reviewLoanTarget?.principalAmount)} at ${reviewLoanTarget?.annualInterestRate}% for ${reviewLoanTarget?.termMonths} months.`}
      >
        <div className="space-y-4">
          <div className="flex gap-4">
            <button
              type="button"
              onClick={() => setReviewApproved(true)}
              className={`flex-1 p-3 rounded-xl border flex items-center justify-center gap-2 font-semibold text-xs transition ${
                reviewApproved
                  ? 'bg-emerald-50 border-emerald-500 text-emerald-800'
                  : 'border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <Check className="w-4 h-4 text-emerald-600" />
              Approve Application
            </button>
            <button
              type="button"
              onClick={() => setReviewApproved(false)}
              className={`flex-1 p-3 rounded-xl border flex items-center justify-center gap-2 font-semibold text-xs transition ${
                !reviewApproved
                  ? 'bg-rose-50 border-rose-500 text-rose-800'
                  : 'border-slate-200 text-slate-600 hover:bg-slate-50'
              }`}
            >
              <X className="w-4 h-4 text-rose-600" />
              Reject Application
            </button>
          </div>

          <Input
            label="Review Notes / Reason"
            value={reviewNotes}
            onChange={(e) => setReviewNotes(e.target.value)}
            helperText="Recorded permanently with approval audit trail"
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button variant="outline" onClick={() => setReviewLoanTarget(null)}>
              Cancel
            </Button>
            <Button
              variant={reviewApproved ? 'primary' : 'danger'}
              isLoading={reviewMutation.isPending}
              onClick={() => {
                if (reviewLoanTarget) {
                  reviewMutation.mutate({
                    loanNumber: reviewLoanTarget.loanNumber,
                    payload: {
                      approved: reviewApproved,
                      notes: reviewNotes,
                    },
                  });
                }
              }}
            >
              Submit Review Decision
            </Button>
          </div>
        </div>
      </Modal>

      {/* Disbursement Confirmation Modal */}
      <ConfirmationModal
        isOpen={!!disburseLoanTarget}
        onClose={() => setDisburseLoanTarget(null)}
        onConfirm={() => {
          if (disburseLoanTarget) {
            disburseMutation.mutate(disburseLoanTarget.loanNumber);
          }
        }}
        title="Authorize Loan Disbursement"
        description={`Disburse ${formatCurrency(disburseLoanTarget?.principalAmount)} directly to borrower account ${disburseLoanTarget?.disbursementAccountNumber}.`}
        confirmLabel="Post Disbursement Journal"
        isLoading={disburseMutation.isPending}
      >
        <div className="p-3 bg-slate-50 border border-slate-100 rounded-lg text-xs text-slate-600 space-y-1">
          <p>• Debits Loan Portfolio Asset (ACC-LOAN-PORTFOLIO-001)</p>
          <p>• Credits Borrower Deposit Account</p>
          <p>• Activates the monthly installment repayment schedule</p>
        </div>
      </ConfirmationModal>
    </div>
  );
};
