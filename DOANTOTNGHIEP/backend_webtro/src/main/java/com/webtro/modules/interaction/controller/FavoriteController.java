package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.CreateFavoriteRequest;
import com.webtro.modules.interaction.dto.response.FavoriteResponse;
import com.webtro.modules.interaction.dto.response.FavoriteToggleResponse;
import com.webtro.modules.interaction.service.FavoriteService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API tin đã lưu (FAV) — canonical mục 4.5.1–4.5.3, {@code [§3.9]}.
 */
@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
@Tag(name = "05. Favorite & History", description = "Lưu tin, lịch sử xem/tìm kiếm")
public class FavoriteController {

    private final FavoriteService favoriteService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Lưu tin vào danh sách yêu thích")
    public ResponseEntity<ApiResponse<FavoriteToggleResponse>> addFavorite(
            @Valid @RequestBody CreateFavoriteRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        FavoriteToggleResponse data = favoriteService.addFavorite(request, user.getId());
        return ResponseEntity.created(URI.create("/api/favorites/" + data.getListingId()))
                .body(ApiResponse.success(data, "Đã lưu tin vào danh sách yêu thích"));
    }

    @DeleteMapping("/{listingId}")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Bỏ lưu tin")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long listingId,
                                               @AuthenticationPrincipal CustomUserDetails user) {
        favoriteService.removeFavorite(listingId, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Danh sách tin đã lưu")
    public ResponseEntity<ApiResponse<PageResponse<FavoriteResponse>>> listFavorites(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<FavoriteResponse> data = favoriteService.listFavorites(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tin đã lưu thành công"));
    }
}
