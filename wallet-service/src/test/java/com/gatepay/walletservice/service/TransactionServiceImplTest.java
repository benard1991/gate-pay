package com.gatepay.walletservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.walletservice.dto.*;
import com.gatepay.walletservice.enums.*;
import com.gatepay.walletservice.exception.*;
import com.gatepay.walletservice.model.*;
import com.gatepay.walletservice.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceImplTest {

    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository transactionRepository;
    @Mock private IdempotencyService idempotencyService;
    @Mock private WalletEventService walletEventService;
    @Mock private ObjectMapper objectMapper;

    private TransactionServiceImpl transactionService;

    // Runs the callback like a real transaction (including commit/rollback
    // bookkeeping) without a database.
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object tx, TransactionDefinition def) { }
        @Override protected void doCommit(DefaultTransactionStatus status) { }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
    }

    @BeforeEach
    void setUp() {
        transactionService = new TransactionServiceImpl(
                walletRepository, transactionRepository, idempotencyService,
                walletEventService, objectMapper,
                new TransactionTemplate(new NoOpTransactionManager()));
    }


    private Wallet activeWallet() {
        Wallet w = new Wallet();
        w.setId(1L);
        w.setUserId(42L);
        w.setBalance(new BigDecimal("1000.00"));
        w.setLedgerBalance(new BigDecimal("1000.00"));
        w.setStatus(WalletStatus.ACTIVE);
        return w;
    }

    private CreditWalletRequest creditRequest(BigDecimal amount) {
        return CreditWalletRequest.builder()
                .userId(42L)
                .amount(amount)
                .source(TransactionSource.TRANSFER)
                .idempotencyKey("idem-credit-001")
                .description("Top-up")
                .build();
    }

    private DebitWalletRequest debitRequest(BigDecimal amount) {
        return DebitWalletRequest.builder()
                .userId(42L)
                .amount(amount)
                .source(TransactionSource.WITHDRAWAL)
                .idempotencyKey("idem-debit-001")
                .description("Payment")
                .build();
    }

    private WalletTransaction savedTransaction(Wallet wallet, TransactionType type,
                                               BigDecimal amount,
                                               BigDecimal before, BigDecimal after) {
        return WalletTransaction.builder()
                .id(99L)
                .wallet(wallet)
                .reference("WLT-ABC123")
                .type(type)
                .source(TransactionSource.TRANSFER)
                .status(TransactionStatus.SUCCESS)
                .amount(amount)
                .balanceBefore(before)
                .balanceAfter(after)
                .description("desc")
                .createdAt(LocalDateTime.now())
                .build();
    }


    // CREDIT


    @Nested
    @DisplayName("credit()")
    class CreditTests {

        @Test
        @DisplayName("credits the wallet and returns a success response")
        void happyPath() throws Exception {
            Wallet wallet = activeWallet();
            CreditWalletRequest request = creditRequest(new BigDecimal("200.00"));

            WalletTransaction tx = savedTransaction(wallet, TransactionType.CREDIT,
                    new BigDecimal("200.00"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1200.00"));

            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.save(any())).thenReturn(tx);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":99}");

            WalletTransactionResponse response = transactionService.credit(request);

            assertThat(response).isNotNull();
            assertThat(response.getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(response.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
            assertThat(response.getAmount()).isEqualByComparingTo("200.00");
            assertThat(response.getBalanceBefore()).isEqualByComparingTo("1000.00");
            assertThat(response.getBalanceAfter()).isEqualByComparingTo("1200.00");

            // wallet balance should have been updated in-memory before save
            assertThat(wallet.getBalance()).isEqualByComparingTo("1200.00");

            verify(walletRepository).save(wallet);
            verify(transactionRepository).save(any(WalletTransaction.class));
            verify(walletEventService).publishCreditEvent(tx);
            verify(idempotencyService).storeResponse(eq("42"), eq("idem-credit-001"), anyString());
        }

        @Test
        @DisplayName("returns cached response when duplicate idempotency key is detected")
        void returnsCachedOnDuplicateKey() throws Exception {
            CreditWalletRequest request = creditRequest(new BigDecimal("200.00"));
            String cachedJson = "{\"id\":99,\"reference\":\"WLT-ABC123\"}";
            WalletTransactionResponse cachedResponse = WalletTransactionResponse.builder()
                    .id(99L).reference("WLT-ABC123").build();

            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.of(cachedJson));
            when(objectMapper.readValue(cachedJson, WalletTransactionResponse.class))
                    .thenReturn(cachedResponse);

            WalletTransactionResponse response = transactionService.credit(request);

            assertThat(response.getReference()).isEqualTo("WLT-ABC123");

            // none of the real processing should have happened
            verifyNoInteractions(walletRepository, transactionRepository, walletEventService);
        }

        @Test
        @DisplayName("throws WalletNotFoundException when wallet does not exist")
        void throwsWhenWalletMissing() {
            CreditWalletRequest request = creditRequest(new BigDecimal("100.00"));

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.credit(request))
                    .isInstanceOf(WalletNotFoundException.class)
                    .hasMessageContaining("42");

            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(walletEventService);
        }

        @Test
        @DisplayName("throws WalletServiceException when wallet is suspended")
        void throwsWhenWalletSuspended() {
            Wallet wallet = activeWallet();
            wallet.setStatus(WalletStatus.SUSPENDED);
            CreditWalletRequest request = creditRequest(new BigDecimal("100.00"));

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.credit(request))
                    .isInstanceOf(WalletServiceException.class);
        }

        @Test
        @DisplayName("throws WalletServiceException when wallet is closed")
        void throwsWhenWalletClosed() {
            Wallet wallet = activeWallet();
            wallet.setStatus(WalletStatus.CLOSED);
            CreditWalletRequest request = creditRequest(new BigDecimal("100.00"));

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.credit(request))
                    .isInstanceOf(WalletServiceException.class);
        }
    }



    // DEBIT


    @Nested
    @DisplayName("debit()")
    class DebitTests {

        @Test
        @DisplayName("debits the wallet and returns a success response")
        void happyPath() throws Exception {
            Wallet wallet = activeWallet(); // balance = 1000
            DebitWalletRequest request = debitRequest(new BigDecimal("300.00"));

            WalletTransaction tx = savedTransaction(wallet, TransactionType.DEBIT,
                    new BigDecimal("300.00"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("700.00"));

            when(idempotencyService.getProcessedResponse("42", "idem-debit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.save(any())).thenReturn(tx);
            when(objectMapper.writeValueAsString(any())).thenReturn("{\"id\":99}");

            WalletTransactionResponse response = transactionService.debit(request);

            assertThat(response.getType()).isEqualTo(TransactionType.DEBIT);
            assertThat(response.getBalanceBefore()).isEqualByComparingTo("1000.00");
            assertThat(response.getBalanceAfter()).isEqualByComparingTo("700.00");

            assertThat(wallet.getBalance()).isEqualByComparingTo("700.00");

            verify(walletRepository).save(wallet);
            verify(walletEventService).publishDebitEvent(tx);
        }

        @Test
        @DisplayName("returns cached response when duplicate idempotency key is detected")
        void returnsCachedOnDuplicateKey() throws Exception {
            DebitWalletRequest request = debitRequest(new BigDecimal("100.00"));
            String cachedJson = "{\"id\":55}";
            WalletTransactionResponse cachedResponse = WalletTransactionResponse.builder().id(55L).build();

            when(idempotencyService.getProcessedResponse("42", "idem-debit-001"))
                    .thenReturn(Optional.of(cachedJson));
            when(objectMapper.readValue(cachedJson, WalletTransactionResponse.class))
                    .thenReturn(cachedResponse);

            WalletTransactionResponse response = transactionService.debit(request);

            assertThat(response.getId()).isEqualTo(55L);
            verifyNoInteractions(walletRepository, transactionRepository, walletEventService);
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when balance is too low")
        void throwsOnInsufficientBalance() {
            Wallet wallet = activeWallet(); // balance = 1000
            DebitWalletRequest request = debitRequest(new BigDecimal("1500.00")); // more than balance

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.debit(request))
                    .isInstanceOf(InsufficientBalanceException.class)
                    .hasMessageContaining("1500");
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when balance equals zero")
        void throwsWhenBalanceIsZero() {
            Wallet wallet = activeWallet();
            wallet.setBalance(BigDecimal.ZERO);
            DebitWalletRequest request = debitRequest(new BigDecimal("0.01"));

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.debit(request))
                    .isInstanceOf(InsufficientBalanceException.class);
        }

        @Test
        @DisplayName("succeeds when debit amount exactly equals available balance")
        void succeedsWhenAmountExactlyEqualsBalance() throws Exception {
            Wallet wallet = activeWallet(); // balance = 1000
            DebitWalletRequest request = debitRequest(new BigDecimal("1000.00"));

            WalletTransaction tx = savedTransaction(wallet, TransactionType.DEBIT,
                    new BigDecimal("1000.00"),
                    new BigDecimal("1000.00"),
                    BigDecimal.ZERO);

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.save(any())).thenReturn(tx);
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            WalletTransactionResponse response = transactionService.debit(request);

            assertThat(response.getBalanceAfter()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("throws WalletNotFoundException when wallet does not exist")
        void throwsWhenWalletMissing() {
            DebitWalletRequest request = debitRequest(new BigDecimal("100.00"));

            when(idempotencyService.getProcessedResponse(any(), any()))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.debit(request))
                    .isInstanceOf(WalletNotFoundException.class);
        }
    }

    // REVERSE

    @Nested
    @DisplayName("reverse()")
    class ReverseTests {

        @Test
        @DisplayName("reverses a credit by debiting the wallet")
        void reversesCreditWithDebit() {
            Wallet wallet = activeWallet(); // balance = 1000

            WalletTransaction original = WalletTransaction.builder()
                    .id(10L)
                    .wallet(wallet)
                    .reference("WLT-ORIG-001")
                    .type(TransactionType.CREDIT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("200.00"))
                    .build();

            WalletTransaction reversal = WalletTransaction.builder()
                    .id(11L)
                    .wallet(wallet)
                    .reference("WLT-REV-001")
                    .type(TransactionType.DEBIT)
                    .source(TransactionSource.REVERSAL)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("200.00"))
                    .balanceBefore(new BigDecimal("1000.00"))
                    .balanceAfter(new BigDecimal("800.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-001"))
                    .thenReturn(Optional.of(original));
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.save(argThat(t -> t.getSource() == TransactionSource.REVERSAL)))
                    .thenReturn(reversal);
            // swallow the save of the original (marking it REVERSED)
            when(transactionRepository.save(original)).thenReturn(original);

            WalletTransactionResponse response = transactionService.reverse("WLT-ORIG-001");

            assertThat(response.getType()).isEqualTo(TransactionType.DEBIT);
            assertThat(response.getBalanceAfter()).isEqualByComparingTo("800.00");

            assertThat(original.getStatus()).isEqualTo(TransactionStatus.REVERSED);
            assertThat(wallet.getBalance()).isEqualByComparingTo("800.00");

            verify(walletEventService).publishReversalEvent(reversal);
        }

        @Test
        @DisplayName("reverses a debit by crediting the wallet")
        void reversesDebitWithCredit() {
            Wallet wallet = activeWallet(); // balance = 1000

            WalletTransaction original = WalletTransaction.builder()
                    .id(20L)
                    .wallet(wallet)
                    .reference("WLT-ORIG-002")
                    .type(TransactionType.DEBIT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("300.00"))
                    .build();

            WalletTransaction reversal = WalletTransaction.builder()
                    .id(21L)
                    .wallet(wallet)
                    .reference("WLT-REV-002")
                    .type(TransactionType.CREDIT)
                    .source(TransactionSource.REVERSAL)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("300.00"))
                    .balanceBefore(new BigDecimal("1000.00"))
                    .balanceAfter(new BigDecimal("1300.00"))
                    .createdAt(LocalDateTime.now())
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-002"))
                    .thenReturn(Optional.of(original));
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.save(argThat(t -> t.getSource() == TransactionSource.REVERSAL)))
                    .thenReturn(reversal);
            when(transactionRepository.save(original)).thenReturn(original);

            WalletTransactionResponse response = transactionService.reverse("WLT-ORIG-002");

            assertThat(response.getType()).isEqualTo(TransactionType.CREDIT);
            assertThat(wallet.getBalance()).isEqualByComparingTo("1300.00");
        }

        @Test
        @DisplayName("throws TransactionNotFoundException when the reference does not exist")
        void throwsWhenReferenceNotFound() {
            when(transactionRepository.findByReference("GHOST-REF"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.reverse("GHOST-REF"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("GHOST-REF");
        }

        @Test
        @DisplayName("throws WalletServiceException with TRANSACTION_ALREADY_REVERSED when transaction is already reversed")
        void throwsWhenAlreadyReversed() {
            Wallet wallet = activeWallet();

            WalletTransaction alreadyReversed = WalletTransaction.builder()
                    .wallet(wallet)
                    .reference("WLT-ORIG-003")
                    .type(TransactionType.CREDIT)
                    .status(TransactionStatus.REVERSED)
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-003"))
                    .thenReturn(Optional.of(alreadyReversed));

            assertThatThrownBy(() -> transactionService.reverse("WLT-ORIG-003"))
                    .isInstanceOf(WalletServiceException.class)
                    .satisfies(ex -> assertThat(((WalletServiceException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TRANSACTION_ALREADY_REVERSED));
        }

        @Test
        @DisplayName("throws WalletServiceException with TRANSACTION_NOT_REVERSIBLE when transaction is FAILED")
        void throwsWhenTransactionIsFailed() {
            Wallet wallet = activeWallet();

            WalletTransaction failedTx = WalletTransaction.builder()
                    .wallet(wallet)
                    .reference("WLT-ORIG-004")
                    .type(TransactionType.CREDIT)
                    .status(TransactionStatus.FAILED)
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-004"))
                    .thenReturn(Optional.of(failedTx));

            assertThatThrownBy(() -> transactionService.reverse("WLT-ORIG-004"))
                    .isInstanceOf(WalletServiceException.class)
                    .satisfies(ex -> assertThat(((WalletServiceException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TRANSACTION_NOT_REVERSIBLE));
        }

        @Test
        @DisplayName("throws WalletServiceException with TRANSACTION_NOT_REVERSIBLE when transaction is PENDING")
        void throwsWhenTransactionIsPending() {
            Wallet wallet = activeWallet();

            WalletTransaction pendingTx = WalletTransaction.builder()
                    .wallet(wallet)
                    .reference("WLT-ORIG-006")
                    .type(TransactionType.CREDIT)
                    .status(TransactionStatus.PENDING)
                    .amount(new BigDecimal("100.00"))
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-006"))
                    .thenReturn(Optional.of(pendingTx));

            assertThatThrownBy(() -> transactionService.reverse("WLT-ORIG-006"))
                    .isInstanceOf(WalletServiceException.class)
                    .satisfies(ex -> assertThat(((WalletServiceException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.TRANSACTION_NOT_REVERSIBLE));
        }

        @Test
        @DisplayName("throws InsufficientBalanceException when reversing a credit but wallet has no funds")
        void throwsWhenReverseCreditButInsufficientBalance() {
            Wallet wallet = activeWallet();
            wallet.setBalance(new BigDecimal("50.00")); // not enough to reverse a 200 credit

            WalletTransaction original = WalletTransaction.builder()
                    .wallet(wallet)
                    .reference("WLT-ORIG-005")
                    .type(TransactionType.CREDIT)
                    .status(TransactionStatus.SUCCESS)
                    .amount(new BigDecimal("200.00"))
                    .build();

            when(transactionRepository.findByReference("WLT-ORIG-005"))
                    .thenReturn(Optional.of(original));
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.reverse("WLT-ORIG-005"))
                    .isInstanceOf(InsufficientBalanceException.class);
        }
    }


    // GET BY REFERENCE

    @Nested
    @DisplayName("getByReference()")
    class GetByReferenceTests {

        @Test
        @DisplayName("returns the transaction when found")
        void returnsTransactionWhenFound() {
            Wallet wallet = activeWallet();
            WalletTransaction tx = savedTransaction(wallet, TransactionType.CREDIT,
                    new BigDecimal("100.00"),
                    new BigDecimal("900.00"),
                    new BigDecimal("1000.00"));

            when(transactionRepository.findByReference("WLT-ABC123"))
                    .thenReturn(Optional.of(tx));

            WalletTransactionResponse response = transactionService.getByReference("WLT-ABC123");

            assertThat(response.getReference()).isEqualTo("WLT-ABC123");
            assertThat(response.getAmount()).isEqualByComparingTo("100.00");
        }

        @Test
        @DisplayName("throws TransactionNotFoundException when reference does not exist")
        void throwsWhenNotFound() {
            when(transactionRepository.findByReference("MISSING"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.getByReference("MISSING"))
                    .isInstanceOf(TransactionNotFoundException.class)
                    .hasMessageContaining("MISSING");
        }
    }


    // GET TRANSACTIONS (USER — FILTERED + PAGED)

    @Nested
    @DisplayName("getTransactions()")
    class GetTransactionsTests {

        @Test
        @DisplayName("returns a paged list of transactions for an active wallet")
        void returnsPaginatedTransactions() {
            Wallet wallet = activeWallet();
            WalletTransaction tx = savedTransaction(wallet, TransactionType.CREDIT,
                    new BigDecimal("100.00"),
                    new BigDecimal("900.00"),
                    new BigDecimal("1000.00"));

            Page<WalletTransaction> page = new PageImpl<>(
                    List.of(tx),
                    PageRequest.of(0, 20),
                    1
            );

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setPage(0);
            filter.setSize(20);

            when(walletRepository.findByUserId(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.findByFilters(
                    eq(1L), isNull(), isNull(), isNull(), isNull(), isNull(),
                    any(Pageable.class)
            )).thenReturn(page);

            PageResponse<WalletTransactionResponse> result =
                    transactionService.getTransactions(42L, filter);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getTotalPages()).isEqualTo(1);
            assertThat(result.isLast()).isTrue();
        }

        @Test
        @DisplayName("returns an empty page when the wallet has no transactions")
        void returnsEmptyPageWhenNoTransactions() {
            Wallet wallet = activeWallet();
            Page<WalletTransaction> emptyPage = Page.empty(PageRequest.of(0, 20));

            TransactionFilterRequest filter = new TransactionFilterRequest();
            filter.setPage(0);
            filter.setSize(20);

            when(walletRepository.findByUserId(42L)).thenReturn(Optional.of(wallet));
            when(transactionRepository.findByFilters(
                    any(), any(), any(), any(), any(), any(), any()
            )).thenReturn(emptyPage);

            PageResponse<WalletTransactionResponse> result =
                    transactionService.getTransactions(42L, filter);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("throws WalletNotFoundException when userId has no wallet")
        void throwsWhenWalletNotFound() {
            when(walletRepository.findByUserId(99L)).thenReturn(Optional.empty());

            TransactionFilterRequest filter = new TransactionFilterRequest();

            assertThatThrownBy(() -> transactionService.getTransactions(99L, filter))
                    .isInstanceOf(WalletNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }


    // GET ALL TRANSACTIONS (ADMIN)

    @Nested
    @DisplayName("getAllTransactions()")
    class GetAllTransactionsTests {

        @Test
        @DisplayName("returns a sorted paged response for admin with descending order")
        void returnsSortedPageForAdmin() {
            Wallet wallet = activeWallet();
            WalletTransaction tx = savedTransaction(wallet, TransactionType.DEBIT,
                    new BigDecimal("50.00"),
                    new BigDecimal("950.00"),
                    new BigDecimal("900.00"));

            Page<WalletTransaction> page = new PageImpl<>(List.of(tx), PageRequest.of(0, 10), 1);

            AdminTransactionFilterRequest filter = new AdminTransactionFilterRequest();
            filter.setPage(0);
            filter.setSize(10);
            filter.setSortBy("createdAt");
            filter.setSortDirection("desc");

            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(page);

            PageResponse<WalletTransactionResponse> result =
                    transactionService.getAllTransactions(filter);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getType()).isEqualTo(TransactionType.DEBIT);
        }

        @Test
        @DisplayName("returns a sorted paged response with ascending order")
        void returnsSortedPageAscending() {
            Page<WalletTransaction> emptyPage = Page.empty(PageRequest.of(0, 5));

            AdminTransactionFilterRequest filter = new AdminTransactionFilterRequest();
            filter.setPage(0);
            filter.setSize(5);
            filter.setSortBy("createdAt");
            filter.setSortDirection("asc");

            when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<WalletTransactionResponse> result =
                    transactionService.getAllTransactions(filter);

            assertThat(result.getContent()).isEmpty();
        }
    }


    // IDEMPOTENCY + CONCURRENCY

    @Nested
    @DisplayName("idempotency and concurrency")
    class IdempotencyAndConcurrencyTests {

        private WalletTransaction existingCredit() {
            WalletTransaction tx = savedTransaction(activeWallet(), TransactionType.CREDIT,
                    new BigDecimal("200.00"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("1200.00"));
            tx.setIdempotencyKey("idem-credit-001");
            return tx;
        }

        @Test
        @DisplayName("stores the idempotency key on the saved transaction")
        void savesIdempotencyKey() throws Exception {
            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(activeWallet()));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            transactionService.credit(creditRequest(new BigDecimal("200.00")));

            ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(transactionRepository).save(captor.capture());
            assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("idem-credit-001");
        }

        @Test
        @DisplayName("returns the existing transaction from the DB when Redis has no entry")
        void returnsExistingFromDbOnRedisMiss() throws Exception {
            WalletTransaction existing = existingCredit();

            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.findByUserIdAndIdempotencyKey(42L, "idem-credit-001"))
                    .thenReturn(Optional.of(existing));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            WalletTransactionResponse response =
                    transactionService.credit(creditRequest(new BigDecimal("200.00")));

            assertThat(response.getReference()).isEqualTo("WLT-ABC123");
            verify(walletRepository, never()).findByUserIdWithLock(any());
            verify(walletRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
            verifyNoInteractions(walletEventService);
            // re-populates the cache so the next duplicate is served from Redis
            verify(idempotencyService).storeResponse(eq("42"), eq("idem-credit-001"), anyString());
        }

        @Test
        @DisplayName("returns the winning transaction when a concurrent duplicate hits the unique key")
        void concurrentDuplicateRejectedByUniqueKey() throws Exception {
            WalletTransaction winner = existingCredit();

            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            // not there when we check, but committed by the other request by the time we insert
            when(transactionRepository.findByUserIdAndIdempotencyKey(42L, "idem-credit-001"))
                    .thenReturn(Optional.empty())
                    .thenReturn(Optional.of(winner));
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(activeWallet()));
            when(transactionRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry for uk_wallet_transactions_wallet_idempotency"));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            WalletTransactionResponse response =
                    transactionService.credit(creditRequest(new BigDecimal("200.00")));

            assertThat(response.getReference()).isEqualTo("WLT-ABC123");
            verifyNoInteractions(walletEventService);
        }

        @Test
        @DisplayName("rethrows a constraint violation that isn't an idempotency duplicate")
        void rethrowsUnrelatedConstraintViolation() {
            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(activeWallet()));
            when(transactionRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("chk_amount"));

            assertThatThrownBy(() -> transactionService.credit(creditRequest(new BigDecimal("200.00"))))
                    .isInstanceOf(DataIntegrityViolationException.class);

            verify(idempotencyService, never()).storeResponse(any(), any(), any());
            verifyNoInteractions(walletEventService);
        }

        @Test
        @DisplayName("retries on an optimistic lock conflict and succeeds with a fresh read")
        void retriesOnOptimisticLockConflict() throws Exception {
            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L))
                    .thenReturn(Optional.of(activeWallet()), Optional.of(activeWallet()));
            when(walletRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");

            WalletTransactionResponse response =
                    transactionService.credit(creditRequest(new BigDecimal("200.00")));

            assertThat(response.getBalanceBefore()).isEqualByComparingTo("1000.00");
            assertThat(response.getBalanceAfter()).isEqualByComparingTo("1200.00");
            verify(walletRepository, times(2)).findByUserIdWithLock(42L);
            verify(transactionRepository, times(1)).save(any());
            verify(walletEventService, times(1)).publishCreditEvent(any());
            verify(idempotencyService, times(1)).storeResponse(eq("42"), eq("idem-credit-001"), anyString());
        }

        @Test
        @DisplayName("gives up with CONCURRENT_UPDATE after 3 conflicts and caches nothing")
        void givesUpAfterMaxAttempts() {
            when(idempotencyService.getProcessedResponse("42", "idem-debit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenAnswer(inv -> Optional.of(activeWallet()));
            when(walletRepository.save(any()))
                    .thenThrow(new ObjectOptimisticLockingFailureException(Wallet.class, 1L));

            assertThatThrownBy(() -> transactionService.debit(debitRequest(new BigDecimal("100.00"))))
                    .isInstanceOf(WalletServiceException.class)
                    .satisfies(ex -> assertThat(((WalletServiceException) ex).getErrorCode())
                            .isEqualTo(ErrorCode.CONCURRENT_UPDATE));

            verify(walletRepository, times(3)).findByUserIdWithLock(42L);
            verify(idempotencyService, never()).storeResponse(any(), any(), any());
            verifyNoInteractions(walletEventService);
        }

        @Test
        @DisplayName("does not cache or publish when the transaction fails")
        void nothingCachedWhenTransactionFails() {
            Wallet wallet = activeWallet();
            wallet.setBalance(new BigDecimal("50.00"));
            when(idempotencyService.getProcessedResponse("42", "idem-debit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> transactionService.debit(debitRequest(new BigDecimal("100.00"))))
                    .isInstanceOf(InsufficientBalanceException.class);

            verify(idempotencyService, never()).storeResponse(any(), any(), any());
            verifyNoInteractions(walletEventService);
        }

        @Test
        @DisplayName("still succeeds when Redis is down after the DB commit")
        void redisFailureAfterCommitDoesNotFailRequest() throws Exception {
            when(idempotencyService.getProcessedResponse("42", "idem-credit-001"))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdWithLock(42L)).thenReturn(Optional.of(activeWallet()));
            when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
            doThrow(new RuntimeException("Redis connection refused"))
                    .when(idempotencyService).storeResponse(any(), any(), any());

            WalletTransactionResponse response =
                    transactionService.credit(creditRequest(new BigDecimal("200.00")));

            assertThat(response.getBalanceAfter()).isEqualByComparingTo("1200.00");
            verify(walletEventService).publishCreditEvent(any());
        }
    }
}
