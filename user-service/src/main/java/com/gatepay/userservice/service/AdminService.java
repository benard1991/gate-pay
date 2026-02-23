package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.mapper.PaginatedResponse;
import org.springframework.data.domain.Pageable;

public interface AdminService {

    public PaginatedResponse<AdminUserResponseDto> fetchAllUsersForAdmin(Pageable pageable);

    AdminUserResponseDto fetchUserByIdForAdmin(Long userId);

    AdminUserResponseDto updateUserStatus(Long userId, String active);

    PaginatedResponse<AdminUserResponseDto> searchUsers(String keyword, Pageable pageable);

    AdminUserResponseDto fetchUserByEmail(String email);

     AdminUserResponseDto updateKycVerified(Long userId, boolean kycVerified);

}
