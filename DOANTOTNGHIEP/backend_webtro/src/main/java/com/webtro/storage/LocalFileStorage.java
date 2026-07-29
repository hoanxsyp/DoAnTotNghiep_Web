package com.webtro.storage;

import com.webtro.config.properties.StorageProperties;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.UUID;

/**
 * Lưu ảnh vào thư mục cục bộ (nằm NGOÀI webroot), phục vụ qua controller có kiểm soát quyền.
 *
 * <p>Thực thi đầy đủ các yêu cầu bảo mật upload (canonical mục 8, §11.9):
 * <ul>
 *   <li>Kiểm tra <b>magic bytes</b> — không tin {@code Content-Type} client gửi.</li>
 *   <li>Chống <b>decompression bomb</b> — đọc kích thước pixel từ header TRƯỚC khi decode; vượt
 *       giới hạn thì từ chối (một PNG 100KB có thể giải nén ra ảnh khổng lồ giết JVM).</li>
 *   <li>Đổi tên thành UUID — không giữ tên gốc để tránh path traversal và lộ thông tin.</li>
 *   <li>Sinh 3 kích thước: original (≤1920), medium (800), thumb (200).</li>
 * </ul>
 *
 * <p>Ngưỡng {@code upload.max_pixels} vốn nằm ở {@code system_configs}; ở đây dùng hằng phòng thủ
 * cứng làm mức trần tuyệt đối để tránh phụ thuộc vòng vào SystemConfigService trong tầng hạ tầng.
 */
@Slf4j
@Service
public class LocalFileStorage implements FileStorage {

    private static final long MAX_PIXELS = 50_000_000L;   // trần cứng ~50MP (canonical upload.max_pixels)
    private static final int ORIGINAL_MAX = 1920;
    private static final int MEDIUM_MAX = 800;
    private static final int THUMB_MAX = 200;

    private final Path root;
    private final String publicBaseUrl;

    public LocalFileStorage(StorageProperties props) {
        this.root = Paths.get(props.getRoot()).toAbsolutePath().normalize();
        this.publicBaseUrl = props.getPublicBaseUrl().replaceAll("/+$", "");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Không tạo được thư mục lưu ảnh: " + root, e);
        }
    }

    @Override
    public StoredImage storeImage(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tệp ảnh rỗng");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được tệp tải lên", e);
        }

        ImageFormat format = detectByMagicBytes(bytes);
        if (format == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "Định dạng ảnh không hợp lệ. Chỉ chấp nhận JPG, PNG, WEBP");
        }

        guardAgainstDecompressionBomb(bytes);

        BufferedImage source = decode(bytes);
        if (source == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không giải mã được ảnh");
        }

        String key = subDir.replaceAll("/+$", "") + "/" + UUID.randomUUID().toString().replace("-", "");
        try {
            Path baseDir = resolveSafe(subDir);
            Files.createDirectories(baseDir);
            writeVariant(source, key, "", ORIGINAL_MAX);
            writeVariant(source, key, "_medium", MEDIUM_MAX);
            writeVariant(source, key, "_thumb", THUMB_MAX);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không lưu được ảnh", e);
        }

        return StoredImage.builder()
                .key(key)
                .originalUrl(resolveUrl(key))
                .mediumUrl(resolveUrl(key) + "_medium")
                .thumbnailUrl(resolveUrl(key) + "_thumb")
                .width(source.getWidth())
                .height(source.getHeight())
                .sizeBytes(bytes.length)
                .build();
    }

    @Override
    public void delete(String key) {
        for (String suffix : new String[]{"", "_medium", "_thumb"}) {
            try {
                Files.deleteIfExists(resolveKeyToPath(key + suffix));
            } catch (IOException e) {
                log.warn("Không xóa được biến thể ảnh {}{}: {}", key, suffix, e.getMessage());
            }
        }
    }

    @Override
    public String resolveUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public byte[] read(String key) {
        try {
            Path path = resolveKeyToPath(key);
            if (!Files.exists(path)) {
                throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy ảnh");
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không đọc được ảnh", e);
        }
    }

    // ==================================================================
    //  Nội bộ
    // ==================================================================

    private enum ImageFormat { JPEG, PNG, WEBP }

    /** Nhận diện định dạng bằng magic bytes — KHÔNG tin Content-Type do client gửi. */
    private ImageFormat detectByMagicBytes(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return ImageFormat.JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return ImageFormat.PNG;
        }
        // WEBP: "RIFF"...."WEBP"
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return ImageFormat.WEBP;
        }
        return null;
    }

    /**
     * Đọc kích thước từ header (không decode toàn bộ) và từ chối nếu tổng pixel vượt trần —
     * chặn decompression bomb.
     */
    private void guardAgainstDecompressionBomb(byte[] bytes) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Không đọc được thông tin ảnh");
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, true);
            long width = reader.getWidth(0);
            long height = reader.getHeight(0);
            reader.dispose();
            if (width * height > MAX_PIXELS) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Ảnh có kích thước pixel quá lớn (" + width + "x" + height + ")");
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Ảnh không hợp lệ", e);
        }
    }

    private BufferedImage decode(byte[] bytes) {
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private void writeVariant(BufferedImage source, String key, String suffix, int maxDim) throws IOException {
        BufferedImage scaled = scaleWithin(source, maxDim);
        Path out = resolveKeyToPath(key + suffix);
        Files.createDirectories(out.getParent());
        // Xuất JPEG cho mọi biến thể để đồng nhất và nhẹ; ảnh trọ không cần nền trong suốt.
        ImageIO.write(scaled, "jpg", out.toFile());
    }

    private BufferedImage scaleWithin(BufferedImage source, int maxDim) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxDim && h <= maxDim) {
            return toRgb(source);
        }
        double ratio = Math.min((double) maxDim / w, (double) maxDim / h);
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    /** Ép về RGB để ghi JPEG không lỗi với ảnh có kênh alpha. */
    private BufferedImage toRgb(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_RGB) {
            return src;
        }
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return rgb;
    }

    /** Chống path traversal: key phải nằm trong thư mục gốc. */
    private Path resolveKeyToPath(String key) {
        Path resolved = root.resolve(key + ".jpg").normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Đường dẫn ảnh không hợp lệ");
        }
        return resolved;
    }

    private Path resolveSafe(String subDir) {
        Path resolved = root.resolve(subDir).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Thư mục không hợp lệ");
        }
        return resolved;
    }
}
