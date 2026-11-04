package com.webtro.modules.user.adapter;

import com.webtro.modules.payment.spi.PaymentUserGateway;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentUserGatewayAdapterTest {

    private UserRepository userRepository;
    private PaymentUserGatewayAdapter adapter;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        adapter = new PaymentUserGatewayAdapter(userRepository);
    }

    @Test
    void returnsEmptyBuyerMapForMissingIds() {
        assertThat(adapter.findBuyers(null)).isEmpty();
        assertThat(adapter.findBuyers(List.of())).isEmpty();
        verify(userRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsOnlyActiveBuyers() {
        User active = user(1L, "Nguyễn An", "an@example.test", null);
        User deleted = user(2L, "Nguyễn Bình", "binh@example.test", Instant.now());
        when(userRepository.findAllById(Set.of(1L, 2L))).thenReturn(List.of(active, deleted));

        Map<Long, PaymentUserGateway.Buyer> result = adapter.findBuyers(Set.of(1L, 2L));

        assertThat(result).containsOnlyKeys(1L);
        assertThat(result.get(1L).fullName()).isEqualTo("Nguyễn An");
        assertThat(result.get(1L).email()).isEqualTo("an@example.test");
    }

    @Test
    void returnsEmptySearchForBlankKeyword() {
        assertThat(adapter.searchBuyerIds(null)).isEmpty();
        assertThat(adapter.searchBuyerIds("  ")).isEmpty();
        verify(userRepository, never()).findActiveIdsByNameOrEmail(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void trimsKeywordAndReturnsMatchedIds() {
        when(userRepository.findActiveIdsByNameOrEmail("Nguyễn")).thenReturn(List.of(1L, 2L));
        assertThat(adapter.searchBuyerIds("  Nguyễn  ")).containsExactlyInAnyOrder(1L, 2L);
    }

    private User user(Long id, String fullName, String email, Instant deletedAt) {
        User user = User.builder().fullName(fullName).email(email).passwordHash("hash").build();
        user.setId(id);
        user.setDeletedAt(deletedAt);
        return user;
    }
}
