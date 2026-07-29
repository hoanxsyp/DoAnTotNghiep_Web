package com.webtro.modules.listing.statemachine;

import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.webtro.common.enums.ListingStatus.ACTIVE;
import static com.webtro.common.enums.ListingStatus.CLOSED;
import static com.webtro.common.enums.ListingStatus.DELETED;
import static com.webtro.common.enums.ListingStatus.DRAFT;
import static com.webtro.common.enums.ListingStatus.EXPIRED;
import static com.webtro.common.enums.ListingStatus.HIDDEN;
import static com.webtro.common.enums.ListingStatus.LOCKED;
import static com.webtro.common.enums.ListingStatus.NEED_REVIEW;
import static com.webtro.common.enums.ListingStatus.PENDING;
import static com.webtro.common.enums.ListingStatus.REJECTED;

/**
 * Máy trạng thái tin đăng (canonical mục 5.1) — <b>cổng DUY NHẤT</b> để đổi {@link ListingStatus}.
 *
 * <p>Bảng chuyển trạng thái hợp lệ được khai báo tường minh: mỗi (sự kiện) → (tập trạng thái nguồn
 * cho phép) → (trạng thái đích). Gọi {@link #transition} với trạng thái hiện tại + sự kiện; nếu
 * không hợp lệ ném {@link BusinessRuleException} (HTTP 422) thay vì âm thầm đặt sai trạng thái.
 *
 * <p>Nhờ tập trung ở đây, các ràng buộc như "LOCKED không được RENEW/SUBMIT/SOFT_DELETE" hay
 * "ẩn bởi hệ thống chỉ Moderator gỡ được" không bị rải rác và quên sót trong service.
 */
@Component
public class ListingStateMachine {

    /** Một luật chuyển: từ tập trạng thái nguồn sang một trạng thái đích. */
    private record Rule(Set<ListingStatus> from, ListingStatus to) {
    }

    private final Map<ListingEvent, Rule> rules = new java.util.EnumMap<>(ListingEvent.class);

    public ListingStateMachine() {
        rules.put(ListingEvent.SUBMIT, new Rule(EnumSet.of(DRAFT, REJECTED), PENDING));
        rules.put(ListingEvent.APPROVE, new Rule(EnumSet.of(PENDING), ACTIVE));
        rules.put(ListingEvent.REJECT, new Rule(EnumSet.of(PENDING), REJECTED));
        rules.put(ListingEvent.HIDE_BY_OWNER, new Rule(EnumSet.of(ACTIVE), HIDDEN));
        rules.put(ListingEvent.UNHIDE_BY_OWNER, new Rule(EnumSet.of(HIDDEN), ACTIVE));
        rules.put(ListingEvent.AUTO_HIDE_BY_SYSTEM, new Rule(EnumSet.of(ACTIVE, NEED_REVIEW), HIDDEN));
        rules.put(ListingEvent.UNHIDE_BY_MODERATOR, new Rule(EnumSet.of(HIDDEN), ACTIVE));
        rules.put(ListingEvent.CLOSE, new Rule(EnumSet.of(ACTIVE, HIDDEN), CLOSED));
        rules.put(ListingEvent.EXPIRE, new Rule(EnumSet.of(ACTIVE, NEED_REVIEW), EXPIRED));
        rules.put(ListingEvent.FLAG_NEED_REVIEW, new Rule(EnumSet.of(ACTIVE), NEED_REVIEW));
        rules.put(ListingEvent.CLEAR_NEED_REVIEW, new Rule(EnumSet.of(NEED_REVIEW), ACTIVE));
        rules.put(ListingEvent.LOCK, new Rule(EnumSet.of(ACTIVE, NEED_REVIEW, HIDDEN, PENDING), LOCKED));
        rules.put(ListingEvent.UNLOCK, new Rule(EnumSet.of(LOCKED), HIDDEN));
        rules.put(ListingEvent.RENEW, new Rule(EnumSet.of(ACTIVE, EXPIRED), ACTIVE));
        rules.put(ListingEvent.RESUBMIT_AFTER_EDIT, new Rule(EnumSet.of(ACTIVE), PENDING));
        // SOFT_DELETE: mọi trạng thái TRỪ LOCKED (canonical mục 5.1).
        rules.put(ListingEvent.SOFT_DELETE, new Rule(
                EnumSet.complementOf(EnumSet.of(LOCKED, DELETED)), DELETED));
    }

    /**
     * Tính trạng thái đích cho một chuyển hợp lệ. Ném lỗi nếu chuyển không được phép.
     *
     * @param current trạng thái hiện tại của tin
     * @param event   sự kiện muốn thực hiện
     * @return trạng thái mới
     */
    public ListingStatus transition(ListingStatus current, ListingEvent event) {
        Rule rule = rules.get(event);
        if (rule == null) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATED,
                    "Sự kiện không được hỗ trợ: " + event);
        }
        if (!rule.from().contains(current)) {
            throw new BusinessRuleException(ErrorCode.LISTING_INVALID_TRANSITION,
                    "Không thể '" + event + "' khi tin đang ở trạng thái " + current.getLabel());
        }
        return rule.to();
    }

    /** Kiểm tra một chuyển có hợp lệ không (không ném lỗi) — dùng để hiện/ẩn nút hành động. */
    public boolean canTransition(ListingStatus current, ListingEvent event) {
        Rule rule = rules.get(event);
        return rule != null && rule.from().contains(current);
    }
}
