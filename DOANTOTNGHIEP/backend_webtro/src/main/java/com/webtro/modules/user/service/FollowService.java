package com.webtro.modules.user.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.user.dto.response.FollowResponse;
import com.webtro.modules.user.dto.response.FollowingItemResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ theo dõi chủ trọ (FOLLOW-01/02, {@code [§2.5]}).
 */
public interface FollowService {

    /** Theo dõi một chủ trọ (FOLLOW-01). */
    FollowResponse follow(Long followerId, Long landlordId);

    /** Bỏ theo dõi (FOLLOW-01) — xóa cứng bản ghi quan hệ. */
    void unfollow(Long followerId, Long landlordId);

    /** Danh sách chủ trọ đang theo dõi (FOLLOW-02). */
    PageResponse<FollowingItemResponse> getFollowing(Long followerId, Pageable pageable);
}
