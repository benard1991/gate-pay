package com.gatepay.userservice.dto;

import com.gatepay.userservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDto {
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private String address;
    private String country;
    private Boolean active;
    private AccountStatus status;
    private Boolean kycVerified;
    private List<String> roles;
}

