package com.gatepay.paymentservice.repository;

import com.gatepay.paymentservice.dto.TransactionFilter;
import com.gatepay.paymentservice.model.Payment;
import com.gatepay.paymentservice.model.PaymentTransaction;
import com.gatepay.paymentservice.model.enums.TransactionStatus;
import com.gatepay.paymentservice.model.enums.TransactionType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT pt FROM PaymentTransaction pt WHERE pt.reference = :reference")
    Optional<PaymentTransaction> findByReferenceForUpdate(@Param("reference") String reference);

    Optional<PaymentTransaction> findByReference(String reference);

    Page<PaymentTransaction> findByCustomerEmail(String email, Pageable pageable);

    List<PaymentTransaction> findByStatus(TransactionStatus status);

    boolean existsByReference(String reference);


    Page<PaymentTransaction> findByStatus(TransactionStatus status, Pageable pageable);

    Page<PaymentTransaction> findAll(Pageable pageable);


    @Query("SELECT t FROM PaymentTransaction t WHERE t.userId = :userId")
    Page<PaymentTransaction> findTransactionsByUserId(
            @Param("userId") String userId,
            Pageable pageable
    );










}