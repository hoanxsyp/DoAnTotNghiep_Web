package com.webtro.modules.interaction.repository;

import com.webtro.common.enums.CommentStatus;
import com.webtro.modules.interaction.entity.Comment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn bình luận. Dùng {@link JpaSpecificationExecutor} cho lọc động (trạng thái, sentiment,
 * cờ rủi ro...). Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Long>, JpaSpecificationExecutor<Comment> {

    /** Bình luận gốc (không phải trả lời) của một tin theo trạng thái, mới nhất trước. */
    Page<Comment> findByListingIdAndParentIsNullAndStatusAndDeletedAtIsNull(
            Long listingId, CommentStatus status, Pageable pageable);

    /** Danh sách trả lời của một bình luận. */
    List<Comment> findByParent_IdAndStatusAndDeletedAtIsNullOrderByCreatedAtAsc(Long parentId, CommentStatus status);

    /** Lấy bình luận còn hiệu lực theo id. */
    Optional<Comment> findByIdAndDeletedAtIsNull(Long id);

    /** Đếm bình luận hiển thị của một tin (đồng bộ {@code listings.comment_count}). */
    long countByListingIdAndStatusAndDeletedAtIsNull(Long listingId, CommentStatus status);
}
