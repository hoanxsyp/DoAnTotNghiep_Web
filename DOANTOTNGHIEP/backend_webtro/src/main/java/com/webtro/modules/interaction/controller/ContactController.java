package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.interaction.dto.request.ContactChannel;
import com.webtro.modules.interaction.dto.request.CreateContactRequest;
import com.webtro.modules.interaction.dto.response.ContactInfoResponse;
import com.webtro.modules.interaction.dto.response.ContactResultResponse;
import com.webtro.modules.interaction.dto.response.LandlordContactPageResponse;
import com.webtro.modules.interaction.service.ContactService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * API liên hệ chủ tin (CONT) — canonical mục 4.6.1–4.6.3, {@code [§3.10]}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "06. Contact & Chat", description = "Liên hệ chủ tin, chat nội bộ")
public class ContactController {

    private final ContactService contactService;

    @GetMapping("/listings/{id}/contact-info")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Xem thông tin liên hệ của tin (ghi nhận lượt liên hệ)")
    public ResponseEntity<ApiResponse<ContactInfoResponse>> getContactInfo(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        ContactInfoResponse data = contactService.getContactInfo(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thông tin liên hệ thành công"));
    }

    @PostMapping("/listings/{id}/contact")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Gửi yêu cầu liên hệ chủ tin")
    public ResponseEntity<ApiResponse<ContactResultResponse>> createContact(
            @PathVariable Long id, @Valid @RequestBody CreateContactRequest request,
            @AuthenticationPrincipal CustomUserDetails user, HttpServletRequest servletRequest) {
        ContactResultResponse data = contactService.createContact(id, request, user.getId(),
                clientIp(servletRequest));
        URI location = data.getConversationId() != null
                ? URI.create("/api/conversations/" + data.getConversationId())
                : URI.create("/api/landlord/contacts");
        return ResponseEntity.created(location)
                .body(ApiResponse.success(data, "Đã gửi yêu cầu liên hệ. Chủ trọ sẽ phản hồi sớm nhất có thể."));
    }

    @GetMapping("/landlord/contacts")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Chủ trọ xem danh sách người liên hệ tin của mình")
    public ResponseEntity<ApiResponse<LandlordContactPageResponse>> listLandlordContacts(
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) ContactChannel type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        Instant fromI = from == null ? null : from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toI = to == null ? null : to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        LandlordContactPageResponse data = contactService.listLandlordContacts(
                user.getId(), listingId, type, fromI, toI, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách người liên hệ thành công"));
    }

    /** Lấy IP client (ưu tiên X-Forwarded-For khi sau proxy). */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
