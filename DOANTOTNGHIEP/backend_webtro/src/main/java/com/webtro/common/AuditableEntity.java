package com.webtro.common;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.LastModifiedBy;

import java.time.Instant;

/**
 * Lớp cha cho <b>bảng nghiệp vụ</b>: thêm người tạo/sửa và cột xóa mềm
 * ({@code docs/00_CANONICAL_DECISIONS.md} mục 6.1).
 *
 * <p><b>Xóa mềm toàn hệ thống</b> {@code [§3.6][§10.2][§11.5]}: không có DELETE vật lý cho dữ
 * liệu nghiệp vụ. Cụ thể {@code [§3.6]}: <i>"Không xóa cứng tin nếu có thanh toán, báo cáo hoặc
 * bình luận liên quan"</i> và <i>"Admin vẫn xem được tin đã xóa mềm"</i>.
 *
 * <p><b>Cố ý KHÔNG dùng {@code @SQLRestriction}/{@code @Where} của Hibernate.</b> Hai annotation
 * đó thêm {@code deleted_at IS NULL} vào <i>mọi</i> truy vấn ở tầng ORM — kể cả truy vấn của
 * Admin. Mà {@code [§3.6]} yêu cầu Admin <i>phải</i> xem được tin đã xóa mềm, nên nếu dùng chúng
 * thì không còn đường nào lấy dữ liệu đó ra ngoài việc viết native query để lách chính ORM của
 * mình. Thay vào đó, điều kiện {@code deleted_at IS NULL} được đặt tường minh trong từng
 * repository query công khai — dài dòng hơn nhưng đúng nghiệp vụ và không có ngoại lệ ngầm.
 */
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
public abstract class AuditableEntity extends BaseEntity {

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    /**
     * Thời điểm bị xóa mềm. {@code null} = chưa xóa.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * Đánh dấu xóa mềm. Gọi lại lần nữa không làm đổi thời điểm xóa ban đầu (idempotent) —
     * mốc thời gian xóa đầu tiên là dữ liệu phục vụ audit, không được ghi đè.
     */
    public void softDelete() {
        if (deletedAt == null) {
            deletedAt = Instant.now();
        }
    }

    /**
     * Khôi phục bản ghi đã xóa mềm (Admin dùng khi xử lý khiếu nại).
     */
    public void restore() {
        deletedAt = null;
    }
}
