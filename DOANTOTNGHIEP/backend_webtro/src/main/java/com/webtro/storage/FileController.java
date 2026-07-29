package com.webtro.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Phục vụ ảnh đã lưu (nằm NGOÀI webroot) qua HTTP có kiểm soát — canonical mục 8 ({@code [§11.1]}:
 * "không phục vụ tĩnh file thực thi"). Ảnh trọ là public nên endpoint này công khai (đã cho phép
 * {@code /api/files/**} trong {@code SecurityConfig}); mọi biến thể kích thước ({@code _thumb},
 * {@code _medium}) đều đi qua đây.
 *
 * <p>Key là toàn bộ phần đường dẫn sau {@code /api/files/} (ví dụ
 * {@code listings/12/ab34..._thumb}); {@link FileStorage#read} tự nối đuôi {@code .jpg} và chặn
 * path traversal.
 */
@Tag(name = "22. Files", description = "Phục vụ ảnh đã lưu")
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileStorage fileStorage;

    @Operation(summary = "Tải ảnh theo key",
            description = "Trả về nội dung ảnh JPEG. Ảnh trọ là công khai, có cache dài hạn vì tên "
                    + "file mang UUID (bất biến).")
    @GetMapping("/**")
    public ResponseEntity<byte[]> serve(HttpServletRequest request) {
        // Lấy toàn bộ phần sau "/api/files/" làm key (giữ nguyên dấu "/").
        String path = request.getRequestURI();
        String key = path.substring(path.indexOf("/api/files/") + "/api/files/".length());
        byte[] data = fileStorage.read(key);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic())
                .body(data);
    }
}
