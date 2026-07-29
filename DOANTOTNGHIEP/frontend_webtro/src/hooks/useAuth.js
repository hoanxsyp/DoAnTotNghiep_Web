import { useSelector } from 'react-redux';
import {
  selectCurrentUser,
  selectIsAuthenticated,
  selectRoles,
  selectPermissions,
} from '@/redux/authSlice';

/**
 * Hook truy cập trạng thái xác thực + kiểm tra role/permission phía client.
 *
 * LƯU Ý: đây chỉ để ĐIỀU HƯỚNG và ẩn/hiện UI (canonical luật F6). Backend LUÔN kiểm tra quyền lại
 * — ẩn nút không phải là phân quyền.
 */
export const useAuth = () => {
  const user = useSelector(selectCurrentUser);
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const roles = useSelector(selectRoles);
  const permissions = useSelector(selectPermissions);

  const hasRole = (role) => roles.includes(role);
  const hasAnyRole = (list) => list.some((r) => roles.includes(r));
  const hasPermission = (perm) => permissions.includes(perm);
  const hasAnyPermission = (list) => list.some((p) => permissions.includes(p));

  return {
    user,
    isAuthenticated,
    roles,
    permissions,
    hasRole,
    hasAnyRole,
    hasPermission,
    hasAnyPermission,
  };
};

export default useAuth;
