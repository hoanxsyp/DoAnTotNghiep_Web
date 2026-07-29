import { lazy, Suspense } from 'react';
import { createBrowserRouter } from 'react-router-dom';
import { useSelector } from 'react-redux';

import PublicLayout from '@/layouts/PublicLayout';
import AuthLayout from '@/layouts/AuthLayout';
import DashboardLayout from '@/layouts/DashboardLayout';

import ProtectedRoute from '@/routes/ProtectedRoute';
import RoleRoute from '@/routes/RoleRoute';

import PageLoader from '@/components/common/PageLoader';
import { ROLES } from '@/constants';
import { selectPermissions } from '@/redux/authSlice';
import { tenantMenu, landlordMenu, adminMenu, buildAdminMenu } from '@/config/menus';

/**
 * File định tuyến trung tâm (canonical mục 12 — sitemap). Dùng createBrowserRouter (React Router v6).
 * MỌI page được lazy-load bằng React.lazy và bọc trong <Suspense fallback={<PageLoader/>}>.
 *
 * Các layout (Public/Auth/Dashboard) render <Outlet/> nên đóng vai route cha; guard bọc quanh layout
 * cho từng khu vực (Tenant/Landlord/Admin). Ẩn/điều hướng chỉ là UX — backend luôn kiểm tra lại (F6).
 */

// ---- Helper lazy-load: trả về element đã bọc Suspense + PageLoader ----
const load = (factory) => {
  const Component = lazy(factory);
  return (
    <Suspense fallback={<PageLoader />}>
      <Component />
    </Suspense>
  );
};

// ================= PUBLIC =================
const HomePage = () => import('@/pages/public/HomePage');
const SearchPage = () => import('@/pages/public/SearchPage');
const ListingDetailPage = () => import('@/pages/public/ListingDetailPage');
const LandlordProfilePage = () => import('@/pages/public/LandlordProfilePage');
const AboutPage = () => import('@/pages/public/AboutPage');
const TermsPage = () => import('@/pages/public/TermsPage');
const ForbiddenPage = () => import('@/pages/public/ForbiddenPage');
const NotFoundPage = () => import('@/pages/public/NotFoundPage');

// ================= AUTH =================
const LoginPage = () => import('@/pages/auth/LoginPage');
const RegisterPage = () => import('@/pages/auth/RegisterPage');
const ForgotPasswordPage = () => import('@/pages/auth/ForgotPasswordPage');
const ResetPasswordPage = () => import('@/pages/auth/ResetPasswordPage');
const VerifyEmailPage = () => import('@/pages/auth/VerifyEmailPage');

// ================= TENANT =================
const ProfilePage = () => import('@/pages/tenant/ProfilePage');
const SavedListingsPage = () => import('@/pages/tenant/SavedListingsPage');
const ViewHistoryPage = () => import('@/pages/tenant/ViewHistoryPage');
const MessagesPage = () => import('@/pages/tenant/MessagesPage');
const NotificationsPage = () => import('@/pages/tenant/NotificationsPage');
const MyReportsPage = () => import('@/pages/tenant/MyReportsPage');
const MyReviewsPage = () => import('@/pages/tenant/MyReviewsPage');
const FollowingPage = () => import('@/pages/tenant/FollowingPage');
const ChangePasswordPage = () => import('@/pages/tenant/ChangePasswordPage');

// ================= LANDLORD =================
const OverviewPage = () => import('@/pages/landlord/OverviewPage');
const MyListingsPage = () => import('@/pages/landlord/MyListingsPage');
const CreateListingPage = () => import('@/pages/landlord/CreateListingPage');
const EditListingPage = () => import('@/pages/landlord/EditListingPage');
const ListingStatsPage = () => import('@/pages/landlord/ListingStatsPage');
const ContactsPage = () => import('@/pages/landlord/ContactsPage');
const LandlordPackagesPage = () => import('@/pages/landlord/PackagesPage');
const LandlordPaymentsPage = () => import('@/pages/landlord/PaymentsPage');
const PaymentResultPage = () => import('@/pages/landlord/PaymentResultPage');
const LandlordProfileEditPage = () => import('@/pages/landlord/LandlordProfileEditPage');

// ================= ADMIN / MODERATOR =================
const DashboardPage = () => import('@/pages/admin/DashboardPage');
const UsersPage = () => import('@/pages/admin/UsersPage');
const LandlordsPage = () => import('@/pages/admin/LandlordsPage');
const AdminListingsPage = () => import('@/pages/admin/ListingsPage');
const ModerationQueuePage = () => import('@/pages/admin/ModerationQueuePage');
const ReportsPage = () => import('@/pages/admin/ReportsPage');
const CommentsPage = () => import('@/pages/admin/CommentsPage');
const ReviewsPage = () => import('@/pages/admin/ReviewsPage');
const CategoriesPage = () => import('@/pages/admin/CategoriesPage');
const AreasPage = () => import('@/pages/admin/AreasPage');
const AmenitiesPage = () => import('@/pages/admin/AmenitiesPage');
const BannedKeywordsPage = () => import('@/pages/admin/BannedKeywordsPage');
const AdminPackagesPage = () => import('@/pages/admin/PackagesPage');
const AdminPaymentsPage = () => import('@/pages/admin/PaymentsPage');
const AiLogsPage = () => import('@/pages/admin/AiLogsPage');
const AiConfigPage = () => import('@/pages/admin/AiConfigPage');
const StatisticsPage = () => import('@/pages/admin/StatisticsPage');
const SystemConfigPage = () => import('@/pages/admin/SystemConfigPage');
const AuditLogPage = () => import('@/pages/admin/AuditLogPage');

/**
 * Layout admin: menu được LỌC theo permission của người dùng hiện tại (canonical §1.2) — Moderator
 * không thấy nhóm Tài chính / Cấu hình hệ thống. adminMenu gốc vẫn được import để làm nguồn lọc.
 */
const AdminDashboardLayout = () => {
  const permissions = useSelector(selectPermissions);
  return <DashboardLayout menu={buildAdminMenu(permissions)} title="Quản trị" />;
};

const router = createBrowserRouter([
  // ---------- PUBLIC ----------
  {
    element: <PublicLayout />,
    children: [
      { path: '/', element: load(HomePage) },
      { path: '/tim-kiem', element: load(SearchPage) },
      { path: '/tin/:slugId', element: load(ListingDetailPage) },
      { path: '/chu-tro/:id', element: load(LandlordProfilePage) },
      { path: '/gioi-thieu', element: load(AboutPage) },
      { path: '/dieu-khoan', element: load(TermsPage) },
    ],
  },

  // ---------- AUTH ----------
  {
    element: <AuthLayout />,
    children: [
      { path: '/dang-nhap', element: load(LoginPage) },
      { path: '/dang-ky', element: load(RegisterPage) },
      { path: '/quen-mat-khau', element: load(ForgotPasswordPage) },
      { path: '/dat-lai-mat-khau', element: load(ResetPasswordPage) },
      { path: '/xac-thuc-email', element: load(VerifyEmailPage) },
    ],
  },

  // ---------- TENANT (đã đăng nhập) ----------
  {
    element: (
      <ProtectedRoute>
        <DashboardLayout menu={tenantMenu} title="Tài khoản" />
      </ProtectedRoute>
    ),
    children: [
      { path: '/tai-khoan/ho-so', element: load(ProfilePage) },
      { path: '/tai-khoan/tin-da-luu', element: load(SavedListingsPage) },
      { path: '/tai-khoan/lich-su-xem', element: load(ViewHistoryPage) },
      { path: '/tai-khoan/tin-nhan', element: load(MessagesPage) },
      { path: '/tai-khoan/thong-bao', element: load(NotificationsPage) },
      { path: '/tai-khoan/bao-cao-cua-toi', element: load(MyReportsPage) },
      { path: '/tai-khoan/danh-gia-cua-toi', element: load(MyReviewsPage) },
      { path: '/tai-khoan/dang-theo-doi', element: load(FollowingPage) },
      { path: '/tai-khoan/doi-mat-khau', element: load(ChangePasswordPage) },
    ],
  },

  // ---------- LANDLORD (ROLE_LANDLORD | ROLE_ADMIN) ----------
  {
    element: (
      <RoleRoute roles={[ROLES.LANDLORD, ROLES.ADMIN]}>
        <DashboardLayout menu={landlordMenu} title="Quản lý" />
      </RoleRoute>
    ),
    children: [
      { path: '/quan-ly/tong-quan', element: load(OverviewPage) },
      { path: '/quan-ly/tin-dang', element: load(MyListingsPage) },
      { path: '/quan-ly/tin-dang/tao', element: load(CreateListingPage) },
      { path: '/quan-ly/tin-dang/:id/sua', element: load(EditListingPage) },
      { path: '/quan-ly/tin-dang/:id/thong-ke', element: load(ListingStatsPage) },
      { path: '/quan-ly/nguoi-lien-he', element: load(ContactsPage) },
      { path: '/quan-ly/goi-dich-vu', element: load(LandlordPackagesPage) },
      { path: '/quan-ly/thanh-toan', element: load(LandlordPaymentsPage) },
      { path: '/quan-ly/thanh-toan/ket-qua', element: load(PaymentResultPage) },
      { path: '/quan-ly/ho-so-chu-tro', element: load(LandlordProfileEditPage) },
    ],
  },

  // ---------- ADMIN / MODERATOR (ROLE_ADMIN | ROLE_MODERATOR) ----------
  {
    element: (
      <RoleRoute roles={[ROLES.ADMIN, ROLES.MODERATOR]}>
        <AdminDashboardLayout />
      </RoleRoute>
    ),
    children: [
      { path: '/admin/dashboard', element: load(DashboardPage) },
      { path: '/admin/nguoi-dung', element: load(UsersPage) },
      { path: '/admin/chu-tro', element: load(LandlordsPage) },
      { path: '/admin/tin-dang', element: load(AdminListingsPage) },
      { path: '/admin/kiem-duyet', element: load(ModerationQueuePage) },
      { path: '/admin/bao-cao', element: load(ReportsPage) },
      { path: '/admin/binh-luan', element: load(CommentsPage) },
      { path: '/admin/danh-gia', element: load(ReviewsPage) },
      { path: '/admin/danh-muc', element: load(CategoriesPage) },
      { path: '/admin/khu-vuc', element: load(AreasPage) },
      { path: '/admin/tien-ich', element: load(AmenitiesPage) },
      { path: '/admin/tu-khoa-cam', element: load(BannedKeywordsPage) },
      { path: '/admin/goi-dich-vu', element: load(AdminPackagesPage) },
      { path: '/admin/thanh-toan', element: load(AdminPaymentsPage) },
      { path: '/admin/ai/log', element: load(AiLogsPage) },
      { path: '/admin/ai/cau-hinh', element: load(AiConfigPage) },
      { path: '/admin/thong-ke', element: load(StatisticsPage) },
      { path: '/admin/cau-hinh', element: load(SystemConfigPage) },
      { path: '/admin/audit-log', element: load(AuditLogPage) },
    ],
  },

  // ---------- LỖI ----------
  { path: '/403', element: load(ForbiddenPage) },
  { path: '*', element: load(NotFoundPage) },
]);

export default router;
