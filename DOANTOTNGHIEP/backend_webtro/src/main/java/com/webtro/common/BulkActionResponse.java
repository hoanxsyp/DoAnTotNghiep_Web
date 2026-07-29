package com.webtro.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả thao tác hàng loạt dùng chung: danh sách id xử lý thành công + danh sách id thất bại kèm
 * lý do. Mỗi phần tử được xử lý độc lập (một giao dịch riêng) nên lỗi của phần tử này không ảnh
 * hưởng phần tử khác.
 *
 * <p>KHÔNG dùng {@code @Builder} ở cấp lớp: các nơi gọi khởi tạo bằng {@code new BulkActionResponse()}
 * rồi {@link #addSuccess}/{@link #addFailure}. Nếu dùng {@code @Builder.Default} thì hằng khởi tạo
 * trường sẽ bị Lombok dời đi và constructor không tham số sẽ để {@code null} (gây NPE).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "BulkActionResponse", description = "Kết quả thao tác hàng loạt")
public class BulkActionResponse {

    /** Các id xử lý thành công. */
    private List<Long> success = new ArrayList<>();

    /** Các id thất bại kèm thông báo lỗi. */
    private List<Failure> failed = new ArrayList<>();

    /** Số phần tử thành công. */
    private int successCount;

    /** Số phần tử thất bại. */
    private int failedCount;

    /** Một phần tử thất bại. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "BulkActionFailure", description = "Phần tử thất bại trong thao tác hàng loạt")
    public static class Failure {
        private Long id;
        private String error;
    }

    /** Ghi nhận một id thành công. */
    public void addSuccess(Long id) {
        success.add(id);
    }

    /** Ghi nhận một id thất bại kèm lý do. */
    public void addFailure(Long id, String error) {
        failed.add(Failure.builder().id(id).error(error).build());
    }

    /** Chốt số đếm sau khi lặp xong (gọi trước khi trả về). */
    public BulkActionResponse finish() {
        this.successCount = success.size();
        this.failedCount = failed.size();
        return this;
    }
}
