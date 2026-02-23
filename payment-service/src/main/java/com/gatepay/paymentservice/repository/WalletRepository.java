package com.gatepay.paymentservice.repository;

import com.gatepay.paymentservice.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

//    Optional<Wallet> findByUserId(Long userId);
//
//    Optional<Wallet> findByEmail(String email);
}
