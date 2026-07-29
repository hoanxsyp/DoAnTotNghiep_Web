import { Navigate, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Box, CircularProgress } from '@mui/material';
import { selectIsAuthenticated, selectAuthBootstrapped, selectRoles } from '@/redux/authSlice';

/**
 * Chặn truy cập khi người dùng KHÔNG có role phù hợp. Chỉ để điều hướng (canonical luật F6) —
 * backend luôn kiểm tra quyền lại.
 *
 * @param {string[]} roles danh sách role được phép (khớp bất kỳ)
 */
const RoleRoute = ({ roles = [], children }) => {
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const bootstrapped = useSelector(selectAuthBootstrapped);
  const userRoles = useSelector(selectRoles);
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

  const allowed = roles.some((r) => userRoles.includes(r));
  if (!allowed) {
    return <Navigate to="/403" replace />;
  }

  return children;
};

export default RoleRoute;
