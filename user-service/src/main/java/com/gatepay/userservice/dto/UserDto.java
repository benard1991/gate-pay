package com.gatepay.userservice.dto;

import com.gatepay.userservice.enums.AccountStatus;
import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
public class UserDto {

    private Long id;

    @NotBlank(message = "First name is required")
    @Size(min = 3, max = 50, message = "First name must be between 3 and 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be between 2 and 50 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @Size(min = 6, max = 100, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Phone number is required")
    @Pattern(
            regexp = "^0[7-9][0-1]\\d{8}$",
            message = "Phone number must be a valid Nigerian mobile number (e.g., 0703XXXXXXX, 0810XXXXXXX)"
    )
    private String phoneNumber;
    private AccountStatus status;

    @Size(max = 255, message = "Address cannot exceed 255 characters")
    private String address;

    @NotBlank(message = "Country is required")
    private String country;


    @NotNull(message = "KYC verification status is required")
    private boolean kycVerified;

    @NotNull(message = "Roles cannot be null")
    private List<String> roles;
}