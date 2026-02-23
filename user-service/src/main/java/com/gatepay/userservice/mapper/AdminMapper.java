package com.gatepay.userservice.mapper;

import com.gatepay.userservice.dto.AdminUserResponseDto;
import com.gatepay.userservice.model.User;

import java.util.stream.Collectors;

public final class AdminMapper {

    private AdminMapper() {}

    public static AdminUserResponseDto toAdminDto(User user) {
        if (user == null) return null;

        return AdminUserResponseDto.builder()
                .id(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .country(user.getCountry())
                .address(user.getAddress())
                .status(user.getStatus())
                .roles(user.getRoles()
                        .stream()
                        .map(role -> role.getName().name())
                        .collect(Collectors.toList()))
                .status(user.getStatus())
                .kycVerified(user.isKycVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
