package com.webtro.modules.catalog.repository;

import com.webtro.modules.catalog.entity.District;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho bảng tra cứu {@link District}.
 */
@Repository
public interface DistrictRepository extends JpaRepository<District, Long> {

    /** Tìm quận/huyện theo mã. */
    Optional<District> findByCode(String code);

    /** Tìm quận/huyện theo slug. */
    Optional<District> findBySlug(String slug);

    /** Danh sách quận/huyện đang hoạt động của một tỉnh/thành, sắp theo thứ tự hiển thị. */
    List<District> findByProvince_IdAndIsActiveTrueOrderByDisplayOrderAsc(Long provinceId);

    /** Kiểm tra tồn tại theo mã. */
    boolean existsByCode(String code);
}
