package com.gatepay.userservice.service;

import com.gatepay.userservice.enums.AccountStatus;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.mapper.PaginatedResponse;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    public PaginatedResponse<AdminUserResponseDto> fetchAllUsersForAdmin(Pageable pageable);

    AdminUserResponseDto fetchUserByIdForAdmin(Long userId);

    AdminUserResponseDto updateUserStatus(Long userId, String active);

    PaginatedResponse<AdminUserResponseDto> searchUsers(String keyword, Pageable pageable);

    // keyword matches name/email/phone; status filters by account status; both optional
    PaginatedResponse<AdminUserResponseDto> searchUsers(String keyword, AccountStatus status, Pageable pageable);

    AdminUserResponseDto fetchUserByEmail(String email);

     AdminUserResponseDto updateKycVerified(Long userId, boolean kycVerified);

}
