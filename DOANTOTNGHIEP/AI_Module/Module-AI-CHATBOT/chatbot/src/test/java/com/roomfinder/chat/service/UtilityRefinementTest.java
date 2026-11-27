package com.roomfinder.chat.service;

import com.roomfinder.chat.config.ChatProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.listing.RoomSource;
import com.roomfinder.chat.model.Filters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UtilityRefinementTest {
    @Test
    void relaxationKeepsTheNewUtilityWhenAnOlderUtilityBlocksMatches() {
        RoomSource source = mock(RoomSource.class);
        Room room = new Room();
        when(source.search(any(Filters.class), isNull(), isNull(), eq(0.0), eq(5)))
                .thenAnswer(call -> {
                    Filters query = call.getArgument(0);
                    return "Thanh Xuân".equals(query.getLocation())
                            && query.getUtilities().equals(List.of("free_hours")) ? List.of(room) : List.of();
                });
        RetrievalService retrieval = new RetrievalService(source, null, null, new ChatProperties());
        Filters filters = new Filters();
        filters.setLocation("Thanh Xuân");
        filters.setUtilities(List.of("washing_machine", "free_hours"));

        var result = retrieval.search(filters, 5, List.of("free_hours"));

        assertEquals(List.of(room), result.getRooms());
        assertTrue(result.isRelaxed());
        assertEquals(List.of("washing_machine", "free_hours"), filters.getUtilities());
    }
}
