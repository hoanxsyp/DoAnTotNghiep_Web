package com.webtro.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình lưu trữ ảnh. Bind từ {@code app.storage.*}. Thư mục gốc nằm NGOÀI webroot và được
 * phục vụ qua controller có kiểm soát quyền, không phục vụ tĩnh {@code [§11.1]}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    private String root = "./storage";
    private String publicBaseUrl = "http://localhost:8080/api/files";
    private int thumbnailWidth = 400;
    private int thumbnailHeight = 300;
}
