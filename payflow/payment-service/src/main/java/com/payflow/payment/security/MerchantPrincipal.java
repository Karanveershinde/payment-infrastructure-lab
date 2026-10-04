package com.payflow.payment.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.security.Principal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantPrincipal implements Principal, Serializable {

    private String merchantId;
    private String name;
    private int rateLimitPerMinute;
    private String status;

    @Override
    public String getName() {
        return name;
    }
}
