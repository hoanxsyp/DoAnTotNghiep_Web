package com.webtro.modules.listing.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu sắp xếp lại thứ tự ảnh (docs/03 mục 4.4.18). {@code imageIds} phải chứa đúng đủ tập id
 * ảnh chưa xóa của tin, không trùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageOrderRequest {

    @NotEmpty(message = "Danh sách thứ tự ảnh không được rỗng")
    private List<Long> imageIds;
}
