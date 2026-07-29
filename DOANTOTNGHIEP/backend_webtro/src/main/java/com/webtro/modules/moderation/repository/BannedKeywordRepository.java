package com.webtro.modules.moderation.repository;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.modules.moderation.entity.BannedKeyword;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn từ khóa cấm. Bảng nghiệp vụ (có xóa mềm) nên truy vấn công khai kèm
 * {@code deleted_at IS NULL}.
 */
@Repository
public interface BannedKeywordRepository extends JpaRepository<BannedKeyword, Long> {

    /** Tìm theo từ khóa đã chuẩn hóa (duy nhất), bỏ qua bản ghi đã xóa mềm. */
    Optional<BannedKeyword> findByNormalizedKeywordAndDeletedAtIsNull(String normalizedKeyword);

    /** Kiểm tra tồn tại từ khóa chuẩn hóa (chống trùng khi thêm mới). */
    boolean existsByNormalizedKeywordAndDeletedAtIsNull(String normalizedKeyword);

    /** Tải toàn bộ từ khóa đang bật để nạp bộ lọc (bỏ qua bản ghi đã xóa mềm). */
    List<BannedKeyword> findByIsActiveTrueAndDeletedAtIsNull();

    /** Từ khóa đang bật theo phạm vi áp dụng cụ thể. */
    List<BannedKeyword> findByIsActiveTrueAndAppliesToAndDeletedAtIsNull(BannedKeywordScope appliesTo);
}
