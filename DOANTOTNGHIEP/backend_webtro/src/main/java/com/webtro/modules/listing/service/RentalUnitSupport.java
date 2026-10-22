package com.webtro.modules.listing.service;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.LocationSource;
import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.RentalUnit;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.repository.RentalUnitRepository;
import com.webtro.modules.listing.statemachine.ListingEvent;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.util.AddressNormalizer;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Nghiệp vụ lõi của Phòng dùng chung cho {@code ListingServiceImpl} (tạo/sửa tin) và
 * {@code RentalUnitServiceImpl} (màn "Phòng của tôi"). Tách riêng để hai service đó không phụ
 * thuộc vòng lẫn nhau.
 *
 * <p>Quy tắc cài ở đây (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3.1):
 * <ul>
 *   <li>RU-2: mỗi phòng tối đa một tin "sống" ({@link #LIVE_STATUSES}).</li>
 *   <li>RU-3: địa chỉ của tin là bản sao địa chỉ phòng, chỉ đồng bộ qua {@link #copyAddress}.</li>
 *   <li>RU-4: phòng đã có đánh giá thì khoá địa chỉ và số phòng (ADMIN được sửa).</li>
 *   <li>RU-6: dò trùng địa chỉ trong cùng phường bằng {@link AddressNormalizer}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RentalUnitSupport {

    /**
     * Tin "sống" của phòng: mọi trạng thái trừ đã đóng và đã xoá. Gồm cả {@code REJECTED} vì tin bị
     * từ chối vẫn sửa rồi gửi duyệt lại được; gồm cả {@code EXPIRED} vì tin hết hạn thì gia hạn
     * ({@code RENEW}) chứ không đăng tin mới.
     */
    public static final Set<ListingStatus> LIVE_STATUSES =
            EnumSet.complementOf(EnumSet.of(ListingStatus.CLOSED, ListingStatus.DELETED));

    private final RentalUnitRepository rentalUnitRepository;
    private final ListingRepository listingRepository;
    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final ListingStateMachine stateMachine;
    private final ListingCategoryCountPublisher categoryCountPublisher;
    private final ListingOwnerStatsPublisher ownerStatsPublisher;

    /** Địa chỉ đã kiểm tra (phường hợp lệ) dùng để tạo hoặc đổi địa chỉ phòng. */
    public record UnitAddress(Ward ward, String addressDetail, String unitLabel,
                              BigDecimal latitude, BigDecimal longitude) {
    }

    // ==================================================================
    //  Đọc
    // ==================================================================

    /** Kiểm tra cặp tỉnh → quận → phường hợp lệ và tỉnh đang được hỗ trợ; trả về phường. */
    public Ward resolveWard(Long provinceId, Long districtId, Long wardId) {
        Province province = provinceRepository.findById(provinceId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROVINCE_NOT_FOUND));
        if (!Boolean.TRUE.equals(province.getIsActive())) {
            throw new BusinessRuleException(ErrorCode.AREA_NOT_SUPPORTED);
        }
        District district = districtRepository.findById(districtId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DISTRICT_NOT_FOUND));
        if (district.getProvince() == null || !Objects.equals(district.getProvince().getId(), provinceId)) {
            throw new BusinessRuleException(ErrorCode.ADDRESS_HIERARCHY_MISMATCH);
        }
        Ward ward = wardRepository.findById(wardId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WARD_NOT_FOUND));
        if (ward.getDistrict() == null || !Objects.equals(ward.getDistrict().getId(), districtId)) {
            throw new BusinessRuleException(ErrorCode.ADDRESS_HIERARCHY_MISMATCH);
        }
        return ward;
    }

    public RentalUnit requireAlive(Long unitId) {
        return rentalUnitRepository.findAliveById(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RENTAL_UNIT_NOT_FOUND));
    }

    public RentalUnit requireOwned(Long unitId, Long userId, boolean admin) {
        RentalUnit unit = requireAlive(unitId);
        if (!admin && !unit.getOwnerId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.RENTAL_UNIT_FORBIDDEN);
        }
        return unit;
    }

    public Optional<Listing> findLiveListing(Long unitId) {
        return listingRepository.findByRentalUnitAndStatuses(unitId, LIVE_STATUSES).stream().findFirst();
    }

    // ==================================================================
    //  Tạo phòng / chọn phòng cho tin mới
    // ==================================================================

    /**
     * Tạo phòng mới cho chủ trọ. Cùng chủ trọ đã có phòng trùng địa chỉ thì chặn (RU-6) và gợi ý
     * phòng có sẵn trong thông điệp lỗi.
     */
    public RentalUnit createUnit(Long ownerId, String name, UnitAddress address) {
        String addressDetail = clean(address.addressDetail());
        String unitLabel = cleanLabel(address.unitLabel());
        findSameOwnerDuplicate(ownerId, address.ward().getId(), addressDetail, unitLabel, null)
                .ifPresent(existing -> {
                    throw new ConflictException(ErrorCode.RENTAL_UNIT_DUPLICATE,
                            "Bạn đã có phòng \"" + existing.getName() + "\" (mã " + existing.getId()
                                    + ") ở địa chỉ này. Hãy chọn phòng có sẵn thay vì tạo phòng mới.");
                });
        RentalUnit unit = RentalUnit.builder()
                .ownerId(ownerId)
                .name(clean(name))
                .unitLabel(unitLabel)
                .wardId(address.ward().getId())
                .addressDetail(addressDetail)
                .status(RentalUnitStatus.ACTIVE)
                .build();
        applyCoordinates(unit, address.latitude(), address.longitude(), address.ward());
        return rentalUnitRepository.save(unit);
    }

    /**
     * Khoá phòng để gắn một tin mới vào (tạo tin hoặc đăng lại). Khoá dòng phòng giúp 2 yêu cầu
     * cùng lúc không tạo được 2 tin sống cho cùng một phòng (RU-2).
     */
    public RentalUnit lockForNewListing(Long unitId, Long ownerId) {
        RentalUnit unit = rentalUnitRepository.findAliveByIdForUpdate(unitId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RENTAL_UNIT_NOT_FOUND));
        if (!unit.getOwnerId().equals(ownerId)) {
            throw new ForbiddenException(ErrorCode.RENTAL_UNIT_FORBIDDEN);
        }
        if (unit.isArchived()) {
            throw new BusinessRuleException(ErrorCode.RENTAL_UNIT_ARCHIVED);
        }
        findLiveListing(unitId).ifPresent(live -> {
            throw new ConflictException(ErrorCode.RENTAL_UNIT_HAS_LIVE_LISTING,
                    "Phòng này đang có tin \"" + live.getTitle() + "\" (" + live.getStatus().getLabel()
                            + "). Hãy sửa hoặc gia hạn tin đó, hoặc đóng tin trước khi đăng tin mới.");
        });
        return unit;
    }

    /** Gắn tin vào phòng: chép địa chỉ phòng sang tin và đặt cờ trùng địa chỉ (RU-3, RU-6). */
    public void attach(Listing listing, RentalUnit unit) {
        listing.setRentalUnitId(unit.getId());
        copyAddress(unit, listing);
        listing.setDuplicateAddress(hasOtherOwnerDuplicate(unit));
    }

    // ==================================================================
    //  Dò trùng (RU-6)
    // ==================================================================

    public Optional<RentalUnit> findSameOwnerDuplicate(Long ownerId, Long wardId, String addressDetail,
                                                       String unitLabel, Long excludeUnitId) {
        String key = AddressNormalizer.unitKey(addressDetail, unitLabel);
        return rentalUnitRepository.findByWardAndStatus(wardId, RentalUnitStatus.ACTIVE).stream()
                .filter(u -> u.getOwnerId().equals(ownerId))
                .filter(u -> excludeUnitId == null || !u.getId().equals(excludeUnitId))
                .filter(u -> key.equals(AddressNormalizer.unitKey(u.getAddressDetail(), u.getUnitLabel())))
                .findFirst();
    }

    public List<RentalUnit> findSameOwnerMatches(Long ownerId, Long wardId, String addressDetail,
                                                 String unitLabel, Long excludeUnitId) {
        String addressKey = AddressNormalizer.unitKey(addressDetail, null);
        return rentalUnitRepository.findByWardAndStatus(wardId, RentalUnitStatus.ACTIVE).stream()
                .filter(u -> u.getOwnerId().equals(ownerId))
                .filter(u -> excludeUnitId == null || !u.getId().equals(excludeUnitId))
                .filter(u -> addressKey.equals(AddressNormalizer.unitKey(u.getAddressDetail(), null)))
                .toList();
    }

    /** Có phòng của chủ trọ KHÁC trùng địa chỉ + số phòng không (gắn cờ, không chặn). */
    public boolean hasOtherOwnerDuplicate(Long ownerId, Long wardId, String addressDetail, String unitLabel) {
        String key = AddressNormalizer.unitKey(addressDetail, unitLabel);
        return rentalUnitRepository.findByWardAndStatus(wardId, RentalUnitStatus.ACTIVE).stream()
                .filter(u -> !u.getOwnerId().equals(ownerId))
                .anyMatch(u -> key.equals(AddressNormalizer.unitKey(u.getAddressDetail(), u.getUnitLabel())));
    }

    public boolean hasOtherOwnerDuplicate(RentalUnit unit) {
        return hasOtherOwnerDuplicate(unit.getOwnerId(), unit.getWardId(), unit.getAddressDetail(),
                unit.getUnitLabel());
    }

    // ==================================================================
    //  Đổi địa chỉ phòng (RU-3, RU-4)
    // ==================================================================

    /**
     * Đổi địa chỉ/số phòng rồi đồng bộ sang mọi tin của phòng.
     *
     * <p>Phòng đã khoá địa chỉ (đã có đánh giá) thì chỉ ADMIN được đổi. Tin {@code ACTIVE} của
     * phòng bị gửi duyệt lại vì địa chỉ là trường nhạy cảm. Tin {@code editedListing} (nếu có) là
     * tin người dùng đang sửa: hàm chỉ chép địa chỉ vào tin đó, còn việc gửi duyệt lại và phát sự
     * kiện bộ đếm do người gọi xử lý cùng các thay đổi khác của tin.
     *
     * @return {@code true} nếu địa chỉ thực sự thay đổi
     */
    public boolean changeAddress(RentalUnit unit, UnitAddress address, boolean adminOverride,
                                 Listing editedListing) {
        String addressDetail = clean(address.addressDetail());
        String unitLabel = address.unitLabel() == null ? unit.getUnitLabel() : cleanLabel(address.unitLabel());
        // Địa chỉ hiển thị trên tin (phường + số nhà/đường): đổi thì tin đang hiển thị phải duyệt lại.
        boolean addressChanged = !Objects.equals(unit.getWardId(), address.ward().getId())
                || !Objects.equals(unit.getAddressDetail(), addressDetail);
        // Số phòng không hiện trên tin nên không cần duyệt lại, nhưng vẫn bị khoá như địa chỉ (RU-4).
        boolean labelChanged = !Objects.equals(unit.getUnitLabel(), unitLabel);
        boolean placeChanged = addressChanged || labelChanged;
        boolean coordinatesChanged = coordinatesChanged(unit, address);
        if (!placeChanged && !coordinatesChanged) {
            return false;
        }
        if (placeChanged && unit.isAddressLocked() && !adminOverride) {
            throw new BusinessRuleException(ErrorCode.RENTAL_UNIT_ADDRESS_LOCKED);
        }
        if (placeChanged) {
            findSameOwnerDuplicate(unit.getOwnerId(), address.ward().getId(), addressDetail, unitLabel, unit.getId())
                    .ifPresent(existing -> {
                        throw new ConflictException(ErrorCode.RENTAL_UNIT_DUPLICATE,
                                "Địa chỉ này trùng với phòng \"" + existing.getName() + "\" (mã "
                                        + existing.getId() + ") của bạn.");
                    });
        }
        unit.setWardId(address.ward().getId());
        unit.setAddressDetail(addressDetail);
        unit.setUnitLabel(unitLabel);
        if (address.latitude() != null && address.longitude() != null) {
            applyCoordinates(unit, address.latitude(), address.longitude(), address.ward());
        } else if (addressChanged) {
            // Đổi địa chỉ mà không gửi toạ độ mới: toạ độ cũ không còn đúng, lấy tâm phường mới.
            applyCoordinates(unit, null, null, address.ward());
        }
        rentalUnitRepository.save(unit);
        if (addressChanged || coordinatesChanged) {
            syncListings(unit, editedListing, addressChanged);
        }
        return true;
    }

    /**
     * Chép địa chỉ phòng sang mọi tin chưa xoá của phòng. Nếu địa chỉ hiển thị đổi thì tin ACTIVE
     * (trừ tin đang sửa — người gọi tự xử lý) bị gửi duyệt lại.
     */
    private void syncListings(RentalUnit unit, Listing editedListing, boolean resubmitActive) {
        boolean duplicate = hasOtherOwnerDuplicate(unit);
        if (editedListing != null) {
            copyAddress(unit, editedListing);
            editedListing.setDuplicateAddress(duplicate);
        }
        for (Listing l : listingRepository.findAllByRentalUnit(unit.getId())) {
            if (l.getDeletedAt() != null || (editedListing != null && l.getId().equals(editedListing.getId()))) {
                continue;
            }
            ListingCategoryCountPublisher.Snapshot categoryBefore = categoryCountPublisher.snapshot(l);
            ListingOwnerStatsPublisher.Snapshot ownerBefore = ownerStatsPublisher.snapshot(l);
            copyAddress(unit, l);
            l.setDuplicateAddress(duplicate);
            if (resubmitActive && l.getStatus() == ListingStatus.ACTIVE) {
                l.setStatus(stateMachine.transition(l.getStatus(), ListingEvent.RESUBMIT_AFTER_EDIT));
            }
            listingRepository.save(l);
            categoryCountPublisher.publishIfChanged(categoryBefore, l);
            ownerStatsPublisher.publishIfChanged(ownerBefore, l);
        }
    }

    /** RU-3: địa chỉ của tin luôn là bản sao địa chỉ phòng. */
    public void copyAddress(RentalUnit unit, Listing listing) {
        listing.setWardId(unit.getWardId());
        listing.setAddressDetail(unit.getAddressDetail());
        listing.setLatitude(unit.getLatitude());
        listing.setLongitude(unit.getLongitude());
        listing.setLocationSource(unit.getLocationSource());
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    /**
     * Toạ độ phòng: chủ trọ gửi đủ cặp lat/lng thì dùng nguyên ({@code OWNER}); thiếu thì lấy tâm
     * phường ({@code WARD}) để lọc theo bán kính vẫn hoạt động. Phường chưa có toạ độ thì để trống.
     */
    private void applyCoordinates(RentalUnit unit, BigDecimal latitude, BigDecimal longitude, Ward ward) {
        if (latitude != null && longitude != null) {
            unit.setLatitude(latitude);
            unit.setLongitude(longitude);
            unit.setLocationSource(LocationSource.OWNER);
        } else if (ward.getLatitude() != null && ward.getLongitude() != null) {
            unit.setLatitude(ward.getLatitude());
            unit.setLongitude(ward.getLongitude());
            unit.setLocationSource(LocationSource.WARD);
        } else {
            unit.setLatitude(null);
            unit.setLongitude(null);
            unit.setLocationSource(null);
        }
    }

    private boolean coordinatesChanged(RentalUnit unit, UnitAddress address) {
        BigDecimal lat = address.latitude();
        BigDecimal lng = address.longitude();
        if (lat == null || lng == null) {
            // Không gửi toạ độ = giữ nguyên toạ độ hiện có (form chưa có ô chọn vị trí).
            return false;
        }
        return unit.getLatitude() == null || unit.getLongitude() == null
                || unit.getLatitude().compareTo(lat) != 0 || unit.getLongitude().compareTo(lng) != 0;
    }

    private static String clean(String value) {
        return value == null ? null : HtmlSanitizer.stripAllHtml(value).trim();
    }

    private static String cleanLabel(String value) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }
}
