package com.webtro.modules.admin.mapper;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.RentalUnitRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminListingMapperTest {

    private final WardRepository wardRepository = mock(WardRepository.class);
    private final RentalUnitRepository rentalUnitRepository = mock(RentalUnitRepository.class);
    private final AdminListingMapper mapper = new AdminListingMapper(wardRepository, rentalUnitRepository);

    @Test
    void mapsProvinceNameFromWardHierarchy() {
        Province province = Province.builder().name("Hà Nội").build();
        province.setId(1L);
        District district = District.builder().province(province).build();
        district.setId(2L);
        Ward ward = Ward.builder().district(district).build();
        ward.setId(3L);
        Listing listing = listing(3L);
        when(wardRepository.findById(3L)).thenReturn(Optional.of(ward));

        var result = mapper.toResponse(listing, 0L);

        assertThat(result.getProvinceId()).isEqualTo(1L);
        assertThat(result.getProvinceName()).isEqualTo("Hà Nội");
    }

    @Test
    void keepsProvinceMetadataEmptyWhenWardIsMissing() {
        Listing listing = listing(null);

        var result = mapper.toResponse(listing, 0L);

        assertThat(result.getProvinceId()).isNull();
        assertThat(result.getProvinceName()).isNull();
    }

    private Listing listing(Long wardId) {
        Listing listing = Listing.builder()
                .wardId(wardId).rentalUnitId(4L).status(ListingStatus.PENDING).build();
        listing.setId(5L);
        when(rentalUnitRepository.findById(4L)).thenReturn(Optional.empty());
        return listing;
    }
}
