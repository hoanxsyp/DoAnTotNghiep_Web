package com.roomfinder.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nguồn dữ liệu phòng — khối `roomfinder.listing`.
 *
 * <ul>
 *   <li>{@code db}: bảng {@code room} trong DB riêng của chatbot (59 phòng demo, seed bởi
 *       {@code data.sql}) — giữ cho frontend demo của module và bộ đo Recall@5.</li>
 *   <li>{@code backend}: tin đăng THẬT của web, đọc qua API công khai của backend_webtro
 *       ({@code /api/search/listings}, {@code /api/listings/{id}}). Chatbot không đọc DB của web
 *       — quy tắc tin công khai ({@code publicStatuses()}, canonical §5.2) do backend lo.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "roomfinder.listing")
public class ListingSourceProperties {

    private String source = "db";
    private String backendUrl = "http://localhost:8080";
    private int timeoutMs = 3000;
    /** Cache chi tiết tin (giây) — 1 lượt chat đọc 5–20 tin, tránh gọi lại backend liên tục. */
    private int detailCacheSeconds = 300;
    /** Cache danh mục quận/phường/tiện ích (phút) — gần như không đổi. */
    private int catalogCacheMinutes = 60;
    /** Số tin tối đa lấy về mỗi lần tìm rồi tự sắp (backend giới hạn 100; tin đẩy luôn bị xếp đầu). */
    private int poolSize = 100;

    public String getSource() { return source; }
    public void setSource(String v) { this.source = v; }
    public String getBackendUrl() { return backendUrl; }
    public void setBackendUrl(String v) { this.backendUrl = v; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int v) { this.timeoutMs = v; }
    public int getDetailCacheSeconds() { return detailCacheSeconds; }
    public void setDetailCacheSeconds(int v) { this.detailCacheSeconds = v; }
    public int getCatalogCacheMinutes() { return catalogCacheMinutes; }
    public void setCatalogCacheMinutes(int v) { this.catalogCacheMinutes = v; }
    public int getPoolSize() { return poolSize; }
    public void setPoolSize(int v) { this.poolSize = v; }
}
