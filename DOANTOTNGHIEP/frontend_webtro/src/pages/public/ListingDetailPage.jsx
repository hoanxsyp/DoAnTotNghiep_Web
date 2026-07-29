import { useCallback, useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import {
  Container, Grid, Box, Typography, Stack, Chip, Button, Paper, Divider, Avatar, Breadcrumbs,
  Link, IconButton, Alert, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  MenuItem, Rating, LinearProgress, Tooltip,
} from '@mui/material';
import PhoneIcon from '@mui/icons-material/Phone';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import VerifiedIcon from '@mui/icons-material/Verified';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import listingApi from '@/api/listingApi';
import commentApi from '@/api/commentApi';
import reviewApi from '@/api/reviewApi';
import favoriteApi from '@/api/favoriteApi';
import contactApi from '@/api/contactApi';
import reportApi from '@/api/reportApi';
import ListingGrid from '@/components/listing/ListingGrid';
import CommentThread from '@/components/interaction/CommentThread';
import RatingStars from '@/components/interaction/RatingStars';
import TrustScoreBadge from '@/components/listing/TrustScoreBadge';
import RichTextViewer from '@/components/common/RichTextViewer';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import useAuth from '@/hooks/useAuth';
import { notify } from '@/utils/toast';
import {
  formatPrice, formatCurrency, formatArea, formatDate, fromNow, compactNumber,
} from '@/utils/format';
import {
  REPORT_REASONS, GENDER_REQUIREMENT, FURNITURE_STATUS,
} from '@/constants';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);

const AMENITY_GROUP_LABEL = {
  FURNITURE: 'Nội thất',
  SECURITY: 'An ninh',
  UTILITY: 'Sinh hoạt',
  TRANSPORT: 'Giao thông',
};

const TOILET_LABEL = { PRIVATE: 'Riêng', SHARED: 'Chung' };
const CURFEW_LABEL = { FREE: 'Tự do', CURFEW: 'Có giờ giấc', UNKNOWN: 'Chưa rõ' };

const mapComment = (c) => ({
  id: c.id,
  userId: c.author?.id,
  userName: c.author?.fullName,
  userAvatar: c.author?.avatarUrl,
  content: c.content,
  createdAt: c.createdAt,
  editedAt: c.editedAt,
  canEdit: c.editable,
  canDelete: c.deletable,
  parentId: c.parentCommentId,
  sentimentLabel: c.sentimentLabel,
  sentimentScore: c.sentimentScore,
  replies: (c.replies || []).map(mapComment),
});

/** Ô thông số nhỏ trong lưới đặc điểm. */
const SpecCell = ({ label, value }) => (
  <Grid item xs={6} sm={3}>
    <Paper variant="outlined" sx={{ p: 1.25, height: '100%' }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 600 }}>
        {value ?? '—'}
      </Typography>
    </Paper>
  </Grid>
);

const ListingDetailPage = () => {
  const { slugId } = useParams();
  const id = useMemo(() => slugId?.split('-').pop(), [slugId]);
  const navigate = useNavigate();
  const location = useLocation();
  const { isAuthenticated, user } = useAuth();

  const [listing, setListing] = useState(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);

  const [galleryIndex, setGalleryIndex] = useState(0);
  const [favorited, setFavorited] = useState(false);
  const [revealedPhone, setRevealedPhone] = useState(null);

  const [comments, setComments] = useState([]);
  const [commentsLoading, setCommentsLoading] = useState(true);

  const [reviews, setReviews] = useState({ items: [], summary: null });
  const [reviewsLoading, setReviewsLoading] = useState(true);

  const [related, setRelated] = useState([]);

  const [loginPrompt, setLoginPrompt] = useState(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportReason, setReportReason] = useState('');
  const [reportDesc, setReportDesc] = useState('');
  const [reportSubmitting, setReportSubmitting] = useState(false);

  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);

  const requireLogin = useCallback(
    (message) => {
      setLoginPrompt({ message });
    },
    [],
  );

  const goLogin = () =>
    navigate('/dang-nhap', { state: { from: location.pathname + location.search } });

  // Tải chi tiết tin
  useEffect(() => {
    if (!id) return undefined;
    let alive = true;
    setLoading(true);
    setNotFound(false);
    setGalleryIndex(0);
    listingApi
      .getDetail(id)
      .then((data) => {
        if (!alive) return;
        setListing(data);
        setFavorited(!!data?.favoritedByMe);
        document.title = `${data?.title || 'Tin đăng'} — Webtro`;
      })
      .catch((err) => {
        if (!alive) return;
        // 404/403 đều hiện 404 để không lộ sự tồn tại của tin non-public.
        setNotFound(true);
        if (err.status !== 404 && err.status !== 403) notify.apiError(err);
      })
      .finally(() => alive && setLoading(false));
    return () => {
      alive = false;
    };
  }, [id]);

  // Bình luận + đánh giá + tin liên quan (tải sau, không chặn nội dung chính)
  const loadComments = useCallback(() => {
    setCommentsLoading(true);
    commentApi
      .getComments(id, { page: 0, size: 20 })
      .then((data) => setComments(toItems(data).map(mapComment)))
      .catch(() => setComments([]))
      .finally(() => setCommentsLoading(false));
  }, [id]);

  useEffect(() => {
    if (!id || notFound) return;
    loadComments();

    setReviewsLoading(true);
    reviewApi
      .getReviews(id, { page: 0, size: 5 })
      .then((data) => setReviews({ items: toItems(data), summary: data?.summary || null }))
      .catch(() => setReviews({ items: [], summary: null }))
      .finally(() => setReviewsLoading(false));

    listingApi
      .getRelated(id)
      .then((data) => setRelated(toItems(data)))
      .catch(() => setRelated([]));
  }, [id, notFound, loadComments]);

  const handleToggleFavorite = async () => {
    if (!isAuthenticated) {
      requireLogin('Đăng nhập để lưu tin vào danh sách của bạn.');
      return;
    }
    const prev = favorited;
    setFavorited(!prev);
    try {
      if (prev) await favoriteApi.remove(id);
      else await favoriteApi.add(id);
      notify.success(prev ? 'Đã bỏ lưu tin.' : 'Đã lưu tin vào danh sách của bạn.');
    } catch (err) {
      setFavorited(prev);
      notify.apiError(err);
    }
  };

  const handleRevealPhone = async () => {
    if (!isAuthenticated) {
      requireLogin('Đăng nhập để xem số điện thoại của chủ trọ.');
      return;
    }
    try {
      const data = await contactApi.contactListing(id, { type: 'VIEW_PHONE' });
      setRevealedPhone(data?.phone || data?.contactPhone || null);
    } catch (err) {
      notify.apiError(err);
    }
  };

  const handleMessage = async () => {
    if (!isAuthenticated) {
      requireLogin('Đăng nhập để nhắn tin cho chủ trọ.');
      return;
    }
    try {
      const data = await contactApi.createConversation({ listingId: Number(id) });
      const convId = data?.id || data?.conversationId;
      navigate(`/tai-khoan/tin-nhan${convId ? `/${convId}` : ''}`);
    } catch (err) {
      notify.apiError(err);
    }
  };

  const openReport = () => {
    if (!isAuthenticated) {
      requireLogin('Đăng nhập để báo cáo tin này.');
      return;
    }
    setReportReason('');
    setReportDesc('');
    setReportOpen(true);
  };

  const submitReport = async () => {
    setReportSubmitting(true);
    try {
      await reportApi.create({
        targetType: 'LISTING',
        targetId: Number(id),
        reason: reportReason,
        description: reportDesc.trim(),
      });
      notify.success('Đã gửi báo cáo. Cảm ơn bạn đã góp phần giữ cộng đồng an toàn.');
      setReportOpen(false);
    } catch (err) {
      if (err.errorCode === 'REPORT_CONFLICT') {
        notify.warning('Bạn đã báo cáo nội dung này với lý do tương tự.');
      } else {
        notify.apiError(err);
      }
    } finally {
      setReportSubmitting(false);
    }
  };

  // Comment handlers
  const handleCreateComment = async (content) => {
    try {
      await commentApi.create(id, { content });
      loadComments();
      return true;
    } catch (err) {
      notify.apiError(err);
      return false;
    }
  };
  const handleReply = async (parentId, content) => {
    try {
      await commentApi.reply(parentId, { content });
      loadComments();
      return true;
    } catch (err) {
      notify.apiError(err);
      return false;
    }
  };
  const handleEditComment = async (commentId, content) => {
    try {
      await commentApi.update(commentId, { content });
      loadComments();
      return true;
    } catch (err) {
      notify.apiError(err);
      return false;
    }
  };
  const handleDeleteComment = async (commentId) => {
    try {
      await commentApi.remove(commentId);
      loadComments();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const submitReview = async () => {
    setReviewSubmitting(true);
    try {
      await reviewApi.create(id, { rating: reviewRating, content: reviewContent.trim() });
      notify.success('Cảm ơn bạn đã đánh giá.');
      setReviewOpen(false);
      setReviewContent('');
      const data = await reviewApi.getReviews(id, { page: 0, size: 5 });
      setReviews({ items: toItems(data), summary: data?.summary || null });
    } catch (err) {
      notify.apiError(err);
    } finally {
      setReviewSubmitting(false);
    }
  };

  if (loading) {
    return (
      <Container maxWidth="lg" sx={{ py: 3 }}>
        <LoadingSkeleton variant="listing-detail" />
      </Container>
    );
  }

  if (notFound || !listing) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <EmptyState
          title="Tin đăng không tồn tại hoặc đã bị gỡ"
          description="Tin bạn tìm có thể đã đóng, hết hạn hoặc bị xóa."
          action={
            <Button variant="contained" component={RouterLink} to="/tim-kiem">
              Tìm tin khác
            </Button>
          }
        />
      </Container>
    );
  }

  const {
    title, price, depositAmount, area, description, fullAddress, shortAddress,
    provinceName, districtName, wardName, addressDetail, images = [], amenities = [],
    landlord, contactName, contactPhone, trustScore, trustLevel,
    viewCount, favoriteCount, commentCount, latitude, longitude, electricityPrice, waterPrice,
    roomCount, toiletCount, currentOccupants, maxOccupants, availableFrom, furnitureStatus,
    curfewType, toiletType, petAllowed, parkingAvailable, genderRequirement, categoryCode,
    canReview, reviewedByMe, publishedAt,
  } = listing;

  const gallery = images.length ? images : [];
  const currentImage = gallery[galleryIndex];
  const displayAddress = fullAddress || [addressDetail, wardName, districtName, provinceName].filter(Boolean).join(', ') || shortAddress;
  const landlordId = landlord?.id;

  const amenityGroups = amenities.reduce((acc, a) => {
    const g = a.group || 'UTILITY';
    (acc[g] = acc[g] || []).push(a);
    return acc;
  }, {});

  const dist = reviews.summary?.distribution || {};
  const totalReviews = reviews.summary?.totalReviews ?? reviews.items.length;
  const avgRating = reviews.summary?.averageRating ?? 0;

  const phoneToShow = revealedPhone || contactPhone;

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 2, md: 3 } }}>
      <Breadcrumbs sx={{ mb: 2 }}>
        <Link component={RouterLink} to="/" underline="hover" color="inherit">
          Trang chủ
        </Link>
        <Link component={RouterLink} to="/tim-kiem" underline="hover" color="inherit">
          Tìm kiếm
        </Link>
        <Typography color="text.primary" noWrap sx={{ maxWidth: 260 }}>
          {title}
        </Typography>
      </Breadcrumbs>

      <Grid container spacing={3}>
        {/* CỘT TRÁI */}
        <Grid item xs={12} md={8}>
          {/* Gallery */}
          <Box
            sx={{
              width: '100%',
              aspectRatio: '16 / 9',
              bgcolor: 'action.hover',
              borderRadius: 2,
              overflow: 'hidden',
            }}
          >
            {currentImage ? (
              <Box
                component="img"
                src={currentImage.url || currentImage.thumbnailUrl}
                alt={title}
                loading="eager"
                sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
              />
            ) : (
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: 'text.disabled' }}>
                Chưa có ảnh
              </Box>
            )}
          </Box>
          {gallery.length > 1 && (
            <Stack direction="row" spacing={1} sx={{ mt: 1, overflowX: 'auto', pb: 1 }}>
              {gallery.map((img, i) => (
                <Box
                  // eslint-disable-next-line react/no-array-index-key
                  key={img.id ?? i}
                  component="img"
                  src={img.thumbnailUrl || img.url}
                  alt={`Ảnh ${i + 1}`}
                  onClick={() => setGalleryIndex(i)}
                  sx={{
                    width: 88,
                    height: 64,
                    objectFit: 'cover',
                    borderRadius: 1,
                    cursor: 'pointer',
                    flexShrink: 0,
                    border: 2,
                    borderColor: i === galleryIndex ? 'primary.main' : 'transparent',
                  }}
                />
              ))}
            </Stack>
          )}

          {/* Cảnh báo uy tín thấp */}
          {trustLevel && trustLevel !== 'NORMAL' && (
            <Box sx={{ mt: 2 }}>
              <TrustScoreBadge score={trustScore} level={trustLevel} variant="alert" />
            </Box>
          )}

          <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 2 }}>
            {title}
          </Typography>
          <Stack direction="row" spacing={1} alignItems="center" sx={{ mt: 1, color: 'text.secondary', flexWrap: 'wrap' }}>
            <PlaceOutlinedIcon sx={{ fontSize: 18 }} />
            <Typography variant="body2">{displayAddress}</Typography>
          </Stack>
          <Stack direction="row" spacing={2} sx={{ mt: 1, color: 'text.secondary' }}>
            <Typography variant="caption">{fromNow(publishedAt)}</Typography>
            <Stack direction="row" spacing={0.5} alignItems="center">
              <VisibilityOutlinedIcon sx={{ fontSize: 16 }} />
              <Typography variant="caption">{compactNumber(viewCount)} lượt xem</Typography>
            </Stack>
            {favoriteCount != null && (
              <Typography variant="caption">♥ {compactNumber(favoriteCount)} lượt lưu</Typography>
            )}
          </Stack>

          {/* Lưới thông số */}
          <Grid container spacing={1} sx={{ mt: 1.5 }}>
            <SpecCell label="Giá thuê" value={formatPrice(price)} />
            <SpecCell label="Diện tích" value={formatArea(area)} />
            <SpecCell label="Số phòng" value={roomCount} />
            <SpecCell label="Nhà vệ sinh" value={toiletType ? TOILET_LABEL[toiletType] : toiletCount} />
            <SpecCell label="Nội thất" value={FURNITURE_STATUS[furnitureStatus]} />
            <SpecCell label="Số người tối đa" value={maxOccupants} />
            <SpecCell label="Giờ giấc" value={CURFEW_LABEL[curfewType]} />
            <SpecCell label="Thú cưng" value={petAllowed ? 'Cho phép' : 'Không'} />
            <SpecCell label="Chỗ để xe" value={parkingAvailable ? 'Có' : 'Không'} />
            <SpecCell label="Tiền điện" value={electricityPrice ? `${formatCurrency(electricityPrice)}/kWh` : '—'} />
            <SpecCell label="Tiền nước" value={waterPrice ? formatCurrency(waterPrice) : '—'} />
            <SpecCell label="Vào ở từ" value={availableFrom ? formatDate(availableFrom) : '—'} />
            {categoryCode === 'ROOMMATE' && (
              <SpecCell label="Yêu cầu giới tính" value={GENDER_REQUIREMENT[genderRequirement]} />
            )}
            {currentOccupants != null && <SpecCell label="Đang ở" value={currentOccupants} />}
            {depositAmount != null && <SpecCell label="Tiền cọc" value={formatCurrency(depositAmount)} />}
          </Grid>

          {/* Mô tả */}
          <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
            Mô tả
          </Typography>
          {description ? (
            <RichTextViewer content={description} maxLines={10} expandable />
          ) : (
            <Typography color="text.secondary">Chưa có mô tả.</Typography>
          )}

          {/* Tiện ích */}
          {amenities.length > 0 && (
            <>
              <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
                Tiện ích
              </Typography>
              <Stack spacing={1.5}>
                {Object.entries(amenityGroups).map(([group, list]) => (
                  <Box key={group}>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 0.5 }}>
                      {AMENITY_GROUP_LABEL[group] || 'Khác'}
                    </Typography>
                    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                      {list.map((a) => (
                        <Chip key={a.id} label={a.name} variant="outlined" size="small" />
                      ))}
                    </Stack>
                  </Box>
                ))}
              </Stack>
            </>
          )}

          {/* Vị trí */}
          {latitude != null && longitude != null && (
            <>
              <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
                Vị trí
              </Typography>
              <Box
                component="iframe"
                title="Bản đồ vị trí"
                src={`https://www.openstreetmap.org/export/embed.html?bbox=${longitude - 0.008}%2C${latitude - 0.006}%2C${longitude + 0.008}%2C${latitude + 0.006}&layer=mapnik&marker=${latitude}%2C${longitude}`}
                sx={{ width: '100%', height: 300, border: 0, borderRadius: 2 }}
                loading="lazy"
              />
            </>
          )}

          {/* Đánh giá */}
          <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
            Đánh giá {totalReviews > 0 ? `(${totalReviews})` : ''}
          </Typography>
          {reviewsLoading ? (
            <LoadingSkeleton variant="comment" count={2} />
          ) : totalReviews === 0 ? (
            <EmptyState size="sm" title="Chưa có đánh giá nào cho tin này" />
          ) : (
            <Box>
              <Stack direction="row" spacing={2} alignItems="center">
                <Typography variant="h4" sx={{ fontWeight: 800 }}>
                  {Number(avgRating).toFixed(1).replace('.', ',')}
                </Typography>
                <Box sx={{ flex: 1, maxWidth: 320 }}>
                  <RatingStars value={avgRating} readOnly count={totalReviews} />
                  {[5, 4, 3, 2, 1].map((star) => {
                    const n = dist[star] || dist[String(star)] || 0;
                    const pct = totalReviews ? (n / totalReviews) * 100 : 0;
                    return (
                      <Stack key={star} direction="row" spacing={1} alignItems="center">
                        <Typography variant="caption" sx={{ width: 24 }}>
                          {star}★
                        </Typography>
                        <LinearProgress
                          variant="determinate"
                          value={pct}
                          sx={{ flex: 1, height: 6, borderRadius: 1 }}
                        />
                        <Typography variant="caption" sx={{ width: 24 }}>
                          {n}
                        </Typography>
                      </Stack>
                    );
                  })}
                </Box>
              </Stack>

              <Stack divider={<Divider flexItem />} sx={{ mt: 2 }}>
                {reviews.items.map((r) => (
                  <Box key={r.id} sx={{ py: 1.5 }}>
                    <Stack direction="row" spacing={1.5}>
                      <Avatar src={r.author?.avatarUrl} sx={{ width: 36, height: 36 }}>
                        {r.author?.fullName?.charAt(0)}
                      </Avatar>
                      <Box sx={{ flex: 1 }}>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                            {r.author?.fullName}
                          </Typography>
                          <Rating value={r.rating} readOnly size="small" />
                          <Typography variant="caption" color="text.secondary">
                            {fromNow(r.createdAt)}
                          </Typography>
                        </Stack>
                        {r.content && <RichTextViewer content={r.content} variant="body2" />}
                      </Box>
                    </Stack>
                  </Box>
                ))}
              </Stack>
            </Box>
          )}
          {isAuthenticated && (
            <Box sx={{ mt: 1 }}>
              {reviewedByMe ? (
                <Typography variant="body2" color="text.secondary">
                  Bạn đã đánh giá tin này.
                </Typography>
              ) : canReview ? (
                <Button variant="outlined" onClick={() => setReviewOpen(true)}>
                  Viết đánh giá
                </Button>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Bạn cần liên hệ chủ trọ trước khi đánh giá tin này.
                </Typography>
              )}
            </Box>
          )}

          {/* Bình luận */}
          <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 1 }}>
            Bình luận {commentCount ? `(${commentCount})` : ''}
          </Typography>
          {!isAuthenticated && (
            <Alert severity="info" sx={{ mb: 1 }} action={
              <Button color="inherit" size="small" onClick={goLogin}>
                Đăng nhập
              </Button>
            }>
              Đăng nhập để đặt câu hỏi hoặc bình luận về tin này.
            </Alert>
          )}
          <CommentThread
            comments={comments}
            listingId={id}
            ownerId={landlordId}
            currentUser={isAuthenticated ? user : null}
            onCreate={isAuthenticated ? handleCreateComment : undefined}
            onReply={isAuthenticated ? handleReply : undefined}
            onEdit={handleEditComment}
            onDelete={handleDeleteComment}
            loading={commentsLoading}
          />
        </Grid>

        {/* CỘT PHẢI (sticky) */}
        <Grid item xs={12} md={4}>
          <Box sx={{ position: { md: 'sticky' }, top: 88 }}>
            <Paper variant="outlined" sx={{ p: 2 }}>
              <Typography variant="h5" color="primary" sx={{ fontWeight: 800 }}>
                {formatPrice(price)}
                <Typography component="span" variant="body2" color="text.secondary">
                  {price != null ? ' /tháng' : ''}
                </Typography>
              </Typography>
              {depositAmount != null && (
                <Typography variant="body2" color="text.secondary">
                  Cọc: {formatCurrency(depositAmount)}
                </Typography>
              )}

              <Divider sx={{ my: 2 }} />

              {/* Chủ trọ */}
              <Stack direction="row" spacing={1.5} alignItems="center">
                <Avatar src={landlord?.avatarUrl} sx={{ width: 48, height: 48 }}>
                  {(landlord?.fullName || contactName)?.charAt(0)}
                </Avatar>
                <Box sx={{ minWidth: 0 }}>
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Typography variant="subtitle1" sx={{ fontWeight: 700 }} noWrap>
                      {landlord?.fullName || contactName || 'Chủ trọ'}
                    </Typography>
                    {landlord?.verified && (
                      <Tooltip title="Đã xác thực">
                        <VerifiedIcon color="primary" sx={{ fontSize: 18 }} />
                      </Tooltip>
                    )}
                  </Stack>
                  {landlord?.memberSince && (
                    <Typography variant="caption" color="text.secondary">
                      Tham gia {formatDate(landlord.memberSince)}
                    </Typography>
                  )}
                </Box>
              </Stack>
              {landlord?.trustLevel && landlord.trustLevel !== 'NORMAL' && (
                <Box sx={{ mt: 1 }}>
                  <TrustScoreBadge score={landlord.trustScore} level={landlord.trustLevel} variant="inline" />
                </Box>
              )}

              <Divider sx={{ my: 2 }} />

              {/* Hành động */}
              <Stack spacing={1}>
                {phoneToShow && (
                  <Button
                    variant="contained"
                    color={revealedPhone ? 'success' : 'primary'}
                    startIcon={<PhoneIcon />}
                    fullWidth
                    component={revealedPhone ? 'a' : 'button'}
                    href={revealedPhone ? `tel:${revealedPhone}` : undefined}
                    onClick={revealedPhone ? undefined : handleRevealPhone}
                  >
                    {revealedPhone ? phoneToShow : `${phoneToShow}  ·  Hiện số`}
                  </Button>
                )}
                <Button variant="outlined" startIcon={<ChatBubbleOutlineIcon />} fullWidth onClick={handleMessage}>
                  Nhắn tin cho chủ trọ
                </Button>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    color={favorited ? 'error' : 'inherit'}
                    startIcon={favorited ? <FavoriteIcon /> : <FavoriteBorderIcon />}
                    fullWidth
                    onClick={handleToggleFavorite}
                  >
                    {favorited ? 'Đã lưu' : 'Lưu tin'}
                  </Button>
                  <IconButton aria-label="Báo cáo tin" onClick={openReport}>
                    <FlagOutlinedIcon />
                  </IconButton>
                </Stack>
                {landlordId && (
                  <Button component={RouterLink} to={`/chu-tro/${landlordId}`} fullWidth>
                    Xem trang chủ trọ →
                  </Button>
                )}
              </Stack>
            </Paper>

            {/* Tin liên quan */}
            {related.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
                  ✨ Tin tương tự
                </Typography>
                <ListingGrid items={related.slice(0, 3)} columns={{ xs: 1, sm: 1, md: 1 }} cardVariant="horizontal" showFavorite={false} />
              </Box>
            )}
          </Box>
        </Grid>
      </Grid>

      {/* Dialog yêu cầu đăng nhập */}
      <Dialog open={!!loginPrompt} onClose={() => setLoginPrompt(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Đăng nhập để tiếp tục</DialogTitle>
        <DialogContent>
          <Typography>{loginPrompt?.message}</Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button color="inherit" onClick={() => setLoginPrompt(null)}>
            Để sau
          </Button>
          <Button variant="contained" onClick={goLogin}>
            Đăng nhập
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog báo cáo */}
      <Dialog open={reportOpen} onClose={() => setReportOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Báo cáo tin đăng</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              select
              label="Lý do"
              value={reportReason}
              onChange={(e) => setReportReason(e.target.value)}
            >
              {Object.entries(REPORT_REASONS).map(([value, label]) => (
                <MenuItem key={value} value={value}>
                  {label}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              label="Mô tả chi tiết"
              value={reportDesc}
              onChange={(e) => setReportDesc(e.target.value)}
              multiline
              minRows={3}
              inputProps={{ maxLength: 500 }}
              helperText={`${reportDesc.length}/500 · tối thiểu 10 ký tự`}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button color="inherit" onClick={() => setReportOpen(false)}>
            Hủy
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={submitReport}
            disabled={!reportReason || reportDesc.trim().length < 10 || reportSubmitting}
          >
            {reportSubmitting ? 'Đang gửi…' : 'Gửi báo cáo'}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Dialog viết đánh giá */}
      <Dialog open={reviewOpen} onClose={() => setReviewOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Viết đánh giá</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }} alignItems="flex-start">
            <RatingStars value={reviewRating} onChange={(v) => setReviewRating(v || 1)} showValue={false} />
            <TextField
              label="Chia sẻ trải nghiệm của bạn"
              value={reviewContent}
              onChange={(e) => setReviewContent(e.target.value)}
              multiline
              minRows={3}
              fullWidth
              inputProps={{ maxLength: 1000 }}
              helperText={reviewRating <= 2 ? 'Với đánh giá thấp, vui lòng nêu rõ lý do.' : ''}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button color="inherit" onClick={() => setReviewOpen(false)}>
            Hủy
          </Button>
          <Button
            variant="contained"
            onClick={submitReview}
            disabled={reviewSubmitting || (reviewRating <= 2 && !reviewContent.trim())}
          >
            {reviewSubmitting ? 'Đang gửi…' : 'Gửi đánh giá'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default ListingDetailPage;
