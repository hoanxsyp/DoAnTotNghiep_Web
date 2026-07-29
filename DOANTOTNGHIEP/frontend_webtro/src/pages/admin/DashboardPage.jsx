import { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Grid, Box, Button, Alert, AlertTitle, Stack, Card, CardHeader, CardContent,
  List, ListItemButton, ListItemIcon, ListItemText, Chip, useTheme,
} from '@mui/material';
import PeopleIcon from '@mui/icons-material/People';
import HomeWorkIcon from '@mui/icons-material/HomeWork';
import PendingActionsIcon from '@mui/icons-material/PendingActions';
import FlagIcon from '@mui/icons-material/Flag';
import PaidIcon from '@mui/icons-material/Paid';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import PsychologyIcon from '@mui/icons-material/Psychology';
import VerifiedUserIcon from '@mui/icons-material/VerifiedUser';
import LockIcon from '@mui/icons-material/Lock';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import RefreshIcon from '@mui/icons-material/Refresh';
import CircleNotificationsIcon from '@mui/icons-material/CircleNotifications';
import { Bar, Doughnut } from 'react-chartjs-2';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import { formatCurrency, formatDateTime, compactNumber } from '@/utils/format';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatCard from '@/components/admin/StatCard';
import ChartCard from '@/components/admin/ChartCard';

const CHART_COLORS = ['#0d9488', '#2563eb', '#f59e0b', '#ef4444', '#8b5cf6', '#10b981', '#ec4899'];

/**
 * Dashboard tổng quan (docs/04 §10.1, API 4.12.1). Hiển thị 10 nhóm chỉ số bằng StatCard + biểu đồ
 * top khu vực / danh mục + danh sách hoạt động gần đây. Chỉ Admin có STATISTIC_VIEW mới vào được.
 */
const DashboardPage = () => {
  const theme = useTheme();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    adminApi.getDashboard()
      .then(setData)
      .catch((err) => {
        setError(err);
        notify.apiError(err);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const u = data?.users ?? {};
  const l = data?.listings ?? {};
  const r = data?.reports ?? {};
  const rev = data?.revenue ?? {};
  const pay = data?.payments ?? {};
  const ai = data?.aiAlerts ?? {};
  const topProvinces = data?.topProvinces ?? [];
  const topCategories = data?.topCategories ?? [];
  const activity = data?.recentActivity ?? [];

  const stats = [
    { label: 'Tổng người dùng', value: compactNumber(u.total), sub: `+${u.newToday ?? 0} hôm nay`, icon: <PeopleIcon />, color: 'primary' },
    { label: 'Chủ trọ', value: compactNumber(u.landlords), sub: `${u.pendingVerifyCount ?? 0} chờ xác thực`, icon: <VerifiedUserIcon />, color: 'info' },
    { label: 'Tài khoản bị khóa', value: compactNumber(u.lockedCount), sub: `${u.activeCount ?? 0} đang hoạt động`, icon: <LockIcon />, color: 'error' },
    { label: 'Tin đang hiển thị', value: compactNumber(l.active), sub: `+${l.newToday ?? 0} hôm nay`, icon: <HomeWorkIcon />, color: 'success' },
    { label: 'Tin chờ duyệt', value: compactNumber(l.pending), sub: `${l.needReview ?? 0} cần kiểm tra`, icon: <PendingActionsIcon />, color: 'warning', onClick: () => navigate('/admin/kiem-duyet') },
    { label: 'Báo cáo chờ xử lý', value: compactNumber(r.pending), sub: `${r.criticalPending ?? 0} nghiêm trọng`, icon: <FlagIcon />, color: 'error', onClick: () => navigate('/admin/bao-cao') },
    { label: 'Doanh thu tháng', value: formatCurrency(rev.thisMonth), sub: `${rev.growthPercent >= 0 ? '+' : ''}${rev.growthPercent ?? 0}% so tháng trước`, icon: <PaidIcon />, color: 'primary' },
    { label: 'Doanh thu hôm nay', value: formatCurrency(rev.today), sub: `Tổng: ${formatCurrency(rev.allTime)}`, icon: <TrendingUpIcon />, color: 'success' },
    { label: 'Tỷ lệ thanh toán', value: `${pay.successRatePercent ?? 0}%`, sub: `${pay.successCount ?? 0} thành công / ${pay.failedCount ?? 0} thất bại`, icon: <CheckCircleIcon />, color: 'info' },
    { label: 'Cảnh báo AI', value: compactNumber((ai.listingsFlaggedBySentiment ?? 0) + (ai.listingsWithPriceDeviation ?? 0)), sub: `${ai.listingsFlaggedBySentiment ?? 0} sentiment · ${ai.listingsWithPriceDeviation ?? 0} lệch giá`, icon: <PsychologyIcon />, color: 'warning', onClick: () => navigate('/admin/ai/log') },
  ];

  const provinceChart = {
    labels: topProvinces.map((p) => p.name),
    datasets: [{
      label: 'Số tin', data: topProvinces.map((p) => p.listingCount),
      backgroundColor: theme.palette.primary.main, borderRadius: 4,
    }],
  };
  const categoryChart = {
    labels: topCategories.map((c) => c.name),
    datasets: [{
      data: topCategories.map((c) => c.listingCount),
      backgroundColor: CHART_COLORS, borderWidth: 0,
    }],
  };

  const severityColor = { ERROR: 'error', WARNING: 'warning', INFO: 'info' };

  return (
    <Box>
      <AdminPageHeader
        title="Bảng điều khiển"
        subtitle={data?.generatedAt ? `Cập nhật lúc ${formatDateTime(data.generatedAt)}` : 'Tổng quan toàn hệ thống'}
        actions={<Button startIcon={<RefreshIcon />} onClick={load} disabled={loading}>Làm mới</Button>}
      />

      {error && !loading && (
        <Alert severity="error" sx={{ mb: 3 }} action={<Button color="inherit" size="small" onClick={load}>Thử lại</Button>}>
          <AlertTitle>Không tải được dữ liệu</AlertTitle>
          {error.message}
        </Alert>
      )}

      <Grid container spacing={2}>
        {stats.map((s) => (
          <Grid key={s.label} item xs={12} sm={6} md={4} lg={2.4}>
            <StatCard {...s} loading={loading} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2} sx={{ mt: 0.5 }}>
        <Grid item xs={12} md={7}>
          <ChartCard title="Top khu vực theo số tin" loading={loading} empty={!loading && !topProvinces.length}>
            <Bar
              data={provinceChart}
              options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }}
            />
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={5}>
          <ChartCard title="Top danh mục" loading={loading} empty={!loading && !topCategories.length}>
            <Doughnut
              data={categoryChart}
              options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }}
            />
          </ChartCard>
        </Grid>

        <Grid item xs={12}>
          <Card variant="outlined">
            <CardHeader title="Hoạt động cần chú ý" titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} />
            <CardContent sx={{ pt: 0 }}>
              {!loading && !activity.length ? (
                <Box sx={{ py: 3, textAlign: 'center', color: 'text.secondary' }}>Không có cảnh báo nào</Box>
              ) : (
                <List disablePadding>
                  {activity.map((a, i) => (
                    <ListItemButton key={i} onClick={() => a.actionUrl && navigate(a.actionUrl)} sx={{ borderRadius: 1 }}>
                      <ListItemIcon>
                        <CircleNotificationsIcon color={severityColor[a.severity] || 'action'} />
                      </ListItemIcon>
                      <ListItemText primary={a.message} />
                      <Chip size="small" label={a.severity} color={severityColor[a.severity] || 'default'} variant="outlined" />
                    </ListItemButton>
                  ))}
                </List>
              )}
              {loading && <Stack spacing={1}>{[1, 2, 3].map((i) => <Box key={i} sx={{ height: 40, bgcolor: 'action.hover', borderRadius: 1 }} />)}</Stack>}
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default DashboardPage;
