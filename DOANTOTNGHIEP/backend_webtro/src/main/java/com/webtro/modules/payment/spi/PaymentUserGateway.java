package com.webtro.modules.payment.spi;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface PaymentUserGateway {

    record Buyer(Long id, String fullName, String email) {
    }

    Map<Long, Buyer> findBuyers(Collection<Long> userIds);

    Set<Long> searchBuyerIds(String keyword);
}
