package com.roomfinder.chat.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ListingSourceProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * Gọi API công khai của backend_webtro và bóc envelope {@code {success, data, ...}}.
 *
 * <p>Đọc JSON dạng cây ({@link JsonNode}) thay vì map vào class: Jackson của chatbot cấu hình
 * SNAKE_CASE toàn cục cho API của chính nó, còn backend trả camelCase — map vào class dễ lệch
 * tên trường mà không báo lỗi. Gọi KHÔNG kèm token (khách) — chỉ thấy tin công khai.
 */
@Component
@ConditionalOnProperty(prefix = "roomfinder.listing", name = "source", havingValue = "backend")
public class WebtroApiClient {

    private final RestClient http;
    private final URI baseUri;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebtroApiClient(ListingSourceProperties props) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getTimeoutMs());
        f.setReadTimeout(props.getTimeoutMs());
        this.http = RestClient.builder().requestFactory(f).build();
        this.baseUri = URI.create(props.getBackendUrl().replaceAll("/+$", "") + "/");
    }

    /** Trả {@code data} của envelope; {@code null} nếu backend trả 404. Lỗi khác ném {@link ListingSourceException}. */
    public JsonNode getData(URI pathAndQuery) {
        try {
            String body = http.get()
                    // URI đã mã hóa sẵn → ghép thành URI tuyệt đối, KHÔNG qua uri(String): bản đó coi
                    // chuỗi là template và mã hóa lần 2 ("%C4%90" → "%25C4%90"), làm từ khóa có dấu/
                    // dấu cách ("Linh Đàm") tới backend thành chuỗi rác → 0 kết quả.
                    .uri(baseUri.resolve(pathAndQuery.toString().replaceFirst("^/+", "")))
                    .retrieve()
                    .body(String.class);
            JsonNode root = body == null ? null : mapper.readTree(body);
            return root == null ? null : root.get("data");
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (HttpClientErrorException e) {
            throw new ListingSourceException("Backend trả " + statusOf(e.getStatusCode()) + " cho " + pathAndQuery, e);
        } catch (ListingSourceException e) {
            throw e;
        } catch (Exception e) {
            throw new ListingSourceException("Không gọi được backend (" + pathAndQuery + "): " + e.getMessage(), e);
        }
    }

    private static String statusOf(HttpStatusCode code) {
        return String.valueOf(code.value());
    }
}
