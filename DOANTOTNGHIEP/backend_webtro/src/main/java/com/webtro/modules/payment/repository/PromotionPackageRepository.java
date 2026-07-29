package com.webtro.modules.payment.repository;

import com.webtro.modules.payment.entity.PromotionPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn gói đẩy tin. Mọi truy vấn công khai kèm điều kiện {@code deleted_at IS NULL} (xóa mềm).
 */
@Repository
public interface PromotionPackageRepository extends JpaRepository<PromotionPackage, Long> {

    /** Lấy một gói chưa xóa mềm theo id. */
    Optional<PromotionPackage> findByIdAndDeletedAtIsNull(Long id);

    /** Lấy gói theo mã (chưa xóa mềm). */
    Optional<PromotionPackage> findByCodeAndDeletedAtIsNull(String code);

    /** Kiểm tra mã gói đã tồn tại (chưa xóa mềm) hay chưa. */
    boolean existsByCodeAndDeletedAtIsNull(String code);

    /** Danh sách gói đang bán, sắp theo thứ tự hiển thị. */
    List<PromotionPackage> findByIsActiveTrueAndDeletedAtIsNullOrderByDisplayOrderAsc();
}
