package com.webtro.modules.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Lịch sử tìm kiếm — bảng {@code search_histories} (append-only, chỉ {@code id} + {@code created_at}).
 *
 * <p><b>Không kế thừa</b> lớp cha audit vì bảng log không có
 * {@code updated_at}/{@code updated_by}/{@code deleted_at} — entity độc lập theo schema V1.
 *
 * <p>{@code criteria} là cột {@code JSON} chứa toàn bộ bộ lọc; map dạng {@code String} và ánh xạ
 * kiểu JSON qua {@link JdbcTypeCode} (canonical: enum/JSON lưu chuỗi, service dùng Jackson để (de)serialize).
 */
@Entity
@Table(
        name = "search_histories",
        indexes = {
                @Index(name = "idx_search_histories_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_search_histories_keyword", columnList = "keyword"),
                @Index(name = "idx_search_histories_result_count", columnList = "result_count")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Người tìm — {@code null} nếu khách ẩn danh (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id")
    private Long userId;

    /** Từ khóa tự do người dùng nhập. */
    @Column(name = "keyword", length = 150)
    private String keyword;

    /** Toàn bộ bộ lọc dạng JSON (provinceId, priceFrom, amenityIds[]...). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "criteria", nullable = false, columnDefinition = "json")
    private String criteria;

    /** Số kết quả trả về — phục vụ phát hiện "tìm kiếm ít kết quả" để gợi ý. */
    @Builder.Default
    @Column(name = "result_count", nullable = false)
    private Integer resultCount = 0;

    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
