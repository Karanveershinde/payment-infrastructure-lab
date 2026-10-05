package com.ledgerx.common.dto.request;

import com.ledgerx.common.domain.enums.DiscrepancyResolutionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveDiscrepancyRequest {

    @NotNull(message = "resolutionStatus is required")
    private DiscrepancyResolutionStatus resolutionStatus;

    @NotBlank(message = "resolutionNotes are required")
    private String resolutionNotes;

    @NotBlank(message = "resolvedBy is required")
    private String resolvedBy;
}
