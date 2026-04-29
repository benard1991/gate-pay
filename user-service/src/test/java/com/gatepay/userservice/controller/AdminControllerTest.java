package com.gatepay.userservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatepay.userservice.config.SecurityConfig;
import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.dto.PaginationResponse;
import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.exception.EmailCannotBeNullException;
import com.gatepay.userservice.exception.GlobalExceptionHandler;
import com.gatepay.userservice.exception.UserNotFoundException;
import com.gatepay.userservice.mapper.PaginatedResponse;
import com.gatepay.userservice.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("AdminController Unit Tests")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    // A reusable response DTO we can reference across all tests
    private AdminUserResponseDto adminUserResponseDto;

    // A reusable paginated response for list endpoint tests
    private PaginatedResponse<AdminUserResponseDto> paginatedResponse;

    @BeforeEach
    void setUp() {
        // Build a standard user response DTO for all tests to use
        adminUserResponseDto = AdminUserResponseDto.builder()
                .id(1L)
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phoneNumber("08012345678")
                .status(AccountStatus.ACTIVE)
                .kycVerified(false)
                .roles(List.of("ROLE_USER"))
                .createdAt(LocalDateTime.now())
                .build();

        // Build a standard paginated wrapper for list endpoint tests
        PaginationResponse pagination = new PaginationResponse(10, 0, 1, 1);
        paginatedResponse = new PaginatedResponse<>(List.of(adminUserResponseDto), pagination);
    }


    // GET /users/admin/getAllUser

    @Nested
    @DisplayName("GET /users/admin/getAllUser")
    class GetAllUsers {

        @Test
        @DisplayName("Returns 200 and a paginated list of users")
        void returns200_withPaginatedUsers() throws Exception {
            when(adminService.fetchAllUsersForAdmin(any(Pageable.class)))
                    .thenReturn(paginatedResponse);

            mockMvc.perform(get("/users/admin/getAllUser")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].email").value("john.doe@example.com"))
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }

        @Test
        @DisplayName("Returns 200 with an empty list when no users exist")
        void returns200_withEmptyList_whenNoUsersExist() throws Exception {
            PaginationResponse pagination = new PaginationResponse(10, 0, 0, 0);
            PaginatedResponse<AdminUserResponseDto> emptyResponse =
                    new PaginatedResponse<>(List.of(), pagination);

            when(adminService.fetchAllUsersForAdmin(any(Pageable.class)))
                    .thenReturn(emptyResponse);

            mockMvc.perform(get("/users/admin/getAllUser")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("Calls the service exactly once when the endpoint is hit")
        void callsServiceExactlyOnce() throws Exception {
            when(adminService.fetchAllUsersForAdmin(any(Pageable.class)))
                    .thenReturn(paginatedResponse);

            mockMvc.perform(get("/users/admin/getAllUser")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(adminService, times(1)).fetchAllUsersForAdmin(any(Pageable.class));
        }

        @Test
        @DisplayName("Returns 500 when the service throws an unexpected exception")
        void returns500_whenServiceThrowsUnexpectedException() throws Exception {
            when(adminService.fetchAllUsersForAdmin(any(Pageable.class)))
                    .thenThrow(new RuntimeException("Unexpected error"));

            mockMvc.perform(get("/users/admin/getAllUser")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("GEN_500"));
        }
    }


    // GET /users/admin/getUser/{userId}

    @Nested
    @DisplayName("GET /users/admin/getUser/{userId}")
    class GetUserById {

        @Test
        @DisplayName("Returns 200 and the user when the user ID exists")
        void returns200_withUser_whenUserIdExists() throws Exception {
            when(adminService.fetchUserByIdForAdmin(1L))
                    .thenReturn(adminUserResponseDto);

            mockMvc.perform(get("/users/admin/getUser/1")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User fetched successfully"))
                    .andExpect(jsonPath("$.data.email").value("john.doe@example.com"))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("Returns 404 with error code USR_001 when no user matches the given ID")
        void returns404_whenUserIdDoesNotExist() throws Exception {
            when(adminService.fetchUserByIdForAdmin(99L))
                    .thenThrow(new UserNotFoundException("User not found with ID: 99"));

            mockMvc.perform(get("/users/admin/getUser/99")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USR_001"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Returns 400 when the user ID in the path is not a valid number")
        void returns400_whenUserIdIsNotANumber() throws Exception {
            mockMvc.perform(get("/users/admin/getUser/abc")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN_400"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }


    // PUT /users/admin/{userId}/updateStatus

    @Nested
    @DisplayName("PUT /users/admin/{userId}/updateStatus")
    class UpdateUserStatus {

        @Test
        @DisplayName("Returns 200 and the updated user when a valid status is provided")
        void returns200_withUpdatedUser_whenStatusIsValid() throws Exception {
            AdminUserResponseDto suspended = AdminUserResponseDto.builder()
                    .id(1L)
                    .email("john.doe@example.com")
                    .status(AccountStatus.SUSPENDED)
                    .build();

            when(adminService.updateUserStatus(1L, "SUSPENDED")).thenReturn(suspended);

            mockMvc.perform(put("/users/admin/1/updateStatus")
                            .param("status", "SUSPENDED")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User status updated successfully"))
                    .andExpect(jsonPath("$.data.status").value("SUSPENDED"));
        }

        @Test
        @DisplayName("Converts the status to uppercase before passing it to the service")
        void convertsStatusToUppercase_beforeCallingService() throws Exception {
            when(adminService.updateUserStatus(1L, "SUSPENDED"))
                    .thenReturn(adminUserResponseDto);

            // The controller calls status.toUpperCase() so "suspended" becomes "SUSPENDED"
            mockMvc.perform(put("/users/admin/1/updateStatus")
                            .param("status", "suspended")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(adminService).updateUserStatus(1L, "SUSPENDED");
        }

        @Test
        @DisplayName("Returns 404 with error code USR_001 when no user matches the given ID")
        void returns404_whenUserIdDoesNotExist() throws Exception {
            when(adminService.updateUserStatus(eq(99L), anyString()))
                    .thenThrow(new UserNotFoundException("User not found with ID: 99"));

            mockMvc.perform(put("/users/admin/99/updateStatus")
                            .param("status", "ACTIVE")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USR_001"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Returns 500 with error code GEN_500 when an invalid status value is provided")
        void returns500_whenStatusValueIsInvalid() throws Exception {
            when(adminService.updateUserStatus(eq(1L), anyString()))
                    .thenThrow(new IllegalArgumentException("Invalid status value: INVALID_STATUS"));

            // IllegalArgumentException hits the fallback handler which returns 500
            mockMvc.perform(put("/users/admin/1/updateStatus")
                            .param("status", "INVALID_STATUS")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("GEN_500"));
        }

        @Test
        @DisplayName("Returns 400 when the status query parameter is missing from the request")
        void returns400_whenStatusParamIsMissing() throws Exception {
            mockMvc.perform(put("/users/admin/1/updateStatus")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN_400"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }


    // PUT /users/admin/{userId}/updateKycVerified

    @Nested
    @DisplayName("PUT /users/admin/{userId}/updateKycVerified")
    class UpdateKycVerified {

        @Test
        @DisplayName("Returns 200 and the updated user when kycVerified is set to true")
        void returns200_whenKycVerifiedSetToTrue() throws Exception {
            AdminUserResponseDto kycVerifiedUser = AdminUserResponseDto.builder()
                    .id(1L)
                    .email("john.doe@example.com")
                    .kycVerified(true)
                    .build();

            when(adminService.updateKycVerified(1L, true)).thenReturn(kycVerifiedUser);

            mockMvc.perform(put("/users/admin/1/updateKycVerified")
                            .param("kycVerified", "true")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User KYC verification updated successfully"))
                    .andExpect(jsonPath("$.data.kycVerified").value(true));
        }

        @Test
        @DisplayName("Returns 200 and the updated user when kycVerified is set to false")
        void returns200_whenKycVerifiedSetToFalse() throws Exception {
            when(adminService.updateKycVerified(1L, false)).thenReturn(adminUserResponseDto);

            mockMvc.perform(put("/users/admin/1/updateKycVerified")
                            .param("kycVerified", "false")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.kycVerified").value(false));
        }

        @Test
        @DisplayName("Returns 404 with error code USR_001 when no user matches the given ID")
        void returns404_whenUserIdDoesNotExist() throws Exception {
            when(adminService.updateKycVerified(99L, true))
                    .thenThrow(new UserNotFoundException("User not found | id=99"));

            mockMvc.perform(put("/users/admin/99/updateKycVerified")
                            .param("kycVerified", "true")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USR_001"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Returns 400 when the kycVerified query parameter is missing from the request")
        void returns400_whenKycVerifiedParamIsMissing() throws Exception {
            mockMvc.perform(put("/users/admin/1/updateKycVerified")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("GEN_400"))
                    .andExpect(jsonPath("$.status").value(400));
        }
    }


    // GET /users/admin/search

    @Nested
    @DisplayName("GET /users/admin/search")
    class SearchUsers {

        @Test
        @DisplayName("Returns 200 and matching users when a keyword is provided")
        void returns200_withMatchingUsers_whenKeywordIsProvided() throws Exception {
            when(adminService.searchUsers(eq("john"), any(Pageable.class)))
                    .thenReturn(paginatedResponse);

            mockMvc.perform(get("/users/admin/search")
                            .param("keyword", "john")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].email").value("john.doe@example.com"));
        }

        @Test
        @DisplayName("Returns 200 and all users when no keyword is provided")
        void returns200_withAllUsers_whenNoKeywordProvided() throws Exception {
            when(adminService.searchUsers(isNull(), any(Pageable.class)))
                    .thenReturn(paginatedResponse);

            // keyword is marked as not required so omitting it should still return 200
            mockMvc.perform(get("/users/admin/search")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Returns 200 with an empty list when the keyword matches no users")
        void returns200_withEmptyList_whenKeywordMatchesNoUsers() throws Exception {
            PaginationResponse pagination = new PaginationResponse(10, 0, 0, 0);
            PaginatedResponse<AdminUserResponseDto> emptyResponse =
                    new PaginatedResponse<>(List.of(), pagination);

            when(adminService.searchUsers(eq("xyz"), any(Pageable.class)))
                    .thenReturn(emptyResponse);

            mockMvc.perform(get("/users/admin/search")
                            .param("keyword", "xyz")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isEmpty());
        }

        @Test
        @DisplayName("Calls the service exactly once with the keyword and pageable from the request")
        void callsServiceExactlyOnce_withKeywordAndPageable() throws Exception {
            when(adminService.searchUsers(eq("john"), any(Pageable.class)))
                    .thenReturn(paginatedResponse);

            mockMvc.perform(get("/users/admin/search")
                            .param("keyword", "john")
                            .param("page", "0")
                            .param("size", "10")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(adminService, times(1)).searchUsers(eq("john"), any(Pageable.class));
        }
    }


    // GET /users/admin/email/{email}

    @Nested
    @DisplayName("GET /users/admin/email/{email}")
    class GetUserByEmail {

        @Test
        @DisplayName("Returns 200 and the user when the email exists")
        void returns200_withUser_whenEmailExists() throws Exception {
            when(adminService.fetchUserByEmail("john.doe@example.com"))
                    .thenReturn(adminUserResponseDto);

            mockMvc.perform(get("/users/admin/email/john.doe@example.com")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(200))
                    .andExpect(jsonPath("$.message").value("User fetched successfully"))
                    .andExpect(jsonPath("$.data.email").value("john.doe@example.com"));
        }

        @Test
        @DisplayName("Returns 404 with error code USR_001 when no user matches the given email")
        void returns404_whenEmailDoesNotExist() throws Exception {
            when(adminService.fetchUserByEmail("ghost@example.com"))
                    .thenThrow(new UserNotFoundException("User not found with email: ghost@example.com"));

            mockMvc.perform(get("/users/admin/email/ghost@example.com")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USR_001"))
                    .andExpect(jsonPath("$.status").value(404));
        }

        @Test
        @DisplayName("Returns 400 with error code AUTH_004 when the service rejects the email as empty")
        void returns400_whenServiceRejectsEmailAsEmpty() throws Exception {
            // Simulate the service rejecting a value it considers blank internally
            when(adminService.fetchUserByEmail("empty@test.com"))
                    .thenThrow(new EmailCannotBeNullException("Email must not be null or empty"));

            mockMvc.perform(get("/users/admin/email/empty@test.com")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("AUTH_004"))
                    .andExpect(jsonPath("$.status").value(400));
        }

        @Test
        @DisplayName("Calls the service with the exact email from the URL path")
        void callsService_withExactEmailFromUrl() throws Exception {
            when(adminService.fetchUserByEmail("john.doe@example.com"))
                    .thenReturn(adminUserResponseDto);

            mockMvc.perform(get("/users/admin/email/john.doe@example.com")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            verify(adminService).fetchUserByEmail("john.doe@example.com");
        }
    }
}