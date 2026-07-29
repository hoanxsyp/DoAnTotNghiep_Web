package com.webtro.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;

/**
 * Lớp cha cho <b>bảng tra cứu</b> (categories, provinces, districts, wards, amenities):
 * chỉ cần {@code id}, {@code created_at}, {@code updated_at}
 * ({@code docs/00_CANONICAL_DECISIONS.md} mục 6.1).
 *
 * <p>Bảng nghiệp vụ dùng {@link AuditableEntity} (có thêm người tạo/sửa và xóa mềm).
 */
@Getter
@Setter
@MappedSuperclass
@SuperBuilder
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * Entity chưa được lưu lần nào (chưa có id do DB sinh).
     */
    public boolean isNew() {
        return id == null;
    }

    /*
     * equals/hashCode cho entity JPA là một cái bẫy kinh điển. Ba ràng buộc phải thỏa cùng lúc:
     *
     *  1. Phải xuyên qua được Hibernate proxy — với lazy loading, `getClass()` trả về lớp proxy
     *     ($HibernateProxy), nên so sánh `getClass() != o.getClass()` sẽ cho FALSE ngay cả khi
     *     hai bên là cùng một hàng dữ liệu.
     *  2. hashCode phải KHÔNG ĐỔI trong suốt vòng đời entity. Nếu hash theo `id` thì entity mới
     *     (id null) bỏ vào HashSet rồi save() -> id được gán -> hashCode đổi -> entity "biến mất"
     *     khỏi Set. Vì vậy hashCode trả về hằng số theo lớp.
     *  3. Entity chưa lưu chỉ bằng chính nó (so sánh tham chiếu), vì chúng chưa có định danh.
     */

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> thisClass = this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : this.getClass();
        Class<?> otherClass = o instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
        if (!thisClass.equals(otherClass)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        // id == null: entity chưa lưu -> chỉ bằng chính nó (đã xét ở `this == o` phía trên).
        return this.getId() != null && Objects.equals(this.getId(), other.getId());
    }

    @Override
    public final int hashCode() {
        return this instanceof HibernateProxy hp
                ? hp.getHibernateLazyInitializer().getPersistentClass().hashCode()
                : getClass().hashCode();
    }
}
