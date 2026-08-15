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
import { selectRole } from '@/redux/authSlice';
import { tenantMenu, buildAdminMenu, buildListingManagementMenu } from '@/config/menus';

/**
 * File định tuyến trung tâm (canonical mục 12 — sitemap). Dùng createBrowserRouter (React Router v6).
 * MỌI page được lazy-load bằng React.lazy và bọc trong <Suspense fallback={<PageLoader/>}>.
 *
 * Các layout (Public/Auth/Dashboard) render <Outlet/> nên đóng vai route cha; guard bọc quanh layout
 * cho từng khu vực (Tenant/Landlord/Admin). Ẩn/điều hướng chỉ là UX — backend luôn kiểm tra lại (F6).
 */

const CHUNK_RELOAD_KEY = 'webtro:chunk-reload-url';

const isDynamicImportError = (error) => {
  const message = String(error?.message || '');
  return message.includes('Failed to fetch dynamically imported module')
    || message.includes('Importing a module script failed')
    || message.includes('Loading chunk')
    || error?.name === 'ChunkLoadError';
};

const lazyWithChunkRetry = (factory) => lazy(() =>
  factory()
    .then((module) => {
      if (typeof window !== 'undefined') {
        window.sessionStorage.removeItem(CHUNK_RELOAD_KEY);
      }
      return module;
    })
    .catch((error) => {
      if (typeof window === 'undefined' || !isDynamicImportError(error)) {
        throw error;
      }

      const currentUrl = window.location.href;
      if (window.sessionStorage.getItem(CHUNK_RELOAD_KEY) === currentUrl) {
        throw error;
      }

      window.sessionStorage.setItem(CHUNK_RELOAD_KEY, currentUrl);
      window.location.reload();
      return new Promise(() => {});
    }));

// ---- Helper lazy-load: trả về element đã bọc Suspense + PageLoader ----
const load = (factory) => {
  const Component = lazyWithChunkRetry(factory);
  return (
    <Suspense fallback={<PageLoader />}>
      <Component />
    </Suspense>
  );
};

const loadForRoles = (roles, factory) => (
  <RoleRoute roles={roles}>
    {load(factory)}
  </RoleRoute>
);

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
 * Layout admin: menu được lọc theo role của người dùng hiện tại. Moderator không thấy nhóm Tài
 * chính / Cấu hình hệ thống.
 */
const AdminDashboardLayout = () => {
  const role = useSelector(selectRole);
  return <DashboardLayout menu={buildAdminMenu(role)} title="Quản trị" />;
};

const ListingManagementLayout = () => {
  const role = useSelector(selectRole);
  return <DashboardLayout menu={buildListingManagementMenu(role)} title="Quản lý" />;
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

  // ---------- LISTING MANAGEMENT (TENANT | LANDLORD | ADMIN) ----------
  {
    element: (
      <RoleRoute roles={[ROLES.TENANT, ROLES.LANDLORD, ROLES.ADMIN]}>
        <ListingManagementLayout />
      </RoleRoute>
    ),
    children: [
      { path: '/quan-ly/tong-quan', element: loadForRoles([ROLES.LANDLORD, ROLES.ADMIN], OverviewPage) },
      { path: '/quan-ly/tin-dang', element: load(MyListingsPage) },
      { path: '/quan-ly/tin-dang/tao', element: load(CreateListingPage) },
      { path: '/quan-ly/tin-dang/:id/sua', element: load(EditListingPage) },
      { path: '/quan-ly/tin-dang/:id/thong-ke', element: load(ListingStatsPage) },
      { path: '/quan-ly/nguoi-lien-he', element: load(ContactsPage) },
      { path: '/quan-ly/goi-dich-vu', element: loadForRoles([ROLES.LANDLORD, ROLES.ADMIN], LandlordPackagesPage) },
      { path: '/quan-ly/thanh-toan', element: loadForRoles([ROLES.LANDLORD, ROLES.ADMIN], LandlordPaymentsPage) },
      { path: '/quan-ly/thanh-toan/ket-qua', element: loadForRoles([ROLES.LANDLORD, ROLES.ADMIN], PaymentResultPage) },
      { path: '/quan-ly/ho-so-chu-tro', element: loadForRoles([ROLES.LANDLORD, ROLES.ADMIN], LandlordProfileEditPage) },
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
      { path: '/admin/dashboard', element: loadForRoles([ROLES.ADMIN], DashboardPage) },
      { path: '/admin/nguoi-dung', element: loadForRoles([ROLES.ADMIN], UsersPage) },
      { path: '/admin/chu-tro', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], LandlordsPage) },
      { path: '/admin/tin-dang', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], AdminListingsPage) },
      { path: '/admin/kiem-duyet', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], ModerationQueuePage) },
      { path: '/admin/bao-cao', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], ReportsPage) },
      { path: '/admin/binh-luan', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], CommentsPage) },
      { path: '/admin/danh-gia', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], ReviewsPage) },
      { path: '/admin/danh-muc', element: loadForRoles([ROLES.ADMIN], CategoriesPage) },
      { path: '/admin/khu-vuc', element: loadForRoles([ROLES.ADMIN], AreasPage) },
      { path: '/admin/tien-ich', element: loadForRoles([ROLES.ADMIN], AmenitiesPage) },
      { path: '/admin/tu-khoa-cam', element: loadForRoles([ROLES.ADMIN], BannedKeywordsPage) },
      { path: '/admin/goi-dich-vu', element: loadForRoles([ROLES.ADMIN], AdminPackagesPage) },
      { path: '/admin/thanh-toan', element: loadForRoles([ROLES.ADMIN], AdminPaymentsPage) },
      { path: '/admin/ai/log', element: loadForRoles([ROLES.ADMIN, ROLES.MODERATOR], AiLogsPage) },
      { path: '/admin/ai/cau-hinh', element: loadForRoles([ROLES.ADMIN], AiConfigPage) },
      { path: '/admin/thong-ke', element: loadForRoles([ROLES.ADMIN], StatisticsPage) },
      { path: '/admin/cau-hinh', element: loadForRoles([ROLES.ADMIN], SystemConfigPage) },
      { path: '/admin/audit-log', element: loadForRoles([ROLES.ADMIN], AuditLogPage) },
    ],
  },

  // ---------- LỖI ----------
  { path: '/403', element: load(ForbiddenPage) },
  { path: '*', element: load(NotFoundPage) },
]);

export default router;
