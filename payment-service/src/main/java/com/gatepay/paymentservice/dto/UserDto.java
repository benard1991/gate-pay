package com.gatepay.paymentservice.dto;
import com.gatepay.paymentservice.model.enums.AccountStatus;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserDto {
    private Long id;

    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private String phoneNumber;
    private String address;
    private String country;
    private boolean kycVerified;
    private AccountStatus status;
    private List<String> roles;
}
