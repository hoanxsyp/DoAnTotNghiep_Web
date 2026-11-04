package com.webtro.modules.user.adapter;

import com.webtro.modules.payment.spi.PaymentUserGateway;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class PaymentUserGatewayAdapter implements PaymentUserGateway {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Map<Long, Buyer> findBuyers(Collection<Long> userIds) {
        Map<Long, Buyer> buyers = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return buyers;
        }
        for (User user : userRepository.findAllById(userIds)) {
            if (user.getDeletedAt() == null) {
                buyers.put(user.getId(), new Buyer(user.getId(), user.getFullName(), user.getEmail()));
            }
        }
        return buyers;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> searchBuyerIds(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Set.of();
        }
        return Set.copyOf(userRepository.findActiveIdsByNameOrEmail(keyword.trim()));
    }
}
