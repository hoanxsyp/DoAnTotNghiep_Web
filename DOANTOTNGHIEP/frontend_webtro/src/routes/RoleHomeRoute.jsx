import { Navigate } from 'react-router-dom';
import { useSelector } from 'react-redux';
import PageLoader from '@/components/common/PageLoader';
import { selectAuthBootstrapped, selectIsAuthenticated, selectRole } from '@/redux/authSlice';
import { getRoleHome } from './roleNavigation';

const RoleHomeRoute = ({ children }) => {
  const bootstrapped = useSelector(selectAuthBootstrapped);
  const isAuthenticated = useSelector(selectIsAuthenticated);
  const role = useSelector(selectRole);

  // Role lấy từ /users/me sau khi khôi phục phiên, không đọc role cũ từ storage.
  if (!bootstrapped) return <PageLoader />;

  const destination = isAuthenticated ? getRoleHome(role) : '/';
  if (destination !== '/') return <Navigate to={destination} replace />;

  return children;
};

export default RoleHomeRoute;
