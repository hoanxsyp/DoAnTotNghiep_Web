package com.webtro.modules.admin.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.modules.admin.dto.response.AuditLogResponse;
import com.webtro.modules.admin.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mapper thủ công {@link AuditLog} → {@link AuditLogResponse} (canonical: mapper là nơi DUY NHẤT
 * chuyển entity↔DTO, dùng Builder, không MapStruct).
 *
 * <p>Suy {@code changes[]} từ hai cột JSON {@code old_value}/{@code new_value}: nếu cả hai là object
 * JSON thì ghép theo khóa; nếu là giá trị vô hướng thì tạo một dòng {@code field = "value"}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogMapper {

    private final ObjectMapper objectMapper;

    /** Chuyển một bản ghi audit sang DTO hiển thị cho Admin. */
    public AuditLogResponse toResponse(AuditLog e) {
        return AuditLogResponse.builder()
                .id(e.getId())
                .action(e.getAction() != null ? e.getAction().name() : null)
                .actionLabel(e.getAction() != null ? e.getAction().getLabel() : null)
                .actorId(e.getActorId())
                .actorEmail(e.getActorEmail())
                .targetType(e.getTargetType())
                .targetId(e.getTargetId())
                .targetLabel(e.getTargetLabel())
                .changes(buildChanges(e.getOldValue(), e.getNewValue()))
                .reason(e.getReason())
                .ipAddress(e.getIpAddress())
                .userAgent(e.getUserAgent())
                .requestId(e.getRequestId())
                .createdAt(e.getCreatedAt())
                .build();
    }

    /**
     * Dựng danh sách thay đổi từ hai chuỗi JSON. An toàn với dữ liệu không phải JSON (bọc try/catch,
     * chỉ log ở mức debug — audit log là chỉ-đọc, không được để lỗi hiển thị làm hỏng tra cứu).
     */
    private List<AuditLogResponse.ChangeEntry> buildChanges(String oldValue, String newValue) {
        JsonNode oldNode = parse(oldValue);
        JsonNode newNode = parse(newValue);
        if (oldNode == null && newNode == null) {
            return List.of();
        }

        // Trường hợp cả hai là object: ghép theo khóa field.
        if ((oldNode == null || oldNode.isObject()) && (newNode == null || newNode.isObject())) {
            Set<String> fields = new LinkedHashSet<>();
            if (newNode != null) {
                newNode.fieldNames().forEachRemaining(fields::add);
            }
            if (oldNode != null) {
                oldNode.fieldNames().forEachRemaining(fields::add);
            }
            if (!fields.isEmpty()) {
                List<AuditLogResponse.ChangeEntry> changes = new ArrayList<>();
                for (String f : fields) {
                    changes.add(AuditLogResponse.ChangeEntry.builder()
                            .field(f)
                            .oldValue(asText(oldNode, f))
                            .newValue(asText(newNode, f))
                            .build());
                }
                return changes;
            }
        }

        // Trường hợp giá trị vô hướng (ví dụ đổi 1 config): một dòng field = "value".
        return List.of(AuditLogResponse.ChangeEntry.builder()
                .field("value")
                .oldValue(oldNode != null ? oldNode.asText() : null)
                .newValue(newNode != null ? newNode.asText() : null)
                .build());
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            log.debug("Không parse được old/new_value JSON của audit log: {}", ex.getMessage());
            return null;
        }
    }

    private String asText(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        JsonNode v = node.get(field);
        return v.isValueNode() ? v.asText() : v.toString();
    }
}
