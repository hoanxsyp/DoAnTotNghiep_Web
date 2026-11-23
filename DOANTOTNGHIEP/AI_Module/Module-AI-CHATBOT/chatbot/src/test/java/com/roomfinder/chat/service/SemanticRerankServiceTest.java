package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ListingSourceProperties;
import com.roomfinder.chat.config.SemanticProperties;
import com.roomfinder.chat.domain.ListingEmbedding;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.embedding.EmbeddingClient;
import com.roomfinder.chat.repository.ListingEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Kiểm thử semantic rerank (§12.1, GĐ3) — thuần logic, EmbeddingClient giả lập. */
class SemanticRerankServiceTest {

    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
    private final ListingEmbeddingRepository embeddingRepo = mock(ListingEmbeddingRepository.class);
    private final SemanticProperties props = new SemanticProperties();
    private final SemanticRerankService service = new SemanticRerankService(
            embeddingClient, embeddingRepo, props, new ObjectMapper(), new ListingSourceProperties());

    private Room room(long id, String desc) {
        Room r = new Room();
        r.setId(id);
        r.setDescription(desc);
        return r;
    }

    @Test
    void returnsNullWhenEmbeddingFails() {
        when(embeddingClient.embed("phòng yên tĩnh")).thenReturn(null);
        assertNull(service.rerank("phòng yên tĩnh", List.of(room(1, "a"), room(2, "b"))));
    }

    @Test
    void ranksRoomWithMatchingDescriptionFirst() {
        Room quiet = room(1, "phòng yên tĩnh thoáng mát");
        Room busy = room(2, "khu sôi động gần chợ đêm");

        when(embeddingClient.embed("tìm phòng yên tĩnh")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("phòng yên tĩnh thoáng mát")).thenReturn(new float[]{0.9f, 0.1f, 0});
        when(embeddingClient.embed("khu sôi động gần chợ đêm")).thenReturn(new float[]{0, 0, 1});

        List<Room> ranked = service.rerank("tìm phòng yên tĩnh", List.of(busy, quiet));

        assertEquals(List.of(1L, 2L), ranked.stream().map(Room::getId).toList());
    }

    @Test
    void reusesCachedVectorWhenDescriptionUnchanged() {
        Room quiet = room(1, "phòng yên tĩnh thoáng mát");
        Room busy = room(2, "khu sôi động gần chợ đêm");
        when(embeddingClient.embed("tìm phòng yên tĩnh")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("phòng yên tĩnh thoáng mát")).thenReturn(new float[]{0.9f, 0.1f, 0});
        when(embeddingClient.embed("khu sôi động gần chợ đêm")).thenReturn(new float[]{0, 0, 1});

        // Lượt 1: chưa có cache -> embed + lưu (khóa "db:<id>", kèm hash mô tả)
        service.rerank("tìm phòng yên tĩnh", List.of(busy, quiet));
        ArgumentCaptor<ListingEmbedding> saved = ArgumentCaptor.forClass(ListingEmbedding.class);
        verify(embeddingRepo, times(2)).save(saved.capture());
        ListingEmbedding cachedQuiet = saved.getAllValues().stream()
                .filter(e -> e.getListingKey().equals("db:1")).findFirst().orElseThrow();

        // Lượt 2: cache trúng (cùng hash) -> KHÔNG embed lại mô tả
        when(embeddingRepo.findById("db:1")).thenReturn(Optional.of(cachedQuiet));
        service.rerank("tìm phòng yên tĩnh", List.of(quiet, room(3, "phòng yên tĩnh thoáng mát")));
        verify(embeddingClient, times(2)).embed("phòng yên tĩnh thoáng mát"); // lượt 1 + phòng 3 (chưa cache)
    }

    @Test
    void recomputesVectorWhenDescriptionChanged() {
        ListingEmbedding stale = new ListingEmbedding();
        stale.setListingKey("db:1");
        stale.setDescriptionHash("hash-cua-mo-ta-cu");
        stale.setVector("[0,0,1]");
        when(embeddingRepo.findById("db:1")).thenReturn(Optional.of(stale));
        when(embeddingClient.embed("q")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("mô tả mới")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("khác")).thenReturn(new float[]{0, 1, 0});

        List<Room> ranked = service.rerank("q", List.of(room(2, "khác"), room(1, "mô tả mới")));

        verify(embeddingClient).embed("mô tả mới");
        assertEquals(List.of(1L, 2L), ranked.stream().map(Room::getId).toList());
    }

    @Test
    void skipsRoomsWithoutDescription() {
        when(embeddingClient.embed("q")).thenReturn(new float[]{1, 0, 0});
        when(embeddingClient.embed("có mô tả")).thenReturn(new float[]{1, 0, 0});

        List<Room> ranked = service.rerank("q", List.of(room(1, null), room(2, "có mô tả")));

        assertEquals(List.of(2L, 1L), ranked.stream().map(Room::getId).toList());
        verify(embeddingRepo, never()).findById("db:1");
        verify(embeddingRepo, times(1)).save(any());
    }
}
