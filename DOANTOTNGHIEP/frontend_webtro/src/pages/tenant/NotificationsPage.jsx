import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Card, List, ListItemButton, ListItemAvatar, ListItemText, Avatar, Stack, Button,
  Tabs, Tab, Pagination, Typography, Divider,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import notificationApi from '@/api/notificationApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { fromNow } from '@/utils/format';

const SIZE = 20;

const ICONS = {
  SUCCESS: { icon: <CheckCircleIcon />, color: 'success.main' },
  WARNING: { icon: <WarningAmberIcon />, color: 'warning.main' },
  ERROR: { icon: <ErrorOutlineIcon />, color: 'error.main' },
  INFO: { icon: <InfoOutlinedIcon />, color: 'info.main' },
};

const NotificationsPage = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState('all');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await notificationApi.getList({ page, size: SIZE, unreadOnly: tab === 'unread', sort: 'createdAt,desc' });
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
      setUnreadCount(data?.unreadCount ?? 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được thông báo');
    } finally {
      setLoading(false);
    }
  }, [page, tab]);

  useEffect(() => { load(); }, [load]);

  const handleClick = async (n) => {
    try {
      if (!n.read) {
        await notificationApi.markAsRead(n.id);
        setItems((prev) => prev.map((i) => (i.id === n.id ? { ...i, read: true } : i)));
        setUnreadCount((c) => Math.max(0, c - 1));
      }
    } catch (e) {
      notify.apiError(e);
    }
    if (n.targetUrl) navigate(n.targetUrl);
  };

  const handleMarkAll = async () => {
    try {
      await notificationApi.markAllAsRead();
      notify.success('Đã đánh dấu tất cả là đã đọc');
      setItems((prev) => prev.map((i) => ({ ...i, read: true })));
      setUnreadCount(0);
      if (tab === 'unread') load();
    } catch (e) {
      notify.apiError(e, 'Thao tác thất bại');
    }
  };

  return (
    <Box>
      <PageHeader
        title="Thông báo"
        subtitle={unreadCount ? `Bạn có ${unreadCount} thông báo chưa đọc` : 'Bạn đã xem hết thông báo'}
        action={unreadCount > 0 && (
          <Button variant="outlined" startIcon={<DoneAllIcon />} onClick={handleMarkAll}>Đánh dấu tất cả đã đọc</Button>
        )}
      />

      <Card>
        <Tabs value={tab} onChange={(_, v) => { setPage(0); setTab(v); }} sx={{ px: 1 }}>
          <Tab value="all" label="Tất cả" />
          <Tab value="unread" label="Chưa đọc" />
        </Tabs>
        <Divider />

        {loading ? (
          <Box sx={{ p: 2 }}><LoadingSkeleton variant="list-item" count={6} /></Box>
        ) : items.length === 0 ? (
          <EmptyState title={tab === 'unread' ? 'Không có thông báo chưa đọc' : 'Chưa có thông báo nào'} />
        ) : (
          <List disablePadding>
            {items.map((n, idx) => {
              const meta = ICONS[n.iconType] || ICONS.INFO;
              return (
                <Box key={n.id}>
                  <ListItemButton onClick={() => handleClick(n)} sx={{ bgcolor: n.read ? 'transparent' : 'action.hover', alignItems: 'flex-start' }}>
                    <ListItemAvatar>
                      <Avatar sx={{ bgcolor: meta.color, color: '#fff' }}>{meta.icon}</Avatar>
                    </ListItemAvatar>
                    <ListItemText
                      primary={(
                        <Typography variant="subtitle2" sx={{ fontWeight: n.read ? 500 : 700 }}>{n.title}</Typography>
                      )}
                      secondary={(
                        <>
                          <Typography variant="body2" color="text.secondary" component="span" sx={{ display: 'block' }}>
                            {n.content}
                          </Typography>
                          <Typography variant="caption" color="text.disabled">{fromNow(n.createdAt)}</Typography>
                        </>
                      )}
                    />
                  </ListItemButton>
                  {idx < items.length - 1 && <Divider component="li" />}
                </Box>
              );
            })}
          </List>
        )}
      </Card>

      {totalPages > 1 && (
        <Stack alignItems="center" sx={{ mt: 3 }}>
          <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
        </Stack>
      )}
    </Box>
  );
};

export default NotificationsPage;
