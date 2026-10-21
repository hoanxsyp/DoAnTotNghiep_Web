package com.webtro.modules.rental.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateViewingDateRequest {

    @NotNull
    @FutureOrPresent(message = "Ngày xem phòng không được ở quá khứ")
    private LocalDate desiredViewingDate;
}
