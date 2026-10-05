package com.ledgerx.common.domain.enums;

public enum AccountType {
    CLEARING,              // Transit/settlement clearing account (Asset)
    SETTLEMENT,            // External bank/provider settled funds (Asset)
    MERCHANT_PAYABLE,      // Merchant balance awaiting payout (Liability)
    CUSTOMER_RECEIVABLE,   // Customer funds pending capture (Asset)
    FEE_REVENUE,           // Platform interchange & fee earnings (Revenue)
    SUSPENSE,              // Unmatched or anomalous funds holding account (Liability/Asset)
    RESERVE                // Merchant rolling dispute reserve (Liability)
}
