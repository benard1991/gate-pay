package com.gatepay.userservice.mapper;

import com.gatepay.userservice.dto.UserDto;
import com.gatepay.userservice.model.User;

import java.util.stream.Collectors;


public class UserMapper {

    public static UserDto toDto(User user) {
        if (user == null) return null;

        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setEmail(user.getEmail());
        dto.setPassword(user.getPassword()); // you can remove if you don't want to expose
        dto.setPhoneNumber(user.getPhoneNumber());
        dto.setAddress(user.getAddress());
        dto.setCountry(user.getCountry());
        dto.setStatus(user.getStatus());
        dto.setStatus(user.getStatus());
        dto.setKycVerified(user.isKycVerified());

        dto.setRoles(
                user.getRoles()
                        .stream()
                        .map(role -> role.getName().name()) // convert RoleEnum to String
                        .collect(Collectors.toList())
        );

        return dto;
    }
}
