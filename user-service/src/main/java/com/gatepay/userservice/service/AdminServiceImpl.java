package com.gatepay.userservice.service;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.dto.ApiResponse;
import com.gatepay.userservice.dto.PaginationResponse;
import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.enums.AccountStatus;
import com.gatepay.userservice.exception.EmailCannotBeNullException;
import com.gatepay.userservice.exception.UserNotFoundException;
import com.gatepay.userservice.mapper.AdminMapper;
import com.gatepay.userservice.mapper.PaginatedResponse;
import com.gatepay.userservice.model.User;
import com.gatepay.userservice.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final AdminRepository adminRepository;

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "adminUsers",
            key = "'page=' + #a0.pageNumber + ',size=' + #a0.pageSize + ',sort=' + #a0.sort"
    )

    public PaginatedResponse<AdminUserResponseDto> fetchAllUsersForAdmin(Pageable pageable) {
        Page<User> pageResult = adminRepository.findAllWithRoles(pageable);

        List<AdminUserResponseDto> users = pageResult.getContent()
                .stream()
                .map(AdminMapper::toAdminDto)
                .collect(Collectors.toList());

        PaginationResponse pagination = new PaginationResponse(
                pageResult.getSize(),
                pageResult.getNumber(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );

        return new PaginatedResponse<>(users, pagination);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(value = "adminUserById", key = "#a0", unless = "#result == null")
    public AdminUserResponseDto fetchUserByIdForAdmin(Long userId) {
        User user = adminRepository.findWithRolesById(userId)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with ID: " + userId));

        return AdminMapper.toAdminDto(user);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "adminUserById", key = "#a0"),  // Clear specific user cache
            @CacheEvict(value = "adminUsers", allEntries = true),   // Clear all paginated lists
            @CacheEvict(value = "adminUserSearch", allEntries = true) // Clear all search results
    })
    public AdminUserResponseDto updateUserStatus(Long userId, String status) {
        User user = adminRepository.findWithRolesById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with ID: " + userId));

        try {
            AccountStatus newStatus = AccountStatus.valueOf(status.toUpperCase());
            user.setStatus(newStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status value: " + status);
        }

        User updatedUser = adminRepository.save(user);

        return AdminMapper.toAdminDto(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "adminUserSearch",
            key = "'keyword=' + (#a0 == null ? '' : #a0.trim())"
                    + " + ',page=' + (#a1 == null ? 0 : #a1.pageNumber)"
                    + " + ',size=' + (#a1 == null ? 10 : #a1.pageSize)",
            unless = "#result == null"
    )
    public PaginatedResponse<AdminUserResponseDto> searchUsers(String keyword, Pageable pageable) {

        if (pageable == null) {
            pageable = PageRequest.of(0, 10);
        }

        Page<User> pageResult;

        if (keyword == null || keyword.trim().isEmpty()) {
            pageResult = adminRepository.findAllWithRoles(pageable);
        } else {
            pageResult = adminRepository.searchUsers(keyword.trim(), pageable);
        }

        List<AdminUserResponseDto> users = pageResult.getContent()
                .stream()
                .map(AdminMapper::toAdminDto)
                .collect(Collectors.toList());

        PaginationResponse pagination = new PaginationResponse(
                pageResult.getSize(),
                pageResult.getNumber(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );

        return new PaginatedResponse<>(users, pagination);
    }


    @Override
    @Transactional(readOnly = true)
    @Cacheable(
            value = "adminUserByEmail",
            key = "#email != null ? #email.toLowerCase() : 'null'",
            unless = "#result == null"
    )
    public AdminUserResponseDto fetchUserByEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new EmailCannotBeNullException("Email must not be null or empty");
        }

        User user = adminRepository.findWithRolesByEmailIgnoreCase(email.trim())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with email: " + email));

        return AdminMapper.toAdminDto(user);
    }


    public AdminUserResponseDto updateKycVerified(Long userId, boolean kycVerified) {
        User user = adminRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found | id=" + userId));

        user.setKycVerified(kycVerified);
        adminRepository.save(user);
        return AdminMapper.toAdminDto(user);
    }
}