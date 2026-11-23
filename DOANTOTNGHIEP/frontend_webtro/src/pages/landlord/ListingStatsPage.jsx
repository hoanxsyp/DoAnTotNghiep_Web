import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  Box, Grid, Card, CardContent, Stack, Typography, Button, Chip, Divider,
} from '@mui/material';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import VisibilityIcon from '@mui/icons-material/Visibility';
import BookmarkIcon from '@mui/icons-material/Bookmark';
import PeopleIcon from '@mui/icons-material/People';
import listingApi from '@/api/listingApi';
import StatCard from '@/components/dashboard/StatCard';
import ChartCard from '@/components/dashboard/ChartCard';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import StatusChip from '@/components/common/StatusChip';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const PERF_LABEL = {
  DUOI_TRUNG_BINH: { label: 'Dưới trung bình khu vực', color: 'warning' },
  TRUNG_BINH: { label: 'Ngang trung bình khu vực', color: 'info' },
  TREN_TRUNG_BINH: { label: 'Trên trung bình khu vực', color: 'success' },
};

const ListingStatsPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    listingApi.getStats(id)
      .then((d) => { if (alive) setData(d); })
      .catch((e) => { if (alive) { setError(true); notify.apiError(e, 'Không tải được thống kê'); } })
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [id]);

  if (loading) {
    return <Box><PageHeader title="Thống kê tin" /><LoadingSkeleton variant="chart" /></Box>;
  }
  if (error || !data) {
    return <Box><PageHeader title="Thống kê tin" /><EmptyState title="Không có dữ liệu thống kê" action={<Button variant="contained" onClick={() => navigate('/quan-ly/tin-dang')}>Về danh sách tin</Button>} /></Box>;
  }

  const s = data.summary || {};
  const perf = data.comparison?.performanceLabel ? PERF_LABEL[data.comparison.performanceLabel] : null;

  const seriesData = data.timeSeries ? {
    labels: data.timeSeries.map((p) => formatDateTime(p.date, 'DD/MM')),
    datasets: [
      { label: 'Lượt xem', data: data.timeSeries.map((p) => p.views) },
      { label: 'Lượt lưu', data: data.timeSeries.map((p) => p.favorites) },
    ],
  } : null;

  return (
    <Box>
      <PageHeader
        title="Thống kê tin đăng"
        subtitle={data.title}
        action={<Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/quan-ly/tin-dang')}>Về danh sách</Button>}
      />

      <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }} alignItems="center">
        {data.status && <StatusChip status={data.status} />}
        {perf && <Chip color={perf.color} label={perf.label} />}
        {s.daysRemaining != null && <Chip variant="outlined" label={`Còn ${s.daysRemaining} ngày hiển thị`} />}
      </Stack>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        <Grid item xs={12} sm={4}><StatCard label="Lượt xem" value={s.viewCount ?? 0} icon={<VisibilityIcon />} color="info" hint={`${s.uniqueViewerCount ?? 0} người xem`} /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="Lượt lưu" value={s.favoriteCount ?? 0} icon={<BookmarkIcon />} color="secondary" /></Grid>
        <Grid item xs={12} sm={4}><StatCard label="Đánh giá" value={s.averageRating != null ? `${s.averageRating}/5` : '—'} icon={<PeopleIcon />} color="success" hint={`${s.reviewCount ?? 0} đánh giá`} /></Grid>
      </Grid>

      <Grid container spacing={3}>
        <Grid item xs={12}>
          <ChartCard title="Diễn biến theo thời gian" type="line" data={seriesData} height={320} />
        </Grid>

        {data.comparison && (
          <Grid item xs={12}>
            <Card>
              <CardContent>
                <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>So sánh với khu vực</Typography>
                <Divider sx={{ mb: 2 }} />
                <Grid container spacing={2}>
                  <Grid item xs={6} md={3}>
                    <Typography variant="caption" color="text.secondary">Lượt xem TB khu vực</Typography>
                    <Typography variant="h6">{data.comparison.averageViewsInDistrict ?? '—'}</Typography>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        )}
      </Grid>
    </Box>
  );
};

export default ListingStatsPage;
