package com.webtro.modules.listing.statemachine;

/**
 * Sự kiện chuyển trạng thái tin đăng (canonical mục 5.1). Mọi thay đổi {@code ListingStatus} phải
 * đi qua {@link ListingStateMachine} với một trong các sự kiện này — không service nào được
 * {@code setStatus()} trực tiếp.
 */
public enum ListingEvent {
    SAVE_DRAFT,
    SUBMIT,
    APPROVE,
    REJECT,
    HIDE_BY_OWNER,
    UNHIDE_BY_OWNER,
    AUTO_HIDE_BY_SYSTEM,
    UNHIDE_BY_MODERATOR,
    CLOSE,
    EXPIRE,
    FLAG_NEED_REVIEW,
    CLEAR_NEED_REVIEW,
    LOCK,
    UNLOCK,
    RENEW,
    SOFT_DELETE,
    RESUBMIT_AFTER_EDIT
}
