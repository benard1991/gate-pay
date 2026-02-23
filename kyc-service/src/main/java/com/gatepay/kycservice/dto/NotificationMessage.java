package com.gatepay.kycservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessage {
    private String to;
    private String type;
    private String subject;
    private String body;
    private Map<String, Object> data;
}
