package com.webtro.modules.user.service.impl;

import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.user.dto.request.UpdateContactRequest;
import com.webtro.modules.user.dto.request.UpdateProfileRequest;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserProfile;
import com.webtro.modules.user.mapper.LandlordProfileMapper;
import com.webtro.modules.user.mapper.UserMapper;
import com.webtro.modules.user.repository.FollowRepository;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserProfileRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.VerificationRepository;
import com.webtro.storage.FileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT hồ sơ cá nhân/liên hệ/ảnh đại diện của người thuê (UT-USR-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceImplTenantTest {

    private static final long TENANT_ID = 30L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private LandlordProfileRepository landlordProfileRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private FollowRepository followRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private LandlordProfileMapper landlordProfileMapper;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private FileStorage fileStorage;

    @InjectMocks
    private UserServiceImpl service;

    private User tenant;

    @BeforeEach
    void setUp() {
        Role role = Role.builder().code(RoleCode.TENANT).build();
        tenant = User.builder().fullName("Người thuê").email("t@test.local").phone("0912345678").role(role).build();
        tenant.setId(TENANT_ID);
        tenant.setPhoneVerifiedAt(Instant.now().minusSeconds(3600));
        when(userRepository.findByIdAndDeletedAtIsNull(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(userProfileRepository.findByUser_IdAndDeletedAtIsNull(TENANT_ID)).thenReturn(Optional.empty());
        when(landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(TENANT_ID)).thenReturn(Optional.empty());
        when(systemConfigService.getInt(any())).thenReturn(5);
    }

    @Test
    @DisplayName("UT-USR-01 Xem hồ sơ tenant → không nhúng hồ sơ chủ trọ, role = ROLE_TENANT")
    void tenantProfileHasNoLandlordSection() {
        service.getMyProfile(TENANT_ID);

        verify(userMapper).toProfileResponse(eq(tenant), isNull(), isNull(), eq(RoleCode.TENANT));
    }

    @Test
    @DisplayName("UT-USR-02 Cập nhật hồ sơ → trim họ tên, tạo UserProfile nếu chưa có")
    void updateProfileCreatesUserProfile() {
        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .fullName("  Nguyễn Văn Thuê  ")
                .dateOfBirth(LocalDate.of(2000, 1, 1))
                .bio("Sinh viên năm 3")
                .build();

        service.updateMyProfile(TENANT_ID, req);

        ArgumentCaptor<UserProfile> saved = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileRepository).save(saved.capture());
        assertEquals("Nguyễn Văn Thuê", tenant.getFullName());
        assertEquals("Sinh viên năm 3", saved.getValue().getBio());
    }

    @Test
    @DisplayName("UT-USR-03 Ngày sinh < 16 tuổi → VALIDATION_FAILED")
    void underAgeRejected() {
        UpdateProfileRequest req = UpdateProfileRequest.builder()
                .fullName("Bé Thuê")
                .dateOfBirth(LocalDate.now().minusYears(15))
                .build();

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateMyProfile(TENANT_ID, req));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-USR-04 Cập nhật liên hệ rỗng cả tên và SĐT → VALIDATION_FAILED")
    void contactRequiresSomething() {
        assertThrows(BusinessException.class, () -> service.updateContact(TENANT_ID, contact(null, " ")));
    }

    @Test
    @DisplayName("UT-USR-05 SĐT sai định dạng → VALIDATION_FAILED")
    void invalidPhone() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateContact(TENANT_ID, contact(null, "12345")));

        assertEquals(ErrorCode.VALIDATION_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-USR-06 SĐT đã thuộc tài khoản khác → PHONE_ALREADY_EXISTS")
    void phoneTakenByOther() {
        User other = User.builder().fullName("Khác").email("o@test.local").phone("0987654321").build();
        other.setId(99L);
        when(userRepository.findByPhoneAndDeletedAtIsNull("0987654321")).thenReturn(Optional.of(other));

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.updateContact(TENANT_ID, contact(null, "0987654321")));

        assertEquals(ErrorCode.PHONE_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-USR-07 Tenant đổi SĐT → cập nhật users.phone, không có hồ sơ chủ trọ nên landlordProfileUpdated=false")
    void tenantChangesPhone() {
        var response = service.updateContact(TENANT_ID, contact(null, "0987654321"));

        assertEquals("0987654321", tenant.getPhone());
        assertFalse(response.isLandlordProfileUpdated());
    }

    @Test
    @DisplayName("UT-USR-08 [BUG-04] Đổi sang SĐT mới → trạng thái 'đã xác thực SĐT' phải bị reset")
    void changingPhoneResetsVerification() {
        service.updateContact(TENANT_ID, contact(null, "0987654321"));

        assertNull(tenant.getPhoneVerifiedAt());
    }

    @Test
    @DisplayName("UT-USR-09 Upload avatar không phải ảnh (magic bytes sai) → AVATAR_INVALID_FORMAT")
    void avatarMustBeImage() {
        MockMultipartFile fake = new MockMultipartFile("file", "a.jpg", "image/jpeg",
                "#!/bin/sh echo hacked".getBytes());

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateAvatar(TENANT_ID, fake));

        assertEquals(ErrorCode.AVATAR_INVALID_FORMAT, ex.getErrorCode());
        verify(fileStorage, never()).storeImage(any(), anyString());
    }

    @Test
    @DisplayName("UT-USR-10 Xóa avatar khi chưa có → AVATAR_NOT_FOUND")
    void deleteMissingAvatar() {
        BusinessException ex = assertThrows(ResourceNotFoundException.class, () -> service.deleteAvatar(TENANT_ID));

        assertEquals(ErrorCode.AVATAR_NOT_FOUND, ex.getErrorCode());
    }

    private UpdateContactRequest contact(String name, String phone) {
        UpdateContactRequest r = new UpdateContactRequest();
        r.setContactName(name);
        r.setContactPhone(phone);
        return r;
    }
}
