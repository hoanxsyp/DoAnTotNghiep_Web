package com.webtro.modules.interaction.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gửi tin nhắn trong hội thoại — {@code POST /api/conversations/{id}/messages} (canonical 4.6.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SendMessageRequest", description = "Yêu cầu gửi tin nhắn")
public class SendMessageRequest {

    @Schema(description = "Nội dung tin nhắn", example = "Dạ em cảm ơn anh, sáng thứ 7 em qua ạ.")
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    @Size(min = 1, max = 2000, message = "Tin nhắn tối đa 2000 ký tự")
    @NoHtml
    private String content;
}
