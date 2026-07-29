import { Navigate, useLocation } from 'react-router-dom';
import { useSelector } from 'react-redux';
import { CircularProgress, Box } from '@mui/material';
import { selectIsAuthenticated, selectAuthBootstrapped } from '@/redux/authSlice';

/**
 * Chặn truy cập khi CHƯA đăng nhập → chuyển tới /dang-nhap, nhớ lại trang đích để quay lại sau khi
 * đăng nhập.
 *
 * Chờ `bootstrapped` (đã thử khôi phục phiên qua refresh token) trước khi quyết định, để không
 * nháy sang trang đăng nhập trong lúc F5 (canonical mục 4.3).
 */
const ProtectedRoute = ({ children }) => {
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const bootstrapped = useSelector(selectAuthBootstrapped);
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

  return children;
};

export default ProtectedRoute;
