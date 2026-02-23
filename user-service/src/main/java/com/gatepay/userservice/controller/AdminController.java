package com.gatepay.userservice.controller;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.dto.ApiResponse;
import com.gatepay.userservice.mapper.PaginatedResponse;
import com.gatepay.userservice.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/getAllUser")
    public ResponseEntity<PaginatedResponse<AdminUserResponseDto>> getAllUsers(
            @PageableDefault(page = 0, size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PaginatedResponse<AdminUserResponseDto> response = adminService.fetchAllUsersForAdmin(pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/getUser/{userId}")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> fetchUserByIdForAdmin(@PathVariable("userId") Long userId) {
        AdminUserResponseDto user = adminService.fetchUserByIdForAdmin(userId);
        return ResponseEntity.ok(new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully", user));
    }

    @PutMapping("/{userId}/updateStatus")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> updateUserStatus(@PathVariable("userId") Long userId, @RequestParam("status") String status) {
        AdminUserResponseDto updatedUser = adminService.updateUserStatus(userId, status.toUpperCase());
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "User status updated successfully", updatedUser)
        );
    }


    @PutMapping("/{userId}/updateKycVerified")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> updateKycVerified(
            @PathVariable("userId") Long userId,
            @RequestParam("kycVerified") boolean kycVerified) {
        AdminUserResponseDto updatedUser = adminService.updateKycVerified(userId, kycVerified);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "User KYC verification updated successfully", updatedUser)
        );
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<AdminUserResponseDto>> searchUsers(@RequestParam(name = "keyword", required = false) String keyword, @PageableDefault(page = 0, size = 10) Pageable pageable) {
        PaginatedResponse<AdminUserResponseDto> response = adminService.searchUsers(keyword, pageable);
        return ResponseEntity.ok(response);

    }

    @GetMapping("/email/{email}")
    public ResponseEntity<ApiResponse<AdminUserResponseDto>> getUserByEmail(
            @PathVariable("email") String email
    ) {
        AdminUserResponseDto user = adminService.fetchUserByEmail(email);
        return ResponseEntity.ok(
                new ApiResponse<>(HttpStatus.OK.value(), "User fetched successfully", user)
        );
    }


}
