package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả sắp xếp thứ tự hiển thị — mục 4.17.21–4.17.22 của {@code docs/03}. Dùng chung cho
 * danh mục và tiện ích.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReorderResultResponse", description = "Kết quả sắp xếp thứ tự hiển thị")
public class ReorderResultResponse {

    @Schema(description = "Số phần tử đã cập nhật", example = "7")
    private Integer updatedCount;

    @Schema(description = "Chi tiết thứ tự mới")
    private List<ReorderItemResponse> items;

    @Schema(description = "Danh sách cache đã xóa")
    private List<String> cacheInvalidated;

    @Schema(description = "Thời điểm cập nhật")
    private Instant updatedAt;

    /**
     * Một phần tử trong kết quả sắp xếp, kèm thứ tự cũ để frontend hiển thị thay đổi.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReorderItemResponse", description = "Một phần tử đã đổi thứ tự")
    public static class ReorderItemResponse {

        @Schema(description = "Định danh", example = "1")
        private Long id;

        @Schema(description = "Mã", example = "BOARDING_HOUSE")
        private String code;

        @Schema(description = "Tên", example = "Phòng trọ")
        private String name;

        @Schema(description = "Thứ tự mới", example = "1")
        private Integer displayOrder;

        @Schema(description = "Thứ tự cũ", example = "2")
        private Integer previousDisplayOrder;
    }
}
