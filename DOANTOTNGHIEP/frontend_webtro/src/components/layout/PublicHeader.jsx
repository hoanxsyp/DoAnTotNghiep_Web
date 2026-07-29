import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  AppBar, Toolbar, Container, Box, Button, IconButton, Avatar, Menu, MenuItem,
  Divider, Typography, useScrollTrigger, ListItemIcon,
} from '@mui/material';
import HomeIcon from '@mui/icons-material/Home';
import AddIcon from '@mui/icons-material/AddCircleOutline';
import LogoutIcon from '@mui/icons-material/Logout';
import DashboardIcon from '@mui/icons-material/Dashboard';
import FavoriteIcon from '@mui/icons-material/FavoriteBorder';
import { useDispatch } from 'react-redux';
import useAuth from '@/hooks/useAuth';
import { logout } from '@/redux/authSlice';
import { ROLES } from '@/constants';
import ThemeToggle from '@/components/common/ThemeToggle';
import NotificationBell from '@/components/common/NotificationBell';

/** Header công khai: logo, tìm kiếm nhanh, hành động theo trạng thái đăng nhập. */
const PublicHeader = () => {
  const { isAuthenticated, user, hasAnyRole } = useAuth();
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState(null);
  const trigger = useScrollTrigger({ disableHysteresis: true, threshold: 0 });

  const isLandlordCapable = hasAnyRole([ROLES.LANDLORD, ROLES.ADMIN]);
  const isStaff = hasAnyRole([ROLES.ADMIN, ROLES.MODERATOR]);

  const handleLogout = async () => {
    setAnchorEl(null);
    await dispatch(logout());
    navigate('/');
  };

  return (
    <AppBar
      position="sticky"
      elevation={trigger ? 2 : 0}
      sx={{ bgcolor: 'background.paper', color: 'text.primary', borderBottom: 1, borderColor: 'divider' }}
    >
      <Container maxWidth="lg">
        <Toolbar disableGutters sx={{ gap: 1 }}>
          <Box component={RouterLink} to="/" sx={{ display: 'flex', alignItems: 'center', gap: 1, textDecoration: 'none', color: 'primary.main', mr: 2 }}>
            <HomeIcon />
            <Typography variant="h6" fontWeight={800} sx={{ display: { xs: 'none', sm: 'block' } }}>
              Webtro
            </Typography>
          </Box>

          <Button component={RouterLink} to="/tim-kiem" color="inherit" sx={{ display: { xs: 'none', md: 'inline-flex' } }}>
            Tìm phòng
          </Button>

          <Box sx={{ flexGrow: 1 }} />

          <ThemeToggle />

          {isLandlordCapable && (
            <Button
              component={RouterLink}
              to="/quan-ly/tin-dang/tao"
              variant="contained"
              startIcon={<AddIcon />}
              sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
            >
              Đăng tin
            </Button>
          )}

          {isAuthenticated ? (
            <>
              <NotificationBell />
              <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
                <Avatar src={user?.avatarUrl} sx={{ width: 34, height: 34 }}>
                  {user?.fullName?.charAt(0)?.toUpperCase()}
                </Avatar>
              </IconButton>
              <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
                <MenuItem disabled>
                  <Typography variant="body2" fontWeight={700}>{user?.fullName}</Typography>
                </MenuItem>
                <Divider />
                <MenuItem component={RouterLink} to="/tai-khoan/ho-so" onClick={() => setAnchorEl(null)}>
                  <ListItemIcon><HomeIcon fontSize="small" /></ListItemIcon> Hồ sơ
                </MenuItem>
                <MenuItem component={RouterLink} to="/tai-khoan/tin-da-luu" onClick={() => setAnchorEl(null)}>
                  <ListItemIcon><FavoriteIcon fontSize="small" /></ListItemIcon> Tin đã lưu
                </MenuItem>
                {isLandlordCapable && (
                  <MenuItem component={RouterLink} to="/quan-ly/tong-quan" onClick={() => setAnchorEl(null)}>
                    <ListItemIcon><DashboardIcon fontSize="small" /></ListItemIcon> Quản lý tin
                  </MenuItem>
                )}
                {isStaff && (
                  <MenuItem component={RouterLink} to="/admin/dashboard" onClick={() => setAnchorEl(null)}>
                    <ListItemIcon><DashboardIcon fontSize="small" /></ListItemIcon> Trang quản trị
                  </MenuItem>
                )}
                <Divider />
                <MenuItem onClick={handleLogout}>
                  <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon> Đăng xuất
                </MenuItem>
              </Menu>
            </>
          ) : (
            <>
              <Button component={RouterLink} to="/dang-nhap" color="inherit">Đăng nhập</Button>
              <Button component={RouterLink} to="/dang-ky" variant="outlined">Đăng ký</Button>
            </>
          )}
        </Toolbar>
      </Container>
    </AppBar>
  );
};

export default PublicHeader;
