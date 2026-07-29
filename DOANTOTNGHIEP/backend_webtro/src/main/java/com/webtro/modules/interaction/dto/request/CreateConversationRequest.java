package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu tạo cuộc trò chuyện — {@code POST /api/conversations} (canonical 4.6.5).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateConversationRequest", description = "Yêu cầu tạo cuộc trò chuyện")
public class CreateConversationRequest {

    @Schema(description = "Id tin đăng làm ngữ cảnh", example = "1024")
    @NotNull(message = "Vui lòng chọn tin đăng cần trao đổi")
    @Positive(message = "Id tin đăng không hợp lệ")
    private Long listingId;

    @Schema(description = "Tin nhắn đầu tiên", example = "Chào anh, phòng còn trống không ạ?")
    @NotBlank(message = "Vui lòng nhập tin nhắn đầu tiên")
    @Size(min = 10, max = 2000, message = "Tin nhắn phải từ 10 đến 2000 ký tự")
    @NoHtml
    private String initialMessage;
}
