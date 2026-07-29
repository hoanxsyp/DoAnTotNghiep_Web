package com.webtro.modules.moderation.repository;

import com.webtro.modules.moderation.entity.ViolationWarning;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Truy vấn cảnh báo vi phạm (append-only, không xóa mềm).
 */
@Repository
public interface ViolationWarningRepository extends JpaRepository<ViolationWarning, Long> {

    /** Lịch sử cảnh báo của một người dùng (mới nhất trước), phân trang. */
    Page<ViolationWarning> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Đếm tổng số cảnh báo của một người dùng (phục vụ ngưỡng khóa tài khoản). */
    long countByUserId(Long userId);

    /** Các cảnh báo người dùng chưa xác nhận đọc. */
    List<ViolationWarning> findByUserIdAndAcknowledgedAtIsNullOrderByCreatedAtDesc(Long userId);
}
