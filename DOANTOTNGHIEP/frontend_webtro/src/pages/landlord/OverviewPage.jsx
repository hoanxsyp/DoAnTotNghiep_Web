import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Box, Grid, Card, CardContent, Stack, Typography, Button, Avatar, List, ListItemButton,
  ListItemAvatar, ListItemText, Alert, TextField, MenuItem, Chip, Divider,
} from '@mui/material';
import ArticleIcon from '@mui/icons-material/Article';
import HourglassEmptyIcon from '@mui/icons-material/HourglassEmpty';
import VisibilityIcon from '@mui/icons-material/Visibility';
import ContactPhoneIcon from '@mui/icons-material/ContactPhone';
import AddBoxIcon from '@mui/icons-material/AddBox';
import WarningAmberIcon from '@mui/icons-material/WarningAmber';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';
import userApi from '@/api/userApi';
import StatCard from '@/components/dashboard/StatCard';
import ChartCard from '@/components/dashboard/ChartCard';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const DAYS_OPTIONS = [
  { value: 7, label: '7 ngày' },
  { value: 30, label: '30 ngày' },
  { value: 90, label: '90 ngày' },
];

const OverviewPage = () => {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [days, setDays] = useState(30);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const res = await userApi.getLandlordDashboard({ days });
      setData(res);
    } catch (e) {
      notify.apiError(e, 'Không tải được tổng quan');
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => { load(); }, [load]);

  const isEmpty = data && (data.activeCount + data.pendingCount) === 0 && (data.topListings || []).length === 0;

  const chartData = data?.chart ? {
    labels: data.chart.map((p) => formatDateTime(p.date, 'DD/MM')),
    datasets: [
      { label: 'Lượt xem', data: data.chart.map((p) => p.views), yAxisID: 'y' },
      { label: 'Lượt liên hệ', data: data.chart.map((p) => p.contacts), yAxisID: 'y' },
    ],
  } : null;

  return (
    <Box>
      <PageHeader
        title="Tổng quan"
        subtitle="Hiệu quả tin đăng của bạn"
        action={(
          <TextField select size="small" value={days} onChange={(e) => setDays(Number(e.target.value))} sx={{ minWidth: 140 }}>
            {DAYS_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
          </TextField>
        )}
      />

      {data?.landlordVerificationStatus && data.landlordVerificationStatus !== 'VERIFIED' && (
        <Alert severity="warning" sx={{ mb: 3 }} action={<Button size="small" component={RouterLink} to="/quan-ly/ho-so-chu-tro">Xác thực ngay</Button>}>
          Tài khoản chủ trọ chưa được xác thực. Xác thực để tăng độ tin cậy với người thuê.
        </Alert>
      )}

      {isEmpty && !loading ? (
        <EmptyState
          title="Bạn chưa có tin đăng nào"
          description="Đăng tin đầu tiên để bắt đầu tiếp cận người thuê."
          action={<Button variant="contained" startIcon={<AddBoxIcon />} onClick={() => navigate('/quan-ly/tin-dang/tao')}>Đăng tin ngay</Button>}
        />
      ) : (
        <>
          {/* Stat cards */}
          <Grid container spacing={2} sx={{ mb: 3 }}>
            <Grid item xs={6} md={3}>
              <StatCard loading={loading} label="Tin đang hiển thị" value={data?.activeCount ?? 0}
                icon={<ArticleIcon />} color="success" delta={data?.deltas?.activeCount}
                deltaSuffix="" onClick={() => navigate('/quan-ly/tin-dang?status=ACTIVE')} />
            </Grid>
            <Grid item xs={6} md={3}>
              <StatCard loading={loading} label="Chờ duyệt" value={data?.pendingCount ?? 0}
                icon={<HourglassEmptyIcon />} color="warning" delta={data?.deltas?.pendingCount}
                deltaSuffix="" onClick={() => navigate('/quan-ly/tin-dang?status=PENDING')} />
            </Grid>
            <Grid item xs={6} md={3}>
              <StatCard loading={loading} label={`Lượt xem (${days} ngày)`} value={data?.viewCount30d ?? 0}
                icon={<VisibilityIcon />} color="info" delta={data?.deltas?.viewCountPercent} deltaLabel="so với kỳ trước" />
            </Grid>
            <Grid item xs={6} md={3}>
              <StatCard loading={loading} label={`Lượt liên hệ (${days} ngày)`} value={data?.contactCount30d ?? 0}
                icon={<ContactPhoneIcon />} color="primary" delta={data?.deltas?.contactCountPercent} deltaLabel="so với kỳ trước" />
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            {/* Chart */}
            <Grid item xs={12} lg={8}>
              <ChartCard
                title="Xu hướng lượt xem & liên hệ"
                subtitle={`${days} ngày gần nhất`}
                type="line"
                loading={loading}
                data={chartData}
                height={300}
              />
            </Grid>

            {/* Action items */}
            <Grid item xs={12} lg={4}>
              <Card sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 1.5 }}>Việc cần xử lý</Typography>
                  {loading ? <LoadingSkeleton variant="list-item" count={4} /> : (
                    <Stack spacing={1}>
                      {(data?.actionItems || []).filter((a) => a.count > 0).length === 0 ? (
                        <Typography variant="body2" color="text.secondary">Không có việc gì cần xử lý. Tốt lắm!</Typography>
                      ) : (
                        (data?.actionItems || []).filter((a) => a.count > 0).map((a) => (
                          <Alert
                            key={a.type}
                            severity={a.severity === 'ERROR' ? 'error' : 'warning'}
                            icon={a.severity === 'ERROR' ? <ErrorOutlineIcon /> : <WarningAmberIcon />}
                            action={<Button size="small" component={RouterLink} to={a.actionUrl}>Xem</Button>}
                          >
                            {a.message}
                          </Alert>
                        ))
                      )}
                    </Stack>
                  )}
                </CardContent>
              </Card>
            </Grid>

            {/* Top listings */}
            <Grid item xs={12}>
              <Card>
                <CardContent>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>Tin đăng nổi bật</Typography>
                  {loading ? <LoadingSkeleton variant="list-item" count={3} /> : (data?.topListings || []).length === 0 ? (
                    <Typography variant="body2" color="text.secondary">Chưa có dữ liệu.</Typography>
                  ) : (
                    <List disablePadding>
                      {data.topListings.map((l, i) => (
                        <Box key={l.id}>
                          <ListItemButton component={RouterLink} to={`/quan-ly/tin-dang/${l.id}/thong-ke`}>
                            <ListItemAvatar><Avatar variant="rounded" src={l.thumbnailUrl} sx={{ width: 56, height: 42 }} /></ListItemAvatar>
                            <ListItemText
                              primary={<Typography variant="subtitle2" sx={{ fontWeight: 600 }} noWrap>{l.title}</Typography>}
                              secondary={(
                                <Stack direction="row" spacing={1.5} component="span">
                                  <Chip size="small" variant="outlined" icon={<VisibilityIcon />} label={l.viewCount} />
                                  <Chip size="small" variant="outlined" icon={<ContactPhoneIcon />} label={l.contactCount} />
                                </Stack>
                              )}
                            />
                          </ListItemButton>
                          {i < data.topListings.length - 1 && <Divider component="li" />}
                        </Box>
                      ))}
                    </List>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </>
      )}
    </Box>
  );
};

export default OverviewPage;
