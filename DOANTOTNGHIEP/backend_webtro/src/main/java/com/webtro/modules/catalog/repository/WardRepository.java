package com.webtro.modules.catalog.repository;

import com.webtro.modules.catalog.entity.Ward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho bảng tra cứu {@link Ward}.
 */
@Repository
public interface WardRepository extends JpaRepository<Ward, Long> {

    /** Tìm phường/xã theo mã. */
    Optional<Ward> findByCode(String code);

    /** Tìm phường/xã theo slug. */
    Optional<Ward> findBySlug(String slug);

    /** Danh sách phường/xã đang hoạt động của một quận/huyện, sắp theo tên. */
    List<Ward> findByDistrict_IdAndIsActiveTrueOrderByNameAsc(Long districtId);

    /** Kiểm tra tồn tại theo mã. */
    boolean existsByCode(String code);
}
