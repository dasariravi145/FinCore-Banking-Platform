package com.fincore.platform.loan.repository;

import com.fincore.platform.loan.entity.Loan;
import com.fincore.platform.loan.entity.LoanStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findByLoanNumber(String loanNumber);

    List<Loan> findByCustomer_Id(UUID customerId);

    Page<Loan> findByCustomer_IdOrderByAppliedAtDesc(UUID customerId, Pageable pageable);

    Page<Loan> findByStatusOrderByAppliedAtDesc(LoanStatus status, Pageable pageable);

    boolean existsByLoanNumber(String loanNumber);
}

