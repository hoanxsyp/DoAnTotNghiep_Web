package com.webtro.modules.ai.engine;

import com.webtro.common.enums.SentimentAction;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.util.TextNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Bộ phân tích cảm xúc tiếng Việt dựa trên từ điển có trọng số (canonical mục 10.1, §9.1;
 * rule-based theo mục 13.2 — không cần ML thật).
 *
 * <p>Luồng: chuẩn hóa → tách token → khớp từ điển đơn từ + cụm n-gram (dạng có dấu và không dấu để
 * bắt teencode) → xử lý <b>phủ định</b> ("không", "chẳng", "chưa"... đảo dấu trong cửa sổ 3 từ) →
 * <b>từ tăng cường</b> ("rất", "cực kỳ", "quá" ×1.5) → cộng điểm <b>emoji</b> → chuẩn hóa điểm về
 * [-1, 1] → suy nhãn/độ tin cậy/hành động đề xuất. Xử lý đủ ngoại lệ §9.1 (quá ngắn → NEUTRAL không
 * tính điểm; MIXED khi vừa khen vừa chê; confidence thấp không kích hoạt hành động nặng; tài khoản
 * mới trọng số thấp).
 */
@Component
public class VietnameseLexiconSentimentAnalyzer implements SentimentAnalyzer {

    private static final String ANALYZER_VERSION = "vi-lexicon-1.0";

    /** Cửa sổ nhìn lui để phát hiện phủ định (số token). */
    private static final int NEGATION_WINDOW = 3;
    /** Cửa sổ nhìn lui để phát hiện từ tăng cường. */
    private static final int INTENSIFIER_WINDOW = 2;
    private static final BigDecimal INTENSIFIER_MULTIPLIER = new BigDecimal("1.5");
    private static final BigDecimal LABEL_BAND = new BigDecimal("0.15");
    private static final BigDecimal STRONG_TERM = new BigDecimal("0.50");

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    // ---- Từ điển đơn từ (dạng KHÔNG DẤU để khớp cả teencode) ----
    private static final Map<String, BigDecimal> POSITIVE_WORDS = new LinkedHashMap<>();
    private static final Map<String, BigDecimal> NEGATIVE_WORDS = new LinkedHashMap<>();
    // ---- Cụm n-gram (không dấu) ----
    private static final Map<String, BigDecimal> POSITIVE_NGRAMS = new LinkedHashMap<>();
    private static final Map<String, BigDecimal> NEGATIVE_NGRAMS = new LinkedHashMap<>();
    // ---- Cụm cảnh báo/rủi ro (không dấu) → đánh dấu isRiskComment ----
    private static final Map<String, BigDecimal> WARNING_NGRAMS = new LinkedHashMap<>();
    // ---- Bộ đảo dấu & tăng cường (không dấu) ----
    private static final java.util.Set<String> NEGATIONS = new java.util.HashSet<>();
    private static final java.util.Set<String> INTENSIFIERS = new java.util.HashSet<>();
    // ---- Emoji ----
    private static final Map<String, BigDecimal> EMOJIS = new LinkedHashMap<>();

    static {
        // Tích cực (đơn từ)
        POSITIVE_WORDS.put("tot", d(0.6));
        POSITIVE_WORDS.put("dep", d(0.6));
        POSITIVE_WORDS.put("sach", d(0.5));
        POSITIVE_WORDS.put("thoang", d(0.5));
        POSITIVE_WORDS.put("rong", d(0.4));
        POSITIVE_WORDS.put("re", d(0.5));
        POSITIVE_WORDS.put("tien", d(0.4));
        POSITIVE_WORDS.put("yen", d(0.4));
        POSITIVE_WORDS.put("antoan", d(0.6));
        POSITIVE_WORDS.put("thich", d(0.5));
        POSITIVE_WORDS.put("hailong", d(0.7));
        POSITIVE_WORDS.put("uytin", d(0.7));
        POSITIVE_WORDS.put("nhiettinh", d(0.6));
        POSITIVE_WORDS.put("tuyet", d(0.8));
        POSITIVE_WORDS.put("ok", d(0.3));
        POSITIVE_WORDS.put("oke", d(0.3));
        POSITIVE_WORDS.put("ngon", d(0.4));
        POSITIVE_WORDS.put("chuandep", d(0.6));
        POSITIVE_WORDS.put("thoangmat", d(0.6));
        POSITIVE_WORDS.put("gangui", d(0.5));

        // Tiêu cực (đơn từ)
        NEGATIVE_WORDS.put("te", d(-0.7));
        NEGATIVE_WORDS.put("do", d(-0.6));
        NEGATIVE_WORDS.put("ban", d(-0.6));
        NEGATIVE_WORDS.put("hoi", d(-0.5));
        NEGATIVE_WORDS.put("chat", d(-0.4));
        NEGATIVE_WORDS.put("nong", d(-0.4));
        NEGATIVE_WORDS.put("on", d(-0.4));
        NEGATIVE_WORDS.put("onao", d(-0.5));
        NEGATIVE_WORDS.put("dat", d(-0.5));
        NEGATIVE_WORDS.put("kem", d(-0.7));
        NEGATIVE_WORDS.put("xau", d(-0.6));
        NEGATIVE_WORDS.put("khodo", d(-0.5));
        NEGATIVE_WORDS.put("bucboi", d(-0.5));
        NEGATIVE_WORDS.put("thatvong", d(-0.8));
        NEGATIVE_WORDS.put("chan", d(-0.5));
        NEGATIVE_WORDS.put("ghe", d(-0.6));
        NEGATIVE_WORDS.put("khoso", d(-0.6));
        NEGATIVE_WORDS.put("dotnat", d(-0.7));
        NEGATIVE_WORDS.put("amthap", d(-0.5));
        NEGATIVE_WORDS.put("mocmeo", d(-0.6));

        // Cụm tích cực (n-gram, không dấu)
        POSITIVE_NGRAMS.put("phong dep", d(0.6));
        POSITIVE_NGRAMS.put("chu de tinh", d(0.7));
        POSITIVE_NGRAMS.put("chu than thien", d(0.7));
        POSITIVE_NGRAMS.put("gia hop ly", d(0.6));
        POSITIVE_NGRAMS.put("gia re", d(0.6));
        POSITIVE_NGRAMS.put("an ninh tot", d(0.7));
        POSITIVE_NGRAMS.put("sach se", d(0.6));
        POSITIVE_NGRAMS.put("thoang mat", d(0.6));
        POSITIVE_NGRAMS.put("dang tien", d(0.6));
        POSITIVE_NGRAMS.put("gio giac tu do", d(0.5));
        POSITIVE_NGRAMS.put("rat hai long", d(0.9));
        POSITIVE_NGRAMS.put("rat dep", d(0.9));

        // Cụm tiêu cực (n-gram, không dấu)
        NEGATIVE_NGRAMS.put("khong dang tien", d(-0.7));
        NEGATIVE_NGRAMS.put("gia dien cao", d(-0.6));
        NEGATIVE_NGRAMS.put("gia dien qua cao", d(-0.75));
        NEGATIVE_NGRAMS.put("cach am kem", d(-0.7));
        NEGATIVE_NGRAMS.put("an ninh kem", d(-0.8));
        NEGATIVE_NGRAMS.put("chu kho tinh", d(-0.7));
        NEGATIVE_NGRAMS.put("khong nhu quang cao", d(-0.8));
        NEGATIVE_NGRAMS.put("khong dung hinh", d(-0.7));
        NEGATIVE_NGRAMS.put("mat ve sinh", d(-0.6));
        NEGATIVE_NGRAMS.put("nhieu muoi", d(-0.5));
        NEGATIVE_NGRAMS.put("hay cup dien", d(-0.6));
        NEGATIVE_NGRAMS.put("khong co phong", d(-0.6));

        // Cụm cảnh báo/rủi ro → isRiskComment = true (lừa đảo/tố cáo)
        WARNING_NGRAMS.put("lua dao", d(-0.9));
        WARNING_NGRAMS.put("can than", d(-0.5));
        WARNING_NGRAMS.put("coc truoc", d(-0.6));
        WARNING_NGRAMS.put("doi coc truoc", d(-0.7));
        WARNING_NGRAMS.put("mat coc", d(-0.7));
        WARNING_NGRAMS.put("khong tra coc", d(-0.8));
        WARNING_NGRAMS.put("bao ket", d(-0.7));
        WARNING_NGRAMS.put("tin gia", d(-0.9));
        WARNING_NGRAMS.put("lua tien", d(-0.9));

        NEGATIONS.add("khong");
        NEGATIONS.add("chang");
        NEGATIONS.add("chua");
        NEGATIONS.add("dech");
        NEGATIONS.add("dau");
        NEGATIONS.add("ko");
        NEGATIONS.add("k");

        INTENSIFIERS.add("rat");
        INTENSIFIERS.add("qua");
        INTENSIFIERS.add("cuc");
        INTENSIFIERS.add("cucky");
        INTENSIFIERS.add("sieu");
        INTENSIFIERS.add("het suc");
        INTENSIFIERS.add("vo cung");

        EMOJIS.put("😊", d(0.6)); // 😊
        EMOJIS.put("😀", d(0.6)); // 😀
        EMOJIS.put("😍", d(0.7)); // 😍
        EMOJIS.put("👍", d(0.6)); // 👍
        EMOJIS.put("❤", d(0.6));       // ❤
        EMOJIS.put("😞", d(-0.6)); // 😞
        EMOJIS.put("😡", d(-0.7)); // 😡
        EMOJIS.put("👎", d(-0.6)); // 👎
        EMOJIS.put("😢", d(-0.6)); // 😢
        EMOJIS.put("💩", d(-0.7)); // 💩
    }

    private static BigDecimal d(double v) {
        return BigDecimal.valueOf(v);
    }

    @Override
    public String version() {
        return ANALYZER_VERSION;
    }

    @Override
    public SentimentOutcome analyze(String text, int minLength, BigDecimal lowConfidence,
                                    boolean newAccount, BigDecimal newAccountWeight) {
        BigDecimal accountWeight = newAccount ? nvl(newAccountWeight, BigDecimal.ONE) : BigDecimal.ONE;
        String raw = text == null ? "" : text.trim();

        // Ngoại lệ §9.1: quá ngắn → NEUTRAL, weight = 0, không tính điểm uy tín.
        if (raw.length() < Math.max(1, minLength)) {
            return new SentimentOutcome(
                    SentimentLabel.NEUTRAL, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO,
                    SentimentAction.NONE, false, false, "TOO_SHORT",
                    List.of(), List.of(), BigDecimal.ZERO,
                    "Độ dài " + raw.length() + " ký tự nhỏ hơn ngưỡng " + minLength
                            + " → gắn NEUTRAL, không tính vào điểm uy tín");
        }

        String lower = TextNormalizer.collapseWhitespace(raw.toLowerCase());
        String noAccent = TextNormalizer.removeAccents(lower);
        List<String> tokens = tokenize(noAccent);
        String joined = " " + String.join(" ", tokens) + " ";

        List<MatchedTerm> positive = new ArrayList<>();
        List<MatchedTerm> negative = new ArrayList<>();
        boolean[] negationApplied = {false};
        boolean risk = false;

        BigDecimal rawScore = BigDecimal.ZERO;

        // 1) Cụm n-gram (ưu tiên khớp trước để không bị đơn từ chồng chéo phá nghĩa).
        rawScore = rawScore.add(matchNgrams(POSITIVE_NGRAMS, joined, tokens, positive, negative, negationApplied));
        rawScore = rawScore.add(matchNgrams(NEGATIVE_NGRAMS, joined, tokens, positive, negative, negationApplied));

        // 2) Cụm cảnh báo/rủi ro.
        BigDecimal warnScore = matchNgrams(WARNING_NGRAMS, joined, tokens, positive, negative, negationApplied);
        if (warnScore.signum() != 0 || containsAny(joined, WARNING_NGRAMS.keySet())) {
            risk = true;
        }
        rawScore = rawScore.add(warnScore);

        // 3) Đơn từ + phủ định + tăng cường.
        rawScore = rawScore.add(matchWords(tokens, positive, negative, negationApplied));

        // 4) Emoji.
        BigDecimal emojiScore = matchEmoji(raw);
        if (emojiScore.signum() > 0) {
            positive.add(new MatchedTerm("emoji", "EMOJI", emojiScore, -1));
        } else if (emojiScore.signum() < 0) {
            negative.add(new MatchedTerm("emoji", "EMOJI", emojiScore, -1));
        }
        rawScore = rawScore.add(emojiScore);

        // 5) Chuẩn hóa điểm về [-1, 1] bằng hàm ép mượt raw / (1 + |raw|).
        BigDecimal score = squash(rawScore);

        BigDecimal posMag = sumAbs(positive);
        BigDecimal negMag = sumAbs(negative);
        BigDecimal total = posMag.add(negMag);

        boolean strongPos = hasStrong(positive);
        boolean strongNeg = hasStrong(negative);

        SentimentLabel label = decideLabel(score, total, strongPos, strongNeg);
        BigDecimal confidence = decideConfidence(label, posMag, negMag, total);

        SentimentAction action = decideAction(label, score, confidence, risk, lowConfidence);

        String reason = buildReason(label, positive, negative, risk);

        return new SentimentOutcome(label, scale(score), scale(confidence), scale2(accountWeight),
                action, risk, negationApplied[0], null,
                List.copyOf(positive), List.copyOf(negative), scale(rawScore), reason);
    }

    // ------------------------------------------------------------------
    //  Khớp từ điển
    // ------------------------------------------------------------------

    private BigDecimal matchNgrams(Map<String, BigDecimal> lexicon, String joined, List<String> tokens,
                                   List<MatchedTerm> positive, List<MatchedTerm> negative,
                                   boolean[] negationApplied) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : lexicon.entrySet()) {
            String phrase = e.getKey();
            String needle = " " + phrase + " ";
            int idx = joined.indexOf(needle);
            if (idx < 0) {
                continue;
            }
            int tokenPos = countTokensBefore(joined, idx);
            BigDecimal weight = e.getValue();
            // Phủ định trong cửa sổ trước cụm.
            if (isNegatedBefore(tokens, tokenPos)) {
                weight = weight.negate();
                negationApplied[0] = true;
            }
            sum = sum.add(weight);
            String type = weight.signum() >= 0 ? "POSITIVE_NGRAM" : "NEGATIVE_NGRAM";
            addTerm(positive, negative, new MatchedTerm(phrase, type, weight, tokenPos));
        }
        return sum;
    }

    private BigDecimal matchWords(List<String> tokens, List<MatchedTerm> positive,
                                  List<MatchedTerm> negative, boolean[] negationApplied) {
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < tokens.size(); i++) {
            String tk = tokens.get(i);
            BigDecimal base = POSITIVE_WORDS.get(tk);
            if (base == null) {
                base = NEGATIVE_WORDS.get(tk);
            }
            if (base == null) {
                continue;
            }
            BigDecimal weight = base;
            // Tăng cường: intensifier ngay trước (trong cửa sổ) → ×1.5.
            boolean intensified = false;
            for (int j = Math.max(0, i - INTENSIFIER_WINDOW); j < i; j++) {
                if (INTENSIFIERS.contains(tokens.get(j))) {
                    intensified = true;
                    break;
                }
            }
            if (intensified) {
                weight = weight.multiply(INTENSIFIER_MULTIPLIER);
            }
            // Phủ định: đảo dấu nếu có từ phủ định trong cửa sổ trước.
            if (isNegatedBefore(tokens, i)) {
                weight = weight.negate();
                negationApplied[0] = true;
            }
            sum = sum.add(weight);
            String type = (weight.signum() >= 0 ? "POSITIVE" : "NEGATIVE")
                    + (intensified ? "_INTENSIFIED" : "_WORD");
            addTerm(positive, negative, new MatchedTerm(tk, type, weight, i));
        }
        return sum;
    }

    private BigDecimal matchEmoji(String raw) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : EMOJIS.entrySet()) {
            int from = 0;
            int idx;
            while ((idx = raw.indexOf(e.getKey(), from)) >= 0) {
                sum = sum.add(e.getValue());
                from = idx + e.getKey().length();
            }
        }
        return sum;
    }

    // ------------------------------------------------------------------
    //  Suy nhãn / độ tin cậy / hành động
    // ------------------------------------------------------------------

    private SentimentLabel decideLabel(BigDecimal score, BigDecimal total,
                                       boolean strongPos, boolean strongNeg) {
        if (total.signum() == 0) {
            return SentimentLabel.NEUTRAL;
        }
        if (strongPos && strongNeg) {
            return SentimentLabel.MIXED; // vừa khen vừa chê §9.1
        }
        if (score.compareTo(LABEL_BAND) >= 0) {
            return SentimentLabel.POSITIVE;
        }
        if (score.compareTo(LABEL_BAND.negate()) <= 0) {
            return SentimentLabel.NEGATIVE;
        }
        return SentimentLabel.NEUTRAL;
    }

    private BigDecimal decideConfidence(SentimentLabel label, BigDecimal posMag,
                                        BigDecimal negMag, BigDecimal total) {
        if (total.signum() == 0) {
            return new BigDecimal("0.50"); // không tín hiệu → không chắc chắn
        }
        double t = total.doubleValue();
        double agreement = Math.abs(posMag.subtract(negMag).doubleValue()) / t; // 1 = một chiều rõ
        double evidence = Math.min(1.0, t / 2.0); // càng nhiều bằng chứng càng chắc
        double conf = (0.5 + 0.5 * agreement) * (0.5 + 0.5 * evidence);
        if (label == SentimentLabel.MIXED) {
            conf = Math.min(conf, 0.65); // MIXED vốn khó → chặn trần
        }
        return clamp01(BigDecimal.valueOf(conf));
    }

    private SentimentAction decideAction(SentimentLabel label, BigDecimal score,
                                         BigDecimal confidence, boolean risk, BigDecimal lowConfidence) {
        SentimentAction action = SentimentAction.NONE;
        boolean strongNegative = label == SentimentLabel.NEGATIVE
                && score.compareTo(new BigDecimal("-0.60")) <= 0;
        if (risk || strongNegative) {
            action = SentimentAction.NEED_REVIEW;
        } else if (label == SentimentLabel.NEGATIVE || label == SentimentLabel.MIXED) {
            action = SentimentAction.WATCH;
        }
        // Confidence thấp → không kích hoạt hành động nặng (§9.1): hạ NEED_REVIEW về WATCH.
        if (action == SentimentAction.NEED_REVIEW
                && confidence.compareTo(nvl(lowConfidence, new BigDecimal("0.5"))) < 0) {
            action = SentimentAction.WATCH;
        }
        return action;
    }

    private String buildReason(SentimentLabel label, List<MatchedTerm> positive,
                               List<MatchedTerm> negative, boolean risk) {
        if (risk) {
            return "Phát hiện dấu hiệu rủi ro/tố cáo (lừa đảo, cọc trước, cẩn thận...) → đánh dấu theo dõi";
        }
        return switch (label) {
            case POSITIVE -> "Có " + positive.size() + " cụm tích cực chiếm ưu thế";
            case NEGATIVE -> "Có " + negative.size() + " cụm tiêu cực chiếm ưu thế";
            case MIXED -> "Có cả cụm tích cực mạnh và tiêu cực mạnh → MIXED";
            case NEUTRAL -> "Không đủ tín hiệu cảm xúc rõ ràng → NEUTRAL";
            case PENDING_ANALYSIS -> "Chưa phân tích";
        };
    }

    // ------------------------------------------------------------------
    //  Tiện ích
    // ------------------------------------------------------------------

    private List<String> tokenize(String noAccent) {
        List<String> out = new ArrayList<>();
        for (String t : TOKEN_SPLIT.split(noAccent)) {
            if (!t.isBlank()) {
                out.add(t);
            }
        }
        return out;
    }

    private boolean isNegatedBefore(List<String> tokens, int pos) {
        for (int j = Math.max(0, pos - NEGATION_WINDOW); j < pos; j++) {
            if (NEGATIONS.contains(tokens.get(j))) {
                return true;
            }
        }
        return false;
    }

    private int countTokensBefore(String joined, int charIdx) {
        int count = 0;
        for (int i = 1; i < charIdx && i < joined.length(); i++) {
            if (joined.charAt(i) == ' ' && joined.charAt(i - 1) != ' ') {
                count++;
            }
        }
        return count;
    }

    private boolean containsAny(String joined, java.util.Set<String> phrases) {
        for (String p : phrases) {
            if (joined.contains(" " + p + " ")) {
                return true;
            }
        }
        return false;
    }

    private void addTerm(List<MatchedTerm> positive, List<MatchedTerm> negative, MatchedTerm term) {
        if (term.weight().signum() >= 0) {
            positive.add(term);
        } else {
            negative.add(term);
        }
    }

    private BigDecimal sumAbs(List<MatchedTerm> terms) {
        BigDecimal s = BigDecimal.ZERO;
        for (MatchedTerm t : terms) {
            s = s.add(t.weight().abs());
        }
        return s;
    }

    private boolean hasStrong(List<MatchedTerm> terms) {
        for (MatchedTerm t : terms) {
            if (t.weight().abs().compareTo(STRONG_TERM) >= 0) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal squash(BigDecimal raw) {
        double r = raw.doubleValue();
        double s = r / (1.0 + Math.abs(r));
        return BigDecimal.valueOf(s);
    }

    private BigDecimal clamp01(BigDecimal v) {
        if (v.signum() < 0) {
            return BigDecimal.ZERO;
        }
        if (v.compareTo(BigDecimal.ONE) > 0) {
            return BigDecimal.ONE;
        }
        return v;
    }

    private BigDecimal scale(BigDecimal v) {
        return v.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal scale2(BigDecimal v) {
        return v.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nvl(BigDecimal v, BigDecimal fallback) {
        return v == null ? fallback : v;
    }
}
