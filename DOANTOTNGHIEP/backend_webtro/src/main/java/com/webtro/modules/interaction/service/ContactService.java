package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.ContactChannel;
import com.webtro.modules.interaction.dto.request.CreateContactRequest;
import com.webtro.modules.interaction.dto.response.ContactInfoResponse;
import com.webtro.modules.interaction.dto.response.ContactResultResponse;
import com.webtro.modules.interaction.dto.response.LandlordContactPageResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Nghiệp vụ liên hệ chủ tin — CONT, canonical mục 4.6, {@code [§3.10]}.
 */
public interface ContactService {

    /** Xem thông tin liên hệ (CONT-01/05): ghi {@code ContactLog} VIEW_PHONE, khử trùng, báo chủ trọ. */
    ContactInfoResponse getContactInfo(Long listingId, Long userId);

    /** Gửi yêu cầu liên hệ (CONT-02/05): tùy hình thức trả SĐT hoặc tạo hội thoại + tin nhắn đầu. */
    ContactResultResponse createContact(Long listingId, CreateContactRequest request, Long userId, String ipAddress);

    /**
     * Chủ trọ xem danh sách người đã liên hệ tin của mình (CONT-04).
     *
     * @param listingId lọc theo tin (phải thuộc chủ trọ) — có thể null
     * @param type      lọc theo hình thức — có thể null
     */
    LandlordContactPageResponse listLandlordContacts(Long ownerId, Long listingId, ContactChannel type,
                                                     Instant from, Instant to, Pageable pageable);
}
