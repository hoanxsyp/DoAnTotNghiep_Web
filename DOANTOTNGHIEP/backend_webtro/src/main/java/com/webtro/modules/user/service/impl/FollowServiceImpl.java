package com.webtro.modules.user.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.TrustLabel;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.user.dto.response.FollowResponse;
import com.webtro.modules.user.dto.response.FollowingItemResponse;
import com.webtro.modules.user.entity.Follow;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.mapper.FollowMapper;
import com.webtro.modules.user.repository.FollowRepository;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hiện thực nghiệp vụ theo dõi chủ trọ (FOLLOW-01/02).
 *
 * <p>Danh sách theo dõi phân trang trong bộ nhớ vì repository (đã cố định, không sửa) chỉ cung cấp
 * {@code findByFollower_IdAndDeletedAtIsNull} trả {@code List}. Số lượng chủ trọ một người theo dõi
 * thường nhỏ nên chi phí chấp nhận được.
 */
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final FollowMapper followMapper;

    /** {@code @Lazy} để phá vòng phụ thuộc tiềm ẩn user ↔ listing (xem UserServiceImpl). */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private TrustScoreService trustScoreService;

    @Override
    @Transactional
    public FollowResponse follow(Long followerId, Long landlordId) {
        if (followerId.equals(landlordId)) {
            throw new com.webtro.exception.BusinessRuleException(ErrorCode.CANNOT_FOLLOW_SELF);
        }
        User landlord = userRepository.findByIdAndDeletedAtIsNull(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (!RoleCode.LANDLORD.equals(roleRepository.findRoleCodeByUserId(landlordId).orElse(null))) {
            throw new com.webtro.exception.BusinessRuleException(ErrorCode.TARGET_NOT_LANDLORD);
        }
        if (followRepository.existsByFollower_IdAndLandlord_IdAndDeletedAtIsNull(followerId, landlordId)) {
            throw new ConflictException(ErrorCode.ALREADY_FOLLOWING);
        }
        User follower = userRepository.findByIdAndDeletedAtIsNull(followerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        Follow follow = Follow.builder()
                .follower(follower)
                .landlord(landlord)
                .notifyNewListing(true)
                .build();
        follow = followRepository.save(follow);

        long followerCount = followRepository.countByLandlord_IdAndDeletedAtIsNull(landlordId);
        return FollowResponse.builder()
                .landlordId(landlordId)
                .following(true)
                .followerCount(followerCount)
                .followedAt(follow.getCreatedAt() != null ? follow.getCreatedAt() : Instant.now())
                .build();
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long landlordId) {
        Follow follow = followRepository
                .findByFollower_IdAndLandlord_IdAndDeletedAtIsNull(followerId, landlordId)
                .orElseThrow(() -> new ConflictException(ErrorCode.NOT_FOLLOWING));
        // Xóa cứng: dữ liệu quan hệ, không cần audit (mục 4.2.8).
        followRepository.delete(follow);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowingItemResponse> getFollowing(Long followerId, Pageable pageable) {
        List<Follow> follows = followRepository.findByFollower_IdAndDeletedAtIsNull(followerId);

        List<FollowingItemResponse> items = new ArrayList<>(follows.size());
        for (Follow follow : follows) {
            User landlord = follow.getLandlord();
            LandlordProfile lp = landlordProfileRepository
                    .findByUser_IdAndDeletedAtIsNull(landlord.getId()).orElse(null);
            TrustLabel label = (lp != null && lp.getTrustScore() != null)
                    ? trustScoreService.labelOf(lp.getTrustScore().setScale(0, java.math.RoundingMode.HALF_UP).intValue())
                    : null;
            items.add(followMapper.toItem(follow, landlord, lp, label));
        }

        sortItems(items, pageable);

        int total = items.size();
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
        int to = (int) Math.min((long) from + pageable.getPageSize(), total);
        List<FollowingItemResponse> pageContent = items.subList(from, to);

        Page<FollowingItemResponse> page = new PageImpl<>(pageContent, pageable, total);
        return PageResponse.from(page);
    }

    /** Sắp xếp theo createdAt (mặc định) hoặc trustScore, giảm dần theo hướng của Sort. */
    private void sortItems(List<FollowingItemResponse> items, Pageable pageable) {
        boolean byTrust = pageable.getSort().stream()
                .anyMatch(o -> "trustScore".equals(o.getProperty()));
        Comparator<FollowingItemResponse> comparator;
        if (byTrust) {
            comparator = Comparator.comparing(
                    i -> i.getTrustScore() == null ? BigDecimal.ZERO : BigDecimal.valueOf(i.getTrustScore()));
        } else {
            comparator = Comparator.comparing(FollowingItemResponse::getFollowedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        }
        boolean ascending = pageable.getSort().stream()
                .findFirst().map(org.springframework.data.domain.Sort.Order::isAscending)
                .orElse(false);
        if (!ascending) {
            comparator = comparator.reversed();
        }
        items.sort(comparator);
    }
}
