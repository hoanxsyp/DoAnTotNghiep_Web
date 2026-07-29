import { useState, useEffect, useCallback } from 'react';
import {
  Box, Grid, Stack, TextField, MenuItem, Button, Alert, useTheme,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { Line, Bar } from 'react-chartjs-2';
import dayjs from 'dayjs';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import { formatCurrency, compactNumber } from '@/utils/format';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatCard from '@/components/admin/StatCard';
import ChartCard from '@/components/admin/ChartCard';

const GRANULARITY = [
  { value: 'DAY', label: 'Theo ngày' },
  { value: 'WEEK', label: 'Theo tuần' },
  { value: 'MONTH', label: 'Theo tháng' },
];

/**
 * Thống kê chi tiết (docs/04 §10.1, API 4.12.2/4.12.3). Chuỗi thời gian người dùng/tin/doanh thu +
 * tỷ lệ duyệt, tỷ lệ thuê thành công. Quyền STATISTIC_VIEW (chỉ Admin).
 */
const StatisticsPage = () => {
  const theme = useTheme();
  const [from, setFrom] = useState(dayjs().subtract(30, 'day').format('YYYY-MM-DD'));
  const [to, setTo] = useState(dayjs().format('YYYY-MM-DD'));
  const [granularity, setGranularity] = useState('DAY');
  const [stats, setStats] = useState(null);
  const [revenue, setRevenue] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    const params = { from, to, granularity };
    Promise.all([adminApi.getStatistics(params), adminApi.getRevenue(params)])
      .then(([s, r]) => { setStats(s); setRevenue(r); })
      .catch((err) => { setError(err); notify.apiError(err); })
      .finally(() => setLoading(false));
  }, [from, to, granularity]);

  useEffect(() => { load(); }, [load]);

  const series = stats?.series || [];
  const totals = stats?.totals || {};
  const rates = stats?.rates || {};
  const labels = series.map((p) => p.date);

  const activityChart = {
    labels,
    datasets: [
      { label: 'Người dùng mới', data: series.map((p) => p.newUsers), borderColor: theme.palette.primary.main, backgroundColor: 'transparent', tension: 0.3 },
      { label: 'Tin mới', data: series.map((p) => p.newListings), borderColor: theme.palette.info.main, backgroundColor: 'transparent', tension: 0.3 },
      { label: 'Tin duyệt', data: series.map((p) => p.approvedListings), borderColor: theme.palette.success.main, backgroundColor: 'transparent', tension: 0.3 },
    ],
  };
  const revenueSeries = revenue?.series || [];
  const revenueChart = {
    labels: revenueSeries.map((p) => p.date),
    datasets: [{ label: 'Doanh thu', data: revenueSeries.map((p) => p.revenue), backgroundColor: theme.palette.primary.main, borderRadius: 4 }],
  };

  const statCards = [
    { label: 'Người dùng mới', value: compactNumber(totals.newUsers), color: 'primary' },
    { label: 'Tin mới', value: compactNumber(totals.newListings), color: 'info' },
    { label: 'Tỷ lệ duyệt', value: `${rates.approvalRatePercent ?? 0}%`, color: 'success' },
    { label: 'Tỷ lệ thuê thành công', value: `${rates.successfulRentalRatePercent ?? 0}%`, color: 'warning' },
    { label: 'Doanh thu', value: formatCurrency(totals.revenue), color: 'primary' },
    { label: 'Lượt liên hệ', value: compactNumber(totals.contacts), color: 'info' },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Thống kê"
        subtitle="Phân tích theo khoảng thời gian"
        actions={<Button startIcon={<RefreshIcon />} onClick={load} disabled={loading}>Làm mới</Button>}
      />

      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 3 }} alignItems={{ sm: 'center' }}>
        <TextField size="small" type="date" label="Từ ngày" InputLabelProps={{ shrink: true }} value={from} onChange={(e) => setFrom(e.target.value)} />
        <TextField size="small" type="date" label="Đến ngày" InputLabelProps={{ shrink: true }} value={to} onChange={(e) => setTo(e.target.value)} />
        <TextField select size="small" label="Mức độ" value={granularity} onChange={(e) => setGranularity(e.target.value)} sx={{ minWidth: 150 }}>
          {GRANULARITY.map((g) => <MenuItem key={g.value} value={g.value}>{g.label}</MenuItem>)}
        </TextField>
        <Button variant="contained" onClick={load} disabled={loading}>Xem</Button>
      </Stack>

      {error && !loading && (
        <Alert severity="error" sx={{ mb: 3 }} action={<Button color="inherit" size="small" onClick={load}>Thử lại</Button>}>
          {error.message}
        </Alert>
      )}

      <Grid container spacing={2} sx={{ mb: 1 }}>
        {statCards.map((c) => (
          <Grid key={c.label} item xs={6} sm={4} md={2}>
            <StatCard {...c} loading={loading} />
          </Grid>
        ))}
      </Grid>

      <Grid container spacing={2}>
        <Grid item xs={12} md={7}>
          <ChartCard title="Hoạt động theo thời gian" loading={loading} empty={!loading && !series.length}>
            <Line data={activityChart} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { position: 'bottom' } } }} />
          </ChartCard>
        </Grid>
        <Grid item xs={12} md={5}>
          <ChartCard title="Doanh thu" loading={loading} empty={!loading && !revenueSeries.length}>
            <Bar data={revenueChart} options={{ responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false } } }} />
          </ChartCard>
        </Grid>
      </Grid>
    </Box>
  );
};

export default StatisticsPage;
