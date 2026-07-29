package com.webtro.modules.catalog.repository;

import com.webtro.common.enums.CategoryCode;
import com.webtro.modules.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho bảng tra cứu {@link Category}.
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Tìm loại tin theo mã enum. */
    Optional<Category> findByCode(CategoryCode code);

    /** Tìm loại tin theo slug. */
    Optional<Category> findBySlug(String slug);

    /** Danh sách loại tin đang hoạt động, sắp theo thứ tự hiển thị. */
    List<Category> findByIsActiveTrueOrderByDisplayOrderAsc();

    /** Kiểm tra tồn tại theo mã. */
    boolean existsByCode(CategoryCode code);

    /** Kiểm tra tồn tại theo slug. */
    boolean existsBySlug(String slug);
}
