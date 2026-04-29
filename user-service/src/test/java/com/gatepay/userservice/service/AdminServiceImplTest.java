package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.exception.EmailCannotBeNullException;
import com.gatepay.userservice.exception.UserNotFoundException;
import com.gatepay.userservice.mapper.PaginatedResponse;
import com.gatepay.userservice.model.User;
import com.gatepay.userservice.repository.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminServiceImpl Unit Tests")
class AdminServiceImplTest {

    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private AdminServiceImpl adminService;

    // A reusable active user we can reference across all tests
    private User activeUser;

    // A reusable pageable for pagination tests
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setFirstName("John");
        activeUser.setLastName("Doe");
        activeUser.setEmail("john.doe@example.com");
        activeUser.setPhoneNumber("08012345678");
        activeUser.setStatus(AccountStatus.ACTIVE);
        activeUser.setKycVerified(false);
        activeUser.setRoles(Set.of());

        pageable = PageRequest.of(0, 10);
    }


    // fetchAllUsersForAdmin

    @Nested
    @DisplayName("fetchAllUsersForAdmin")
    class FetchAllUsersForAdmin {

        @Test
        @DisplayName("Returns a paginated list of users when users exist in the database")
        void returnsPaginatedUsers_whenUsersExist() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);
            when(adminRepository.findAllWithRoles(pageable)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.fetchAllUsersForAdmin(pageable);

            assertThat(result).isNotNull();
            assertThat(result.getData()).hasSize(1);
            assertThat(result.getPagination().getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("Returns an empty list when no users exist in the database")
        void returnsEmptyList_whenNoUsersExist() {
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(adminRepository.findAllWithRoles(pageable)).thenReturn(emptyPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.fetchAllUsersForAdmin(pageable);

            assertThat(result.getData()).isEmpty();
            assertThat(result.getPagination().getTotalElements()).isZero();
        }

        @Test
        @DisplayName("Returns correct pagination metadata matching the page and size requested")
        void returnsCorrectPaginationMetadata() {
            Pageable secondPage = PageRequest.of(1, 5);
            Page<User> userPage = new PageImpl<>(List.of(activeUser), secondPage, 6);
            when(adminRepository.findAllWithRoles(secondPage)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.fetchAllUsersForAdmin(secondPage);

            assertThat(result.getPagination().getPage()).isEqualTo(1);
            assertThat(result.getPagination().getPageSize()).isEqualTo(5);
            assertThat(result.getPagination().getTotalPages()).isEqualTo(2);
            assertThat(result.getPagination().getTotalElements()).isEqualTo(6);
        }

        @Test
        @DisplayName("Returns multiple users when more than one user exists")
        void returnsMultipleUsers_whenMultipleUsersExist() {
            User secondUser = new User();
            secondUser.setId(2L);
            secondUser.setFirstName("Jane");
            secondUser.setLastName("Smith");
            secondUser.setEmail("jane.smith@example.com");
            secondUser.setStatus(AccountStatus.ACTIVE);
            secondUser.setRoles(Set.of());

            Page<User> userPage = new PageImpl<>(List.of(activeUser, secondUser), pageable, 2);
            when(adminRepository.findAllWithRoles(pageable)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.fetchAllUsersForAdmin(pageable);

            assertThat(result.getData()).hasSize(2);
        }
    }


    // fetchUserByIdForAdmin

    @Nested
    @DisplayName("fetchUserByIdForAdmin")
    class FetchUserByIdForAdmin {

        @Test
        @DisplayName("Returns the user when the given ID exists in the database")
        void returnsUser_whenUserIdExists() {
            when(adminRepository.findWithRolesById(1L))
                    .thenReturn(Optional.of(activeUser));

            AdminUserResponseDto result = adminService.fetchUserByIdForAdmin(1L);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given ID")
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            when(adminRepository.findWithRolesById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.fetchUserByIdForAdmin(99L))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }


    // updateUserStatus

    @Nested
    @DisplayName("updateUserStatus")
    class UpdateUserStatus {

        @Test
        @DisplayName("Updates and returns the user with the new status when a valid status is provided")
        void updatesStatus_whenValidStatusIsProvided() {
            when(adminRepository.findWithRolesById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(adminRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AdminUserResponseDto result = adminService.updateUserStatus(1L, "SUSPENDED");

            assertThat(result).isNotNull();
            verify(adminRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Accepts a lowercase status value and converts it correctly")
        void acceptsLowercaseStatus_andConvertsItCorrectly() {
            when(adminRepository.findWithRolesById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(adminRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // Should not throw — the service calls toUpperCase() before parsing
            AdminUserResponseDto result = adminService.updateUserStatus(1L, "suspended");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when the status value is not a recognised enum constant")
        void throwsIllegalArgumentException_whenStatusIsInvalid() {
            when(adminRepository.findWithRolesById(1L))
                    .thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> adminService.updateUserStatus(1L, "UNKNOWN_STATUS"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid status value");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given ID")
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            when(adminRepository.findWithRolesById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateUserStatus(99L, "ACTIVE"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }


    // searchUsers

    @Nested
    @DisplayName("searchUsers")
    class SearchUsers {

        @Test
        @DisplayName("Returns matching users when a valid keyword is provided")
        void returnsMatchingUsers_whenKeywordIsProvided() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);
            when(adminRepository.searchUsers("john", pageable)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.searchUsers("john", pageable);

            assertThat(result.getData()).hasSize(1);
            verify(adminRepository).searchUsers(eq("john"), eq(pageable));
        }

        @Test
        @DisplayName("Returns all users when the keyword is null — falls back to findAllWithRoles")
        void returnsAllUsers_whenKeywordIsNull() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);
            when(adminRepository.findAllWithRoles(pageable)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.searchUsers(null, pageable);

            assertThat(result.getData()).hasSize(1);
            verify(adminRepository).findAllWithRoles(pageable);
            verify(adminRepository, never()).searchUsers(anyString(), any());
        }

        @Test
        @DisplayName("Returns all users when the keyword is blank — falls back to findAllWithRoles")
        void returnsAllUsers_whenKeywordIsBlank() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);
            when(adminRepository.findAllWithRoles(pageable)).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.searchUsers("   ", pageable);

            assertThat(result.getData()).hasSize(1);
            verify(adminRepository).findAllWithRoles(pageable);
            verify(adminRepository, never()).searchUsers(anyString(), any());
        }

        @Test
        @DisplayName("Defaults to page 0 with size 10 when a null pageable is passed in")
        void defaultsToPageZero_whenPageableIsNull() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser));
            when(adminRepository.findAllWithRoles(any(Pageable.class))).thenReturn(userPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.searchUsers(null, null);

            assertThat(result).isNotNull();
            verify(adminRepository).findAllWithRoles(any(Pageable.class));
        }

        @Test
        @DisplayName("Trims whitespace from the keyword before passing it to the repository")
        void trimsKeyword_beforePassingToRepository() {
            Page<User> userPage = new PageImpl<>(List.of(activeUser), pageable, 1);
            when(adminRepository.searchUsers("john", pageable)).thenReturn(userPage);

            adminService.searchUsers("  john  ", pageable);

            // The keyword should be trimmed before hitting the repository
            verify(adminRepository).searchUsers(eq("john"), eq(pageable));
        }

        @Test
        @DisplayName("Returns an empty list when no users match the given keyword")
        void returnsEmptyList_whenNoUsersMatchKeyword() {
            Page<User> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(adminRepository.searchUsers("xyz", pageable)).thenReturn(emptyPage);

            PaginatedResponse<AdminUserResponseDto> result =
                    adminService.searchUsers("xyz", pageable);

            assertThat(result.getData()).isEmpty();
        }
    }


    // fetchUserByEmail

    @Nested
    @DisplayName("fetchUserByEmail")
    class FetchUserByEmail {

        @Test
        @DisplayName("Returns the user when the email exists in the database")
        void returnsUser_whenEmailExists() {
            when(adminRepository.findWithRolesByEmailIgnoreCase("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));

            AdminUserResponseDto result =
                    adminService.fetchUserByEmail("john.doe@example.com");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        @DisplayName("Throws EmailCannotBeNullException when a null email is passed in")
        void throwsEmailCannotBeNullException_whenEmailIsNull() {
            assertThatThrownBy(() -> adminService.fetchUserByEmail(null))
                    .isInstanceOf(EmailCannotBeNullException.class)
                    .hasMessageContaining("Email must not be null or empty");
        }

        @Test
        @DisplayName("Throws EmailCannotBeNullException when a blank email is passed in")
        void throwsEmailCannotBeNullException_whenEmailIsBlank() {
            assertThatThrownBy(() -> adminService.fetchUserByEmail("   "))
                    .isInstanceOf(EmailCannotBeNullException.class)
                    .hasMessageContaining("Email must not be null or empty");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given email")
        void throwsUserNotFoundException_whenEmailDoesNotExist() {
            when(adminRepository.findWithRolesByEmailIgnoreCase("ghost@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.fetchUserByEmail("ghost@example.com"))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("ghost@example.com");
        }

        @Test
        @DisplayName("Trims whitespace from the email before passing it to the repository")
        void trimsEmail_beforePassingToRepository() {
            when(adminRepository.findWithRolesByEmailIgnoreCase("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));

            adminService.fetchUserByEmail("  john.doe@example.com  ");

            verify(adminRepository).findWithRolesByEmailIgnoreCase("john.doe@example.com");
        }
    }


    // updateKycVerified

    @Nested
    @DisplayName("updateKycVerified")
    class UpdateKycVerified {

        @Test
        @DisplayName("Sets KYC verified to true and returns the updated user")
        void setsKycVerifiedTrue_andReturnsUpdatedUser() {
            when(adminRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(adminRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AdminUserResponseDto result = adminService.updateKycVerified(1L, true);

            assertThat(result).isNotNull();
            assertThat(activeUser.isKycVerified()).isTrue();
            verify(adminRepository).save(activeUser);
        }

        @Test
        @DisplayName("Sets KYC verified to false and returns the updated user")
        void setsKycVerifiedFalse_andReturnsUpdatedUser() {
            activeUser.setKycVerified(true);

            when(adminRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(adminRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            AdminUserResponseDto result = adminService.updateKycVerified(1L, false);

            assertThat(result).isNotNull();
            assertThat(activeUser.isKycVerified()).isFalse();
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given ID")
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            when(adminRepository.findById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> adminService.updateKycVerified(99L, true))
                    .isInstanceOf(UserNotFoundException.class)
                    .hasMessageContaining("99");
        }
    }
}