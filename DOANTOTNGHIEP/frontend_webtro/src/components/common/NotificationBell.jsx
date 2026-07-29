import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  IconButton, Badge, Popover, List, ListItemButton, ListItemText,
  Typography, Box, Button, Divider,
} from '@mui/material';
import NotificationsIcon from '@mui/icons-material/Notifications';
import { useDispatch, useSelector } from 'react-redux';
import dayjs from 'dayjs';
import {
  fetchUnreadCount, fetchRecent, selectUnreadCount, selectRecentNotifications,
} from '@/redux/notificationSlice';
import notificationApi from '@/api/notificationApi';

/**
 * Chuông thông báo: badge số chưa đọc + popover danh sách gần đây. Poll số chưa đọc mỗi 60s.
 */
const NotificationBell = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const unreadCount = useSelector(selectUnreadCount);
  const recent = useSelector(selectRecentNotifications);
  const [anchorEl, setAnchorEl] = useState(null);

  useEffect(() => {
    dispatch(fetchUnreadCount());
    const timer = setInterval(() => dispatch(fetchUnreadCount()), 60000);
    return () => clearInterval(timer);
  }, [dispatch]);

  const handleOpen = (e) => {
    setAnchorEl(e.currentTarget);
    dispatch(fetchRecent());
  };

  const handleItemClick = async (n) => {
    await notificationApi.markAsRead(n.id);
    dispatch(fetchUnreadCount());
    setAnchorEl(null);
    if (n.actionUrl) {
      navigate(n.actionUrl);
    }
  };

  const handleReadAll = async () => {
    await notificationApi.markAllAsRead();
    dispatch(fetchUnreadCount());
    dispatch(fetchRecent());
  };

  return (
    <>
      <IconButton color="inherit" onClick={handleOpen}>
        <Badge badgeContent={unreadCount} color="error" max={99}>
          <NotificationsIcon />
        </Badge>
      </IconButton>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={() => setAnchorEl(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{ paper: { sx: { width: 360, maxHeight: 480 } } }}
      >
        <Box sx={{ px: 2, py: 1.5, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle1" fontWeight={700}>Thông báo</Typography>
          {unreadCount > 0 && (
            <Button size="small" onClick={handleReadAll}>Đánh dấu đã đọc</Button>
          )}
        </Box>
        <Divider />
        {recent.length === 0 ? (
          <Box sx={{ p: 3, textAlign: 'center', color: 'text.secondary' }}>
            <Typography variant="body2">Chưa có thông báo nào</Typography>
          </Box>
        ) : (
          <List disablePadding>
            {recent.map((n) => (
              <ListItemButton
                key={n.id}
                onClick={() => handleItemClick(n)}
                sx={{ bgcolor: n.read ? 'transparent' : 'action.hover', alignItems: 'flex-start' }}
              >
                <ListItemText
                  primary={n.title}
                  secondary={
                    <>
                      <Typography variant="body2" component="span" color="text.secondary">
                        {n.content}
                      </Typography>
                      <br />
                      <Typography variant="caption" color="text.disabled">
                        {n.createdAt ? dayjs(n.createdAt).format('DD/MM/YYYY HH:mm') : ''}
                      </Typography>
                    </>
                  }
                  primaryTypographyProps={{ fontWeight: n.read ? 400 : 700, fontSize: 14 }}
                />
              </ListItemButton>
            ))}
          </List>
        )}
        <Divider />
        <Box sx={{ p: 1, textAlign: 'center' }}>
          <Button fullWidth size="small" onClick={() => { setAnchorEl(null); navigate('/tai-khoan/thong-bao'); }}>
            Xem tất cả
          </Button>
        </Box>
      </Popover>
    </>
  );
};

export default NotificationBell;
