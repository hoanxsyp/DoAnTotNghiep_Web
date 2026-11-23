package com.roomfinder.chat.repository;

import com.roomfinder.chat.domain.ListingEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListingEmbeddingRepository extends JpaRepository<ListingEmbedding, String> {
}
