package com.gatepay.walletservice.repository;

import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") Long userId);

    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId AND w.status = :status")
    Optional<Wallet> findByUserIdAndStatus(
            @Param("userId") Long userId,
            @Param("status") WalletStatus status
    );
}