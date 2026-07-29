package com.webtro.modules.admin.repository;

import com.webtro.common.enums.PaymentStatus;
import com.webtro.modules.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Repository chỉ-đọc do module {@code admin} sở hữu để tổng hợp doanh thu/giao dịch cho Dashboard &
 * Thống kê (canonical §10.1). Trỏ tới entity {@link Payment} của module payment nhưng đặt trong
 * module admin để KHÔNG sửa file module khác (Spring Data cho phép nhiều repository trên cùng entity).
 */
@Repository
public interface AdminPaymentMetricsRepository extends JpaRepository<Payment, Long> {

    /** Tổng doanh thu thực thu (SUCCESS) theo khoảng thời gian thanh toán. */
    @Query("""
            SELECT COALESCE(SUM(p.finalAmount), 0) FROM Payment p
            WHERE p.status = com.webtro.common.enums.PaymentStatus.SUCCESS
              AND p.deletedAt IS NULL
              AND p.paidAt >= :from AND p.paidAt < :to
            """)
    BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Tổng doanh thu thực thu (SUCCESS) toàn thời gian. */
    @Query("""
            SELECT COALESCE(SUM(p.finalAmount), 0) FROM Payment p
            WHERE p.status = com.webtro.common.enums.PaymentStatus.SUCCESS
              AND p.deletedAt IS NULL
            """)
    BigDecimal sumRevenueAllTime();

    /** Số giao dịch theo trạng thái (bỏ giao dịch đã xóa mềm). */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = :status AND p.deletedAt IS NULL")
    long countByStatus(@Param("status") PaymentStatus status);

    /** Số giao dịch thực thu (SUCCESS) theo khoảng thời gian thanh toán. */
    @Query("""
            SELECT COUNT(p) FROM Payment p
            WHERE p.status = com.webtro.common.enums.PaymentStatus.SUCCESS
              AND p.deletedAt IS NULL
              AND p.paidAt >= :from AND p.paidAt < :to
            """)
    long countSuccessBetween(@Param("from") Instant from, @Param("to") Instant to);

    /**
     * Phân rã doanh thu thực thu (SUCCESS) theo gói dịch vụ trong khoảng thời gian. Mỗi phần tử là
     * {@code [packageId (Long, có thể null), revenue (BigDecimal), transactionCount (Long)]}.
     */
    @Query("""
            SELECT p.packageId, COALESCE(SUM(p.finalAmount), 0), COUNT(p) FROM Payment p
            WHERE p.status = com.webtro.common.enums.PaymentStatus.SUCCESS
              AND p.deletedAt IS NULL
              AND p.paidAt >= :from AND p.paidAt < :to
            GROUP BY p.packageId
            """)
    List<Object[]> sumRevenueByPackageBetween(@Param("from") Instant from, @Param("to") Instant to);
}
