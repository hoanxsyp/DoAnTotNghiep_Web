package com.webtro.modules.rental.dto.request;

import com.webtro.validator.NoHtml;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RentalDecisionRequest {

    @Size(max = 500)
    @NoHtml
    private String note;
}
