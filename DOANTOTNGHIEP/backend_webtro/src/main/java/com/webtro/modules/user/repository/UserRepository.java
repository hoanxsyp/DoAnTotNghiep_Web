package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link User}.
 *
 * <p>Kèm {@link JpaSpecificationExecutor} vì màn quản trị người dùng cần lọc động (trạng thái,
 * vai trò, từ khóa...). Uniqueness email/phone là case-insensitive và loại trừ tài khoản đã xóa
 * (ép ở DB qua cột sinh {@code email_uk}/{@code phone_uk}); ở tầng ứng dụng dùng các method
 * {@code ...IgnoreCaseAndDeletedAtIsNull} để kiểm tra trùng trước khi ghi.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Lấy user còn sống theo id (mọi luồng nghiệp vụ thông thường). */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /** Đăng nhập / tra cứu theo email (không phân biệt hoa thường, bỏ tài khoản đã xóa). */
    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /** Kiểm tra email đã tồn tại (đăng ký / đổi email). */
    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    /** Tra cứu theo số điện thoại (bỏ tài khoản đã xóa). */
    Optional<User> findByPhoneAndDeletedAtIsNull(String phone);

    /** Kiểm tra số điện thoại đã tồn tại. */
    boolean existsByPhoneAndDeletedAtIsNull(String phone);

    /**
     * Người dùng còn sống mang một trong các vai trò cho trước — dùng để gửi thông báo cho toàn
     * bộ nhân sự vận hành (ADMIN + MODERATOR) mà không phải quét cả bảng.
     */
    List<User> findByRole_CodeInAndDeletedAtIsNull(Collection<String> roleCodes);
}
