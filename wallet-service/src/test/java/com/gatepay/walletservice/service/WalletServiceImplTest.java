package com.gatepay.walletservice.service;

import com.gatepay.walletservice.dto.*;
import com.gatepay.walletservice.enums.WalletStatus;
import com.gatepay.walletservice.exception.WalletAlreadyExistsException;
import com.gatepay.walletservice.exception.WalletNotFoundException;
import com.gatepay.walletservice.exception.WalletOperationException;
import com.gatepay.walletservice.model.Wallet;
import com.gatepay.walletservice.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletServiceImpl Unit Tests")
class WalletServiceImplTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private WalletEventService walletEventService;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet activeWallet;

    @BeforeEach
    void setUp() {
        activeWallet = Wallet.builder()
                .id(1L)
                .userId(100L)
                .balance(BigDecimal.valueOf(5000))
                .ledgerBalance(BigDecimal.valueOf(5000))
                .currency("NGN")
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // CREATE WALLET
    @Nested
    @DisplayName("createWallet()")
    class CreateWalletTests {

        @Test
        @DisplayName("should create wallet with NGN currency when currency is provided")
        void shouldCreateWallet_withProvidedCurrency() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(100L)
                    .currency("NGN")
                    .build();

            when(walletRepository.existsByUserId(100L)).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(activeWallet);

            WalletResponse response = walletService.createWallet(request);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(100L);
            assertThat(response.getCurrency()).isEqualTo("NGN");
            assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.valueOf(5000));

            verify(walletRepository).save(any(Wallet.class));
            verify(walletEventService).publishWalletCreatedEvent(100L, "NGN");
        }

        @Test
        @DisplayName("should default to NGN when currency is null")
        void shouldDefaultToNGN_whenCurrencyIsNull() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(100L)
                    .currency(null)
                    .build();

            Wallet savedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .balance(BigDecimal.ZERO)
                    .ledgerBalance(BigDecimal.ZERO)
                    .currency("NGN")
                    .status(WalletStatus.ACTIVE)
                    .build();

            when(walletRepository.existsByUserId(100L)).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

            WalletResponse response = walletService.createWallet(request);

            assertThat(response.getCurrency()).isEqualTo("NGN");

            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(walletCaptor.capture());
            assertThat(walletCaptor.getValue().getCurrency()).isEqualTo("NGN");
        }

        @Test
        @DisplayName("should throw WalletAlreadyExistsException when wallet already exists for user")
        void shouldThrowException_whenWalletAlreadyExists() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(100L)
                    .currency("NGN")
                    .build();

            when(walletRepository.existsByUserId(100L)).thenReturn(true);

            assertThatThrownBy(() -> walletService.createWallet(request))
                    .isInstanceOf(WalletAlreadyExistsException.class)
                    .hasMessageContaining("100");

            verify(walletRepository, never()).save(any());
            verify(walletEventService, never()).publishWalletCreatedEvent(any(), any());
        }

        @Test
        @DisplayName("should save wallet with zero balance on creation")
        void shouldSaveWallet_withZeroBalance() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(200L)
                    .currency("NGN")
                    .build();

            Wallet zeroBalanceWallet = Wallet.builder()
                    .id(2L)
                    .userId(200L)
                    .balance(BigDecimal.ZERO)
                    .ledgerBalance(BigDecimal.ZERO)
                    .currency("NGN")
                    .status(WalletStatus.ACTIVE)
                    .build();

            when(walletRepository.existsByUserId(200L)).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(zeroBalanceWallet);

            WalletResponse response = walletService.createWallet(request);

            assertThat(response.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(response.getLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("should publish wallet created event after successful creation")
        void shouldPublishCreatedEvent_afterWalletCreation() {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(100L)
                    .currency("NGN")
                    .build();

            when(walletRepository.existsByUserId(100L)).thenReturn(false);
            when(walletRepository.save(any(Wallet.class))).thenReturn(activeWallet);

            walletService.createWallet(request);

            verify(walletEventService, times(1))
                    .publishWalletCreatedEvent(100L, "NGN");
        }
    }

    // GET WALLET BY USER ID
    @Nested
    @DisplayName("getWalletByUserId()")
    class GetWalletByUserIdTests {

        @Test
        @DisplayName("should return wallet response for active wallet")
        void shouldReturnWalletResponse_forActiveWallet() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.of(activeWallet));

            WalletResponse response = walletService.getWalletByUserId(100L);

            assertThat(response).isNotNull();
            assertThat(response.getUserId()).isEqualTo(100L);
            assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when no active wallet found")
        void shouldThrowException_whenNoActiveWalletFound() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getWalletByUserId(100L))
                    .isInstanceOf(WalletNotFoundException.class)
                    .hasMessageContaining("100");
        }
    }

    // GET WALLET BY ID

    @Nested
    @DisplayName("getWalletById()")
    class GetWalletByIdTests {

        @Test
        @DisplayName("should return wallet response for valid wallet id")
        void shouldReturnWalletResponse_forValidWalletId() {
            when(walletRepository.findById(1L)).thenReturn(Optional.of(activeWallet));

            WalletResponse response = walletService.getWalletById(1L);

            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when wallet id not found")
        void shouldThrowException_whenWalletIdNotFound() {
            when(walletRepository.findById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.getWalletById(999L))
                    .isInstanceOf(WalletNotFoundException.class)
                    .hasMessageContaining("999");
        }
    }

    // SUSPEND WALLET

    @Nested
    @DisplayName("suspendWallet()")
    class SuspendWalletTests {

        @Test
        @DisplayName("should suspend active wallet and publish event")
        void shouldSuspendWallet_andPublishEvent() {
            Wallet suspendedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .balance(BigDecimal.valueOf(5000))
                    .ledgerBalance(BigDecimal.valueOf(5000))
                    .currency("NGN")
                    .status(WalletStatus.SUSPENDED)
                    .build();

            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.of(activeWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(suspendedWallet);

            WalletResponse response = walletService.suspendWallet(100L);

            assertThat(response.getStatus()).isEqualTo(WalletStatus.SUSPENDED);
            verify(walletEventService).publishWalletSuspendedEvent(100L);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when suspending non-existent active wallet")
        void shouldThrowException_whenSuspendingNonExistentWallet() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.suspendWallet(100L))
                    .isInstanceOf(WalletNotFoundException.class);

            verify(walletEventService, never()).publishWalletSuspendedEvent(any());
        }

        @Test
        @DisplayName("should set wallet status to SUSPENDED before saving")
        void shouldSetStatusToSuspended_beforeSaving() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.of(activeWallet));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

            walletService.suspendWallet(100L);

            ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WalletStatus.SUSPENDED);
        }
    }

    // CLOSE WALLET

    @Nested
    @DisplayName("closeWallet()")
    class CloseWalletTests {

        @Test
        @DisplayName("should close active wallet successfully")
        void shouldCloseWallet_successfully() {
            Wallet closedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .balance(BigDecimal.valueOf(5000))
                    .ledgerBalance(BigDecimal.valueOf(5000))
                    .currency("NGN")
                    .status(WalletStatus.CLOSED)
                    .build();

            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.of(activeWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(closedWallet);

            WalletResponse response = walletService.closeWallet(100L);

            assertThat(response.getStatus()).isEqualTo(WalletStatus.CLOSED);
        }

        @Test
        @DisplayName("should set wallet status to CLOSED before saving")
        void shouldSetStatusToClosed_beforeSaving() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.of(activeWallet));
            when(walletRepository.save(any(Wallet.class))).thenAnswer(i -> i.getArgument(0));

            walletService.closeWallet(100L);

            ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(WalletStatus.CLOSED);
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when closing non-existent wallet")
        void shouldThrowException_whenClosingNonExistentWallet() {
            when(walletRepository.findByUserIdAndStatus(100L, WalletStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.closeWallet(100L))
                    .isInstanceOf(WalletNotFoundException.class);
        }
    }

    // REACTIVATE WALLET

    @Nested
    @DisplayName("reactivateWallet()")
    class ReactivateWalletTests {

        @Test
        @DisplayName("should reactivate suspended wallet successfully")
        void shouldReactivate_suspendedWallet() {
            Wallet suspendedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .balance(BigDecimal.valueOf(5000))
                    .ledgerBalance(BigDecimal.valueOf(5000))
                    .currency("NGN")
                    .status(WalletStatus.SUSPENDED)
                    .build();

            Wallet reactivatedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .balance(BigDecimal.valueOf(5000))
                    .ledgerBalance(BigDecimal.valueOf(5000))
                    .currency("NGN")
                    .status(WalletStatus.ACTIVE)
                    .build();

            when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(suspendedWallet));
            when(walletRepository.save(any(Wallet.class))).thenReturn(reactivatedWallet);

            WalletResponse response = walletService.reactivateWallet(100L);

            assertThat(response.getStatus()).isEqualTo(WalletStatus.ACTIVE);
        }

        @Test
        @DisplayName("should throw WalletOperationException when wallet is already active")
        void shouldThrowException_whenWalletAlreadyActive() {
            when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(activeWallet));

            assertThatThrownBy(() -> walletService.reactivateWallet(100L))
                    .isInstanceOf(WalletOperationException.class)
                    .hasMessageContaining("already active");

            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw WalletOperationException when wallet is closed")
        void shouldThrowException_whenWalletIsClosed() {
            Wallet closedWallet = Wallet.builder()
                    .id(1L)
                    .userId(100L)
                    .status(WalletStatus.CLOSED)
                    .build();

            when(walletRepository.findByUserId(100L)).thenReturn(Optional.of(closedWallet));

            assertThatThrownBy(() -> walletService.reactivateWallet(100L))
                    .isInstanceOf(WalletOperationException.class)
                    .hasMessageContaining("Closed wallet cannot be reactivated");

            verify(walletRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw WalletNotFoundException when wallet does not exist")
        void shouldThrowException_whenWalletNotFound() {
            when(walletRepository.findByUserId(100L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> walletService.reactivateWallet(100L))
                    .isInstanceOf(WalletNotFoundException.class)
                    .hasMessageContaining("100");
        }
    }

    // WALLET EXISTS
    @Nested
    @DisplayName("walletExists()")
    class WalletExistsTests {

        @Test
        @DisplayName("should return true when wallet exists for user")
        void shouldReturnTrue_whenWalletExists() {
            when(walletRepository.existsByUserId(100L)).thenReturn(true);
            assertThat(walletService.walletExists(100L)).isTrue();
        }

        @Test
        @DisplayName("should return false when wallet does not exist for user")
        void shouldReturnFalse_whenWalletDoesNotExist() {
            when(walletRepository.existsByUserId(999L)).thenReturn(false);
            assertThat(walletService.walletExists(999L)).isFalse();
        }
    }

    // GET ALL WALLETS (ADMIN)

    @Nested
    @DisplayName("getAllWallets()")
    class GetAllWalletsTests {

        @Test
        @DisplayName("should return paginated wallet list with default filter")
        void shouldReturnPaginatedWallets_withDefaultFilter() {
            WalletFilterRequest filter = WalletFilterRequest.builder()
                    .page(0)
                    .size(20)
                    .sortBy("createdAt")
                    .sortDirection("desc")
                    .build();

            Page<Wallet> walletPage = new PageImpl<>(List.of(activeWallet));

            when(walletRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(walletPage);

            PageResponse<WalletResponse> result = walletService.getAllWallets(filter);

            assertThat(result).isNotNull();
            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getPage()).isEqualTo(0);
        }

        @Test
        @DisplayName("should apply ascending sort when sortDirection is asc")
        void shouldApplyAscendingSort_whenSortDirectionIsAsc() {
            WalletFilterRequest filter = WalletFilterRequest.builder()
                    .page(0)
                    .size(20)
                    .sortBy("createdAt")
                    .sortDirection("asc")
                    .build();

            Page<Wallet> walletPage = new PageImpl<>(List.of());

            when(walletRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(walletPage);

            walletService.getAllWallets(filter);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(walletRepository).findAll(any(Specification.class), pageableCaptor.capture());

            Sort.Order order = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
            assertThat(order).isNotNull();
            assertThat(order.isAscending()).isTrue();
        }

        @Test
        @DisplayName("should return empty page when no wallets match filter")
        void shouldReturnEmptyPage_whenNoWalletsMatchFilter() {
            WalletFilterRequest filter = WalletFilterRequest.builder()
                    .page(0)
                    .size(20)
                    .sortBy("createdAt")
                    .sortDirection("desc")
                    .status(WalletStatus.CLOSED)
                    .build();

            Page<Wallet> emptyPage = new PageImpl<>(List.of());
            when(walletRepository.findAll(any(Specification.class), any(Pageable.class)))
                    .thenReturn(emptyPage);

            PageResponse<WalletResponse> result = walletService.getAllWallets(filter);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }
}