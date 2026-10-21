package com.webtro.modules.rental.dto.request;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateRentalRequest {

    @NotNull
    @Positive
    private Long listingId;

    @NotNull
    @FutureOrPresent(message = "Ngày xem phòng không được ở quá khứ")
    private LocalDate desiredViewingDate;
}
