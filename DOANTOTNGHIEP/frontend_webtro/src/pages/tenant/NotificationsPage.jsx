import { useCallback, useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Alert, Avatar, Box, Button, Card, Chip, CircularProgress, Divider, List,
  ListItemAvatar, ListItemButton, ListItemText, Pagination, Stack, Switch, Tab,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Tabs,
  Tooltip, Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import DoneAllIcon from '@mui/icons-material/DoneAll';
import SaveIcon from '@mui/icons-material/Save';
import RefreshIcon from '@mui/icons-material/Refresh';
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

const LISTING_TYPES = new Set([
  'LISTING_APPROVED',
  'LISTING_REJECTED',
  'LISTING_EXPIRING',
  'LISTING_EXPIRED',
  'LISTING_LOCKED',
  'LISTING_HIDDEN',
]);

const INTERACTION_TYPES = new Set([
  'NEW_CONTACT',
  'NEW_COMMENT',
  'NEW_REVIEW',
  'FOLLOWED_LANDLORD_NEW_LISTING',
  'NEW_MATCHING_LISTING',
]);

const preferenceGroup = (type) => {
  if (LISTING_TYPES.has(type)) return 'Tin đăng';
  if (INTERACTION_TYPES.has(type)) return 'Tương tác';
  return 'Hệ thống';
};

const NotificationsPage = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState('all');
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [unreadCount, setUnreadCount] = useState(0);
  const [preferences, setPreferences] = useState([]);
  const [originalPreferences, setOriginalPreferences] = useState([]);
  const [preferencesLoaded, setPreferencesLoaded] = useState(false);
  const [preferencesLoading, setPreferencesLoading] = useState(false);
  const [preferencesSaving, setPreferencesSaving] = useState(false);
  const [preferencesError, setPreferencesError] = useState('');

  const load = useCallback(async () => {
    if (tab === 'settings') return;
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

  const loadPreferences = useCallback(async () => {
    setPreferencesLoading(true);
    setPreferencesError('');
    try {
      const data = await notificationApi.getPreferences();
      const rows = data?.preferences || [];
      setPreferences(rows);
      setOriginalPreferences(rows);
      setPreferencesLoaded(true);
    } catch (e) {
      setPreferencesError('Không tải được cài đặt thông báo');
      notify.apiError(e, 'Không tải được cài đặt thông báo');
    } finally {
      setPreferencesLoading(false);
    }
  }, []);

  useEffect(() => {
    if (tab === 'settings' && !preferencesLoaded && !preferencesLoading) {
      loadPreferences();
    }
  }, [loadPreferences, preferencesLoaded, preferencesLoading, tab]);

  const changedPreferences = useMemo(() => {
    const originalByType = new Map(originalPreferences.map((item) => [item.type, item]));
    return preferences.filter((item) => {
      const original = originalByType.get(item.type);
      if (!original) return true;
      return Boolean(item.inApp) !== Boolean(original.inApp)
        || Boolean(item.email) !== Boolean(original.email);
    });
  }, [originalPreferences, preferences]);

  const hasPreferenceChanges = changedPreferences.length > 0;

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

  const handlePreferenceChange = (type, field) => (event) => {
    const checked = event.target.checked;
    setPreferences((prev) => prev.map((item) => {
      if (item.type !== type || item.optional === false) return item;
      return { ...item, [field]: checked };
    }));
  };

  const handleSavePreferences = async () => {
    if (!hasPreferenceChanges) return;
    setPreferencesSaving(true);
    setPreferencesError('');
    try {
      const payload = {
        preferences: changedPreferences.map((item) => ({
          type: item.type,
          inApp: Boolean(item.inApp),
          email: Boolean(item.email),
        })),
      };
      const data = await notificationApi.updatePreferences(payload);
      const rows = data?.preferences || [];
      setPreferences(rows);
      setOriginalPreferences(rows);
      setPreferencesLoaded(true);
      notify.success('Đã lưu cài đặt thông báo');
    } catch (e) {
      setPreferencesError('Không lưu được cài đặt thông báo');
      notify.apiError(e, 'Không lưu được cài đặt thông báo');
    } finally {
      setPreferencesSaving(false);
    }
  };

  const renderPreferenceSwitch = (item, field) => {
    const locked = item.optional === false || preferencesSaving;
    const control = (
      <Switch
        checked={Boolean(item[field])}
        disabled={locked}
        onChange={handlePreferenceChange(item.type, field)}
        inputProps={{ 'aria-label': `${item.typeLabel} ${field === 'inApp' ? 'trên app' : 'email'}` }}
      />
    );
    if (item.optional === false) {
      return (
        <Tooltip title="Thông báo bắt buộc">
          <span>{control}</span>
        </Tooltip>
      );
    }
    return control;
  };

  return (
    <Box>
      <PageHeader
        title="Thông báo"
        subtitle={unreadCount ? `Bạn có ${unreadCount} thông báo chưa đọc` : 'Bạn đã xem hết thông báo'}
        action={tab !== 'settings' && unreadCount > 0 && (
          <Button variant="outlined" startIcon={<DoneAllIcon />} onClick={handleMarkAll}>Đánh dấu tất cả đã đọc</Button>
        )}
      />

      <Card>
        <Tabs
          value={tab}
          onChange={(_, v) => {
            if (v !== 'settings') setPage(0);
            setTab(v);
          }}
          sx={{ px: 1 }}
        >
          <Tab value="all" label="Tất cả" />
          <Tab value="unread" label="Chưa đọc" />
          <Tab value="settings" label="Thiết lập" />
        </Tabs>
        <Divider />

        {tab === 'settings' ? (
          <Box sx={{ p: { xs: 2, md: 3 } }}>
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              justifyContent="space-between"
              alignItems={{ xs: 'stretch', sm: 'center' }}
              spacing={2}
              sx={{ mb: 2 }}
            >
              <Box>
                <Typography variant="h6">Kênh nhận thông báo</Typography>
                <Typography variant="body2" color="text.secondary">
                  {preferences.length} loại thông báo
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} justifyContent={{ xs: 'flex-end', sm: 'initial' }}>
                <Button
                  variant="outlined"
                  startIcon={<RefreshIcon />}
                  onClick={loadPreferences}
                  disabled={preferencesLoading || preferencesSaving}
                >
                  Tải lại
                </Button>
                <Button
                  variant="contained"
                  startIcon={preferencesSaving ? <CircularProgress color="inherit" size={18} /> : <SaveIcon />}
                  onClick={handleSavePreferences}
                  disabled={!hasPreferenceChanges || preferencesLoading || preferencesSaving}
                >
                  Lưu
                </Button>
              </Stack>
            </Stack>

            {preferencesError && <Alert severity="error" sx={{ mb: 2 }}>{preferencesError}</Alert>}

            {preferencesLoading ? (
              <LoadingSkeleton variant="list-item" count={8} />
            ) : preferences.length === 0 ? (
              <EmptyState title="Không tải được cài đặt thông báo" />
            ) : (
              <TableContainer sx={{ overflowX: 'auto' }}>
                <Table size="small" aria-label="Cài đặt thông báo">
                  <TableHead>
                    <TableRow>
                      <TableCell>Loại thông báo</TableCell>
                      <TableCell>Nhóm</TableCell>
                      <TableCell align="center">Trên app</TableCell>
                      <TableCell align="center">Email</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {preferences.map((item) => (
                      <TableRow key={item.type} hover>
                        <TableCell sx={{ minWidth: 240 }}>
                          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
                            <Typography variant="body2" fontWeight={600}>{item.typeLabel || item.type}</Typography>
                            {item.optional === false && <Chip size="small" label="Bắt buộc" />}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" color="text.secondary">{preferenceGroup(item.type)}</Typography>
                        </TableCell>
                        <TableCell align="center">{renderPreferenceSwitch(item, 'inApp')}</TableCell>
                        <TableCell align="center">{renderPreferenceSwitch(item, 'email')}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </Box>
        ) : loading ? (
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

      {tab !== 'settings' && totalPages > 1 && (
        <Stack alignItems="center" sx={{ mt: 3 }}>
          <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
        </Stack>
      )}
    </Box>
  );
};

export default NotificationsPage;
