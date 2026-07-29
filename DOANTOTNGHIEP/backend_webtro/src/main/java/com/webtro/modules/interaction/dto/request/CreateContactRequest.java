package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import com.webtro.validator.ValidPhone;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu liên hệ tin — {@code POST /api/listings/{id}/contact} (canonical 4.6.2).
 *
 * <p>{@code message} bắt buộc khi {@code type ∈ {SEND_FORM, START_CHAT}} — kiểm tra ở service
 * (phụ thuộc giá trị field khác nên không đặt được bằng annotation đơn lẻ).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateContactRequest", description = "Yêu cầu liên hệ chủ tin")
public class CreateContactRequest {

    @Schema(description = "Hình thức liên hệ", example = "START_CHAT")
    @NotNull(message = "Vui lòng chọn hình thức liên hệ")
    private ContactChannel type;

    @Schema(description = "Nội dung liên hệ (bắt buộc khi gửi form hoặc chat)",
            example = "Chào anh, phòng còn trống không ạ?")
    @Size(min = 10, max = 1000, message = "Nội dung liên hệ phải từ 10 đến 1000 ký tự")
    @NoHtml
    private String message;

    @Schema(description = "Số điện thoại để chủ trọ gọi lại", example = "0912345678")
    @ValidPhone
    private String callbackPhone;
}
