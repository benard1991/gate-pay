package com.gatepay.authservice.dto;

import com.gatepay.authservice.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class UserInfo {
    private Long id;
    private String email;
    private  String firstName;
    private  String lastName;
    private AccountStatus status;
    private List<String> roles;
}