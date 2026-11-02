import { apiClient } from './client';
import { 
  EmiCalculationResponse, 
  LoanResponse, 
  LoanRepaymentReceipt, 
  LoanType, 
  LoanStatus, 
  PageResponse 
} from '../types';

export interface CalculateEmiPayload {
  principal: number;
  annualInterestRate: number;
  termMonths: number;
}

export interface ApplyLoanPayload {
  loanType: LoanType;
  principal: number;
  annualInterestRate: number;
  termMonths: number;
  disbursementAccountNumber: string;
}

export interface RepayLoanPayload {
  sourceAccountNumber: string;
  amount: number;
}

export interface ReviewLoanPayload {
  approved: boolean;
  notes?: string;
}

export const loanApi = {
  calculateEmi: async (payload: CalculateEmiPayload): Promise<EmiCalculationResponse> => {
    const { data } = await apiClient.post<EmiCalculationResponse>('/api/v1/loans/calculate-emi', payload);
    return data;
  },

  applyForLoan: async (payload: ApplyLoanPayload): Promise<LoanResponse> => {
    const { data } = await apiClient.post<LoanResponse>('/api/v1/loans/apply', payload);
    return data;
  },

  getMyLoans: async (page = 0, size = 10): Promise<PageResponse<LoanResponse>> => {
    const { data } = await apiClient.get<PageResponse<LoanResponse>>('/api/v1/loans/my-loans', {
      params: { page, size },
    });
    return data;
  },

  getLoanDetails: async (loanNumber: string): Promise<LoanResponse> => {
    const { data } = await apiClient.get<LoanResponse>(`/api/v1/loans/${loanNumber}`);
    return data;
  },

  repayLoan: async (loanNumber: string, payload: RepayLoanPayload): Promise<LoanRepaymentReceipt> => {
    const { data } = await apiClient.post<LoanRepaymentReceipt>(`/api/v1/loans/${loanNumber}/repay`, payload);
    return data;
  },

  // Admin APIs
  getAllLoans: async (
    page = 0, 
    size = 20, 
    status?: LoanStatus
  ): Promise<PageResponse<LoanResponse>> => {
    const { data } = await apiClient.get<PageResponse<LoanResponse>>('/api/v1/admin/loans', {
      params: { page, size, status },
    });
    return data;
  },

  reviewLoan: async (loanNumber: string, payload: ReviewLoanPayload): Promise<LoanResponse> => {
    const { data } = await apiClient.post<LoanResponse>(`/api/v1/admin/loans/${loanNumber}/review`, payload);
    return data;
  },

  disburseLoan: async (loanNumber: string): Promise<LoanResponse> => {
    const { data } = await apiClient.post<LoanResponse>(`/api/v1/admin/loans/${loanNumber}/disburse`);
    return data;
  },
};

