package com.webtro.modules.user.repository;

import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import com.webtro.modules.user.entity.Verification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Verification} (yêu cầu xác thực email/phone/chủ trọ).
 */
@Repository
public interface VerificationRepository extends JpaRepository<Verification, Long> {

    /** Tra cứu theo băm token (xác nhận link xác thực). */
    Optional<Verification> findByTokenHashAndDeletedAtIsNull(String tokenHash);

    /** Các yêu cầu xác thực của một người dùng theo loại và trạng thái. */
    List<Verification> findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(
            Long userId, VerificationType type, VerificationStatus status);

    /** Toàn bộ yêu cầu xác thực của một người dùng theo loại. */
    List<Verification> findByUser_IdAndTypeAndDeletedAtIsNull(Long userId, VerificationType type);
}
