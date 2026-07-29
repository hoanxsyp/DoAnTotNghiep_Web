package com.webtro.modules.payment.repository;

import com.webtro.modules.payment.entity.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Truy vấn mã giảm giá. Có {@link JpaSpecificationExecutor} phục vụ lọc động ở màn hình quản trị.
 * Mọi truy vấn công khai kèm điều kiện {@code deleted_at IS NULL} (xóa mềm).
 */
@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long>, JpaSpecificationExecutor<Coupon> {

    /** Lấy một coupon chưa xóa mềm theo id. */
    Optional<Coupon> findByIdAndDeletedAtIsNull(Long id);

    /** Lấy coupon theo mã (chưa xóa mềm) để kiểm tra khi áp mã. */
    Optional<Coupon> findByCodeAndDeletedAtIsNull(String code);

    /** Kiểm tra mã coupon đã tồn tại (chưa xóa mềm) hay chưa. */
    boolean existsByCodeAndDeletedAtIsNull(String code);
}
