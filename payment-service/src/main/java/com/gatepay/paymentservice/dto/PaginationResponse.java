package com.gatepay.paymentservice.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaginationResponse<K> {

    private int pageSize;
    private int page;
    private int totalPages;
    private long totalElements;
    private List<K> content;
}
