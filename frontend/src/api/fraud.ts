import { apiClient } from './client';
import { FraudAssessment, FraudDecision, FraudAssessmentStatus, PageResponse } from '../types';

export interface FraudReviewPayload {
  approved: boolean;
  notes?: string;
}

export const fraudApi = {
  getAssessments: async (
    page = 0,
    size = 20,
    decision?: FraudDecision,
    status?: FraudAssessmentStatus
  ): Promise<PageResponse<FraudAssessment>> => {
    const { data } = await apiClient.get<PageResponse<FraudAssessment>>('/api/v1/admin/fraud/assessments', {
      params: { page, size, decision, status },
    });
    return data;
  },

  reviewAssessment: async (
    id: string,
    payload: FraudReviewPayload
  ): Promise<FraudAssessment> => {
    const { data } = await apiClient.post<FraudAssessment>(
      `/api/v1/admin/fraud/assessments/${id}/review`,
      payload
    );
    return data;
  },
};

