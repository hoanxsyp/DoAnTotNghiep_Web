package com.webtro.constant;

/**
 * Hằng cho toàn bộ key của bảng {@code system_configs} — canonical mục 9 (105 key).
 *
 * <p>KHÔNG HARDCODE ngưỡng nghiệp vụ trong code {@code [§13.4]}. Mọi ngưỡng đọc qua
 * {@code SystemConfigService.getInt(ConfigKey.X)} / {@code getBoolean} / {@code getDecimal}.
 * Giá trị mặc định nằm trong {@code V5__seed_system_configs.sql}, sửa được qua giao diện Admin
 * lúc chạy {@code [§10.10]}.
 */
public final class ConfigKey {

    // ============================ Listing ============================
    public static final String LISTING_DISPLAY_DAYS = "listing.display_days";
    public static final String LISTING_IMAGE_MIN = "listing.image.min";
    public static final String LISTING_IMAGE_MAX = "listing.image.max";
    public static final String LISTING_IMAGE_MAX_SIZE_MB = "listing.image.max_size_mb";
    public static final String LISTING_TITLE_MIN = "listing.title.min";
    public static final String LISTING_TITLE_MAX = "listing.title.max";
    public static final String LISTING_DESCRIPTION_MIN = "listing.description.min";
    public static final String LISTING_DESCRIPTION_MAX = "listing.description.max";
    public static final String LISTING_EXPIRY_REMINDER_DAYS = "listing.expiry.reminder_days";
    public static final String LISTING_RENEW_FREE_PER_MONTH = "listing.renew.free_per_month";
    public static final String LISTING_NEED_REVIEW_PUBLICLY_VISIBLE = "listing.need_review.publicly_visible";
    public static final String LISTING_AUTO_APPROVE_TRUSTED_LANDLORD = "listing.auto_approve.trusted_landlord";

    // ========================== Moderation ==========================
    public static final String MOD_AUTOHIDE_REPORT_COUNT = "moderation.autohide.report_count";
    public static final String MOD_AUTOHIDE_DISTINCT_REPORTERS = "moderation.autohide.distinct_reporters";
    public static final String MOD_AUTOHIDE_WINDOW_HOURS = "moderation.autohide.window_hours";
    public static final String MOD_AUTOHIDE_SENTIMENT_REQUIRES_PRIOR_WARNING = "moderation.autohide.sentiment_requires_prior_warning";
    public static final String MOD_THRESHOLD_WARNING_COUNT = "moderation.threshold.warning_count";
    public static final String MOD_THRESHOLD_WARNING_WINDOW_DAYS = "moderation.threshold.warning_window_days";
    public static final String MOD_THRESHOLD_LOCKED_LISTING_COUNT = "moderation.threshold.locked_listing_count";
    public static final String MOD_THRESHOLD_LOCKED_LISTING_WINDOW_DAYS = "moderation.threshold.locked_listing_window_days";
    public static final String MOD_THRESHOLD_SPAM_COMMENT_COUNT = "moderation.threshold.spam_comment_count";
    public static final String MOD_THRESHOLD_SPAM_COMMENT_WINDOW_HOURS = "moderation.threshold.spam_comment_window_hours";

    // ============================= Trust ============================
    public static final String TRUST_BASE_SCORE = "trust.base_score";
    public static final String TRUST_WEIGHT_POSITIVE_COMMENT = "trust.weight.positive_comment";
    public static final String TRUST_WEIGHT_NEGATIVE_COMMENT = "trust.weight.negative_comment";
    public static final String TRUST_WEIGHT_AVERAGE_RATING = "trust.weight.average_rating";
    public static final String TRUST_WEIGHT_VALID_REPORT = "trust.weight.valid_report";
    public static final String TRUST_WEIGHT_VIOLATION_WARNING = "trust.weight.violation_warning";
    public static final String TRUST_WEIGHT_LANDLORD_RESPONSE_RATE = "trust.weight.landlord_response_rate";
    public static final String TRUST_MIN = "trust.min";
    public static final String TRUST_MAX = "trust.max";
    public static final String TRUST_THRESHOLD_RISKY = "trust.threshold.risky";
    public static final String TRUST_THRESHOLD_NEED_REVIEW = "trust.threshold.need_review";
    public static final String TRUST_RESPONSE_RATE_WINDOW_DAYS = "trust.response_rate.window_days";
    public static final String TRUST_RESPONSE_RATE_SLA_HOURS = "trust.response_rate.sla_hours";
    public static final String TRUST_RESPONSE_RATE_MIN_CONVERSATIONS = "trust.response_rate.min_conversations";
    public static final String TRUST_RESPONSE_RATE_NEUTRAL_PERCENT = "trust.response_rate.neutral_percent";

    // ======================= AI - Sentiment =========================
    public static final String AI_SENTIMENT_ENABLED = "ai.sentiment.enabled";
    public static final String AI_SENTIMENT_MIN_COMMENTS_L1 = "ai.sentiment.min_comments_l1";
    public static final String AI_SENTIMENT_NEGATIVE_RATIO_L1 = "ai.sentiment.negative_ratio_l1";
    public static final String AI_SENTIMENT_MIN_COMMENTS_L2 = "ai.sentiment.min_comments_l2";
    public static final String AI_SENTIMENT_NEGATIVE_RATIO_L2 = "ai.sentiment.negative_ratio_l2";
    public static final String AI_SENTIMENT_NEED_REVIEW_COUNT_FOR_LOCK = "ai.sentiment.need_review_count_for_lock";
    public static final String AI_SENTIMENT_NEED_REVIEW_WINDOW_DAYS = "ai.sentiment.need_review_window_days";
    public static final String AI_SENTIMENT_LANDLORD_ALERT_LISTING_COUNT = "ai.sentiment.landlord_alert_listing_count";
    public static final String AI_SENTIMENT_MIN_LENGTH = "ai.sentiment.min_length";
    public static final String AI_SENTIMENT_NEW_ACCOUNT_DAYS = "ai.sentiment.new_account_days";
    public static final String AI_SENTIMENT_NEW_ACCOUNT_WEIGHT = "ai.sentiment.new_account_weight";
    public static final String AI_SENTIMENT_TIMEOUT_MS = "ai.sentiment.timeout_ms";
    public static final String AI_SENTIMENT_MAX_RETRY = "ai.sentiment.max_retry";
    public static final String AI_SENTIMENT_LOW_CONFIDENCE_THRESHOLD = "ai.sentiment.low_confidence_threshold";

    // ==================== AI - Recommendation =======================
    public static final String AI_RECOMMENDATION_ENABLED = "ai.recommendation.enabled";
    public static final String AI_RECOMMENDATION_SIZE = "ai.recommendation.size";
    public static final String AI_RECOMMENDATION_CACHE_TTL_MINUTES = "ai.recommendation.cache_ttl_minutes";
    public static final String AI_RECOMMENDATION_PROMOTED_BOOST = "ai.recommendation.promoted_boost";
    public static final String AI_RECOMMENDATION_TIMEOUT_MS = "ai.recommendation.timeout_ms";
    public static final String AI_RECOMMENDATION_NOTIFY_ENABLED = "ai.recommendation.notify_enabled";
    public static final String AI_RECOMMENDATION_NOTIFY_MIN_SCORE = "ai.recommendation.notify_min_score";
    public static final String AI_RECOMMENDATION_NOTIFY_MAX_PER_USER = "ai.recommendation.notify_max_per_user";
    public static final String AI_RECOMMENDATION_NOTIFY_LOOKBACK_HOURS = "ai.recommendation.notify_lookback_hours";
    public static final String AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS = "ai.recommendation.notify_active_user_days";

    // ======================== AI - Price ============================
    public static final String AI_PRICE_ENABLED = "ai.price.enabled";
    public static final String AI_PRICE_MIN_SAMPLES = "ai.price.min_samples";
    public static final String AI_PRICE_DEVIATION_FLAG_RATIO = "ai.price.deviation_flag_ratio";
    public static final String AI_PRICE_TIMEOUT_MS = "ai.price.timeout_ms";
    public static final String AI_PRICE_COMPARABLE_DAYS = "ai.price.comparable_days";
    public static final String AI_PRICE_COMPARABLE_AREA_TOLERANCE = "ai.price.comparable_area_tolerance";
    public static final String AI_PRICE_HEDONIC_FURNITURE_FULL = "ai.price.hedonic.furniture_full";
    public static final String AI_PRICE_HEDONIC_TOILET_PRIVATE = "ai.price.hedonic.toilet_private";
    public static final String AI_PRICE_HEDONIC_ELEVATOR = "ai.price.hedonic.elevator";
    public static final String AI_PRICE_HEDONIC_PARKING = "ai.price.hedonic.parking";
    public static final String AI_PRICE_HEDONIC_CURFEW_FREE = "ai.price.hedonic.curfew_free";
    public static final String AI_PRICE_HEDONIC_STREET_FRONT = "ai.price.hedonic.street_front";

    // ======================= AI - Chatbot ===========================
    public static final String AI_CHATBOT_ENABLED = "ai.chatbot.enabled";
    public static final String AI_CHATBOT_MAX_CLARIFY_TURNS = "ai.chatbot.max_clarify_turns";
    public static final String AI_CHATBOT_TIMEOUT_MS = "ai.chatbot.timeout_ms";

    // ====================== Interaction misc ========================
    public static final String CONTACT_DEDUP_MINUTES = "contact.dedup_minutes";
    public static final String VIEW_DEDUP_MINUTES = "view.dedup_minutes";
    public static final String COMMENT_EDIT_WINDOW_MINUTES = "comment.edit_window_minutes";
    public static final String REVIEW_EDIT_WINDOW_HOURS = "review.edit_window_hours";
    public static final String REVIEW_REQUIRE_CONTACT = "review.require_contact";
    public static final String CHAT_MESSAGE_MAX_LENGTH = "chat.message.max_length";
    public static final String CHATBOT_MESSAGE_MAX_LENGTH = "chatbot.message.max_length";

    // ========================== Promotion ===========================
    public static final String PROMOTION_MAX_PRIORITY = "promotion.max_priority";

    // ============================ Report ============================
    public static final String REPORT_ABUSE_REJECTED_COUNT = "report.abuse.rejected_count";
    public static final String REPORT_ABUSE_WINDOW_DAYS = "report.abuse.window_days";

    // =========================== Payment ============================
    public static final String PAYMENT_ORDER_EXPIRY_MINUTES = "payment.order.expiry_minutes";
    public static final String PAYMENT_CALLBACK_MAX_SKEW_SECONDS = "payment.callback.max_skew_seconds";

    // =========================== Security ===========================
    public static final String SECURITY_LOGIN_MAX_ATTEMPTS = "security.login.max_attempts";
    public static final String SECURITY_LOGIN_WINDOW_MINUTES = "security.login.window_minutes";
    public static final String SECURITY_LOGIN_LOCK_MINUTES = "security.login.lock_minutes";
    public static final String SECURITY_LOGIN_CAPTCHA_AFTER_ATTEMPTS = "security.login.captcha_after_attempts";
    public static final String SECURITY_REGISTER_RATE = "security.register.rate";
    public static final String SECURITY_REFRESH_GRACE_SECONDS = "security.refresh.grace_seconds";

    // ============================= Spam =============================
    public static final String SPAM_LISTING_NEW_ACCOUNT_DAILY = "spam.listing.new_account_daily";
    public static final String SPAM_LISTING_DAILY = "spam.listing.daily";
    public static final String SPAM_COMMENT_PER_MINUTE = "spam.comment.per_minute";
    public static final String SPAM_REPORT_DAILY = "spam.report.daily";
    public static final String SPAM_MESSAGE_PER_MINUTE = "spam.message.per_minute";
    public static final String SPAM_CHATBOT_PER_MINUTE = "spam.chatbot.per_minute";

    // ============================ Search ============================
    public static final String SEARCH_KEYWORD_MAX_LENGTH = "search.keyword.max_length";
    public static final String SEARCH_AMENITY_FILTER_MAX_COUNT = "search.amenity_filter.max_count";

    // ============================ Upload ============================
    public static final String UPLOAD_MAX_PIXELS = "upload.max_pixels";

    // ======================== Static content ========================
    public static final String PAGE_ABOUT = "page.about";
    public static final String PAGE_TERMS = "page.terms";

    private ConfigKey() {
    }
}
