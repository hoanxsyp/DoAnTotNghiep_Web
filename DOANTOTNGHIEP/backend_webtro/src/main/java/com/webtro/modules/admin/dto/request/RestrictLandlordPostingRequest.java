package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Body cho {@code PUT /api/admin/landlords/{id}/restrict-posting}. Tạm hạn chế chức năng đăng tin
 * của chủ trọ tới thời điểm {@code restrictedUntil}; bắt buộc lý do.
 */
@Getter
@Setter
@Schema(name = "RestrictLandlordPostingRequest", description = "Yêu cầu tạm hạn chế đăng tin của chủ trọ")
public class RestrictLandlordPostingRequest {

    @Schema(description = "Hạn chế đăng tin đến thời điểm này (UTC)", example = "2026-09-01T00:00:00Z")
    @NotNull(message = "Vui lòng chọn thời điểm hết hạn chế")
    @Future(message = "Thời điểm hết hạn chế phải ở tương lai")
    private Instant restrictedUntil;

    @Schema(description = "Lý do hạn chế", example = "Nhiều tin vi phạm, tạm hạn chế đăng tin để rà soát.")
    @NotBlank(message = "Vui lòng nhập lý do hạn chế đăng tin")
    @Size(min = 10, max = 500, message = "Lý do phải từ 10 đến 500 ký tự")
    private String reason;
}
