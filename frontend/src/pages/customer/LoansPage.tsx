import React, { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { loanApi, ApplyLoanPayload, CalculateEmiPayload } from '../../api/loans';
import { accountApi } from '../../api/accounts';
import { useToast } from '../../context/ToastContext';
import { getErrorMessage } from '../../utils/errors';
import { Card } from '../../components/common/Card';
import { Button } from '../../components/common/Button';
import { Input } from '../../components/common/Input';
import { Select } from '../../components/common/Select';
import { Modal } from '../../components/common/Modal';
import { StatusBadge } from '../../components/common/StatusBadge';
import { Skeleton } from '../../components/common/Skeleton';
import { EmptyState } from '../../components/common/EmptyState';
import { formatCurrency, formatDate } from '../../utils/formatters';
import { EmiCalculationResponse } from '../../types';
import {
  Landmark,
  Calculator,
  PlusCircle,
  ArrowUpRight,
  ShieldCheck,
} from 'lucide-react';

const emiCalcSchema = z.object({
  principal: z.number().positive('Principal must be greater than 0'),
  annualInterestRate: z.number().min(1, 'Rate must be at least 1%').max(36, 'Max rate is 36%'),
  termMonths: z.number().int().min(3, 'Min term is 3 months').max(120, 'Max term is 120 months'),
});

const applyLoanSchema = z.object({
  loanType: z.enum(['PERSONAL', 'HOME', 'AUTO', 'BUSINESS']),
  principal: z.number().positive('Principal must be greater than 0').max(5000000, 'Max loan is ₹5,000,000.00'),
  annualInterestRate: z.number().min(1).max(36),
  termMonths: z.number().int().min(3).max(120),
  disbursementAccountNumber: z.string().min(1, 'Please select a disbursement account'),
});

type EmiCalcFormData = z.infer<typeof emiCalcSchema>;
type ApplyLoanFormData = z.infer<typeof applyLoanSchema>;

export const LoansPage: React.FC = () => {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { error: toastError, success: toastSuccess } = useToast();

  const [isApplyModalOpen, setIsApplyModalOpen] = useState(false);
  const [calculatedQuote, setCalculatedQuote] = useState<EmiCalculationResponse | null>(null);

  // 1. Fetch Customer Loans
  const { data: loansPage, isLoading: isLoansLoading } = useQuery({
    queryKey: ['loans', 'my-loans', 0, 20],
    queryFn: () => loanApi.getMyLoans(0, 20),
  });

  // 2. Fetch Customer Accounts (for disbursement selection)
  const { data: accounts = [] } = useQuery({
    queryKey: ['accounts', 'my-accounts'],
    queryFn: () => accountApi.getMyAccounts(),
  });

  // EMI Calculator Form
  const {
    register: registerCalc,
    handleSubmit: handleSubmitCalc,
    formState: { errors: calcErrors },
  } = useForm<EmiCalcFormData>({
    resolver: zodResolver(emiCalcSchema),
    defaultValues: {
      principal: 100000,
      annualInterestRate: 12.0,
      termMonths: 12,
    },
  });

  // Loan Application Form
  const {
    register: registerApply,
    handleSubmit: handleSubmitApply,
    reset: resetApply,
    formState: { errors: applyErrors },
  } = useForm<ApplyLoanFormData>({
    resolver: zodResolver(applyLoanSchema),
    defaultValues: {
      loanType: 'PERSONAL',
      principal: 100000,
      annualInterestRate: 12.0,
      termMonths: 12,
      disbursementAccountNumber: accounts[0]?.accountNumber || '',
    },
  });

  // EMI calculation mutation
  const calcMutation = useMutation({
    mutationFn: (data: CalculateEmiPayload) => loanApi.calculateEmi(data),
    onSuccess: (data) => {
      setCalculatedQuote(data);
    },
    onError: (err) => {
      toastError('Calculation Failed', getErrorMessage(err));
    },
  });

  // Loan application mutation
  const applyMutation = useMutation({
    mutationFn: (data: ApplyLoanPayload) => loanApi.applyForLoan(data),
    onSuccess: (newLoan) => {
      queryClient.invalidateQueries({ queryKey: ['loans'] });
      toastSuccess('Loan Application Submitted', `Application ${newLoan.loanNumber} created.`);
      setIsApplyModalOpen(false);
      resetApply();
      navigate(`/loans/${newLoan.loanNumber}`);
    },
    onError: (err) => {
      toastError('Loan Application Failed', getErrorMessage(err));
    },
  });

  const onCalculate = (data: EmiCalcFormData) => {
    calcMutation.mutate(data);
  };

  const onApply = (data: ApplyLoanFormData) => {
    applyMutation.mutate(data);
  };

  const loans = loansPage?.content || [];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">Loans & Amortization</h1>
          <p className="text-xs sm:text-sm text-slate-500 mt-0.5">
            Calculate reducing-balance installments, apply for synthetic loans, and track repayments
          </p>
        </div>
        <Button
          variant="primary"
          leftIcon={<PlusCircle className="w-4 h-4" />}
          onClick={() => setIsApplyModalOpen(true)}
        >
          Apply for a Loan
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left 2 Cols: Customer Loans Portfolio */}
        <div className="lg:col-span-2 space-y-4">
          <h3 className="text-base font-bold text-slate-900">My Loan Applications & Facilities</h3>

          {isLoansLoading ? (
            <div className="space-y-3">
              <Skeleton className="h-32 w-full" />
              <Skeleton className="h-32 w-full" />
            </div>
          ) : loans.length === 0 ? (
            <EmptyState
              icon={<Landmark className="w-8 h-8 text-slate-400" />}
              title="No Loans on File"
              description="You have not submitted any loan applications. Use the EMI calculator to model repayments or submit a simulated loan application."
              action={
                <Button
                  size="sm"
                  variant="secondary"
                  leftIcon={<PlusCircle className="w-3.5 h-3.5" />}
                  onClick={() => setIsApplyModalOpen(true)}
                >
                  Apply Now
                </Button>
              }
            />
          ) : (
            <div className="space-y-4">
              {loans.map((loan) => (
                <div
                  key={loan.id}
                  onClick={() => navigate(`/loans/${loan.loanNumber}`)}
                  className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:shadow-md hover:border-royal-300 transition cursor-pointer group"
                >
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-slate-100">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-mono font-bold text-slate-900 text-sm">
                          {loan.loanNumber}
                        </span>
                        <span className="text-xs font-semibold px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                          {loan.loanType}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-500 mt-0.5">
                        Applied on {formatDate(loan.appliedAt)}
                      </p>
                    </div>
                    <StatusBadge status={loan.status} />
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mt-4 text-xs">
                    <div>
                      <span className="text-slate-500">Principal:</span>
                      <p className="font-bold text-slate-900 mt-0.5">
                        {formatCurrency(loan.principalAmount)}
                      </p>
                    </div>
                    <div>
                      <span className="text-slate-500">Interest Rate:</span>
                      <p className="font-bold text-slate-900 mt-0.5">
                        {loan.annualInterestRate}% p.a.
                      </p>
                    </div>
                    <div>
                      <span className="text-slate-500">Monthly EMI:</span>
                      <p className="font-bold text-royal-600 mt-0.5">
                        {formatCurrency(loan.monthlyEmi)}
                      </p>
                    </div>
                    <div>
                      <span className="text-slate-500">Remaining Balance:</span>
                      <p className="font-bold text-slate-900 mt-0.5">
                        {formatCurrency(loan.outstandingPrincipal)}
                      </p>
                    </div>
                  </div>

                  <div className="mt-4 pt-3 border-t border-slate-100 flex items-center justify-between text-xs text-royal-600 font-semibold group-hover:text-royal-700">
                    <span>View full 1..N repayment schedule & make payments</span>
                    <ArrowUpRight className="w-3.5 h-3.5 transition group-hover:translate-x-0.5 group-hover:-translate-y-0.5" />
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Right Col: Interactive EMI Calculator */}
        <div>
          <Card
            title={
              <div className="flex items-center gap-2">
                <Calculator className="w-5 h-5 text-gold-600" />
                <span>EMI Quotation Calculator</span>
              </div>
            }
            subtitle="Standard reducing-balance amortization model"
          >
            <form onSubmit={handleSubmitCalc(onCalculate)} className="space-y-4">
              <Input
                label="Loan Amount (INR ₹)"
                type="number"
                step="1000"
                error={calcErrors.principal?.message}
                {...registerCalc('principal', { valueAsNumber: true })}
              />

              <Input
                label="Annual Interest Rate (%)"
                type="number"
                step="0.1"
                error={calcErrors.annualInterestRate?.message}
                {...registerCalc('annualInterestRate', { valueAsNumber: true })}
              />

              <Input
                label="Tenure (Months)"
                type="number"
                step="1"
                error={calcErrors.termMonths?.message}
                {...registerCalc('termMonths', { valueAsNumber: true })}
              />

              <Button
                type="submit"
                variant="primary"
                className="w-full"
                isLoading={calcMutation.isPending}
              >
                Calculate Monthly EMI
              </Button>
            </form>

            {/* Calculated Results */}
            {calculatedQuote && (
              <div className="mt-6 p-4 rounded-xl bg-gold-50/60 border border-gold-200/80 space-y-3">
                <p className="text-xs font-semibold text-gold-900 uppercase tracking-wide">
                  Amortization Summary Quote
                </p>

                <div className="space-y-2 text-xs">
                  <div className="flex justify-between items-baseline">
                    <span className="text-slate-600">Monthly EMI:</span>
                    <span className="text-lg font-bold text-slate-900 font-mono">
                      {formatCurrency(calculatedQuote.monthlyEmi)}
                    </span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-600">Total Interest Payable:</span>
                    <span className="font-semibold text-gold-700">
                      {formatCurrency(calculatedQuote.totalInterest)}
                    </span>
                  </div>
                  <div className="flex justify-between pt-2 border-t border-gold-200">
                    <span className="text-slate-700 font-semibold">Total Amount Payable:</span>
                    <span className="font-bold text-slate-900">
                      {formatCurrency(calculatedQuote.totalPayable)}
                    </span>
                  </div>
                </div>

                <Button
                  size="sm"
                  variant="secondary"
                  className="w-full mt-2"
                  onClick={() => setIsApplyModalOpen(true)}
                >
                  Apply with this Quote
                </Button>
              </div>
            )}
          </Card>
        </div>
      </div>

      {/* Loan Application Modal */}
      <Modal
        isOpen={isApplyModalOpen}
        onClose={() => setIsApplyModalOpen(false)}
        title="Submit Loan Application"
        description="Apply for a new synthetic loan facility disbursed directly to your bank account upon administrative approval."
        maxWidth="lg"
      >
        <form onSubmit={handleSubmitApply(onApply)} className="space-y-4">
          <Select
            label="Loan Purpose / Category"
            options={[
              { value: 'PERSONAL', label: 'PERSONAL LOAN (Unsecured personal credit)' },
              { value: 'HOME', label: 'HOME MORTGAGE LOAN' },
              { value: 'AUTO', label: 'AUTOMOBILE PURCHASE LOAN' },
              { value: 'BUSINESS', label: 'COMMERCIAL WORKING CAPITAL LOAN' },
            ]}
            error={applyErrors.loanType?.message}
            {...registerApply('loanType')}
          />

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Principal Amount (₹)"
              type="number"
              step="1000"
              error={applyErrors.principal?.message}
              {...registerApply('principal', { valueAsNumber: true })}
            />

            <Input
              label="Annual Rate (%)"
              type="number"
              step="0.1"
              error={applyErrors.annualInterestRate?.message}
              {...registerApply('annualInterestRate', { valueAsNumber: true })}
            />
          </div>

          <div className="grid grid-cols-2 gap-3">
            <Input
              label="Tenure (Months)"
              type="number"
              step="1"
              error={applyErrors.termMonths?.message}
              {...registerApply('termMonths', { valueAsNumber: true })}
            />

            <Select
              label="Disbursement Account"
              options={[
                { value: '', label: '-- Select Linked Account --', disabled: true },
                ...accounts.map((a) => ({
                  value: a.accountNumber,
                  label: `${a.accountType} (${a.accountNumber})`,
                })),
              ]}
              error={applyErrors.disbursementAccountNumber?.message}
              {...registerApply('disbursementAccountNumber')}
            />
          </div>

          <div className="p-3 bg-slate-50 border border-slate-100 rounded-lg text-xs text-slate-600 space-y-1">
            <span className="font-semibold text-slate-800 flex items-center gap-1.5">
              <ShieldCheck className="w-4 h-4 text-emerald-600" />
              Banking Simulation Flow:
            </span>
            <p>1. Status will be recorded as `APPLIED` and 1..N installment schedules created.</p>
            <p>2. A bank administrator reviews and approves the application.</p>
            <p>3. Upon administrative disbursement, funds credit your account via double-entry journal.</p>
          </div>

          <div className="flex justify-end gap-3 pt-3 border-t border-slate-100">
            <Button
              type="button"
              variant="outline"
              onClick={() => setIsApplyModalOpen(false)}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              variant="primary"
              isLoading={applyMutation.isPending}
            >
              Submit Application
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
};
