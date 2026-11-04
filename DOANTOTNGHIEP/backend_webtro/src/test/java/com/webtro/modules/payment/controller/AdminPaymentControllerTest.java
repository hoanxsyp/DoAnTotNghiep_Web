package com.webtro.modules.payment.controller;

import com.webtro.common.enums.PaymentMethod;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.modules.payment.dto.response.PaymentHistoryResponse;
import com.webtro.modules.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminPaymentControllerTest {

    @Test
    void passesKeywordAndPaymentMethodToService() {
        PaymentService paymentService = mock(PaymentService.class);
        AdminPaymentController controller = new AdminPaymentController(paymentService);
        PageRequest pageable = PageRequest.of(0, 20);
        PaymentHistoryResponse response = PaymentHistoryResponse.builder().build();
        when(paymentService.adminListPayments(any(), eq(pageable))).thenReturn(response);

        var result = controller.list(
                List.of(PaymentStatus.SUCCESS), List.of(PaymentMethod.SANDBOX),
                1L, 2L, 3L, "WT2026", "người mua",
                BigDecimal.TEN, BigDecimal.valueOf(100),
                LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 4), pageable);

        assertThat(result.getBody()).isNotNull();
        verify(paymentService).adminListPayments(
                org.mockito.ArgumentMatchers.argThat(filter ->
                        filter.methods().equals(List.of(PaymentMethod.SANDBOX))
                                && filter.keyword().equals("người mua")
                                && filter.transactionCode().equals("WT2026")),
                eq(pageable));
    }
}
