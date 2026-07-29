package com.webtro.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * Trừu tượng lưu trữ tệp (canonical mục 12, §11.6). Tách interface NGAY từ đầu để sau này thay
 * {@link LocalFileStorage} bằng cloud storage (S3...) mà không đụng tầng nghiệp vụ.
 */
public interface FileStorage {

    /**
     * Lưu ảnh đã kiểm tra hợp lệ và sinh các kích thước. Trả về khóa (key) để tham chiếu về sau.
     *
     * @param file    tệp ảnh upload
     * @param subDir  thư mục con phân loại (ví dụ "listings/123")
     * @return thông tin ảnh đã lưu (key, các kích thước, width/height gốc)
     */
    StoredImage storeImage(MultipartFile file, String subDir);

    /** Xóa ảnh (và mọi biến thể kích thước) theo key. Không lỗi nếu tệp đã không còn. */
    void delete(String key);

    /** Dựng URL công khai đầy đủ từ key. */
    String resolveUrl(String key);

    /**
     * Đọc nội dung tệp theo key để phục vụ có kiểm soát quyền (ảnh nằm ngoài webroot, không phục
     * vụ tĩnh — {@code [§11.1]}).
     */
    byte[] read(String key);
}
