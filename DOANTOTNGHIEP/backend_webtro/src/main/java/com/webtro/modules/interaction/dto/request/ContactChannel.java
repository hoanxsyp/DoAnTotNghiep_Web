package com.webtro.modules.interaction.dto.request;

import com.webtro.common.enums.ContactType;

/**
 * Hình thức liên hệ ở tầng API — dùng đúng tên trong tài liệu {@code [§3.10]}
 * ({@code VIEW_PHONE} / {@code SEND_FORM} / {@code START_CHAT}) và ánh xạ sang {@link ContactType}
 * lưu DB ({@code VIEW_PHONE} / {@code FORM} / {@code CHAT}).
 */
public enum ContactChannel {

    VIEW_PHONE(ContactType.VIEW_PHONE),
    SEND_FORM(ContactType.FORM),
    START_CHAT(ContactType.CHAT);

    private final ContactType entityType;

    ContactChannel(ContactType entityType) {
        this.entityType = entityType;
    }

    public ContactType toEntityType() {
        return entityType;
    }

    /** Có yêu cầu nội dung {@code message} không (SEND_FORM/START_CHAT bắt buộc). */
    public boolean requiresMessage() {
        return this == SEND_FORM || this == START_CHAT;
    }
}
