package com.webtro.modules.admin.repository;

import com.webtro.common.enums.AuditAction;
import com.webtro.modules.admin.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository cho {@link AuditLog} (append-only). Dùng {@link JpaSpecificationExecutor} cho màn hình
 * tra cứu/lọc nhật ký kiểm toán của admin. Không có điều kiện xóa mềm vì bảng không xóa.
 */
@Repository
public interface AuditLogRepository
        extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /** Nhật ký theo actor, mới nhất trước. */
    Page<AuditLog> findByActorIdOrderByCreatedAtDesc(Long actorId, Pageable pageable);

    /** Nhật ký theo loại đối tượng và id đối tượng, mới nhất trước. */
    Page<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            String targetType, Long targetId, Pageable pageable);

    /** Nhật ký theo loại hành động, mới nhất trước. */
    Page<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action, Pageable pageable);

    /** Id các bản ghi audit cũ hơn {@code threshold} — {@code DataRetentionJob} xóa theo lô (quá hạn lưu). */
    @Query("SELECT a.id FROM AuditLog a WHERE a.createdAt < :threshold ORDER BY a.id ASC")
    List<Long> findIdsCreatedBefore(@Param("threshold") Instant threshold, Pageable pageable);
}
