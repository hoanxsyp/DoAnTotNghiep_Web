package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu thêm/sửa từ khóa cấm (canonical 4.20.4/4.20.5). Dùng chung cho POST và PUT.
 *
 * <p><b>Lưu ý bám sát entity {@code BannedKeyword} đã chốt:</b> phạm vi áp dụng là enum
 * {@code BannedKeywordScope = LISTING | COMMENT | BOTH} (đúng canonical §5) chứ không phải tập 7
 * giá trị ở ví dụ 4.20.4; và cách khớp dùng cờ {@code isRegex} (cột {@code is_regex}) thay cho
 * {@code matchType WORD/PHRASE}. Xem phần "điểm quyết định" ở báo cáo bàn giao.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BannedKeywordRequest", description = "Dữ liệu từ khóa cấm")
public class BannedKeywordRequest {

    @NotBlank(message = "Vui lòng nhập từ khóa")
    @Size(min = 2, max = 100, message = "Từ khóa từ 2 đến 100 ký tự")
    @Schema(description = "Từ/cụm từ cấm", example = "cọc trước mới cho xem")
    private String keyword;

    @NotNull(message = "Vui lòng chọn mức độ")
    @Schema(description = "SEVERE = chặn nội dung; MILD = cho qua nhưng chuyển kiểm duyệt", example = "SEVERE")
    private BannedKeywordSeverity severity;

    @NotNull(message = "Vui lòng chọn phạm vi áp dụng")
    @Schema(description = "Phạm vi áp dụng: LISTING | COMMENT | BOTH", example = "BOTH")
    private BannedKeywordScope appliesTo;

    @Schema(description = "Từ khóa là biểu thức chính quy (regex) hay chuỗi thường", example = "false")
    @Builder.Default
    private Boolean isRegex = false;

    @Size(max = 30, message = "Nhóm phân loại tối đa 30 ký tự")
    @Schema(description = "Nhóm/phân loại từ khóa — tùy chọn", example = "scam")
    private String category;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    @Schema(description = "Ghi chú của admin — tùy chọn")
    private String note;

    @Schema(description = "Bật/tắt hiệu lực", example = "true")
    @Builder.Default
    private Boolean active = true;
}
