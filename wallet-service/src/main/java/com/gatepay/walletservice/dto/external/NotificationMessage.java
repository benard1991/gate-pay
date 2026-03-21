package com.gatepay.walletservice.dto.external;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NotificationMessage implements Serializable {
    private String recipient;
    private String type;
    private String subject;
    private String body;
    private Map<String, Object> metadata;
}