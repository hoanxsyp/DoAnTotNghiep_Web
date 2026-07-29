package com.webtro.modules.catalog.repository;

import com.webtro.modules.catalog.entity.Province;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho bảng tra cứu {@link Province}.
 */
@Repository
public interface ProvinceRepository extends JpaRepository<Province, Long> {

    /** Tìm tỉnh/thành theo mã. */
    Optional<Province> findByCode(String code);

    /** Tìm tỉnh/thành theo slug. */
    Optional<Province> findBySlug(String slug);

    /** Danh sách tỉnh/thành đang hoạt động, sắp theo thứ tự hiển thị. */
    List<Province> findByIsActiveTrueOrderByDisplayOrderAsc();

    /** Kiểm tra tồn tại theo mã. */
    boolean existsByCode(String code);
}
