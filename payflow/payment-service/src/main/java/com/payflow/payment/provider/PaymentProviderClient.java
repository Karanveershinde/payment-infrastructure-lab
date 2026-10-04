package com.payflow.payment.provider;

import com.payflow.common.domain.enums.ProviderType;
import com.payflow.common.dto.provider.ProviderModels;

public interface PaymentProviderClient {
    ProviderModels.ProviderResponse authorize(ProviderType provider, ProviderModels.AuthorizeRequest request);
    ProviderModels.ProviderResponse capture(ProviderType provider, ProviderModels.CaptureRequest request);
    ProviderModels.ProviderResponse refund(ProviderType provider, ProviderModels.RefundRequest request);
    ProviderModels.ProviderResponse getStatus(ProviderType provider, String providerTxnId);
}
