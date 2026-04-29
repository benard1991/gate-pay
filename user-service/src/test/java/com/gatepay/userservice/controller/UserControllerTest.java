package com.gatepay.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.userservice.config.SecurityConfig;
import com.gatepay.userservice.dto.*;
import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@DisplayName("UserController Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    private UserDto sampleUserDto;

    @BeforeEach
    void setUp() {
        sampleUserDto = new UserDto();
        sampleUserDto.setId(1L);
        sampleUserDto.setFirstName("John");
        sampleUserDto.setLastName("Doe");
        sampleUserDto.setEmail("john.doe@example.com");
        sampleUserDto.setPhoneNumber("08031234567");
        sampleUserDto.setStatus(AccountStatus.ACTIVE);
        sampleUserDto.setAddress("123 Main Street");
        sampleUserDto.setCountry("Nigeria");
        sampleUserDto.setKycVerified(true);
        sampleUserDto.setRoles(List.of("USER"));
    }

    // GET /users?email=

    @Nested
    @DisplayName("GET /users - getUserByEmail")
    class GetUserByEmail {

        @Test
        @DisplayName("Returns the user when a matching email is found")
        void returns_user_when_email_exists() throws Exception {
            when(userService.getUserByEmail("john.doe@example.com")).thenReturn(sampleUserDto);

            mockMvc.perform(get("/users")
                            .param("email", "john.doe@example.com"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User fetched successfully"))
                    .andExpect(jsonPath("$.data.email").value("john.doe@example.com"))
                    .andExpect(jsonPath("$.data.firstName").value("John"))
                    .andExpect(jsonPath("$.data.lastName").value("Doe"));

            verify(userService, times(1)).getUserByEmail("john.doe@example.com");
        }

        @Test
        @DisplayName("Fails with 400 when the email query param is missing")
        void fails_when_email_param_is_not_provided() throws Exception {
            mockMvc.perform(get("/users"))
                    .andExpect(status().isBadRequest());
        }
    }

    // GET /users/{userId}/profile

    @Nested
    @DisplayName("GET /users/{userId}/profile - getUserProfile")
    class GetUserProfile {

        @Test
        @DisplayName("Returns the full profile for a valid user ID")
        void returns_user_profile_for_valid_user_id() throws Exception {
            when(userService.getUserProfile(1L)).thenReturn(sampleUserDto);

            mockMvc.perform(get("/users/1/profile"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User fetched successfully"))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.email").value("john.doe@example.com"));

            verify(userService, times(1)).getUserProfile(1L);
        }

        @Test
        @DisplayName("Fails with 400 when the user ID in the path is not a number")
        void fails_when_user_id_is_not_a_number() throws Exception {
            mockMvc.perform(get("/users/abc/profile"))
                    .andExpect(status().isBadRequest());
        }
    }


    // POST /users/register

    @Nested
    @DisplayName("POST /users/register - createUser")
    class CreateUser {

        private UserDto validUserDto;

        @BeforeEach
        void setUp() {
            validUserDto = new UserDto();
            validUserDto.setFirstName("Jane");
            validUserDto.setLastName("Doe");
            validUserDto.setEmail("jane.doe@example.com");
            validUserDto.setPassword("secret123");
            validUserDto.setPhoneNumber("07031234567");
            validUserDto.setCountry("Nigeria");
            validUserDto.setKycVerified(false);
            validUserDto.setRoles(List.of("USER"));
        }

        @Test
        @DisplayName("Registers a new user and returns the saved user with status 201 in the body")
        void registers_new_user_and_returns_saved_user() throws Exception {
            UserDto savedUser = new UserDto();
            savedUser.setId(2L);
            savedUser.setEmail("jane.doe@example.com");
            savedUser.setFirstName("Jane");
            savedUser.setLastName("Doe");
            savedUser.setPhoneNumber("07031234567");
            savedUser.setCountry("Nigeria");
            savedUser.setKycVerified(false);
            savedUser.setRoles(List.of("USER"));

            when(userService.createUser(any(UserDto.class))).thenReturn(savedUser);

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isOk()) // HTTP status is 200 (controller returns body, not ResponseEntity)
                    .andExpect(jsonPath("$.status").value(201))
                    .andExpect(jsonPath("$.message").value("User created successfully"))
                    .andExpect(jsonPath("$.data.email").value("jane.doe@example.com"))
                    .andExpect(jsonPath("$.data.id").value(2));

            verify(userService, times(1)).createUser(any(UserDto.class));
        }

        @Test
        @DisplayName("Fails with 400 when first name is blank")
        void fails_when_first_name_is_blank() throws Exception {
            validUserDto.setFirstName("");

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when last name is blank")
        void fails_when_last_name_is_blank() throws Exception {
            validUserDto.setLastName("");

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when email is not a valid email address")
        void fails_when_email_format_is_invalid() throws Exception {
            validUserDto.setEmail("not-an-email");

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when phone number is not a valid Nigerian mobile number")
        void fails_when_phone_number_format_is_invalid() throws Exception {
            validUserDto.setPhoneNumber("12345");

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when country is blank")
        void fails_when_country_is_blank() throws Exception {
            validUserDto.setCountry("");

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when roles list is null")
        void fails_when_roles_are_null() throws Exception {
            validUserDto.setRoles(null);

            mockMvc.perform(post("/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUserDto)))
                    .andExpect(status().isBadRequest());
        }
    }

    // PUT /users/reset-password

    @Nested
    @DisplayName("PUT /users/reset-password - resetPassword")
    class ResetPassword {

        private PasswordUpdateRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new PasswordUpdateRequest();
            validRequest.setEmail("john.doe@example.com");
            validRequest.setNewPassword("newSecret123");
        }

        @Test
        @DisplayName("Resets the password successfully and confirms with a 200 response")
        void resets_password_and_returns_success_message() throws Exception {
            when(userService.updatePassword("john.doe@example.com", "newSecret123")).thenReturn(true);

            mockMvc.perform(put("/users/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("Password updated successfully"));

            verify(userService, times(1)).updatePassword("john.doe@example.com", "newSecret123");
        }

        @Test
        @DisplayName("Returns 500 when the password update fails internally")
        void returns_500_when_password_update_fails() throws Exception {
            when(userService.updatePassword(anyString(), anyString())).thenReturn(false);

            mockMvc.perform(put("/users/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.status").value(500))
                    .andExpect(jsonPath("$.message").value("Failed to update password"));
        }

        @Test
        @DisplayName("Fails with 400 when email is blank")
        void fails_when_email_is_blank() throws Exception {
            validRequest.setEmail("");

            mockMvc.perform(put("/users/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when email is not a valid email address")
        void fails_when_email_format_is_invalid() throws Exception {
            validRequest.setEmail("bad-email");

            mockMvc.perform(put("/users/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when new password is blank")
        void fails_when_new_password_is_blank() throws Exception {
            validRequest.setNewPassword("");

            mockMvc.perform(put("/users/reset-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    // PUT /users/change-password

    @Nested
    @DisplayName("PUT /users/change-password - changePassword")
    class ChangePassword {

        private ChangePasswordRequest validRequest;

        @BeforeEach
        void setUp() {
            validRequest = new ChangePasswordRequest();
            validRequest.setEmail("john.doe@example.com");
            validRequest.setOldPassword("oldSecret123");
            validRequest.setNewPassword("newSecret123");
            validRequest.setConfirmPassword("newSecret123");
        }

        @Test
        @DisplayName("Changes the password successfully and returns a confirmation message")
        void changes_password_and_returns_success_message() throws Exception {
            when(userService.changePassword(any(ChangePasswordRequest.class))).thenReturn(true);

            mockMvc.perform(put("/users/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("Password changed successfully"));

            verify(userService, times(1)).changePassword(any(ChangePasswordRequest.class));
        }

        @Test
        @DisplayName("Fails with 400 when email is blank")
        void fails_when_email_is_blank() throws Exception {
            validRequest.setEmail("");

            mockMvc.perform(put("/users/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when old password is blank")
        void fails_when_old_password_is_blank() throws Exception {
            validRequest.setOldPassword("");

            mockMvc.perform(put("/users/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when new password is blank")
        void fails_when_new_password_is_blank() throws Exception {
            validRequest.setNewPassword("");

            mockMvc.perform(put("/users/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Fails with 400 when confirm password is blank")
        void fails_when_confirm_password_is_blank() throws Exception {
            validRequest.setConfirmPassword("");

            mockMvc.perform(put("/users/change-password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest)))
                    .andExpect(status().isBadRequest());
        }
    }

    // PUT /users/{userId}/profile

    @Nested
    @DisplayName("PUT /users/{userId}/profile - updateUserProfile")
    class UpdateUserProfile {

        private UpdateUserDto validUpdateDto;

        @BeforeEach
        void setUp() {
            validUpdateDto = UpdateUserDto.builder()
                    .firstName("UpdatedFirst")
                    .lastName("UpdatedLast")
                    .email("updated@example.com")
                    .phoneNumber("09012345678")
                    .address("456 New Street")
                    .country("Nigeria")
                    .active(true)
                    .status(AccountStatus.ACTIVE)
                    .kycVerified(true)
                    .roles(List.of("USER", "ADMIN"))
                    .build();
        }

        @Test
        @DisplayName("Updates the user profile and returns the updated details")
        void updates_profile_and_returns_updated_user() throws Exception {
            UserDto updatedUser = new UserDto();
            updatedUser.setId(1L);
            updatedUser.setFirstName("UpdatedFirst");
            updatedUser.setLastName("UpdatedLast");
            updatedUser.setEmail("updated@example.com");
            updatedUser.setPhoneNumber("09012345678");
            updatedUser.setCountry("Nigeria");
            updatedUser.setKycVerified(true);
            updatedUser.setRoles(List.of("USER", "ADMIN"));

            when(userService.updateUserProfile(eq(1L), any(UpdateUserDto.class))).thenReturn(updatedUser);

            mockMvc.perform(put("/users/1/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateDto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User profile updated successfully"))
                    .andExpect(jsonPath("$.data.firstName").value("UpdatedFirst"))
                    .andExpect(jsonPath("$.data.email").value("updated@example.com"));

            verify(userService, times(1)).updateUserProfile(eq(1L), any(UpdateUserDto.class));
        }

        @Test
        @DisplayName("Fails with 400 when the user ID in the path is not a number")
        void fails_when_user_id_is_not_a_number() throws Exception {
            mockMvc.perform(put("/users/abc/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validUpdateDto)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Applies a partial update when only some fields are provided")
        void applies_partial_update_when_only_some_fields_are_provided() throws Exception {
            UpdateUserDto partialUpdate = UpdateUserDto.builder()
                    .firstName("PartialFirst")
                    .build();

            UserDto updatedUser = new UserDto();
            updatedUser.setId(1L);
            updatedUser.setFirstName("PartialFirst");
            updatedUser.setLastName("Doe");
            updatedUser.setEmail("john.doe@example.com");
            updatedUser.setPhoneNumber("08031234567");
            updatedUser.setCountry("Nigeria");
            updatedUser.setKycVerified(true);
            updatedUser.setRoles(List.of("" +
                    "USER"));

            when(userService.updateUserProfile(eq(1L), any(UpdateUserDto.class))).thenReturn(updatedUser);

            mockMvc.perform(put("/users/1/profile")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(partialUpdate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.firstName").value("PartialFirst"));
        }
    }
}