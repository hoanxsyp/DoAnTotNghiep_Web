package com.webtro.modules.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Body sắp xếp thứ tự hiển thị — mục 4.17.21–4.17.22. Sắp xếp là {@code PUT} trên toàn tập, một
 * giao dịch; phần tử lỗi → rollback tất cả.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReorderRequest", description = "Danh sách thứ tự mới")
public class ReorderRequest {

    @NotEmpty(message = "Danh sách sắp xếp không được rỗng")
    @Size(max = 200, message = "Chỉ được sắp xếp tối đa 200 phần tử mỗi lần")
    @Valid
    @Schema(description = "Danh sách phần tử cần đặt lại thứ tự")
    private List<ReorderItemRequest> items;

    /**
     * Một phần tử trong yêu cầu sắp xếp.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ReorderItemRequest", description = "Thứ tự mới của một phần tử")
    public static class ReorderItemRequest {

        @NotNull(message = "Id không được để trống")
        @Schema(description = "Định danh danh mục/tiện ích", example = "1")
        private Long id;

        @NotNull(message = "Thứ tự hiển thị không được để trống")
        @Min(value = 0, message = "Thứ tự hiển thị nhỏ nhất là 0")
        @Max(value = 999, message = "Thứ tự hiển thị lớn nhất là 999")
        @Schema(description = "Thứ tự mới", example = "1")
        private Integer displayOrder;
    }
}
