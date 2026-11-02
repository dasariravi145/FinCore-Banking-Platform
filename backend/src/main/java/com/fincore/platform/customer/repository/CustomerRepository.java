package com.fincore.platform.customer.repository;

import com.fincore.platform.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {
    Optional<Customer> findByUserId(UUID userId);
    Optional<Customer> findByCustomerNumber(String customerNumber);
    boolean existsByUserId(UUID userId);
    boolean existsByCustomerNumber(String customerNumber);
}

