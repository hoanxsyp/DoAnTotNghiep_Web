import { Navigate, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Box, CircularProgress } from '@mui/material';
import {
  selectIsAuthenticated,
  selectAuthBootstrapped,
  selectPermissions,
} from '@/redux/authSlice';

/**
 * Chặn truy cập khi thiếu permission cụ thể (RBAC 2 tầng, canonical mục 4.2). Dùng cho các mục
 * quản trị tinh: ví dụ Moderator KHÔNG có PAYMENT_MANAGE nên không vào được trang thanh toán.
 * Chỉ để điều hướng (canonical luật F6) — backend luôn kiểm tra lại.
 *
 * @param {string[]} permissions danh sách permission được phép (khớp bất kỳ)
 */
const PermissionRoute = ({ permissions = [], children }) => {
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const bootstrapped = useSelector(selectAuthBootstrapped);
  const userPermissions = useSelector(selectPermissions);
  const location = useLocation();

  if (!bootstrapped) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/dang-nhap" state={{ from: location }} replace />;
  }

  const allowed = permissions.some((p) => userPermissions.includes(p));
  if (!allowed) {
    return <Navigate to="/403" replace />;
  }

  return children;
};

export default PermissionRoute;
