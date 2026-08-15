import { useSelector } from 'react-redux';
import {
  selectCurrentUser,
  selectIsAuthenticated,
  selectRole,
} from '@/redux/authSlice';

/**
 * Hook truy cập trạng thái xác thực + kiểm tra role phía client.
 *
 * `role` là MỘT chuỗi (mỗi người dùng đúng một vai trò), nên `hasAnyRole(list)` hỏi "vai trò của
 * tôi có nằm trong danh sách được phép không" — chú ý chiều của `includes`.
 *
 * LƯU Ý: đây chỉ để ĐIỀU HƯỚNG và ẩn/hiện UI (canonical luật F6). Backend LUÔN kiểm tra quyền lại
 * — ẩn nút không phải là phân quyền.
 */
export const useAuth = () => {
  const user = useSelector(selectCurrentUser);
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const role = useSelector(selectRole);

  const hasRole = (target) => role === target;
  const hasAnyRole = (list) => list.includes(role);

  return {
    user,
    isAuthenticated,
    role,
    hasRole,
    hasAnyRole,
  };
};

export default useAuth;
