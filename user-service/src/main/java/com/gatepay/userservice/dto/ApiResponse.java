package com.gatepay.userservice.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private int status;
    private String message;
    private T data;
}
