package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang bình luận (canonical 4.7.1).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CommentPageResponse", description = "Trang bình luận")
public class CommentPageResponse {

    @JsonUnwrapped
    private PageResponse<CommentResponse> page;
}
