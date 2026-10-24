import { ROLES } from '../constants/index.js';

// Trang chủ công khai riêng để người dùng có dashboard vẫn chủ động xem website.
export const PUBLIC_HOME_PATH = '/trang-chu';

export const getRoleHome = (role) => {
  switch (role) {
    case ROLES.ADMIN: return '/admin/dashboard';
    case ROLES.MODERATOR: return '/admin/kiem-duyet';
    case ROLES.LANDLORD: return '/quan-ly/tong-quan';
    default: return '/';
  }
};

export const getLoginDestination = (role, returnState, canAccessRoute) => {
  const home = getRoleHome(role);
  const { from, allowedRoles } = returnState || {};
  const pathname = typeof from === 'string' ? from : from?.pathname;

  // Chỉ quay lại đường dẫn nội bộ và role được guard của trang đích cho phép.
  if (!pathname?.startsWith('/') || pathname.startsWith('//')
    || pathname === '/' || pathname === '/dang-nhap'
    || (allowedRoles && !allowedRoles.includes(role))
    || (canAccessRoute && !canAccessRoute(pathname, role))) {
    return home;
  }

  return typeof from === 'string' ? from : {
    pathname,
    search: from.search || '',
    hash: from.hash || '',
  };
};
