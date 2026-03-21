package com.gatepay.walletservice.dto;
import com.gatepay.walletservice.enums.AccountStatus;
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
    private AccountStatus status;
    private List<String> roles;
}
