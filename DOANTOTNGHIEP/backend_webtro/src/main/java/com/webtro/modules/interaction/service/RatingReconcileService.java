package com.webtro.modules.interaction.service;

import com.webtro.modules.interaction.spi.ListingGateway;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Đối soát điểm (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 10.4): tính lại điểm mọi phòng có
 * đánh giá, chép sang tin, tính lại điểm chủ trọ, rồi đếm số tin còn lệch với phòng.
 *
 * <p>Bình thường số tin lệch luôn bằng 0 vì mọi thay đổi đều đi qua
 * {@link ReviewRatingRecalculator}. Nếu khác 0 nghĩa là có chỗ ghi điểm sai đường — job ghi log
 * cảnh báo để phát hiện sớm. Mỗi phòng xử lý trong transaction riêng để lỗi một phòng không chặn
 * các phòng khác.
 */
@Slf4j
@Component
public class RatingReconcileService {

    private final ListingGateway listingGateway;
    private final ReviewRatingRecalculator ratingRecalculator;
    private final TransactionTemplate txTemplate;

    public RatingReconcileService(ListingGateway listingGateway,
                                  ReviewRatingRecalculator ratingRecalculator,
                                  PlatformTransactionManager txManager) {
        this.listingGateway = listingGateway;
        this.ratingRecalculator = ratingRecalculator;
        this.txTemplate = new TransactionTemplate(txManager);
    }

    /** Kết quả một lượt đối soát. */
    public record Report(int units, int errors, long mismatchBefore, long mismatchAfter) {
    }

    public Report reconcileAll() {
        long mismatchBefore = listingGateway.countRatingMismatch();
        if (mismatchBefore > 0) {
            log.warn("RatingReconcile: phát hiện {} tin có điểm lệch với phòng TRƯỚC khi đối soát", mismatchBefore);
        }
        List<Long> unitIds = listingGateway.rentalUnitIdsNeedingRatingReconcile();
        int errors = 0;
        for (Long unitId : unitIds) {
            try {
                txTemplate.executeWithoutResult(status -> ratingRecalculator.lockAndRecalc(unitId));
            } catch (RuntimeException e) {
                errors++;
                log.error("RatingReconcile: lỗi khi tính lại điểm phòng {}: {}", unitId, e.getMessage(), e);
            }
        }
        long mismatchAfter = listingGateway.countRatingMismatch();
        if (mismatchAfter > 0) {
            log.error("RatingReconcile: vẫn còn {} tin lệch điểm với phòng SAU khi đối soát", mismatchAfter);
        }
        return new Report(unitIds.size(), errors, mismatchBefore, mismatchAfter);
    }
}
