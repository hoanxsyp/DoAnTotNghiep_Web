package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link PasswordResetToken} (AUTH-04).
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** Tra cứu theo băm token (xác nhận link đặt lại mật khẩu). */
    Optional<PasswordResetToken> findByTokenHashAndDeletedAtIsNull(String tokenHash);

    /** Các token đặt lại mật khẩu còn hiệu lực của một người dùng (vô hiệu hóa token cũ khi phát mới). */
    List<PasswordResetToken> findByUser_IdAndUsedAtIsNullAndDeletedAtIsNull(Long userId);

    /**
     * Xóa CỨNG các token đặt lại mật khẩu đã hết hạn trước {@code threshold} ({@code TokenCleanupJob}).
     * Cần chạy trong transaction (job đảm nhiệm).
     *
     * @return số bản ghi đã xóa
     */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
