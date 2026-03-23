package com.gatepay.walletservice.repository;

import com.gatepay.walletservice.enums.TransactionSource;
import com.gatepay.walletservice.enums.TransactionStatus;
import com.gatepay.walletservice.enums.TransactionType;
import com.gatepay.walletservice.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long>,
        JpaSpecificationExecutor<WalletTransaction> {

    Optional<WalletTransaction> findByReference(String reference);

    boolean existsByReference(String reference);

    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(
            Long walletId,
            Pageable pageable
    );

    Page<WalletTransaction> findByWalletIdAndTypeOrderByCreatedAtDesc(
            Long walletId,
            TransactionType type,
            Pageable pageable
    );

    Page<WalletTransaction> findByWalletIdAndSourceOrderByCreatedAtDesc(
            Long walletId,
            TransactionSource source,
            Pageable pageable
    );

    Page<WalletTransaction> findByWalletIdAndStatusOrderByCreatedAtDesc(
            Long walletId,
            TransactionStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
            AND t.createdAt BETWEEN :from AND :to
            ORDER BY t.createdAt DESC
            """)
    Page<WalletTransaction> findByWalletIdAndDateRange(
            @Param("walletId") Long walletId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            SELECT t FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
            AND (:type IS NULL OR t.type = :type)
            AND (:source IS NULL OR t.source = :source)
            AND (:status IS NULL OR t.status = :status)
            AND (:from IS NULL OR t.createdAt >= :from)
            AND (:to IS NULL OR t.createdAt <= :to)
            ORDER BY t.createdAt DESC
            """)
    Page<WalletTransaction> findByFilters(
            @Param("walletId") Long walletId,
            @Param("type") TransactionType type,
            @Param("source") TransactionSource source,
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}