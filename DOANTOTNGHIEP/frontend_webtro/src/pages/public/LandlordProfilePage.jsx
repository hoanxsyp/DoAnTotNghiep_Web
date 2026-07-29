import { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import {
  Container, Grid, Box, Typography, Stack, Button, Paper, Avatar, Divider, Chip,
  Pagination, Tooltip,
} from '@mui/material';
import VerifiedIcon from '@mui/icons-material/Verified';
import PersonAddAltIcon from '@mui/icons-material/PersonAddAlt';
import HowToRegIcon from '@mui/icons-material/HowToReg';
import userApi from '@/api/userApi';
import ListingGrid from '@/components/listing/ListingGrid';
import TrustScoreBadge from '@/components/listing/TrustScoreBadge';
import RatingStars from '@/components/interaction/RatingStars';
import EmptyState from '@/components/common/EmptyState';
import useAuth from '@/hooks/useAuth';
import { notify } from '@/utils/toast';
import { formatDate, compactNumber } from '@/utils/format';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);
const PAGE = 12;

// trustLabel công khai -> level cho TrustScoreBadge.
const labelToLevel = (label) => {
  if (label === 'RUI_RO') return 'RISKY';
  if (label === 'CAN_KIEM_DUYET') return 'NEED_REVIEW';
  return 'NORMAL';
};

const LandlordProfilePage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated } = useAuth();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [following, setFollowing] = useState(false);
  const [followerCount, setFollowerCount] = useState(0);
  const [followBusy, setFollowBusy] = useState(false);

  const [listings, setListings] = useState({ items: [], totalPages: 0 });
  const [listingsLoading, setListingsLoading] = useState(true);
  const [page, setPage] = useState(0);

  useEffect(() => {
    if (!id) return undefined;
    let alive = true;
    setLoading(true);
    setNotFound(false);
    userApi
      .getPublicProfile(id)
      .then((data) => {
        if (!alive) return;
        setProfile(data);
        setFollowing(!!data?.followedByMe);
        setFollowerCount(data?.followerCount ?? 0);
        document.title = `${data?.fullName || 'Chủ trọ'} — Webtro`;
      })
      .catch((err) => {
        if (!alive) return;
        setNotFound(true);
        if (err.status !== 404) notify.apiError(err);
      })
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [id]);

  useEffect(() => {
    if (!id || notFound) return;
    let alive = true;
    setListingsLoading(true);
    userApi
      .getPublicListings(id, { page, size: PAGE, sort: 'publishedAt,desc' })
      .then((data) => {
        if (!alive) return;
        setListings({ items: toItems(data), totalPages: data?.totalPages ?? 1 });
      })
      .catch(() => alive && setListings({ items: [], totalPages: 0 }))
      .finally(() => alive && setListingsLoading(false));
    return () => {
      alive = false;
    };
  }, [id, page, notFound]);

  const handleFollow = async () => {
    if (!isAuthenticated) {
      navigate('/dang-nhap', { state: { from: location.pathname } });
      return;
    }
    const prev = following;
    setFollowBusy(true);
    setFollowing(!prev);
    setFollowerCount((n) => n + (prev ? -1 : 1));
    try {
      if (prev) await userApi.unfollow(id);
      else await userApi.follow(id);
    } catch (err) {
      setFollowing(prev);
      setFollowerCount((n) => n + (prev ? 1 : -1));
      notify.apiError(err);
    } finally {
      setFollowBusy(false);
    }
  };

  if (notFound) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <EmptyState
          title="Không tìm thấy chủ trọ"
          description="Tài khoản này không tồn tại hoặc không còn hoạt động."
          action={
            <Button variant="contained" component={RouterLink} to="/">
              Về trang chủ
            </Button>
          }
        />
      </Container>
    );
  }

  const level = labelToLevel(profile?.trustLabel);

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 2, md: 3 } }}>
      <Grid container spacing={3}>
        {/* Thông tin chủ trọ */}
        <Grid item xs={12} md={4}>
          <Paper variant="outlined" sx={{ p: 3, position: { md: 'sticky' }, top: 88 }}>
            {loading ? (
              <Stack spacing={2} alignItems="center">
                <Avatar sx={{ width: 96, height: 96 }} />
                <Typography color="text.secondary">Đang tải…</Typography>
              </Stack>
            ) : (
              <Stack spacing={1.5} alignItems="center" textAlign="center">
                <Avatar src={profile?.avatarUrl} sx={{ width: 96, height: 96 }}>
                  {profile?.fullName?.charAt(0)}
                </Avatar>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>
                    {profile?.fullName}
                  </Typography>
                  {profile?.verified && (
                    <Tooltip title="Đã xác thực">
                      <VerifiedIcon color="primary" sx={{ fontSize: 20 }} />
                    </Tooltip>
                  )}
                </Stack>

                <TrustScoreBadge score={profile?.trustScore} level={level} variant="badge" />

                {profile?.averageRating != null && profile?.totalReviews > 0 && (
                  <RatingStars value={profile.averageRating} readOnly count={profile.totalReviews} />
                )}

                <Stack direction="row" spacing={2} sx={{ mt: 1 }}>
                  <Box textAlign="center">
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {profile?.totalActiveListings ?? 0}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Tin đang đăng
                    </Typography>
                  </Box>
                  <Divider orientation="vertical" flexItem />
                  <Box textAlign="center">
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>
                      {compactNumber(followerCount)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      Người theo dõi
                    </Typography>
                  </Box>
                  {profile?.responseRatePercent != null && (
                    <>
                      <Divider orientation="vertical" flexItem />
                      <Box textAlign="center">
                        <Typography variant="h6" sx={{ fontWeight: 700 }}>
                          {profile.responseRatePercent}%
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          Tỷ lệ phản hồi
                        </Typography>
                      </Box>
                    </>
                  )}
                </Stack>

                {profile?.memberSince && (
                  <Chip size="small" variant="outlined" label={`Tham gia ${formatDate(profile.memberSince)}`} />
                )}

                <Button
                  variant={following ? 'outlined' : 'contained'}
                  startIcon={following ? <HowToRegIcon /> : <PersonAddAltIcon />}
                  onClick={handleFollow}
                  disabled={followBusy}
                  fullWidth
                  sx={{ mt: 1 }}
                >
                  {following ? 'Đang theo dõi' : 'Theo dõi'}
                </Button>
              </Stack>
            )}
          </Paper>
        </Grid>

        {/* Tin đang đăng */}
        <Grid item xs={12} md={8}>
          <Typography variant="h5" component="h1" sx={{ fontWeight: 700, mb: 2 }}>
            Tin đang đăng
          </Typography>
          <ListingGrid
            items={listings.items}
            loading={listingsLoading}
            skeletonCount={6}
            columns={{ xs: 1, sm: 2, md: 3 }}
            emptyState={
              <EmptyState
                title="Chủ trọ chưa có tin đang hiển thị"
                description="Hãy theo dõi để nhận thông báo khi có tin mới."
              />
            }
          />
          {listings.totalPages > 1 && (
            <Stack alignItems="center" sx={{ mt: 3 }}>
              <Pagination
                count={listings.totalPages}
                page={page + 1}
                onChange={(_, p) => setPage(p - 1)}
                color="primary"
              />
            </Stack>
          )}
        </Grid>
      </Grid>
    </Container>
  );
};

export default LandlordProfilePage;
