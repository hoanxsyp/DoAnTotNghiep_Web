package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link RefreshToken} (xoay vòng + reuse detection theo họ token) [§8].
 */
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Tra cứu theo băm token (mỗi lần refresh). */
    Optional<RefreshToken> findByTokenHashAndDeletedAtIsNull(String tokenHash);

    /** Toàn bộ token cùng họ — dùng khi phát hiện reuse để thu hồi cả họ. */
    List<RefreshToken> findByFamilyIdAndDeletedAtIsNull(String familyId);

    /** Các token còn hiệu lực của một người dùng (quản lý phiên đăng nhập). */
    List<RefreshToken> findByUser_IdAndDeletedAtIsNull(Long userId);

    /**
     * Xóa CỨNG các refresh token đã hết hạn trước {@code threshold} ({@code TokenCleanupJob}).
     * Token hết hạn không còn giá trị bảo mật/nghiệp vụ nên xóa vật lý (không soft-delete).
     * Cần chạy trong transaction (job đảm nhiệm).
     *
     * @return số bản ghi đã xóa
     */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
