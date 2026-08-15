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
import { ROLES } from '@/constants';

/**
 * Nguồn sự thật duy nhất cho sidebar của 3 khu vực dashboard (canonical mục 12, docs/04 §4.5).
 *
 * Cấu trúc mỗi menu = mảng nhóm; mỗi nhóm có `heading` (tiêu đề nhóm, có thể null cho nhóm gốc),
 * `base` (đường dẫn gốc để tô sáng đúng mục), và `items[]` gồm `label`, `path`, `icon` (React node)
 * và tùy chọn `roles` — role được thấy mục đó.
 *
 * DashboardLayout nhận menu ĐÃ LỌC theo role. Nhóm rỗng sau khi lọc thì bỏ luôn cả nhóm. Nhờ đó
 * Moderator KHÔNG thấy nhóm Tài chính / Cấu hình hệ thống. Ẩn menu chỉ là UX — backend luôn kiểm
 * tra lại (luật F6).
 */

/** Tạo icon element từ component (menus.js là .js nên không dùng JSX trực tiếp). */
const icon = (Component) => createElement(Component, { fontSize: 'small' });

const ADMIN_ONLY = [ROLES.ADMIN];
const MODERATION_ROLES = [ROLES.MODERATOR, ROLES.ADMIN];

// ===================== TENANT (/tai-khoan/*) =====================
export const tenantMenu = [
  {
    heading: null,
    base: '/tai-khoan',
    items: [
      { label: 'Hồ sơ', path: '/tai-khoan/ho-so', icon: icon(PersonIcon) },
      { label: 'Tin đã đăng', path: '/quan-ly/tin-dang', icon: icon(ArticleIcon) },
      { label: 'Đăng tin ở ghép', path: '/quan-ly/tin-dang/tao', icon: icon(AddBoxIcon) },
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

export const listingManagerMenu = [
  {
    heading: 'Tin đăng',
    base: '/quan-ly',
    items: [
      { label: 'Tin đăng', path: '/quan-ly/tin-dang', icon: icon(ArticleIcon) },
      { label: 'Đăng tin ở ghép', path: '/quan-ly/tin-dang/tao', icon: icon(AddBoxIcon) },
      { label: 'Người liên hệ', path: '/quan-ly/nguoi-lien-he', icon: icon(ContactPhoneIcon) },
    ],
  },
];

// ===================== ADMIN / MODERATOR (/admin/*) =====================
export const adminMenu = [
  {
    heading: null,
    base: '/admin',
    items: [
      { label: 'Dashboard', path: '/admin/dashboard', icon: icon(DashboardIcon), roles: ADMIN_ONLY },
    ],
  },
  {
    heading: 'Người dùng',
    base: '/admin',
    items: [
      { label: 'Người dùng', path: '/admin/nguoi-dung', icon: icon(PeopleIcon), roles: ADMIN_ONLY },
      { label: 'Chủ trọ', path: '/admin/chu-tro', icon: icon(VerifiedUserIcon), roles: MODERATION_ROLES },
    ],
  },
  {
    heading: 'Nội dung',
    base: '/admin',
    items: [
      { label: 'Tin đăng', path: '/admin/tin-dang', icon: icon(ArticleIcon), roles: MODERATION_ROLES },
      { label: 'Kiểm duyệt', path: '/admin/kiem-duyet', icon: icon(FactCheckIcon), roles: MODERATION_ROLES },
      { label: 'Báo cáo', path: '/admin/bao-cao', icon: icon(FlagIcon), roles: MODERATION_ROLES },
      { label: 'Bình luận', path: '/admin/binh-luan', icon: icon(CommentIcon), roles: MODERATION_ROLES },
      { label: 'Đánh giá', path: '/admin/danh-gia', icon: icon(StarIcon), roles: MODERATION_ROLES },
    ],
  },
  {
    heading: 'Danh mục',
    base: '/admin',
    items: [
      { label: 'Danh mục', path: '/admin/danh-muc', icon: icon(CategoryIcon), roles: ADMIN_ONLY },
      { label: 'Khu vực', path: '/admin/khu-vuc', icon: icon(MapIcon), roles: ADMIN_ONLY },
      { label: 'Tiện ích', path: '/admin/tien-ich', icon: icon(ChecklistIcon), roles: ADMIN_ONLY },
      { label: 'Từ khóa cấm', path: '/admin/tu-khoa-cam', icon: icon(BlockIcon), roles: ADMIN_ONLY },
    ],
  },
  {
    heading: 'Tài chính',
    base: '/admin',
    // Moderator KHÔNG có PACKAGE_MANAGE / PAYMENT_MANAGE -> cả nhóm biến mất (canonical §1.2).
    items: [
      { label: 'Gói dịch vụ', path: '/admin/goi-dich-vu', icon: icon(SellIcon), roles: ADMIN_ONLY },
      { label: 'Thanh toán', path: '/admin/thanh-toan', icon: icon(PaymentIcon), roles: ADMIN_ONLY },
    ],
  },
  {
    heading: 'AI',
    base: '/admin',
    items: [
      { label: 'Log AI', path: '/admin/ai/log', icon: icon(PsychologyIcon), roles: MODERATION_ROLES },
      { label: 'Cấu hình AI', path: '/admin/ai/cau-hinh', icon: icon(TuneIcon), roles: ADMIN_ONLY },
    ],
  },
  {
    heading: 'Hệ thống',
    base: '/admin',
    items: [
      { label: 'Thống kê', path: '/admin/thong-ke', icon: icon(BarChartIcon), roles: ADMIN_ONLY },
      { label: 'Cấu hình', path: '/admin/cau-hinh', icon: icon(SettingsIcon), roles: ADMIN_ONLY },
      { label: 'Audit log', path: '/admin/audit-log', icon: icon(HistoryIcon), roles: ADMIN_ONLY },
    ],
  },
];

/**
 * Lọc một menu theo role của người dùng. Mục không có `roles` luôn hiện;
 * nhóm rỗng sau khi lọc bị loại bỏ hoàn toàn.
 */
export const filterMenuByRole = (menu, role) =>
  menu
    .map((group) => {
      const items = group.items.filter(
        (item) => !item.roles || item.roles.includes(role),
      );
      return items.length ? { ...group, items } : null;
    })
    .filter(Boolean);

/** Menu admin đã lọc theo role — truyền thẳng vào DashboardLayout. */
export const buildAdminMenu = (role) => filterMenuByRole(adminMenu, role);

/** Menu quản lý tin: Tenant chỉ thấy phần tin; Landlord/Admin thấy đầy đủ. */
export const buildListingManagementMenu = (role) =>
  role === ROLES.TENANT ? listingManagerMenu : landlordMenu;

export default { tenantMenu, landlordMenu, listingManagerMenu, adminMenu, filterMenuByRole, buildAdminMenu, buildListingManagementMenu };
