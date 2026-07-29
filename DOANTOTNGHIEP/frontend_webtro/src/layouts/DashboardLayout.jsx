import { useState } from 'react';
import { Outlet, useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import {
  Box, Drawer, AppBar, Toolbar, List, ListItemButton, ListItemIcon, ListItemText,
  Typography, IconButton, Avatar, Menu, MenuItem, Divider, useTheme, Tooltip,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import HomeIcon from '@mui/icons-material/Home';
import LogoutIcon from '@mui/icons-material/Logout';
import { useDispatch } from 'react-redux';
import useAuth from '@/hooks/useAuth';
import useResponsive from '@/hooks/useResponsive';
import { logout } from '@/redux/authSlice';
import NotificationBell from '@/components/common/NotificationBell';
import ThemeToggle from '@/components/common/ThemeToggle';

const DRAWER_WIDTH = 264;

/**
 * Khung dashboard dùng chung cho Tenant / Landlord / Admin. Nhận `menu` (mảng nhóm mục) và `title`.
 * Sidebar cố định ở desktop, thu thành drawer trượt ở mobile [§11.7]. Mục menu đã được lọc theo
 * quyền TRƯỚC khi truyền vào (canonical mục 12) — Moderator không thấy mục tài chính.
 */
const DashboardLayout = ({ menu = [], title = 'Bảng điều khiển' }) => {
  const theme = useTheme();
  const { isMobile } = useResponsive();
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useDispatch();
  const { user } = useAuth();

  const [mobileOpen, setMobileOpen] = useState(false);
  const [anchorEl, setAnchorEl] = useState(null);

  const handleLogout = async () => {
    await dispatch(logout());
    navigate('/dang-nhap');
  };

  const drawerContent = (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Toolbar sx={{ gap: 1 }}>
        <HomeIcon color="primary" />
        <Typography variant="h6" color="primary" fontWeight={700}>
          Webtro
        </Typography>
      </Toolbar>
      <Divider />
      <Box sx={{ overflowY: 'auto', flexGrow: 1, py: 1 }}>
        {menu.map((group) => (
          <Box key={group.heading} sx={{ mb: 1 }}>
            {group.heading && (
              <Typography
                variant="caption"
                sx={{ px: 2.5, py: 0.5, color: 'text.secondary', textTransform: 'uppercase', fontWeight: 700, display: 'block' }}
              >
                {group.heading}
              </Typography>
            )}
            <List dense disablePadding>
              {group.items.map((item) => {
                const active = location.pathname === item.path
                  || (item.path !== group.base && location.pathname.startsWith(item.path));
                return (
                  <ListItemButton
                    key={item.path}
                    component={RouterLink}
                    to={item.path}
                    selected={active}
                    onClick={() => isMobile && setMobileOpen(false)}
                    sx={{ mx: 1, borderRadius: 2, mb: 0.25 }}
                  >
                    <ListItemIcon sx={{ minWidth: 40 }}>{item.icon}</ListItemIcon>
                    <ListItemText primary={item.label} primaryTypographyProps={{ fontSize: 14 }} />
                  </ListItemButton>
                );
              })}
            </List>
          </Box>
        ))}
      </Box>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        elevation={0}
        sx={{
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
          bgcolor: 'background.paper',
          color: 'text.primary',
          borderBottom: `1px solid ${theme.palette.divider}`,
        }}
      >
        <Toolbar sx={{ gap: 1 }}>
          <IconButton
            edge="start"
            onClick={() => setMobileOpen(true)}
            sx={{ display: { md: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Typography variant="h6" sx={{ flexGrow: 1, fontSize: 18 }}>
            {title}
          </Typography>
          <Tooltip title="Về trang chủ">
            <IconButton component={RouterLink} to="/">
              <HomeIcon />
            </IconButton>
          </Tooltip>
          <ThemeToggle />
          <NotificationBell />
          <IconButton onClick={(e) => setAnchorEl(e.currentTarget)}>
            <Avatar src={user?.avatarUrl} sx={{ width: 34, height: 34 }}>
              {user?.fullName?.charAt(0)?.toUpperCase()}
            </Avatar>
          </IconButton>
          <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => setAnchorEl(null)}>
            <MenuItem disabled>
              <Typography variant="body2" fontWeight={600}>{user?.fullName}</Typography>
            </MenuItem>
            <Divider />
            <MenuItem component={RouterLink} to="/tai-khoan/ho-so" onClick={() => setAnchorEl(null)}>
              Hồ sơ cá nhân
            </MenuItem>
            <MenuItem onClick={handleLogout}>
              <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
              Đăng xuất
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', md: 'none' },
            '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
          }}
        >
          {drawerContent}
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: 'none', md: 'block' },
            '& .MuiDrawer-paper': {
              width: DRAWER_WIDTH,
              boxSizing: 'border-box',
              borderRight: `1px solid ${theme.palette.divider}`,
            },
          }}
        >
          {drawerContent}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          p: { xs: 2, sm: 3 },
          mt: 8,
        }}
      >
        <Outlet />
      </Box>
    </Box>
  );
};

export default DashboardLayout;
