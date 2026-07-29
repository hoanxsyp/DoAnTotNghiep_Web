package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Follow} (người thuê theo dõi chủ trọ) [§2.5].
 */
@Repository
public interface FollowRepository extends JpaRepository<Follow, Long> {

    /** Lấy quan hệ theo dõi cụ thể (còn hiệu lực). */
    Optional<Follow> findByFollower_IdAndLandlord_IdAndDeletedAtIsNull(Long followerId, Long landlordId);

    /** Kiểm tra người thuê đã theo dõi chủ trọ chưa. */
    boolean existsByFollower_IdAndLandlord_IdAndDeletedAtIsNull(Long followerId, Long landlordId);

    /** Danh sách chủ trọ mà một người thuê đang theo dõi. */
    List<Follow> findByFollower_IdAndDeletedAtIsNull(Long followerId);

    /** Danh sách người theo dõi một chủ trọ (gửi thông báo tin mới). */
    List<Follow> findByLandlord_IdAndDeletedAtIsNull(Long landlordId);

    /** Đếm số người theo dõi một chủ trọ. */
    long countByLandlord_IdAndDeletedAtIsNull(Long landlordId);
}
