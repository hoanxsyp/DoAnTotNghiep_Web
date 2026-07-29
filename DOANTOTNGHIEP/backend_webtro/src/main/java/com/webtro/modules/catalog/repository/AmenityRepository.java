package com.webtro.modules.catalog.repository;

import com.webtro.common.enums.AmenityGroup;
import com.webtro.modules.catalog.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho bảng tra cứu {@link Amenity}.
 */
@Repository
public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    /** Tìm tiện ích theo mã. */
    Optional<Amenity> findByCode(String code);

    /** Danh sách tiện ích đang hoạt động, sắp theo thứ tự hiển thị. */
    List<Amenity> findByIsActiveTrueOrderByDisplayOrderAsc();

    /** Danh sách tiện ích đang hoạt động theo nhóm, sắp theo thứ tự hiển thị. */
    List<Amenity> findByGroupCodeAndIsActiveTrueOrderByDisplayOrderAsc(AmenityGroup groupCode);

    /** Kiểm tra tồn tại theo mã. */
    boolean existsByCode(String code);
}
