package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.RetrievalResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harness đánh giá tầng Retrieval — DoD-3 (SPEC §1.3 / §14.3): Recall@5 ≥ 0.80.
 *
 * <p>Chạy TRỰC TIẾP trên {@link RetrievalService} (không qua NLU) với bộ tiêu chí
 * đã dựng sẵn — đo đúng chất lượng lọc/geo/xếp hạng, không lẫn lỗi trích entity
 * của tầng NLU. Bộ vàng (ground truth) ở {@code src/test/resources/retrieval_eval.jsonl}:
 * mỗi câu kèm tập phòng đúng {@code relevant_ids} xác định ĐỘC LẬP từ seed data
 * (không sinh ra bằng chính câu SQL đang kiểm) — nên việc hệ thống trả đúng tập
 * này là bằng chứng vi phân (differential) rằng filter cứng hoạt động đúng, không
 * phải phép lặp vòng.
 *
 * <p><b>Phương pháp:</b> mọi câu được thiết kế có |G| ≤ 5 để công thức SPEC
 * {@code Recall@5 = |Top5 ∩ G| / |G|} đo được sạch (|G|>5 sẽ bị chặn cơ học bởi
 * K=5). Với hệ thống lọc cứng, Recall@5 tụt khi và chỉ khi: filter quá chặt bỏ
 * sót phòng đúng, hoặc geo tính sai bán kính/khoảng cách — đúng thứ DoD-3 cần canh.
 *
 * <p><b>Cần MySQL thật</b> (query dùng {@code ST_Distance_Sphere}) + seed data.sql.
 * Vì thế test được cổng bằng biến môi trường để {@code mvn test} thường không đòi
 * hạ tầng. Chạy:
 * <pre>
 *   docker compose up -d mysql       # hoặc MySQL cục bộ đã có DB roomfinder
 *   RETRIEVAL_EVAL=true mvn test -Dtest=RetrievalRecallEvalTest
 *   # PowerShell: $env:RETRIEVAL_EVAL="true"; mvn test -Dtest=RetrievalRecallEvalTest
 * </pre>
 * Báo cáo ghi ra {@code ml/eval-results/retrieval_recall.md}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfEnvironmentVariable(named = "RETRIEVAL_EVAL", matches = "true")
class RetrievalRecallEvalTest {

    private static final int K = 5;
    private static final double DOD3_THRESHOLD = 0.80;

    @Autowired private RetrievalService retrievalService;
    @Autowired private ObjectMapper mapper;

    private record EvalCase(String id, String desc, Filters filters, Set<Long> relevant) {}

    private record Scored(EvalCase c, List<Long> got, double recall, double rr) {}

    @Test
    void recallAt5MeetsDoD3() throws Exception {
        List<EvalCase> cases = loadCases();
        assertTrue(cases.size() >= 20, "Bộ đánh giá cần ≥20 câu (SPEC §14.3 gợi ý ~50), hiện " + cases.size());

        List<Scored> results = new ArrayList<>();
        double sumRecall = 0, sumRr = 0;
        for (EvalCase c : cases) {
            RetrievalResult rr = retrievalService.search(c.filters(), K);
            List<Long> got = rr.getRooms().stream().map(Room::getId).limit(K).toList();

            long hit = got.stream().filter(c.relevant()::contains).count();
            double recall = (double) hit / c.relevant().size();

            double reciprocal = 0.0;
            for (int i = 0; i < got.size(); i++) {
                if (c.relevant().contains(got.get(i))) { reciprocal = 1.0 / (i + 1); break; }
            }
            sumRecall += recall;
            sumRr += reciprocal;
            results.add(new Scored(c, got, recall, reciprocal));
        }

        double avgRecall = sumRecall / cases.size();
        double avgMrr = sumRr / cases.size();

        String report = buildReport(results, avgRecall, avgMrr);
        System.out.println(report);
        writeReport(report);

        assertTrue(avgRecall >= DOD3_THRESHOLD, String.format(
                "DoD-3 KHÔNG đạt: Recall@5 trung bình = %.3f < %.2f. Xem câu fail ở bảng trên / báo cáo.",
                avgRecall, DOD3_THRESHOLD));
    }

    // --- Nạp bộ vàng -----------------------------------------------------

    private List<EvalCase> loadCases() throws Exception {
        List<EvalCase> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                new ClassPathResource("retrieval_eval.jsonl").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode n = mapper.readTree(line);
                Filters f = mapper.treeToValue(n.get("filters"), Filters.class);
                Set<Long> rel = new LinkedHashSet<>();
                n.get("relevant_ids").forEach(x -> rel.add(x.asLong()));
                out.add(new EvalCase(n.get("id").asText(), n.get("desc").asText(), f, rel));
            }
        }
        return out;
    }

    // --- Báo cáo ---------------------------------------------------------

    private String buildReport(List<Scored> results, double avgRecall, double avgMrr) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Đánh giá tầng Retrieval — Recall@5 (DoD-3, SPEC §14.3)\n\n");
        sb.append(String.format("- Số câu truy vấn: **%d** (bộ vàng `src/test/resources/retrieval_eval.jsonl`)%n", results.size()));
        sb.append(String.format("- **Recall@5 trung bình (macro): %.3f** — ngưỡng DoD-3 ≥ %.2f → %s%n",
                avgRecall, DOD3_THRESHOLD, avgRecall >= DOD3_THRESHOLD ? "**ĐẠT** ✅" : "**KHÔNG ĐẠT** ❌"));
        sb.append(String.format("- MRR trung bình: %.3f%n%n", avgMrr));
        sb.append("| Câu | Mô tả | \\|G\\| | Recall@5 | RR | Top-K trả về | Bộ vàng |\n");
        sb.append("|---|---|---|---|---|---|---|\n");
        for (Scored s : results) {
            sb.append(String.format("| %s | %s | %d | %.2f | %.2f | %s | %s |%n",
                    s.c().id(), s.c().desc(), s.c().relevant().size(),
                    s.recall(), s.rr(), s.got(), s.c().relevant()));
        }
        sb.append("\n> **Cách đọc:** đo trực tiếp trên `RetrievalService` (bỏ qua NLU). `relevant_ids` gán ")
          .append("độc lập từ seed `data.sql`. Mọi câu có |G| ≤ 5 để công thức `|Top5∩G|/|G|` không bị K=5 ")
          .append("chặn cơ học. Với tầng lọc cứng, MRR=1.0 là kỳ vọng (kết quả trả về đều thỏa filter nên ")
          .append("phần tử đầu luôn liên quan); tín hiệu chính là **Recall@5** — tụt khi filter bỏ sót phòng ")
          .append("đúng hoặc geo tính sai khoảng cách.\n");
        return sb.toString();
    }

    private void writeReport(String report) {
        try {
            Path dir = Path.of("ml", "eval-results");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("retrieval_recall.md"), report, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("Không ghi được báo cáo retrieval_recall.md: " + e.getMessage());
        }
    }
}
