package com.gatepay.kycservice.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocumentDto {
    private String documentName;
    private String documentType;
    private String documentUrl;
}
