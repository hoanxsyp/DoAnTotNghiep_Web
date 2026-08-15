import { Navigate, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { Box, CircularProgress } from '@mui/material';
import { selectIsAuthenticated, selectAuthBootstrapped, selectRole } from '@/redux/authSlice';

/**
 * Chặn truy cập khi người dùng KHÔNG có role phù hợp. Chỉ để điều hướng (canonical luật F6) —
 * backend luôn kiểm tra quyền lại.
 *
 * @param {string[]} roles danh sách role ĐƯỢC PHÉP vào route. Người dùng chỉ có một vai trò nên
 *   điều kiện là "vai trò của tôi nằm trong danh sách này".
 */
const RoleRoute = ({ roles = [], children }) => {
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const bootstrapped = useSelector(selectAuthBootstrapped);
  const userRole = useSelector(selectRole);
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

  const allowed = roles.includes(userRole);
  if (!allowed) {
    return <Navigate to="/403" replace />;
  }

  return children;
};

export default RoleRoute;
