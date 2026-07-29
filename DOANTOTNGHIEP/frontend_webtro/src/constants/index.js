/**
 * Hằng dùng chung phía client. Enum PHẢI trùng khớp backend (canonical mục 5).
 */

export const ROLES = {
  TENANT: 'ROLE_TENANT',
  LANDLORD: 'ROLE_LANDLORD',
  MODERATOR: 'ROLE_MODERATOR',
  ADMIN: 'ROLE_ADMIN',
};

export const PERMISSIONS = {
  LISTING_CREATE: 'LISTING_CREATE',
  LISTING_MODERATE: 'LISTING_MODERATE',
  LISTING_LOCK: 'LISTING_LOCK',
  USER_MANAGE: 'USER_MANAGE',
  USER_ROLE_ASSIGN: 'USER_ROLE_ASSIGN',
  PAYMENT_MANAGE: 'PAYMENT_MANAGE',
  PACKAGE_MANAGE: 'PACKAGE_MANAGE',
  CATALOG_MANAGE: 'CATALOG_MANAGE',
  AI_CONFIG_MANAGE: 'AI_CONFIG_MANAGE',
  AI_LOG_VIEW: 'AI_LOG_VIEW',
  SYSTEM_CONFIG_MANAGE: 'SYSTEM_CONFIG_MANAGE',
  STATISTIC_VIEW: 'STATISTIC_VIEW',
  AUDIT_LOG_VIEW: 'AUDIT_LOG_VIEW',
  REPORT_RESOLVE: 'REPORT_RESOLVE',
  COMMENT_MODERATE: 'COMMENT_MODERATE',
  REVIEW_MODERATE: 'REVIEW_MODERATE',
  LANDLORD_VERIFY: 'LANDLORD_VERIFY',
  WARNING_SEND: 'WARNING_SEND',
};

export const LISTING_STATUS = {
  DRAFT: 'DRAFT',
  PENDING: 'PENDING',
  ACTIVE: 'ACTIVE',
  REJECTED: 'REJECTED',
  HIDDEN: 'HIDDEN',
  EXPIRED: 'EXPIRED',
  CLOSED: 'CLOSED',
  LOCKED: 'LOCKED',
  NEED_REVIEW: 'NEED_REVIEW',
  DELETED: 'DELETED',
};

/** Nhãn tiếng Việt + màu (theo palette) cho từng trạng thái tin — dùng ở StatusChip. */
export const LISTING_STATUS_META = {
  DRAFT: { label: 'Nháp', color: 'default' },
  PENDING: { label: 'Chờ duyệt', color: 'warning' },
  ACTIVE: { label: 'Đang hiển thị', color: 'success' },
  REJECTED: { label: 'Bị từ chối', color: 'error' },
  HIDDEN: { label: 'Đã ẩn', color: 'default' },
  EXPIRED: { label: 'Hết hạn', color: 'default' },
  CLOSED: { label: 'Đã đóng', color: 'info' },
  LOCKED: { label: 'Bị khóa', color: 'error' },
  NEED_REVIEW: { label: 'Cần kiểm tra', color: 'warning' },
  DELETED: { label: 'Đã xóa', color: 'default' },
};

export const CATEGORY_CODES = {
  BOARDING_HOUSE: 'Phòng trọ',
  MINI_APARTMENT: 'Chung cư mini',
  APARTMENT: 'Căn hộ',
  WHOLE_HOUSE: 'Nhà nguyên căn',
  HOMESTAY: 'Homestay cho thuê',
  ROOMMATE: 'Ở ghép',
  SMALL_PREMISES: 'Mặt bằng nhỏ',
};

export const GENDER_REQUIREMENT = {
  MALE_ONLY: 'Chỉ nam',
  FEMALE_ONLY: 'Chỉ nữ',
  ANY: 'Nam hoặc nữ',
};

export const FURNITURE_STATUS = {
  NONE: 'Không nội thất',
  BASIC: 'Nội thất cơ bản',
  FULL: 'Nội thất đầy đủ',
};

export const REPORT_REASONS = {
  WRONG_INFO: 'Sai thông tin',
  ALREADY_RENTED: 'Đã cho thuê',
  SCAM: 'Lừa đảo',
  FAKE_IMAGE: 'Ảnh không thật',
  WRONG_PRICE: 'Giá sai',
  OFFENSIVE: 'Nội dung phản cảm',
  SPAM: 'Spam',
  OTHER: 'Khác',
};

export const SENTIMENT_META = {
  POSITIVE: { label: 'Tích cực', color: 'success' },
  NEUTRAL: { label: 'Trung lập', color: 'default' },
  NEGATIVE: { label: 'Tiêu cực', color: 'error' },
  MIXED: { label: 'Khen chê lẫn lộn', color: 'warning' },
  PENDING_ANALYSIS: { label: 'Đang phân tích', color: 'info' },
};

export const PAYMENT_STATUS_META = {
  PENDING: { label: 'Chờ thanh toán', color: 'warning' },
  SUCCESS: { label: 'Thành công', color: 'success' },
  FAILED: { label: 'Thất bại', color: 'error' },
  CANCELLED: { label: 'Đã hủy', color: 'default' },
  REFUNDED: { label: 'Đã hoàn tiền', color: 'info' },
};

export const SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Mới nhất' },
  { value: 'price,asc', label: 'Giá thấp đến cao' },
  { value: 'price,desc', label: 'Giá cao đến thấp' },
  { value: 'area,desc', label: 'Diện tích lớn nhất' },
  { value: 'viewCount,desc', label: 'Xem nhiều nhất' },
];

export const PAGE_SIZE = 12;
