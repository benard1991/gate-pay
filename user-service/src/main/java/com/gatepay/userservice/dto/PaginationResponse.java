package com.gatepay.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaginationResponse {

    private int pageSize;
    private int page;
    private int totalPages;
    private long totalElements;
}
