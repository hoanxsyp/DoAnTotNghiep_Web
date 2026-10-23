package com.webtro.modules.user.service.impl;

import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.user.entity.Follow;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.mapper.FollowMapper;
import com.webtro.modules.user.repository.FollowRepository;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT theo dõi/bỏ theo dõi chủ trọ của người thuê (UT-FOL-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FollowServiceImplTest {

    private static final long TENANT_ID = 30L;
    private static final long LANDLORD_ID = 40L;

    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private LandlordProfileRepository landlordProfileRepository;

    private FollowServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FollowServiceImpl(followRepository, userRepository, roleRepository,
                landlordProfileRepository, new FollowMapper());
        when(userRepository.findByIdAndDeletedAtIsNull(TENANT_ID)).thenReturn(Optional.of(user(TENANT_ID)));
        when(userRepository.findByIdAndDeletedAtIsNull(LANDLORD_ID)).thenReturn(Optional.of(user(LANDLORD_ID)));
        when(followRepository.save(any(Follow.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("UT-FOL-01 Theo dõi chủ trọ → lưu Follow (bật báo tin mới), trả followerCount")
    void followLandlord() {
        when(roleRepository.findRoleCodeByUserId(LANDLORD_ID)).thenReturn(Optional.of(RoleCode.LANDLORD));
        when(followRepository.countByLandlord_IdAndDeletedAtIsNull(LANDLORD_ID)).thenReturn(3L);

        var response = service.follow(TENANT_ID, LANDLORD_ID);

        ArgumentCaptor<Follow> saved = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(saved.capture());
        assertEquals(TENANT_ID, saved.getValue().getFollower().getId());
        assertTrue(saved.getValue().getNotifyNewListing());
        assertTrue(response.isFollowing());
        assertEquals(3L, response.getFollowerCount());
    }

    @Test
    @DisplayName("UT-FOL-02 Tự theo dõi chính mình → CANNOT_FOLLOW_SELF")
    void followSelf() {
        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.follow(TENANT_ID, TENANT_ID));
        assertEquals(ErrorCode.CANNOT_FOLLOW_SELF, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-FOL-03 Theo dõi người không phải chủ trọ (tenant khác) → TARGET_NOT_LANDLORD")
    void followNonLandlord() {
        when(roleRepository.findRoleCodeByUserId(LANDLORD_ID)).thenReturn(Optional.of(RoleCode.TENANT));

        BusinessException ex = assertThrows(BusinessRuleException.class, () -> service.follow(TENANT_ID, LANDLORD_ID));

        assertEquals(ErrorCode.TARGET_NOT_LANDLORD, ex.getErrorCode());
        verify(followRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FOL-04 Theo dõi lần 2 → ALREADY_FOLLOWING")
    void followTwice() {
        when(roleRepository.findRoleCodeByUserId(LANDLORD_ID)).thenReturn(Optional.of(RoleCode.LANDLORD));
        when(followRepository.existsByFollower_IdAndLandlord_IdAndDeletedAtIsNull(TENANT_ID, LANDLORD_ID))
                .thenReturn(true);

        BusinessException ex = assertThrows(ConflictException.class, () -> service.follow(TENANT_ID, LANDLORD_ID));

        assertEquals(ErrorCode.ALREADY_FOLLOWING, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-FOL-05 Theo dõi user không tồn tại → USER_NOT_FOUND")
    void followMissingUser() {
        when(userRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.follow(TENANT_ID, 99L));
    }

    @Test
    @DisplayName("UT-FOL-06 Bỏ theo dõi khi chưa theo dõi → NOT_FOLLOWING; khi đã theo dõi → xóa cứng")
    void unfollow() {
        Follow follow = Follow.builder().follower(user(TENANT_ID)).landlord(user(LANDLORD_ID)).build();
        when(followRepository.findByFollower_IdAndLandlord_IdAndDeletedAtIsNull(TENANT_ID, LANDLORD_ID))
                .thenReturn(Optional.of(follow))
                .thenReturn(Optional.empty());

        service.unfollow(TENANT_ID, LANDLORD_ID);
        verify(followRepository).delete(follow);

        BusinessException ex = assertThrows(ConflictException.class, () -> service.unfollow(TENANT_ID, LANDLORD_ID));
        assertEquals(ErrorCode.NOT_FOLLOWING, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-FOL-07 Danh sách đang theo dõi: phân trang trong bộ nhớ, mặc định mới nhất trước")
    void followingListPaginatesNewestFirst() {
        Follow older = followAt(41L, Instant.parse("2026-01-01T00:00:00Z"));
        Follow newer = followAt(42L, Instant.parse("2026-02-01T00:00:00Z"));
        Follow newest = followAt(43L, Instant.parse("2026-03-01T00:00:00Z"));
        when(followRepository.findByFollower_IdAndDeletedAtIsNull(TENANT_ID)).thenReturn(List.of(older, newest, newer));

        var page0 = service.getFollowing(TENANT_ID, PageRequest.of(0, 2));
        var page1 = service.getFollowing(TENANT_ID, PageRequest.of(1, 2));
        var asc = service.getFollowing(TENANT_ID, PageRequest.of(0, 5, Sort.by("createdAt").ascending()));

        assertEquals(3, page0.getTotalElements());
        assertEquals(List.of(43L, 42L), page0.getItems().stream().map(i -> i.getLandlordId()).toList());
        assertEquals(List.of(41L), page1.getItems().stream().map(i -> i.getLandlordId()).toList());
        assertEquals(41L, asc.getItems().get(0).getLandlordId());
    }

    private Follow followAt(long landlordId, Instant at) {
        Follow f = Follow.builder().follower(user(TENANT_ID)).landlord(user(landlordId)).build();
        f.setCreatedAt(at);
        return f;
    }

    private User user(long id) {
        User u = User.builder().fullName("User " + id).email(id + "@test.local").build();
        u.setId(id);
        return u;
    }
}
