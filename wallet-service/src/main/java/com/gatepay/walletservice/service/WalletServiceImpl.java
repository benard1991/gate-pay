package com.gatepay.walletservice.service;



import com.gatepay.walletservice.dto.CreateWalletRequest;
import com.gatepay.walletservice.dto.WalletResponse;
import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.exception.WalletAlreadyExistsException;
import com.gatepay.walletservice.exception.WalletNotFoundException;
import com.gatepay.walletservice.exception.WalletOperationException;
import com.gatepay.walletservice.model.Wallet;
import com.gatepay.walletservice.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final WalletRepository walletRepository;
    private final WalletEventService walletEventService;

    @Override
    @Transactional
    public WalletResponse createWallet(CreateWalletRequest request) {
        if (walletRepository.existsByUserId(request.getUserId())) {
            throw new WalletAlreadyExistsException(
                    "Wallet already exists for user: " + request.getUserId()
            );
        }

        Wallet wallet = Wallet.builder()
                .userId(request.getUserId())
                .currency(request.getCurrency() != null ? request.getCurrency() : "NGN")
                .status(WalletStatus.ACTIVE)
                .balance(BigDecimal.ZERO)        // ← add this
                .ledgerBalance(BigDecimal.ZERO)  // ← add this
                .build();

        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet created for userId: {}", request.getUserId());

        walletEventService.publishWalletCreatedEvent(saved.getUserId(), saved.getCurrency());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWalletByUserId(Long userId) {
        return mapToResponse(findActiveWallet(userId));
    }

    @Override
    @Transactional(readOnly = true)
    public WalletResponse getWalletById(Long walletId) {
        Wallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found with id: " + walletId
                ));
        return mapToResponse(wallet);
    }

    @Override
    @Transactional
    public WalletResponse suspendWallet(Long userId) {
        Wallet wallet = findActiveWallet(userId);
        wallet.setStatus(WalletStatus.SUSPENDED);
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet suspended for userId: {}", userId);
        walletEventService.publishWalletSuspendedEvent(userId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WalletResponse closeWallet(Long userId) {
        Wallet wallet = findActiveWallet(userId);
        wallet.setStatus(WalletStatus.CLOSED);
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet closed for userId: {}", userId);
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public WalletResponse reactivateWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Wallet not found for user: " + userId
                ));

        if (wallet.getStatus() == WalletStatus.ACTIVE) {
            throw new WalletOperationException("Wallet is already active");
        }
        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletOperationException("Closed wallet cannot be reactivated");
        }

        wallet.setStatus(WalletStatus.ACTIVE);
        Wallet saved = walletRepository.save(wallet);
        log.info("Wallet reactivated for userId: {}", userId);
        return mapToResponse(saved);
    }

    @Override
    public boolean walletExists(Long userId) {
        return walletRepository.existsByUserId(userId);
    }

    private Wallet findActiveWallet(Long userId) {
        return walletRepository.findByUserIdAndStatus(userId, WalletStatus.ACTIVE)
                .orElseThrow(() -> new WalletNotFoundException(
                        "Active wallet not found for user: " + userId
                ));
    }

    private WalletResponse mapToResponse(Wallet wallet) {
        return WalletResponse.builder()
                .id(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .ledgerBalance(wallet.getLedgerBalance())
                .currency(wallet.getCurrency())
                .status(wallet.getStatus())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }
}