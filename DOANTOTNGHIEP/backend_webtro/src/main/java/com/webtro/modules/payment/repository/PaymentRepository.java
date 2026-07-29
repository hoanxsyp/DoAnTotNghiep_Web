package com.webtro.modules.payment.repository;

import com.webtro.common.enums.PaymentStatus;
import com.webtro.modules.payment.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn giao dịch thanh toán. Có {@link JpaSpecificationExecutor} phục vụ lọc động
 * (lịch sử giao dịch, đối soát). Mọi truy vấn công khai kèm điều kiện {@code deleted_at IS NULL}.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    /** Lấy một giao dịch chưa xóa mềm theo id. */
    Optional<Payment> findByIdAndDeletedAtIsNull(Long id);

    /** Lấy giao dịch theo mã giao dịch nội bộ (chưa xóa mềm). */
    Optional<Payment> findByTransactionCodeAndDeletedAtIsNull(String transactionCode);

    /** Lấy giao dịch theo mã tham chiếu phía cổng thanh toán (dùng khi nhận callback). */
    Optional<Payment> findByGatewayTxnRefAndDeletedAtIsNull(String gatewayTxnRef);

    /** Lịch sử giao dịch của một người dùng (phân trang, mới nhất trước). */
    Page<Payment> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Danh sách giao dịch theo trạng thái (phục vụ job quét quá hạn/đối soát). */
    Page<Payment> findByStatusAndDeletedAtIsNull(PaymentStatus status, Pageable pageable);

    /**
     * Giao dịch {@code PENDING} được tạo trước {@code cutoff} (= now − {@code payment.order.expiry_minutes})
     * — ứng viên cho {@code PaymentReconcileJob} chuyển sang {@code FAILED}. Sắp cũ nhất trước, phân trang.
     */
    @Query("SELECT p FROM Payment p WHERE p.status = com.webtro.common.enums.PaymentStatus.PENDING "
            + "AND p.createdAt < :cutoff AND p.deletedAt IS NULL ORDER BY p.createdAt ASC")
    List<Payment> findStalePendingPayments(@Param("cutoff") Instant cutoff, Pageable pageable);
}
