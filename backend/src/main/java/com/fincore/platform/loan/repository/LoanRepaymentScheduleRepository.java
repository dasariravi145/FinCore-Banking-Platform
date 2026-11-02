package com.fincore.platform.loan.repository;

import com.fincore.platform.loan.entity.LoanRepaymentSchedule;
import com.fincore.platform.loan.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepaymentScheduleRepository extends JpaRepository<LoanRepaymentSchedule, UUID> {

    List<LoanRepaymentSchedule> findByLoan_IdOrderByInstallmentNumberAsc(UUID loanId);

    Optional<LoanRepaymentSchedule> findFirstByLoan_IdAndStatusInOrderByInstallmentNumberAsc(
            UUID loanId, List<ScheduleStatus> statuses);

    List<LoanRepaymentSchedule> findByLoan_IdAndStatusOrderByInstallmentNumberAsc(UUID loanId, ScheduleStatus status);
}

