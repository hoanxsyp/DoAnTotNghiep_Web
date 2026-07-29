package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository cho {@link UserProfile} (hồ sơ người thuê, 1-1 với user).
 */
@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    /** Lấy hồ sơ theo người dùng (còn hiệu lực). */
    Optional<UserProfile> findByUser_IdAndDeletedAtIsNull(Long userId);

    /** Kiểm tra người dùng đã có hồ sơ chưa. */
    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);
}
