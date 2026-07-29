package com.webtro.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Bọc kết quả phân trang theo đúng hình dạng chốt ở
 * {@code docs/00_CANONICAL_DECISIONS.md} mục 7.1.
 *
 * <p>Tồn tại vì <b>không được trả thẳng {@link Page} của Spring Data ra API</b>: hình dạng JSON
 * của {@code PageImpl} do thư viện quyết định, đổi theo phiên bản Spring, và lộ cả cấu trúc
 * {@code Pageable}/{@code Sort} nội bộ — frontend sẽ phụ thuộc vào chi tiết cài đặt của backend.
 *
 * @param <T> kiểu phần tử (luôn là DTO, không bao giờ là entity — luật phụ thuộc số 2)
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(name = "PageResponse", description = "Kết quả phân trang")
public class PageResponse<T> {

    @Schema(description = "Danh sách phần tử của trang hiện tại")
    private List<T> items;

    @Schema(description = "Chỉ số trang, bắt đầu từ 0", example = "0")
    private int page;

    @Schema(description = "Số phần tử mỗi trang", example = "20")
    private int size;

    @Schema(description = "Tổng số phần tử khớp điều kiện", example = "137")
    private long totalElements;

    @Schema(description = "Tổng số trang", example = "7")
    private int totalPages;

    @Schema(description = "Có phải trang đầu không", example = "true")
    private boolean first;

    @Schema(description = "Có phải trang cuối không", example = "false")
    private boolean last;

    /**
     * Chuyển {@code Page<E>} (entity) thành {@code PageResponse<T>} (DTO) bằng hàm map.
     *
     * <p>Đây là cách <b>duy nhất</b> nên dùng ở tầng service: nó ép việc map entity→DTO xảy ra,
     * nên không có đường nào để entity lọt ra ngoài controller.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return PageResponse.<T>builder()
                .items(page.getContent().stream().map(mapper).toList())
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .build();
    }

    /** Dùng khi phần tử đã là DTO sẵn (ví dụ query projection trả thẳng DTO). */
    public static <T> PageResponse<T> from(Page<T> page) {
        return from(page, Function.identity());
    }

    /**
     * Trang rỗng — dùng khi biết chắc không có kết quả mà không cần chạm DB
     * (ví dụ: bộ lọc mâu thuẫn logic).
     */
    public static <T> PageResponse<T> empty(int page, int size) {
        return PageResponse.<T>builder()
                .items(List.of())
                .page(page)
                .size(size)
                .totalElements(0L)
                .totalPages(0)
                .first(true)
                .last(true)
                .build();
    }
}
