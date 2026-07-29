import { createElement } from 'react';
import DashboardIcon from '@mui/icons-material/Dashboard';
import PeopleIcon from '@mui/icons-material/People';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import ArticleIcon from '@mui/icons-material/Article';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import FlagIcon from '@mui/icons-material/Flag';
import CommentIcon from '@mui/icons-material/Comment';
import StarIcon from '@mui/icons-material/Star';
import CategoryIcon from '@mui/icons-material/Category';
import MapIcon from '@mui/icons-material/Map';
import ChecklistIcon from '@mui/icons-material/Checklist';
import BlockIcon from '@mui/icons-material/Block';
import SellIcon from '@mui/icons-material/Sell';
import PaymentIcon from '@mui/icons-material/Payment';
import PsychologyIcon from '@mui/icons-material/Psychology';
import TuneIcon from '@mui/icons-material/Tune';
import BarChartIcon from '@mui/icons-material/BarChart';
import SettingsIcon from '@mui/icons-material/Settings';
import HistoryIcon from '@mui/icons-material/History';
import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ChatIcon from '@mui/icons-material/Chat';
import NotificationsIcon from '@mui/icons-material/Notifications';
import RateReviewIcon from '@mui/icons-material/RateReview';
import PeopleAltIcon from '@mui/icons-material/PeopleAlt';
import LockResetIcon from '@mui/icons-material/LockReset';
import PersonIcon from '@mui/icons-material/Person';
import AddBoxIcon from '@mui/icons-material/AddBox';
import ContactPhoneIcon from '@mui/icons-material/ContactPhone';
import StorefrontIcon from '@mui/icons-material/Storefront';
import { PERMISSIONS } from '@/constants';

/**
 * Nguồn sự thật duy nhất cho sidebar của 3 khu vực dashboard (canonical mục 12, docs/04 §4.5).
 *
 * Cấu trúc mỗi menu = mảng nhóm; mỗi nhóm có `heading` (tiêu đề nhóm, có thể null cho nhóm gốc),
 * `base` (đường dẫn gốc để tô sáng đúng mục), và `items[]` gồm `label`, `path`, `icon` (React node)
 * và tùy chọn `permission` — permission cần để mục hiện ra.
 *
 * DashboardLayout nhận menu ĐÃ LỌC theo quyền. Dùng `buildAdminMenu(permissions)` để lọc: mục thiếu
 * quyền bị bỏ; nhóm rỗng sau khi lọc thì bỏ luôn cả nhóm (không để tiêu đề trống lơ lửng). Nhờ đó
 * Moderator KHÔNG thấy nhóm Tài chính / Cấu hình hệ thống (canonical §1.2). Ẩn menu chỉ là UX —
 * backend luôn kiểm tra quyền lại (luật F6).
 */

// `LISTING_VIEW_ANY` chưa nằm trong PERMISSIONS (constants) nên khai báo hằng cục bộ để tránh lỗi.
const LISTING_VIEW_ANY = 'LISTING_VIEW_ANY';

/** Tạo icon element từ component (menus.js là .js nên không dùng JSX trực tiếp). */
const icon = (Component) => createElement(Component, { fontSize: 'small' });

// ===================== TENANT (/tai-khoan/*) =====================
export const tenantMenu = [
  {
    heading: null,
    base: '/tai-khoan',
    items: [
      { label: 'Hồ sơ', path: '/tai-khoan/ho-so', icon: icon(PersonIcon) },
      { label: 'Tin đã lưu', path: '/tai-khoan/tin-da-luu', icon: icon(BookmarkIcon) },
      { label: 'Lịch sử xem', path: '/tai-khoan/lich-su-xem', icon: icon(VisibilityIcon) },
      { label: 'Tin nhắn', path: '/tai-khoan/tin-nhan', icon: icon(ChatIcon) },
      { label: 'Thông báo', path: '/tai-khoan/thong-bao', icon: icon(NotificationsIcon) },
      { label: 'Báo cáo của tôi', path: '/tai-khoan/bao-cao-cua-toi', icon: icon(FlagIcon) },
      { label: 'Đánh giá của tôi', path: '/tai-khoan/danh-gia-cua-toi', icon: icon(RateReviewIcon) },
      { label: 'Đang theo dõi', path: '/tai-khoan/dang-theo-doi', icon: icon(PeopleAltIcon) },
      { label: 'Đổi mật khẩu', path: '/tai-khoan/doi-mat-khau', icon: icon(LockResetIcon) },
    ],
  },
];

// ===================== LANDLORD (/quan-ly/*) =====================
export const landlordMenu = [
  {
    heading: null,
    base: '/quan-ly',
    items: [
      { label: 'Tổng quan', path: '/quan-ly/tong-quan', icon: icon(SpaceDashboardIcon) },
    ],
  },
  {
    heading: 'Tin đăng',
    base: '/quan-ly',
    items: [
      { label: 'Tin đăng', path: '/quan-ly/tin-dang', icon: icon(ArticleIcon) },
      { label: 'Đăng tin mới', path: '/quan-ly/tin-dang/tao', icon: icon(AddBoxIcon) },
      { label: 'Người liên hệ', path: '/quan-ly/nguoi-lien-he', icon: icon(ContactPhoneIcon) },
      { label: 'Tin nhắn', path: '/quan-ly/tin-nhan', icon: icon(ChatIcon) },
    ],
  },
  {
    heading: 'Tài khoản',
    base: '/quan-ly',
    items: [
      { label: 'Gói dịch vụ', path: '/quan-ly/goi-dich-vu', icon: icon(SellIcon) },
      { label: 'Thanh toán', path: '/quan-ly/thanh-toan', icon: icon(PaymentIcon) },
      { label: 'Hồ sơ chủ trọ', path: '/quan-ly/ho-so-chu-tro', icon: icon(StorefrontIcon) },
    ],
  },
];

// ===================== ADMIN / MODERATOR (/admin/*) =====================
export const adminMenu = [
  {
    heading: null,
    base: '/admin',
    items: [
      { label: 'Dashboard', path: '/admin/dashboard', icon: icon(DashboardIcon) },
    ],
  },
  {
    heading: 'Người dùng',
    base: '/admin',
    items: [
      { label: 'Người dùng', path: '/admin/nguoi-dung', icon: icon(PeopleIcon), permission: PERMISSIONS.USER_MANAGE },
      { label: 'Chủ trọ', path: '/admin/chu-tro', icon: icon(VerifiedUserIcon), permission: PERMISSIONS.LANDLORD_VERIFY },
    ],
  },
  {
    heading: 'Nội dung',
    base: '/admin',
    items: [
      { label: 'Tin đăng', path: '/admin/tin-dang', icon: icon(ArticleIcon), permission: LISTING_VIEW_ANY },
      { label: 'Kiểm duyệt', path: '/admin/kiem-duyet', icon: icon(FactCheckIcon), permission: PERMISSIONS.LISTING_MODERATE },
      { label: 'Báo cáo', path: '/admin/bao-cao', icon: icon(FlagIcon), permission: PERMISSIONS.REPORT_RESOLVE },
      { label: 'Bình luận', path: '/admin/binh-luan', icon: icon(CommentIcon), permission: PERMISSIONS.COMMENT_MODERATE },
      { label: 'Đánh giá', path: '/admin/danh-gia', icon: icon(StarIcon), permission: PERMISSIONS.REVIEW_MODERATE },
    ],
  },
  {
    heading: 'Danh mục',
    base: '/admin',
    items: [
      { label: 'Danh mục', path: '/admin/danh-muc', icon: icon(CategoryIcon), permission: PERMISSIONS.CATALOG_MANAGE },
      { label: 'Khu vực', path: '/admin/khu-vuc', icon: icon(MapIcon), permission: PERMISSIONS.CATALOG_MANAGE },
      { label: 'Tiện ích', path: '/admin/tien-ich', icon: icon(ChecklistIcon), permission: PERMISSIONS.CATALOG_MANAGE },
      { label: 'Từ khóa cấm', path: '/admin/tu-khoa-cam', icon: icon(BlockIcon), permission: PERMISSIONS.SYSTEM_CONFIG_MANAGE },
    ],
  },
  {
    heading: 'Tài chính',
    base: '/admin',
    // Moderator KHÔNG có PACKAGE_MANAGE / PAYMENT_MANAGE -> cả nhóm biến mất (canonical §1.2).
    items: [
      { label: 'Gói dịch vụ', path: '/admin/goi-dich-vu', icon: icon(SellIcon), permission: PERMISSIONS.PACKAGE_MANAGE },
      { label: 'Thanh toán', path: '/admin/thanh-toan', icon: icon(PaymentIcon), permission: PERMISSIONS.PAYMENT_MANAGE },
    ],
  },
  {
    heading: 'AI',
    base: '/admin',
    items: [
      { label: 'Log AI', path: '/admin/ai/log', icon: icon(PsychologyIcon), permission: PERMISSIONS.AI_LOG_VIEW },
      { label: 'Cấu hình AI', path: '/admin/ai/cau-hinh', icon: icon(TuneIcon), permission: PERMISSIONS.AI_CONFIG_MANAGE },
    ],
  },
  {
    heading: 'Hệ thống',
    base: '/admin',
    items: [
      { label: 'Thống kê', path: '/admin/thong-ke', icon: icon(BarChartIcon), permission: PERMISSIONS.STATISTIC_VIEW },
      { label: 'Cấu hình', path: '/admin/cau-hinh', icon: icon(SettingsIcon), permission: PERMISSIONS.SYSTEM_CONFIG_MANAGE },
      { label: 'Audit log', path: '/admin/audit-log', icon: icon(HistoryIcon), permission: PERMISSIONS.AUDIT_LOG_VIEW },
    ],
  },
];

/**
 * Lọc một menu theo danh sách permission của người dùng. Mục không có `permission` luôn hiện;
 * nhóm rỗng sau khi lọc bị loại bỏ hoàn toàn.
 */
export const filterMenuByPermissions = (menu, permissions = []) =>
  menu
    .map((group) => {
      const items = group.items.filter(
        (item) => !item.permission || permissions.includes(item.permission),
      );
      return items.length ? { ...group, items } : null;
    })
    .filter(Boolean);

/** Menu admin đã lọc theo quyền — truyền thẳng vào DashboardLayout. */
export const buildAdminMenu = (permissions = []) => filterMenuByPermissions(adminMenu, permissions);

export default { tenantMenu, landlordMenu, adminMenu, filterMenuByPermissions, buildAdminMenu };
