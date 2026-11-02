// TypeScript definitions aligning 1:1 with FinCore Spring Boot models

export type RoleType = 'ROLE_CUSTOMER' | 'ROLE_ADMIN' | 'ROLE_AUDITOR';

export type UserStatus = 'ACTIVE' | 'LOCKED' | 'SUSPENDED';

export interface UserProfile {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  phoneNumber: string;
  status: UserStatus;
  roles: RoleType[];
  permissions: string[];
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken?: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export type CustomerStatus = 'ACTIVE' | 'SUSPENDED' | 'CLOSED';

export interface CustomerProfile {
  id: string;
  userId: string;
  customerNumber: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  country: string;
  nationalId: string;
  status: CustomerStatus;
  createdAt: string;
  updatedAt?: string;
}

export type AccountType = 
  | 'SAVINGS' 
  | 'CURRENT' 
  | 'LOAN_PORTFOLIO' 
  | 'CENTRAL_SETTLEMENT' 
  | 'INTEREST_INCOME' 
  | 'SYSTEM_RESERVE';

export type AccountCategory = 'ASSET' | 'LIABILITY' | 'EQUITY' | 'REVENUE' | 'EXPENSE';

export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface BankAccount {
  id: string;
  accountNumber: string;
  accountType: AccountType;
  accountCategory: AccountCategory;
  currency: string;
  balance: number;
  status: AccountStatus;
  createdAt: string;
}

export interface AccountBalanceResponse {
  accountNumber: string;
  currency: string;
  availableBalance: number;
  status: AccountStatus;
  asOf: string;
}

export interface ReconciliationResult {
  accountNumber: string;
  accountBalance: number;
  ledgerBalance: number;
  reconciled: boolean;
  difference: number;
}

export interface TransferReceipt {
  transactionReference: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  type: string;
  status: string;
  timestamp: string;
  description: string;
  idempotencyKey: string;
}

export interface TransactionSummary {
  id: string;
  transactionReference: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  currency: string;
  type: string;
  status: string;
  timestamp: string;
  description: string;
  entryType?: 'DEBIT' | 'CREDIT';
}

export type LoanType = 'PERSONAL' | 'HOME' | 'AUTO' | 'BUSINESS';

export type LoanStatus = 
  | 'APPLIED' 
  | 'APPROVED' 
  | 'REJECTED' 
  | 'ACTIVE' 
  | 'CLOSED' 
  | 'DEFAULTED';

export type ScheduleStatus = 'SCHEDULED' | 'PAID' | 'OVERDUE';

export interface LoanScheduleItem {
  id?: string;
  installmentNumber: number;
  dueDate: string;
  principalDue: number;
  interestDue: number;
  totalEmi: number;
  remainingBalance: number;
  status: ScheduleStatus;
  paidAt?: string;
}

export interface LoanResponse {
  id: string;
  loanNumber: string;
  customerNumber: string;
  disbursementAccountNumber: string;
  loanType: LoanType;
  principalAmount: number;
  annualInterestRate: number;
  termMonths: number;
  monthlyEmi: number;
  totalInterest: number;
  totalPayable: number;
  outstandingPrincipal: number;
  outstandingInterest: number;
  status: LoanStatus;
  appliedAt: string;
  approvedAt?: string;
  approvedBy?: string;
  disbursedAt?: string;
  rejectionReason?: string;
  notes?: string;
  schedules?: LoanScheduleItem[];
}

export interface EmiCalculationResponse {
  principal: number;
  annualInterestRate: number;
  termMonths: number;
  monthlyEmi: number;
  totalInterest: number;
  totalPayable: number;
}

export interface LoanRepaymentReceipt {
  repaymentReference: string;
  loanNumber: string;
  transactionReference: string;
  amountPaid: number;
  principalPaid: number;
  interestPaid: number;
  remainingPrincipal: number;
  loanStatus: LoanStatus;
  timestamp: string;
}

export interface NotificationItem {
  id: string;
  userId: string;
  type: string;
  title: string;
  message: string;
  isRead: boolean;
  createdAt: string;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

export type FraudDecision = 'ALLOW' | 'REVIEW' | 'BLOCK';
export type RiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type FraudAssessmentStatus = 'AUTO_APPROVED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED';

export interface FraudAssessment {
  id: string;
  transactionReference: string;
  sourceAccountNumber: string;
  destinationAccountNumber: string;
  amount: number;
  riskScore: number;
  riskLevel: RiskLevel;
  decision: FraudDecision;
  status: FraudAssessmentStatus;
  triggeredRules: string[];
  notes?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  createdAt: string;
}

export interface SecurityAuditEvent {
  id: string;
  eventType: string;
  principal: string;
  status: string;
  ipAddress: string;
  userAgent?: string;
  details?: string;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  correlationId?: string;
  validationErrors?: Record<string, string>;
}

