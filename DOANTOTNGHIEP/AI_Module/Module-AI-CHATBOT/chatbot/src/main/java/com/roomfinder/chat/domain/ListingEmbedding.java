package com.roomfinder.chat.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Cache embedding mô tả phòng — GĐ3 semantic rerank (SPEC §12.1). Tách khỏi bảng {@code room}
 * vì tin đăng thật nằm ở web (chatbot không ghi được), và chỉ cần tính 1 lần/phòng.
 *
 * <p>{@code listingKey} = {@code "<nguồn>:<id>"} (vd {@code backend:12}, {@code db:12}) để id
 * của hai nguồn không đè nhau. {@code descriptionHash} đổi (chủ trọ sửa mô tả) → tính lại.
 */
@Entity
@Table(name = "listing_embedding")
@Getter
@Setter
public class ListingEmbedding {

    @Id
    @Column(name = "listing_key", length = 40)
    private String listingKey;

    @Column(name = "description_hash", length = 64, nullable = false)
    private String descriptionHash;

    /** Mảng float dạng JSON (tính bằng {@code EmbeddingClient}). */
    @Column(columnDefinition = "JSON", nullable = false)
    private String vector;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
