package com.webtro.modules.ai.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi tin nhắn chatbot (docs/03 mục 7.3.1, §3.15, §9.3). Công khai (khách ẩn danh dùng
 * {@code sessionId}). Độ dài tối đa đọc từ {@code chatbot.message.max_length} (500) — kiểm ở service;
 * bean-validation ở đây chặn biên trị hiển nhiên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ChatbotMessageRequest", description = "Tin nhắn gửi tới chatbot")
public class ChatbotMessageRequest {

    @NotBlank(message = "Vui lòng nhập câu hỏi")
    @Size(max = 500, message = "Câu hỏi tối đa 500 ký tự")
    @Schema(description = "Câu hỏi/nhu cầu của người dùng", example = "tìm phòng trọ quận 1 dưới 4 triệu")
    private String message;

    @Schema(description = "Phiên hội thoại; bỏ trống → tạo mới", example = "1201")
    private Long conversationId;

    @Schema(description = "Định danh khách ẩn danh (UUID v4) — bắt buộc khi chưa đăng nhập",
            example = "3c7d9e0b-2f45-4a18-9e61-5d02b8a7c134")
    private String sessionId;

    @Schema(description = "Bắt đầu lại nhu cầu (bỏ slot đã thu thập)", example = "false")
    private Boolean resetContext;
}
