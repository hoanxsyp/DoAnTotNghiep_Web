/**
 * Nhãn tiếng Việt + màu cho các enum chỉ dùng trong khu vực quản trị (canonical mục 5). Tách riêng
 * khỏi @/constants (không sửa file đã có) để các trang admin dùng chung, tránh lặp map nhãn.
 */
import { ROLES } from '@/constants';

export const ROLE_META = {
  [ROLES.TENANT]: { label: 'Người thuê', color: 'default' },
  [ROLES.LANDLORD]: { label: 'Chủ trọ', color: 'info' },
  [ROLES.MODERATOR]: { label: 'Kiểm duyệt viên', color: 'warning' },
  [ROLES.ADMIN]: { label: 'Quản trị viên', color: 'error' },
};

export const USER_STATUS_META = {
  ACTIVE: { label: 'Đang hoạt động', color: 'success' },
  PENDING_VERIFY: { label: 'Chờ xác thực', color: 'warning' },
  LOCKED: { label: 'Đã bị khóa', color: 'error' },
  DELETED: { label: 'Đã xóa', color: 'default' },
};

export const REPORT_STATUS_META = {
  PENDING: { label: 'Chờ xử lý', color: 'warning' },
  REVIEWING: { label: 'Đang xem xét', color: 'info' },
  RESOLVED: { label: 'Đã xử lý', color: 'success' },
  REJECTED: { label: 'Đã bác bỏ', color: 'default' },
};

export const REPORT_SEVERITY_META = {
  LOW: { label: 'Thấp', color: 'default' },
  MEDIUM: { label: 'Trung bình', color: 'info' },
  HIGH: { label: 'Cao', color: 'warning' },
  CRITICAL: { label: 'Nghiêm trọng', color: 'error' },
};

export const REPORT_TARGET_META = {
  LISTING: 'Tin đăng',
  COMMENT: 'Bình luận',
  USER: 'Người dùng',
  REVIEW: 'Đánh giá',
};

export const MODERATION_RESULT_META = {
  NO_VIOLATION: { label: 'Không vi phạm', color: 'default' },
  MINOR_WARN: { label: 'Cảnh báo nhẹ', color: 'info' },
  MEDIUM_HIDE: { label: 'Ẩn tin', color: 'warning' },
  SEVERE_LOCK: { label: 'Khóa tin', color: 'error' },
};

export const MODERATION_RESULT_OPTIONS = [
  { value: 'NO_VIOLATION', label: 'Không vi phạm — bác bỏ báo cáo' },
  { value: 'MINOR_WARN', label: 'Vi phạm nhẹ — gửi cảnh báo' },
  { value: 'MEDIUM_HIDE', label: 'Vi phạm vừa — ẩn tin' },
  { value: 'SEVERE_LOCK', label: 'Vi phạm nặng — khóa tin' },
];

export const VERIFICATION_STATUS_META = {
  PENDING: { label: 'Chờ duyệt', color: 'warning' },
  VERIFIED: { label: 'Đã xác thực', color: 'success' },
  REJECTED: { label: 'Bị từ chối', color: 'error' },
  EXPIRED: { label: 'Hết hạn', color: 'default' },
  NONE: { label: 'Chưa gửi', color: 'default' },
};

export const COMMENT_STATUS_META = {
  VISIBLE: { label: 'Hiển thị', color: 'success' },
  PENDING: { label: 'Chờ duyệt', color: 'warning' },
  HIDDEN: { label: 'Đã ẩn', color: 'default' },
  DELETED: { label: 'Đã xóa', color: 'error' },
};

export const REVIEW_STATUS_META = {
  VISIBLE: { label: 'Hiển thị', color: 'success' },
  HIDDEN: { label: 'Đã ẩn', color: 'default' },
  DELETED: { label: 'Đã xóa', color: 'error' },
};

export const PAYMENT_METHOD_META = {
  SANDBOX: 'Thử nghiệm',
  VNPAY: 'VNPay',
  MOMO: 'MoMo',
  BANK_TRANSFER: 'Chuyển khoản',
};

export const AMENITY_GROUP_META = {
  FURNITURE: 'Nội thất',
  SECURITY: 'An ninh',
  UTILITY: 'Sinh hoạt',
  TRANSPORT: 'Giao thông',
};

export const BANNED_KEYWORD_SEVERITY_META = {
  MILD: { label: 'Nhẹ', color: 'warning' },
  SEVERE: { label: 'Nghiêm trọng', color: 'error' },
};

export const BANNED_KEYWORD_SCOPE_META = {
  LISTING: 'Tin đăng',
  COMMENT: 'Bình luận',
  BOTH: 'Cả hai',
};

export const AI_MODULE_META = {
  SENTIMENT: 'Phân tích cảm xúc',
  RECOMMENDATION: 'Gợi ý',
  CHATBOT: 'Trợ lý ảo',
  PRICE: 'Dự đoán giá',
};

export const AUDIT_ACTION_META = {
  USER_LOCK: 'Khóa người dùng',
  USER_UNLOCK: 'Mở khóa người dùng',
  ROLE_CHANGE: 'Đổi vai trò',
  LISTING_APPROVE: 'Duyệt tin',
  LISTING_REJECT: 'Từ chối tin',
  LISTING_LOCK: 'Khóa tin',
  LISTING_UNLOCK: 'Mở khóa tin',
  LISTING_EDIT: 'Sửa tin',
  AI_CONFIG_CHANGE: 'Đổi cấu hình AI',
  PACKAGE_CHANGE: 'Đổi gói dịch vụ',
  SYSTEM_CONFIG_CHANGE: 'Đổi cấu hình hệ thống',
  PAYMENT_REFUND: 'Hoàn tiền',
};

export const REJECT_REASON_OPTIONS = [
  { value: 'MISSING_INFO', label: 'Thiếu thông tin' },
  { value: 'WRONG_PRICE', label: 'Giá không hợp lý' },
  { value: 'FAKE_IMAGE', label: 'Ảnh không thật' },
  { value: 'BANNED_CONTENT', label: 'Nội dung bị cấm' },
  { value: 'WRONG_AREA', label: 'Sai khu vực' },
  { value: 'DUPLICATE', label: 'Tin trùng lặp' },
  { value: 'OTHER', label: 'Khác' },
];

export const SEVERITY_OPTIONS = [
  { value: 'LOW', label: 'Thấp' },
  { value: 'MEDIUM', label: 'Trung bình' },
  { value: 'HIGH', label: 'Cao' },
  { value: 'CRITICAL', label: 'Nghiêm trọng' },
];

/** Chip nhỏ dùng chung: trả về prop cho <Chip> từ một map meta. */
export const metaChip = (map, key) => map[key] || { label: key || '—', color: 'default' };
