package com.gatepay.walletservice.repository;

import com.gatepay.walletservice.enums.*;
import com.gatepay.walletservice.model.Wallet;
import com.gatepay.walletservice.model.WalletTransaction;
import com.gatepay.walletservice.service.BaseIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// All tests run inside a transaction that rolls back automatically — no manual cleanup needed.
// Each test builds its own wallet and transactions so there is no shared mutable state.
@Transactional
@DisplayName("WalletTransactionRepository")
class WalletTransactionRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired WalletTransactionRepository transactionRepository;
    @Autowired WalletRepository            walletRepository;
    @Autowired EntityManager               entityManager;


    // Persists a minimal wallet that transactions can be attached to.
    private Wallet persistWallet(Long userId) {
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("5000.00"))
                .ledgerBalance(new BigDecimal("5000.00"))
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .version(0L)
                .build();
        return walletRepository.saveAndFlush(wallet);
    }

    // Persists a single transaction attached to the given wallet.
    //
    // @CreationTimestamp always writes the current time at insert and ignores any
    // value we set on the entity. To make date-range tests deterministic we save
    // the transaction first and then overwrite created_at via a native SQL update.
    // The EntityManager cache is cleared afterward so subsequent queries read the
    // corrected timestamp from the database rather than the stale in-memory value.
    private WalletTransaction persistTransaction(Wallet wallet,
                                                 TransactionType type,
                                                 TransactionSource source,
                                                 TransactionStatus status,
                                                 LocalDateTime createdAt) {
        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .reference("WLT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase())
                .type(type)
                .source(source)
                .status(status)
                .amount(new BigDecimal("100.00"))
                .balanceBefore(new BigDecimal("1000.00"))
                .balanceAfter(new BigDecimal("1100.00"))
                .description("test transaction")
                .build();

        WalletTransaction saved = transactionRepository.saveAndFlush(tx);

        // Force the timestamp we actually want — @CreationTimestamp cannot be overridden any other way
        entityManager.createNativeQuery(
                        "UPDATE wallet_transactions SET created_at = :ts WHERE id = :id")
                .setParameter("ts", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();

        // Clear the first-level cache so the next read hits the DB and sees our updated timestamp
        entityManager.flush();
        entityManager.clear();

        return transactionRepository.findById(saved.getId()).orElseThrow();
    }

    // Convenience overload — uses a fixed recent timestamp for tests that don't care about ordering.
    private WalletTransaction persistTransaction(Wallet wallet,
                                                 TransactionType type,
                                                 TransactionSource source,
                                                 TransactionStatus status) {
        return persistTransaction(wallet, type, source, status, LocalDateTime.now());
    }

    private Pageable firstPage() {
        return PageRequest.of(0, 20, Sort.by("createdAt").descending());
    }

    // findByReference

    @Nested
    @DisplayName("findByReference()")
    class FindByReference {

        @Test
        @DisplayName("finds the transaction when the reference exists")
        void findsTransactionByReference() {
            Wallet wallet = persistWallet(1L);
            WalletTransaction tx = persistTransaction(wallet,
                    TransactionType.CREDIT, TransactionSource.TRANSFER, TransactionStatus.SUCCESS);

            Optional<WalletTransaction> result = transactionRepository.findByReference(tx.getReference());

            assertThat(result).isPresent();
            assertThat(result.get().getReference()).isEqualTo(tx.getReference());
            assertThat(result.get().getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(result.get().getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("returns empty when the reference does not exist")
        void returnsEmptyForUnknownReference() {
            Optional<WalletTransaction> result = transactionRepository.findByReference("GHOST-REF-001");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("does not return a different transaction with a similar reference")
        void doesNotReturnTransactionWithSimilarReference() {
            Wallet wallet = persistWallet(2L);
            persistTransaction(wallet,
                    TransactionType.DEBIT, TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            // searching for a prefix of the real reference — should not match
            Optional<WalletTransaction> result = transactionRepository.findByReference("WLT-");

            assertThat(result).isEmpty();
        }
    }

    // existsByReference


    @Nested
    @DisplayName("existsByReference()")
    class ExistsByReference {

        @Test
        @DisplayName("returns true when a transaction with the reference exists")
        void returnsTrueWhenReferenceExists() {
            Wallet wallet = persistWallet(3L);
            WalletTransaction tx = persistTransaction(wallet,
                    TransactionType.CREDIT, TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            assertThat(transactionRepository.existsByReference(tx.getReference())).isTrue();
        }

        @Test
        @DisplayName("returns false when no transaction has the given reference")
        void returnsFalseWhenReferenceDoesNotExist() {
            assertThat(transactionRepository.existsByReference("DOES-NOT-EXIST")).isFalse();
        }
    }

    // findByWalletIdOrderByCreatedAtDesc

    @Nested
    @DisplayName("findByWalletIdOrderByCreatedAtDesc()")
    class FindByWalletId {

        @Test
        @DisplayName("returns all transactions for the wallet ordered newest first")
        void returnsTransactionsOrderedByCreatedAtDescending() {
            Wallet wallet = persistWallet(4L);

            WalletTransaction older = persistTransaction(wallet,
                    TransactionType.CREDIT, TransactionSource.TRANSFER, TransactionStatus.SUCCESS,
                    LocalDateTime.now().minusDays(2));
            WalletTransaction newer = persistTransaction(wallet,
                    TransactionType.DEBIT, TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS,
                    LocalDateTime.now().minusDays(1));

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), firstPage());

            assertThat(page.getContent()).hasSize(2);
            // newest should come first
            assertThat(page.getContent().get(0).getId()).isEqualTo(newer.getId());
            assertThat(page.getContent().get(1).getId()).isEqualTo(older.getId());
        }

        @Test
        @DisplayName("returns an empty page when the wallet has no transactions")
        void returnsEmptyPageWhenNoTransactions() {
            Wallet wallet = persistWallet(5L);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), firstPage());

            assertThat(page.getContent()).isEmpty();
            assertThat(page.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("does not return transactions belonging to a different wallet")
        void doesNotReturnAnotherWalletsTransactions() {
            Wallet walletA = persistWallet(6L);
            Wallet walletB = persistWallet(7L);

            persistTransaction(walletA, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(walletB, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdOrderByCreatedAtDesc(walletA.getId(), firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getWallet().getId()).isEqualTo(walletA.getId());
        }

        @Test
        @DisplayName("respects page size and returns correct pagination metadata")
        void respectsPageSizeAndReturnsPaginationMetadata() {
            Wallet wallet = persistWallet(8L);

            // persist 5 transactions but request a page of 2
            for (int i = 0; i < 5; i++) {
                persistTransaction(wallet, TransactionType.CREDIT,
                        TransactionSource.TOPUP, TransactionStatus.SUCCESS);
            }

            Pageable page2 = PageRequest.of(0, 2, Sort.by("createdAt").descending());
            Page<WalletTransaction> result = transactionRepository
                    .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), page2);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getTotalElements()).isEqualTo(5);
            assertThat(result.getTotalPages()).isEqualTo(3);
            assertThat(result.isLast()).isFalse();
        }
    }

    // findByWalletIdAndTypeOrderByCreatedAtDesc

    @Nested
    @DisplayName("findByWalletIdAndTypeOrderByCreatedAtDesc()")
    class FindByWalletIdAndType {

        @Test
        @DisplayName("returns only credit transactions when type is CREDIT")
        void returnsOnlyCreditTransactions() {
            Wallet wallet = persistWallet(9L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndTypeOrderByCreatedAtDesc(
                            wallet.getId(), TransactionType.CREDIT, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getType()).isEqualTo(TransactionType.CREDIT);
        }

        @Test
        @DisplayName("returns only debit transactions when type is DEBIT")
        void returnsOnlyDebitTransactions() {
            Wallet wallet = persistWallet(10L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndTypeOrderByCreatedAtDesc(
                            wallet.getId(), TransactionType.DEBIT, firstPage());

            assertThat(page.getContent()).hasSize(2);
            page.getContent().forEach(t ->
                    assertThat(t.getType()).isEqualTo(TransactionType.DEBIT));
        }

        @Test
        @DisplayName("returns an empty page when no transactions match the type")
        void returnsEmptyWhenNoTransactionsMatchType() {
            Wallet wallet = persistWallet(11L);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndTypeOrderByCreatedAtDesc(
                            wallet.getId(), TransactionType.CREDIT, firstPage());

            assertThat(page.getContent()).isEmpty();
        }
    }


    // findByWalletIdAndSourceOrderByCreatedAtDesc

    @Nested
    @DisplayName("findByWalletIdAndSourceOrderByCreatedAtDesc()")
    class FindByWalletIdAndSource {

        @Test
        @DisplayName("returns only transactions matching the given source")
        void returnsOnlyTransactionsMatchingSource() {
            Wallet wallet = persistWallet(12L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndSourceOrderByCreatedAtDesc(
                            wallet.getId(), TransactionSource.TRANSFER, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getSource()).isEqualTo(TransactionSource.TRANSFER);
        }

        @Test
        @DisplayName("returns an empty page when no transactions match the source")
        void returnsEmptyWhenNoTransactionsMatchSource() {
            Wallet wallet = persistWallet(13L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndSourceOrderByCreatedAtDesc(
                            wallet.getId(), TransactionSource.REVERSAL, firstPage());

            assertThat(page.getContent()).isEmpty();
        }
    }

    // findByWalletIdAndStatusOrderByCreatedAtDesc

    @Nested
    @DisplayName("findByWalletIdAndStatusOrderByCreatedAtDesc()")
    class FindByWalletIdAndStatus {

        @Test
        @DisplayName("returns only successful transactions when status is SUCCESS")
        void returnsOnlySuccessfulTransactions() {
            Wallet wallet = persistWallet(14L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.FAILED);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndStatusOrderByCreatedAtDesc(
                            wallet.getId(), TransactionStatus.SUCCESS, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("returns only reversed transactions when status is REVERSED")
        void returnsOnlyReversedTransactions() {
            Wallet wallet = persistWallet(15L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.REVERSED);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndStatusOrderByCreatedAtDesc(
                            wallet.getId(), TransactionStatus.REVERSED, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.REVERSED);
        }

        @Test
        @DisplayName("returns an empty page when no transactions match the status")
        void returnsEmptyWhenNoTransactionsMatchStatus() {
            Wallet wallet = persistWallet(16L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository
                    .findByWalletIdAndStatusOrderByCreatedAtDesc(
                            wallet.getId(), TransactionStatus.PENDING, firstPage());

            assertThat(page.getContent()).isEmpty();
        }
    }

    // findByWalletIdAndDateRange

    @Nested
    @DisplayName("findByWalletIdAndDateRange()")
    class FindByWalletIdAndDateRange {

        @Test
        @DisplayName("returns transactions that fall within the given date range")
        void returnsTransactionsWithinDateRange() {
            Wallet wallet = persistWallet(17L);

            LocalDateTime base = LocalDateTime.now().minusDays(10);

            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS,
                    base.minusDays(5)); // before range — should be excluded
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS,
                    base);             // inside range — should be included
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS,
                    base.plusDays(3)); // inside range — should be included
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS,
                    base.plusDays(20)); // after range — should be excluded

            Page<WalletTransaction> page = transactionRepository.findByWalletIdAndDateRange(
                    wallet.getId(),
                    base.minusDays(1),  // from: one day before base
                    base.plusDays(10),  // to: 10 days after base
                    firstPage()
            );

            assertThat(page.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("returns an empty page when no transactions fall within the date range")
        void returnsEmptyWhenNoTransactionsInRange() {
            Wallet wallet = persistWallet(18L);

            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS,
                    LocalDateTime.now().minusDays(30));

            Page<WalletTransaction> page = transactionRepository.findByWalletIdAndDateRange(
                    wallet.getId(),
                    LocalDateTime.now().minusDays(5),
                    LocalDateTime.now(),
                    firstPage()
            );

            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("includes transactions on the exact boundary of the date range")
        void includesTransactionsOnRangeBoundary() {
            Wallet wallet = persistWallet(19L);
            LocalDateTime boundary = LocalDateTime.now().minusDays(5).withNano(0);

            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS, boundary);

            Page<WalletTransaction> page = transactionRepository.findByWalletIdAndDateRange(
                    wallet.getId(),
                    boundary,
                    boundary,
                    firstPage()
            );

            assertThat(page.getContent()).hasSize(1);
        }
    }

    // findByFilters — the composite query

    @Nested
    @DisplayName("findByFilters()")
    class FindByFilters {

        @Test
        @DisplayName("returns all transactions for the wallet when all filters are null")
        void returnsAllTransactionsWhenFiltersAreNull() {
            Wallet wallet = persistWallet(20L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.FAILED);

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), null, null, null, null, null, firstPage());

            assertThat(page.getContent()).hasSize(2);
        }

        @Test
        @DisplayName("filters down to only CREDIT transactions when type is provided")
        void filtersByTypeWhenProvided() {
            Wallet wallet = persistWallet(21L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), TransactionType.CREDIT, null, null, null, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getType()).isEqualTo(TransactionType.CREDIT);
        }

        @Test
        @DisplayName("filters down to only TRANSFER transactions when source is provided")
        void filtersBySourceWhenProvided() {
            Wallet wallet = persistWallet(22L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), null, TransactionSource.TRANSFER, null, null, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getSource()).isEqualTo(TransactionSource.TRANSFER);
        }

        @Test
        @DisplayName("filters down to only FAILED transactions when status is provided")
        void filtersByStatusWhenProvided() {
            Wallet wallet = persistWallet(23L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.FAILED);

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), null, null, TransactionStatus.FAILED, null, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.FAILED);
        }

        @Test
        @DisplayName("filters by a combination of type and status at the same time")
        void filtersByTypeAndStatusTogether() {
            Wallet wallet = persistWallet(24L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.FAILED);
            persistTransaction(wallet, TransactionType.DEBIT,
                    TransactionSource.WITHDRAWAL, TransactionStatus.SUCCESS);

            // only CREDIT + SUCCESS should match — 1 result
            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(),
                    TransactionType.CREDIT, null, TransactionStatus.SUCCESS,
                    null, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(page.getContent().get(0).getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        }

        @Test
        @DisplayName("respects the from date when provided and excludes older transactions")
        void respectsFromDateFilter() {
            Wallet wallet = persistWallet(25L);
            LocalDateTime cutoff = LocalDateTime.now().minusDays(5);

            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS,
                    cutoff.minusDays(3)); // before cutoff — excluded
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS,
                    cutoff.plusDays(1)); // after cutoff — included

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), null, null, null, cutoff, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("respects the to date when provided and excludes newer transactions")
        void respectsToDateFilter() {
            Wallet wallet = persistWallet(26L);
            LocalDateTime cutoff = LocalDateTime.now().minusDays(5);

            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS,
                    cutoff.minusDays(1)); // before cutoff — included
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS,
                    cutoff.plusDays(3)); // after cutoff — excluded

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(), null, null, null, null, cutoff, firstPage());

            assertThat(page.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("returns an empty page when filters match nothing")
        void returnsEmptyWhenNoTransactionsMatchFilters() {
            Wallet wallet = persistWallet(27L);
            persistTransaction(wallet, TransactionType.CREDIT,
                    TransactionSource.TOPUP, TransactionStatus.SUCCESS);

            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    wallet.getId(),
                    TransactionType.DEBIT, null, TransactionStatus.FAILED,
                    null, null, firstPage());

            assertThat(page.getContent()).isEmpty();
        }

        @Test
        @DisplayName("does not return transactions belonging to a different wallet even when filters match")
        void doesNotLeakTransactionsFromOtherWallets() {
            Wallet walletA = persistWallet(28L);
            Wallet walletB = persistWallet(29L);

            persistTransaction(walletA, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);
            persistTransaction(walletB, TransactionType.CREDIT,
                    TransactionSource.TRANSFER, TransactionStatus.SUCCESS);

            // query wallet A — should not see wallet B's transaction
            Page<WalletTransaction> page = transactionRepository.findByFilters(
                    walletA.getId(), TransactionType.CREDIT, null, null, null, null, firstPage());

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).getWallet().getId()).isEqualTo(walletA.getId());
        }
    }
}