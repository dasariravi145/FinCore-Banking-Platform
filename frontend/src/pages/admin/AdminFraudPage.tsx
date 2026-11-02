import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fraudApi, FraudReviewPayload } from '../../api/fraud';
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
import { formatCurrency, formatDateTime } from '../../utils/formatters';
import { FraudAssessment, FraudDecision, FraudAssessmentStatus } from '../../types';
import { AlertOctagon, Check, X } from 'lucide-react';

export const AdminFraudPage: React.FC = () => {
  const queryClient = useQueryClient();
  const { success: toastSuccess, error: toastError } = useToast();
  const [page, setPage] = useState(0);
  const size = 15;
  const [selectedDecision, setSelectedDecision] = useState<FraudDecision | ''>('');
  const [selectedStatus, setSelectedStatus] = useState<FraudAssessmentStatus | ''>('');

  const [reviewTarget, setReviewTarget] = useState<FraudAssessment | null>(null);
  const [reviewApproved, setReviewApproved] = useState(true);
  const [reviewNotes, setReviewNotes] = useState('Customer confirmed transaction legitimacy via branch phone call');

  const { data: fraudPage, isLoading } = useQuery({
    queryKey: ['admin', 'fraud', page, size, selectedDecision, selectedStatus],
    queryFn: () =>
      fraudApi.getAssessments(
        page,
        size,
        selectedDecision || undefined,
        selectedStatus || undefined
      ),
  });

  const reviewMutation = useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: FraudReviewPayload }) =>
      fraudApi.reviewAssessment(id, payload),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'fraud'] });
      toastSuccess(
        'Assessment Reviewed',
        `Fraud evaluation for ${updated.transactionReference} marked ${updated.status}.`
      );
      setReviewTarget(null);
    },
    onError: (err) => {
      toastError('Review Failed', getErrorMessage(err));
    },
  });

  const assessments = fraudPage?.content || [];

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Fraud & Risk Compliance</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Real-time heuristic rule evaluations, risk scores, and institutional compliance decisioning
          </p>
        </div>

        {/* Filter Bar */}
        <div className="flex gap-2">
          <Select
            options={[
              { value: '', label: 'All Decisions' },
              { value: 'BLOCK', label: 'BLOCK (Risk Score >= 70)' },
              { value: 'REVIEW', label: 'REVIEW (Score 40-69)' },
              { value: 'ALLOW', label: 'ALLOW (Score < 40)' },
            ]}
            value={selectedDecision}
            onChange={(e) => {
              setSelectedDecision(e.target.value as FraudDecision | '');
              setPage(0);
            }}
          />
          <Select
            options={[
              { value: '', label: 'All Statuses' },
              { value: 'PENDING_REVIEW', label: 'PENDING REVIEW' },
              { value: 'APPROVED', label: 'APPROVED' },
              { value: 'REJECTED', label: 'REJECTED' },
              { value: 'AUTO_APPROVED', label: 'AUTO APPROVED' },
            ]}
            value={selectedStatus}
            onChange={(e) => {
              setSelectedStatus(e.target.value as FraudAssessmentStatus | '');
              setPage(0);
            }}
          />
        </div>
      </div>

      <Card title="Evaluated Transaction Assessments">
        {isLoading ? (
          <TableSkeleton rows={6} cols={7} />
        ) : assessments.length === 0 ? (
          <EmptyState
            icon={<AlertOctagon className="w-8 h-8 text-slate-400" />}
            title="No Fraud Assessments Found"
            description="No risk evaluations match the specified filters."
          />
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 text-slate-600 uppercase tracking-wider border-b border-slate-200">
                <tr>
                  <th className="py-3 px-4 font-semibold">Timestamp</th>
                  <th className="py-3 px-4 font-semibold">Tx Ref</th>
                  <th className="py-3 px-4 font-semibold">Accounts</th>
                  <th className="py-3 px-4 font-semibold text-right">Amount</th>
                  <th className="py-3 px-4 font-semibold text-center">Score</th>
                  <th className="py-3 px-4 font-semibold text-center">Decision</th>
                  <th className="py-3 px-4 font-semibold">Triggered Rules</th>
                  <th className="py-3 px-4 font-semibold text-center">Status</th>
                  <th className="py-3 px-4 font-semibold text-center">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {assessments.map((item) => (
                  <tr key={item.id} className="hover:bg-slate-50/60 transition">
                    <td className="py-3 px-4 font-mono text-slate-600">
                      {formatDateTime(item.createdAt)}
                    </td>
                    <td className="py-3 px-4 font-mono font-medium text-slate-900">
                      {item.transactionReference}
                    </td>
                    <td className="py-3 px-4 text-slate-700 font-mono text-[11px]">
                      <div>{item.sourceAccountNumber} &rarr;</div>
                      <div className="text-slate-500">{item.destinationAccountNumber}</div>
                    </td>
                    <td className="py-3 px-4 text-right font-bold text-slate-900">
                      {formatCurrency(item.amount)}
                    </td>
                    <td className="py-3 px-4 text-center font-bold">
                      <span
                        className={`inline-block px-2 py-0.5 rounded text-xs ${
                          item.riskScore >= 70
                            ? 'bg-rose-100 text-rose-800'
                            : item.riskScore >= 40
                            ? 'bg-amber-100 text-amber-800'
                            : 'bg-emerald-100 text-emerald-800'
                        }`}
                      >
                        {item.riskScore} / 100
                      </span>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={item.decision} size="sm" />
                    </td>
                    <td className="py-3 px-4 max-w-xs">
                      <div className="flex flex-wrap gap-1">
                        {item.triggeredRules && item.triggeredRules.length > 0 ? (
                          item.triggeredRules.map((r) => (
                            <span
                              key={r}
                              className="text-[10px] px-1.5 py-0.5 rounded bg-slate-100 text-slate-700 font-mono"
                            >
                              {r}
                            </span>
                          ))
                        ) : (
                          <span className="text-slate-400 text-[11px]">None (Normal)</span>
                        )}
                      </div>
                    </td>
                    <td className="py-3 px-4 text-center">
                      <StatusBadge status={item.status} size="sm" />
                    </td>
                    <td className="py-3 px-4 text-center">
                      {item.status === 'PENDING_REVIEW' && (
                        <Button
                          size="sm"
                          variant="secondary"
                          onClick={() => setReviewTarget(item)}
                        >
                          Review Alert
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>

            {fraudPage && fraudPage.totalPages > 1 && (
              <div className="flex items-center justify-between pt-4 mt-2 border-t border-slate-100 text-xs text-slate-500">
                <span>
                  Page {fraudPage.number + 1} of {fraudPage.totalPages} ({fraudPage.totalElements} records)
                </span>
                <div className="flex gap-2">
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={fraudPage.first}
                    onClick={() => setPage((p) => Math.max(0, p - 1))}
                  >
                    Previous
                  </Button>
                  <Button
                    size="sm"
                    variant="outline"
                    disabled={fraudPage.last}
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

      {/* Compliance Officer Decision Modal */}
      <Modal
        isOpen={!!reviewTarget}
        onClose={() => setReviewTarget(null)}
        title={`Fraud Assessment Review — ${reviewTarget?.transactionReference}`}
        description={`Risk Score: ${reviewTarget?.riskScore}/100. Amount: ${formatCurrency(reviewTarget?.amount)}`}
      >
        <div className="space-y-4">
          <div className="p-3 bg-slate-50 border border-slate-100 rounded-lg text-xs space-y-1">
            <span className="font-semibold text-slate-800">Triggered Heuristics:</span>
            {reviewTarget?.triggeredRules && reviewTarget.triggeredRules.length > 0 ? (
              <ul className="list-disc pl-4 text-slate-700">
                {reviewTarget.triggeredRules.map((r) => (
                  <li key={r}>{r}</li>
                ))}
              </ul>
            ) : (
              <p className="text-slate-500">None</p>
            )}
          </div>

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
              Approve (Clear Flag)
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
              Reject / Confirm Fraud
            </button>
          </div>

          <Input
            label="Compliance Officer Notes"
            value={reviewNotes}
            onChange={(e) => setReviewNotes(e.target.value)}
            helperText="Reason is recorded permanently in compliance audit trail"
          />

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button variant="outline" onClick={() => setReviewTarget(null)}>
              Cancel
            </Button>
            <Button
              variant={reviewApproved ? 'primary' : 'danger'}
              isLoading={reviewMutation.isPending}
              onClick={() => {
                if (reviewTarget) {
                  reviewMutation.mutate({
                    id: reviewTarget.id,
                    payload: {
                      approved: reviewApproved,
                      notes: reviewNotes,
                    },
                  });
                }
              }}
            >
              Submit Compliance Decision
            </Button>
          </div>
        </div>
      </Modal>
    </div>
  );
};
