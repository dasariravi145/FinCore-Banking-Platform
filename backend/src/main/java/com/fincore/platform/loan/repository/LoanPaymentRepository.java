package com.fincore.platform.loan.repository;

import com.fincore.platform.loan.entity.LoanPayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LoanPaymentRepository extends JpaRepository<LoanPayment, UUID> {

    List<LoanPayment> findByLoan_IdOrderByPaidAtDesc(UUID loanId);

    Page<LoanPayment> findByLoan_IdOrderByPaidAtDesc(UUID loanId, Pageable pageable);
}

