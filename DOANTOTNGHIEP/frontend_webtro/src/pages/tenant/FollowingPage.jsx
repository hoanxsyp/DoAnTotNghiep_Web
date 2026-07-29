import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import {
  Box, Grid, Card, CardContent, Stack, Avatar, Typography, Chip, Button, Pagination, Badge,
} from '@mui/material';
import VerifiedIcon from '@mui/icons-material/Verified';
import StarIcon from '@mui/icons-material/Star';
import userApi from '@/api/userApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import notify from '@/utils/toast';
import { formatDate } from '@/utils/format';

const SIZE = 12;

const FollowingPage = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [unfollowing, setUnfollowing] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await userApi.getFollowing({ page, size: SIZE, sort: 'createdAt,desc' });
      setItems(data?.items || []);
      setTotalPages(data?.totalPages || 0);
    } catch (e) {
      notify.apiError(e, 'Không tải được danh sách theo dõi');
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => { load(); }, [load]);

  const handleUnfollow = async () => {
    const l = unfollowing;
    setUnfollowing(null);
    if (!l) return;
    try {
      await userApi.unfollow(l.landlordId);
      notify.success(`Đã bỏ theo dõi ${l.fullName}`);
      setItems((prev) => prev.filter((i) => i.landlordId !== l.landlordId));
    } catch (e) {
      notify.apiError(e, 'Bỏ theo dõi thất bại');
    }
  };

  return (
    <Box>
      <PageHeader title="Chủ trọ đang theo dõi" subtitle="Nhận thông báo khi các chủ trọ này đăng tin mới" />

      {loading ? (
        <Grid container spacing={2}>
          {Array.from({ length: 6 }).map((_, i) => (
            <Grid item xs={12} sm={6} md={4} key={i}><Card sx={{ p: 2 }}><LoadingSkeleton variant="list-item" /></Card></Grid>
          ))}
        </Grid>
      ) : items.length === 0 ? (
        <EmptyState
          title="Bạn chưa theo dõi chủ trọ nào"
          description="Theo dõi chủ trọ uy tín để không bỏ lỡ tin đăng mới của họ."
          action={<Button variant="contained" onClick={() => navigate('/tim-kiem')}>Khám phá tin đăng</Button>}
        />
      ) : (
        <>
          <Grid container spacing={2}>
            {items.map((l) => (
              <Grid item xs={12} sm={6} md={4} key={l.landlordId}>
                <Card sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack direction="row" spacing={2} alignItems="center">
                      <Badge
                        color="error" overlap="circular" badgeContent={l.newListingCountSinceLastVisit || 0}
                        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
                      >
                        <Avatar src={l.avatarUrl} sx={{ width: 56, height: 56 }}>{l.fullName?.charAt(0)}</Avatar>
                      </Badge>
                      <Box sx={{ minWidth: 0, flex: 1 }}>
                        <Stack direction="row" spacing={0.5} alignItems="center">
                          <Typography
                            component={RouterLink} to={`/chu-tro/${l.landlordId}`}
                            variant="subtitle1" sx={{ fontWeight: 700, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }} noWrap
                          >
                            {l.fullName}
                          </Typography>
                          {l.verified && <VerifiedIcon color="success" sx={{ fontSize: 18 }} />}
                        </Stack>
                        <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                          <Chip size="small" variant="outlined" label={`Uy tín ${l.trustScore ?? '—'}`} />
                          {l.averageRating != null && (
                            <Chip size="small" variant="outlined" icon={<StarIcon />} label={`${l.averageRating}`} />
                          )}
                        </Stack>
                      </Box>
                    </Stack>

                    <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mt: 2 }}>
                      <Typography variant="caption" color="text.secondary">
                        {l.activeListingCount ?? 0} tin đang hiển thị · Theo dõi từ {formatDate(l.followedAt)}
                      </Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} sx={{ mt: 1.5 }}>
                      <Button size="small" variant="outlined" fullWidth component={RouterLink} to={`/chu-tro/${l.landlordId}`}>Xem hồ sơ</Button>
                      <Button size="small" color="error" onClick={() => setUnfollowing(l)}>Bỏ theo dõi</Button>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            ))}
          </Grid>
          {totalPages > 1 && (
            <Stack alignItems="center" sx={{ mt: 3 }}>
              <Pagination count={totalPages} page={page + 1} onChange={(_, p) => setPage(p - 1)} color="primary" />
            </Stack>
          )}
        </>
      )}

      <ConfirmDialog
        open={!!unfollowing}
        title="Bỏ theo dõi"
        message={`Bỏ theo dõi chủ trọ ${unfollowing?.fullName || ''}?`}
        confirmText="Bỏ theo dõi"
        severity="warning"
        onConfirm={handleUnfollow}
        onCancel={() => setUnfollowing(null)}
      />
    </Box>
  );
};

export default FollowingPage;
