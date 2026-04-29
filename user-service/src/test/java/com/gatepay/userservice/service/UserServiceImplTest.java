package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.ChangePasswordRequest;
import com.gatepay.userservice.dto.UpdateUserDto;
import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.enums.RoleEnum;
import com.gatepay.userservice.exception.AccountDisabledException;
import com.gatepay.userservice.exception.InvalidPasswordException;
import com.gatepay.userservice.exception.UserAlreadyExistsException;
import com.gatepay.userservice.exception.UserNotFoundException;
import com.gatepay.userservice.model.Role;
import com.gatepay.userservice.model.User;
import com.gatepay.userservice.repository.RoleRepository;
import com.gatepay.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl Unit Tests")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserServiceImpl userService;

    // A reusable active user we can reference across tests
    private User activeUser;

    // A reusable UserDto for creation tests
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        // Build a standard active user for all tests to use
        activeUser = new User();
        activeUser.setId(1L);
        activeUser.setFirstName("John");
        activeUser.setLastName("Doe");
        activeUser.setEmail("john.doe@example.com");
        activeUser.setPassword("encodedPassword123");
        activeUser.setPhoneNumber("08012345678");
        activeUser.setStatus(AccountStatus.ACTIVE);
        activeUser.setKycVerified(false);
        activeUser.setRoles(Set.of());

        // Build a standard UserDto for creation tests
        userDto = new UserDto();
        userDto.setFirstName("John");
        userDto.setLastName("Doe");
        userDto.setEmail("john.doe@example.com");
        userDto.setPassword("plainPassword123");
        userDto.setPhoneNumber("08012345678");
        userDto.setStatus(AccountStatus.ACTIVE);
        userDto.setKycVerified(false);
    }


    // getUserByEmail

    @Nested
    @DisplayName("getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("Returns the user DTO when the email exists in the database")
        void returnsUserDto_whenEmailExists() {
            when(userRepository.findWithRolesByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));

            UserDto result = userService.getUserByEmail("john.doe@example.com");

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
            assertThat(result.getFirstName()).isEqualTo("John");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the email")
        void throwsUserNotFoundException_whenEmailDoesNotExist() {
            when(userRepository.findWithRolesByEmail(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserByEmail("ghost@example.com"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }



    // createUser

    @Nested
    @DisplayName("createUser")
    class CreateUser {

        @Test
        @DisplayName("Creates and returns a new user when the email and phone number are not already taken")
        void createsUser_whenEmailAndPhoneAreUnique() {
            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByPhoneNumber(userDto.getPhoneNumber()))
                    .thenReturn(false);
            when(passwordEncoder.encode(userDto.getPassword()))
                    .thenReturn("encodedPassword123");
            when(userRepository.save(any(User.class)))
                    .thenReturn(activeUser);

            UserDto result = userService.createUser(userDto);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when a null UserDto is passed in")
        void throwsIllegalArgumentException_whenUserDtoIsNull() {
            assertThatThrownBy(() -> userService.createUser(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User data must not be null");
        }

        @Test
        @DisplayName("Throws UserAlreadyExistsException when the email is already registered")
        void throwsUserAlreadyExistsException_whenEmailIsTaken() {
            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> userService.createUser(userDto))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(userDto.getEmail());
        }

        @Test
        @DisplayName("Throws UserAlreadyExistsException when the phone number is already registered")
        void throwsUserAlreadyExistsException_whenPhoneNumberIsTaken() {
            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByPhoneNumber(userDto.getPhoneNumber()))
                    .thenReturn(true);

            assertThatThrownBy(() -> userService.createUser(userDto))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining(userDto.getPhoneNumber());
        }

        @Test
        @DisplayName("Assigns an existing role from the database when the role already exists")
        void assignsExistingRole_whenRoleAlreadyExistsInDatabase() {
            userDto.setRoles(List.of("USER"));

            Role existingRole = Role.builder().name(RoleEnum.USER
            ).build();

            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByPhoneNumber(userDto.getPhoneNumber()))
                    .thenReturn(false);
            when(passwordEncoder.encode(anyString()))
                    .thenReturn("encodedPassword123");
            when(roleRepository.findByName(RoleEnum.USER))
                    .thenReturn(Optional.of(existingRole));

            User savedUser = new User();
            savedUser.setId(1L);
            savedUser.setEmail(userDto.getEmail());
            savedUser.setRoles(Set.of(existingRole));
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserDto result = userService.createUser(userDto);

            assertThat(result).isNotNull();
            // Role was found in DB, so it should NOT have been saved again
            verify(roleRepository, never()).save(any(Role.class));
        }

        @Test
        @DisplayName("Creates and saves a new role when the role does not yet exist in the database")
        void createsAndSavesNewRole_whenRoleDoesNotExistInDatabase() {
            userDto.setRoles(List.of("USER"));

            Role newRole = Role.builder().name(RoleEnum.USER).build();

            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByPhoneNumber(userDto.getPhoneNumber()))
                    .thenReturn(false);
            when(passwordEncoder.encode(anyString()))
                    .thenReturn("encodedPassword123");
            when(roleRepository.findByName(RoleEnum.USER))
                    .thenReturn(Optional.empty());
            when(roleRepository.save(any(Role.class)))
                    .thenReturn(newRole);

            User savedUser = new User();
            savedUser.setId(1L);
            savedUser.setEmail(userDto.getEmail());
            savedUser.setRoles(Set.of(newRole));
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            UserDto result = userService.createUser(userDto);

            assertThat(result).isNotNull();
            // Role did not exist, so it should have been saved
            verify(roleRepository).save(any(Role.class));
        }

        @Test
        @DisplayName("Encodes the plain-text password before saving the user")
        void encodesPassword_beforeSavingUser() {
            when(userRepository.findWithRolesByEmail(userDto.getEmail()))
                    .thenReturn(Optional.empty());
            when(userRepository.existsByPhoneNumber(userDto.getPhoneNumber()))
                    .thenReturn(false);
            when(passwordEncoder.encode("plainPassword123"))
                    .thenReturn("encodedPassword123");
            when(userRepository.save(any(User.class)))
                    .thenReturn(activeUser);

            userService.createUser(userDto);

            verify(passwordEncoder).encode("plainPassword123");
        }
    }


    // getUserProfile

    @Nested
    @DisplayName("getUserProfile")
    class GetUserProfile {

        @Test
        @DisplayName("Returns the user profile when the user ID exists and the account is active")
        void returnsUserProfile_whenUserExistsAndIsActive() {
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));

            UserDto result = userService.getUserProfile(1L);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("john.doe@example.com");
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when a null user ID is passed in")
        void throwsIllegalArgumentException_whenUserIdIsNull() {
            assertThatThrownBy(() -> userService.getUserProfile(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("userId cannot be null");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given ID")
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            when(userRepository.findById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.getUserProfile(99L))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Throws AccountDisabledException when the account is disabled")
        void throwsAccountDisabledException_whenAccountIsDisabled() {
            activeUser.setStatus(AccountStatus.DISABLED);
            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> userService.getUserProfile(1L))
                    .isInstanceOf(AccountDisabledException.class);
        }
    }


    // updatePassword

    @Nested
    @DisplayName("updatePassword")
    class UpdatePassword {

        @Test
        @DisplayName("Returns true and updates the password when the user exists and the account is active")
        void returnsTrue_whenPasswordUpdatedSuccessfully() {
            when(userRepository.findWithRolesByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.encode("newPassword123"))
                    .thenReturn("encodedNewPassword123");
            when(userRepository.updatePassword("john.doe@example.com", "encodedNewPassword123"))
                    .thenReturn(1);

            boolean result = userService.updatePassword("john.doe@example.com", "newPassword123");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Returns false when the database update affects zero rows")
        void returnsFalse_whenDatabaseUpdateAffectsZeroRows() {
            when(userRepository.findWithRolesByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.encode(anyString()))
                    .thenReturn("encodedNewPassword123");
            when(userRepository.updatePassword(anyString(), anyString()))
                    .thenReturn(0);

            boolean result = userService.updatePassword("john.doe@example.com", "newPassword123");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Throws UserNotFoundException when the email does not match any account")
        void throwsUserNotFoundException_whenEmailDoesNotExist() {
            when(userRepository.findWithRolesByEmail(anyString()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updatePassword("ghost@example.com", "newPassword123"))
                    .isInstanceOf(UserNotFoundException.class);
        }
    }


    // changePassword

    @Nested
    @DisplayName("changePassword")
    class ChangePassword {

        private ChangePasswordRequest buildRequest(String oldPassword, String newPassword, String confirmPassword) {
            ChangePasswordRequest request = new ChangePasswordRequest();
            request.setEmail("john.doe@example.com");
            request.setOldPassword(oldPassword);
            request.setNewPassword(newPassword);
            request.setConfirmPassword(confirmPassword);
            return request;
        }

        @Test
        @DisplayName("Returns true when the old password matches and the new passwords are identical")
        void returnsTrue_whenPasswordChangedSuccessfully() {
            ChangePasswordRequest request = buildRequest("oldPassword123", "newPassword123", "newPassword123");

            when(userRepository.findByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("oldPassword123", "encodedPassword123"))
                    .thenReturn(true);
            when(passwordEncoder.encode("newPassword123"))
                    .thenReturn("encodedNewPassword123");
            when(userRepository.save(any(User.class)))
                    .thenReturn(activeUser);

            boolean result = userService.changePassword(request);

            assertThat(result).isTrue();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Throws InvalidPasswordException when the old password does not match")
        void throwsInvalidPasswordException_whenOldPasswordIsWrong() {
            ChangePasswordRequest request = buildRequest("wrongOldPassword", "newPassword123", "newPassword123");

            when(userRepository.findByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("wrongOldPassword", "encodedPassword123"))
                    .thenReturn(false);

            assertThatThrownBy(() -> userService.changePassword(request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("Old password is incorrect");
        }

        @Test
        @DisplayName("Throws InvalidPasswordException when the new password and confirm password do not match")
        void throwsInvalidPasswordException_whenNewPasswordsDoNotMatch() {
            ChangePasswordRequest request = buildRequest("oldPassword123", "newPassword123", "differentPassword");

            when(userRepository.findByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));
            when(passwordEncoder.matches("oldPassword123", "encodedPassword123"))
                    .thenReturn(true);

            assertThatThrownBy(() -> userService.changePassword(request))
                    .isInstanceOf(InvalidPasswordException.class)
                    .hasMessageContaining("do not match");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when the email does not belong to any account")
        void throwsUserNotFoundException_whenEmailDoesNotExist() {
            ChangePasswordRequest request = buildRequest("oldPassword123", "newPassword123", "newPassword123");
            request.setEmail("ghost@example.com");

            when(userRepository.findByEmail("ghost@example.com"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.changePassword(request))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Throws AccountDisabledException when the account is disabled")
        void throwsAccountDisabledException_whenAccountIsDisabled() {
            activeUser.setStatus(AccountStatus.DISABLED);
            ChangePasswordRequest request = buildRequest("oldPassword123", "newPassword123", "newPassword123");

            when(userRepository.findByEmail("john.doe@example.com"))
                    .thenReturn(Optional.of(activeUser));

            assertThatThrownBy(() -> userService.changePassword(request))
                    .isInstanceOf(AccountDisabledException.class);
        }
    }


    // -------------------------------------------------------------------------
    // updateUserProfile
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("updateUserProfile")
    class UpdateUserProfile {

        @Test
        @DisplayName("Updates and returns the user profile when the user exists and the account is active")
        void updatesProfile_whenUserExistsAndIsActive() {
            UpdateUserDto updateDto = new UpdateUserDto();
            updateDto.setFirstName("Jane");
            updateDto.setLastName("Smith");

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(activeUser);

            UserDto result = userService.updateUserProfile(1L, updateDto);

            assertThat(result).isNotNull();
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("Throws IllegalArgumentException when a null user ID is passed in")
        void throwsIllegalArgumentException_whenUserIdIsNull() {
            UpdateUserDto updateDto = new UpdateUserDto();

            assertThatThrownBy(() -> userService.updateUserProfile(null, updateDto))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("User ID cannot be null");
        }

        @Test
        @DisplayName("Throws UserNotFoundException when no user matches the given ID")
        void throwsUserNotFoundException_whenUserIdDoesNotExist() {
            UpdateUserDto updateDto = new UpdateUserDto();

            when(userRepository.findById(99L))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> userService.updateUserProfile(99L, updateDto))
                    .isInstanceOf(UserNotFoundException.class);
        }

        @Test
        @DisplayName("Throws UserAlreadyExistsException when the new phone number belongs to another account")
        void throwsUserAlreadyExistsException_whenPhoneNumberIsTakenByAnotherUser() {
            UpdateUserDto updateDto = new UpdateUserDto();
            updateDto.setPhoneNumber("08099999999"); // different from the user's current number

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.existsByPhoneNumber("08099999999"))
                    .thenReturn(true);

            assertThatThrownBy(() -> userService.updateUserProfile(1L, updateDto))
                    .isInstanceOf(UserAlreadyExistsException.class)
                    .hasMessageContaining("Phone number already in use");
        }

        @Test
        @DisplayName("Skips the phone number uniqueness check when the number has not changed")
        void skipsPhoneNumberCheck_whenPhoneNumberIsUnchanged() {
            UpdateUserDto updateDto = new UpdateUserDto();
            updateDto.setPhoneNumber("08012345678"); // same as the user's current number

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.save(any(User.class)))
                    .thenReturn(activeUser);

            userService.updateUserProfile(1L, updateDto);

            // The phone number didn't change, so we should never hit the DB to check uniqueness
            verify(userRepository, never()).existsByPhoneNumber(anyString());
        }

        @Test
        @DisplayName("Only updates fields that are provided and leaves null fields unchanged")
        void updatesOnlyProvidedFields_andLeavesNullFieldsUnchanged() {
            UpdateUserDto updateDto = new UpdateUserDto();
            updateDto.setFirstName("Jane"); // only first name is changing

            when(userRepository.findById(1L))
                    .thenReturn(Optional.of(activeUser));
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            userService.updateUserProfile(1L, updateDto);

            // Last name was not provided in the update, so it should still be "Doe"
            assertThat(activeUser.getFirstName()).isEqualTo("Jane");
            assertThat(activeUser.getLastName()).isEqualTo("Doe");
        }
    }
}