package com.webtro.modules.interaction.service;

import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Đường ghi DUY NHẤT vào điểm phòng, điểm tin (bản sao) và điểm chủ trọ —
 * docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 10.
 *
 * <p>Cách dùng, trong cùng transaction với thay đổi đánh giá:
 * <pre>
 *   Scope scope = recalculator.lock(unitId);   // 1. khoá TRƯỚC khi đọc/ghi đánh giá
 *   ... tạo / sửa / ẩn / xoá đánh giá ...
 *   Result r = recalculator.recalc(scope);     // 2. tính lại từ đầu bằng AVG/COUNT
 * </pre>
 *
 * <p><b>Thứ tự khoá: hồ sơ chủ trọ trước, phòng sau.</b> Điểm chủ trọ được tính bằng cách quét
 * đánh giá của mọi phòng thuộc chủ trọ; nếu khoá phòng trước thì 2 đánh giá ghi cùng lúc vào 2
 * phòng của cùng chủ trọ sẽ chờ nhau (deadlock). Khoá chủ trọ trước khiến mọi thay đổi đánh giá
 * của một chủ trọ chạy lần lượt.
 *
 * <p>Điểm được tính lại từ đầu (không cộng trừ tăng dần) nên không tích luỹ sai số, và các truy
 * vấn đọc khoá ({@code LOCK IN SHARE MODE}) luôn thấy bản đã commit mới nhất.
 */
@Component
@RequiredArgsConstructor
public class ReviewRatingRecalculator {

    private final ReviewRepository reviewRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;

    /** Phạm vi đã khoá: phòng, chủ phòng và việc chủ phòng có hồ sơ chủ trọ hay không. */
    public record Scope(Long rentalUnitId, Long ownerId, boolean hasLandlordProfile) {
    }

    /**
     * Điểm sau khi tính lại. {@code landlordAverage}/{@code landlordCount} là {@code null} khi chủ
     * phòng không có hồ sơ chủ trọ.
     */
    public record Result(BigDecimal unitAverage, int unitCount, BigDecimal landlordAverage, Integer landlordCount) {
    }

    /** Bước 1: khoá hồ sơ chủ trọ rồi khoá phòng. Gọi trước khi thay đổi đánh giá. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Scope lock(Long rentalUnitId) {
        Long ownerId = listingGateway.getUnitBrief(rentalUnitId).ownerId();
        boolean hasProfile = userGateway.lockLandlordProfile(ownerId);
        listingGateway.lockRentalUnitForRating(rentalUnitId);
        return new Scope(rentalUnitId, ownerId, hasProfile);
    }

    /** Bước 2: tính lại điểm phòng, chép sang mọi tin của phòng, tính lại điểm chủ trọ. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result recalc(Scope scope) {
        int unitCount = (int) reviewRepository.countVisibleByUnitLocking(scope.rentalUnitId());
        long unitSum = reviewRepository.sumVisibleRatingByUnitLocking(scope.rentalUnitId());
        BigDecimal unitAverage = average(unitSum, unitCount);
        listingGateway.applyUnitRating(scope.rentalUnitId(), unitAverage, unitCount);

        if (!scope.hasLandlordProfile()) {
            return new Result(unitAverage, unitCount, null, null);
        }
        int landlordCount = (int) reviewRepository.countVisibleByOwnerLocking(scope.ownerId());
        long landlordSum = reviewRepository.sumVisibleRatingByOwnerLocking(scope.ownerId());
        BigDecimal landlordAverage = average(landlordSum, landlordCount);
        userGateway.updateLandlordReviewAggregate(scope.ownerId(), landlordAverage, landlordCount);
        return new Result(unitAverage, unitCount, landlordAverage, landlordCount);
    }

    /** Khoá và tính lại ngay — dùng cho job đối soát và dữ liệu demo. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Result lockAndRecalc(Long rentalUnitId) {
        return recalc(lock(rentalUnitId));
    }

    static BigDecimal average(long sum, int count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(sum).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
