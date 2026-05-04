package com.gatepay.walletservice.repository;

import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.model.Wallet;
import com.gatepay.walletservice.service.BaseIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Repository integration tests use the real MySQL Testcontainer and Flyway migrations
// inherited from BaseIntegrationTest. Every test runs inside a transaction that rolls
// back automatically at the end, so each test starts with a clean slate.
@Transactional
@DisplayName("WalletRepository")
class WalletRepositoryIntegrationTest extends BaseIntegrationTest {

    @Autowired
    WalletRepository walletRepository;

    private Wallet persistWallet(Long userId, WalletStatus status) {
        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(new BigDecimal("1000.00"))
                .ledgerBalance(new BigDecimal("1000.00"))
                .currency("NGN")
                .status(status)
                .version(0L)
                .build();
        return walletRepository.saveAndFlush(wallet);
    }

    // findByUserId

    @Nested
    @DisplayName("findByUserId()")
    class FindByUserId {

        @Test
        @DisplayName("finds the wallet when the userId exists in the database")
        void findsWalletForExistingUser() {
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result = walletRepository.findByUserId(42L);

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(42L);
            assertThat(result.get().getCurrency()).isEqualTo("NGN");
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }

        @Test
        @DisplayName("returns empty when no wallet exists for the given userId")
        void returnsEmptyForUnknownUser() {
            Optional<Wallet> result = walletRepository.findByUserId(9999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns only the wallet that belongs to the requested userId")
        void doesNotReturnAnotherUsersWallet() {
            // two wallets in the DB — make sure we only get back the right one
            persistWallet(1L, WalletStatus.ACTIVE);
            persistWallet(2L, WalletStatus.ACTIVE);

            Optional<Wallet> result = walletRepository.findByUserId(1L);

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(1L);
        }
    }

    // existsByUserId

    @Nested
    @DisplayName("existsByUserId()")
    class ExistsByUserId {

        @Test
        @DisplayName("returns true when a wallet exists for the given userId")
        void returnsTrueWhenWalletExists() {
            persistWallet(42L, WalletStatus.ACTIVE);

            assertThat(walletRepository.existsByUserId(42L)).isTrue();
        }

        @Test
        @DisplayName("returns false when no wallet exists for the given userId")
        void returnsFalseWhenNoWalletExists() {
            assertThat(walletRepository.existsByUserId(9999L)).isFalse();
        }
    }


    // findByUserIdWithLock


    @Nested
    @DisplayName("findByUserIdWithLock()")
    class FindByUserIdWithLock {

        @Test
        @DisplayName("finds the wallet and returns the correct data when the optimistic lock is acquired")
        void findsWalletWithLockAndReturnsCorrectData() {
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result = walletRepository.findByUserIdWithLock(42L);

            // OPTIMISTIC_FORCE_INCREMENT flushes its version increment at commit,
            // not at read time — so we can't observe the bump within the same
            // transaction. What we can assert is that the query returns the right
            // wallet and doesn't blow up when the lock is applied.
            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(42L);
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(result.get().getBalance()).isEqualByComparingTo("1000.00");
            // version starts at 0 on a fresh insert — the lock acquired it cleanly
            assertThat(result.get().getVersion()).isNotNull();
        }

        @Test
        @DisplayName("returns empty when no wallet exists for the given userId")
        void returnsEmptyWhenWalletNotFound() {
            Optional<Wallet> result = walletRepository.findByUserIdWithLock(9999L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns correct wallet data — not just a shell — when the lock is acquired")
        void returnsFullWalletDataWithLock() {
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result = walletRepository.findByUserIdWithLock(42L);

            assertThat(result).isPresent();
            assertThat(result.get().getBalance()).isEqualByComparingTo("1000.00");
            assertThat(result.get().getCurrency()).isEqualTo("NGN");
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }
    }

    // findByUserIdAndStatus

    @Nested
    @DisplayName("findByUserIdAndStatus()")
    class FindByUserIdAndStatus {

        @Test
        @DisplayName("finds the wallet when the userId and status both match")
        void findsWalletWhenBothUserIdAndStatusMatch() {
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(42L, WalletStatus.ACTIVE);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }

        @Test
        @DisplayName("returns empty when the userId matches but the status does not")
        void returnsEmptyWhenStatusDoesNotMatch() {
            // wallet is ACTIVE but we query for SUSPENDED — should find nothing
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(42L, WalletStatus.SUSPENDED);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns empty when the status matches but the userId does not")
        void returnsEmptyWhenUserIdDoesNotMatch() {
            persistWallet(42L, WalletStatus.ACTIVE);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(9999L, WalletStatus.ACTIVE);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("finds a suspended wallet correctly by userId and status")
        void findsSuspendedWallet() {
            persistWallet(42L, WalletStatus.SUSPENDED);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(42L, WalletStatus.SUSPENDED);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.SUSPENDED);
        }

        @Test
        @DisplayName("finds a closed wallet correctly by userId and status")
        void findsClosedWallet() {
            persistWallet(42L, WalletStatus.CLOSED);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(42L, WalletStatus.CLOSED);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(WalletStatus.CLOSED);
        }

        @Test
        @DisplayName("does not return another user's wallet even when the status matches")
        void doesNotReturnAnotherUsersWalletWhenStatusMatches() {
            // user 1 is ACTIVE, user 2 is ACTIVE — querying user 1 should not return user 2
            persistWallet(1L, WalletStatus.ACTIVE);
            persistWallet(2L, WalletStatus.ACTIVE);

            Optional<Wallet> result =
                    walletRepository.findByUserIdAndStatus(1L, WalletStatus.ACTIVE);

            assertThat(result).isPresent();
            assertThat(result.get().getUserId()).isEqualTo(1L);
        }
    }

    // General persistence behaviour

    @Nested
    @DisplayName("general persistence")
    class GeneralPersistence {

        @Test
        @DisplayName("saves and retrieves a wallet with all fields intact")
        void savesAndRetrievesAllFields() {
            Wallet saved = persistWallet(42L, WalletStatus.ACTIVE);

            Wallet fetched = walletRepository.findById(saved.getId()).orElseThrow();

            assertThat(fetched.getUserId()).isEqualTo(42L);
            assertThat(fetched.getBalance()).isEqualByComparingTo("1000.00");
            assertThat(fetched.getLedgerBalance()).isEqualByComparingTo("1000.00");
            assertThat(fetched.getCurrency()).isEqualTo("NGN");
            assertThat(fetched.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(fetched.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("enforces uniqueness — saving two wallets for the same userId fails")
        void rejectsSecondWalletForSameUser() {
            persistWallet(42L, WalletStatus.ACTIVE);

            // the userId column has a unique constraint — saving a duplicate should throw
            Wallet duplicate = Wallet.builder()
                    .userId(42L)
                    .balance(BigDecimal.ZERO)
                    .ledgerBalance(BigDecimal.ZERO)
                    .currency("NGN")
                    .status(WalletStatus.ACTIVE)
                    .version(0L)
                    .build();

            org.junit.jupiter.api.Assertions.assertThrows(
                    Exception.class,
                    () -> walletRepository.saveAndFlush(duplicate),
                    "Expected a constraint violation when saving a duplicate userId"
            );
        }
    }
}