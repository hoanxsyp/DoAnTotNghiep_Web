import { useCallback, useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';
import { useParams, useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import {
  Container, Grid, Box, Typography, Stack, Chip, Button, Paper, Divider, Avatar, Breadcrumbs,
  Link, IconButton, Alert, Dialog, DialogTitle, DialogContent, DialogActions, TextField,
  Menu, MenuItem, ListItemIcon, Rating, LinearProgress, Tooltip,
} from '@mui/material';
import PhoneIcon from '@mui/icons-material/Phone';
import ChatBubbleOutlineIcon from '@mui/icons-material/ChatBubbleOutline';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import VerifiedIcon from '@mui/icons-material/Verified';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import HomeWorkOutlinedIcon from '@mui/icons-material/HomeWorkOutlined';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import listingApi from '@/api/listingApi';
import commentApi from '@/api/commentApi';
import reviewApi from '@/api/reviewApi';
import favoriteApi from '@/api/favoriteApi';
import contactApi from '@/api/contactApi';
import reportApi from '@/api/reportApi';
import CommentThread from '@/components/interaction/CommentThread';
import RatingStars from '@/components/interaction/RatingStars';
import RichTextViewer from '@/components/common/RichTextViewer';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import RentalRequestDialog from '@/components/rental/RentalRequestDialog';
import useAuth from '@/hooks/useAuth';
import { notify } from '@/utils/toast';
import { getReviewContentError, normalizeReviewContent } from '@/utils/reviewValidation';
import {
  formatPrice, formatCurrency, formatArea, formatDate, fromNow, compactNumber,
} from '@/utils/format';
import {
  REPORT_REASONS, GENDER_REQUIREMENT, FURNITURE_STATUS, ROLES,
} from '@/constants';

const toItems = (d) => (Array.isArray(d) ? d : d?.items ?? []);

const DEFAULT_FIRST_MESSAGE = 'Chào anh/chị, phòng này còn trống không ạ?';
const FIRST_MESSAGE_MIN = 10;
const FIRST_MESSAGE_MAX = 2000;

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
  const { isAuthenticated, user, role } = useAuth();

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

  const [loginPrompt, setLoginPrompt] = useState(null);
  const [reportOpen, setReportOpen] = useState(false);
  const [reportReason, setReportReason] = useState('');
  const [reportDesc, setReportDesc] = useState('');
  const [reportSubmitting, setReportSubmitting] = useState(false);

  const [reviewOpen, setReviewOpen] = useState(false);
  const [reviewRating, setReviewRating] = useState(5);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewContentServerError, setReviewContentServerError] = useState('');
  const [reviewSubmitting, setReviewSubmitting] = useState(false);
  const [reviewEligibility, setReviewEligibility] = useState(null);
  const [editingReview, setEditingReview] = useState(null);
  const [reviewMenuAnchor, setReviewMenuAnchor] = useState(null);
  const [reviewMenuReview, setReviewMenuReview] = useState(null);
  const [deletingReview, setDeletingReview] = useState(null);
  const [reviewDeleting, setReviewDeleting] = useState(false);
  const [rentalOpen, setRentalOpen] = useState(false);

  const [chatOpen, setChatOpen] = useState(false);
  const [chatMessage, setChatMessage] = useState('');
  const [chatSubmitting, setChatSubmitting] = useState(false);

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

  const loadReviews = useCallback(async () => {
    const data = await reviewApi.getReviews(id, { page: 0, size: 5 });
    setReviews({ items: toItems(data), summary: data?.summary || null });
  }, [id]);

  useEffect(() => {
    if (!id || notFound) return;
    loadComments();

    setReviewsLoading(true);
    loadReviews()
      .catch(() => setReviews({ items: [], summary: null }))
      .finally(() => setReviewsLoading(false));
  }, [id, notFound, loadComments, loadReviews]);

  useEffect(() => {
    if (!id || !isAuthenticated || role !== ROLES.TENANT) {
      setReviewEligibility(null);
      return;
    }
    reviewApi.getEligibility(id)
      .then(setReviewEligibility)
      .catch(() => setReviewEligibility(null));
  }, [id, isAuthenticated, role]);

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
      // 409/404: server đã ở trạng thái đích -> chỉ đồng bộ lại UI.
      if (err.errorCode === 'FAVORITE_ALREADY_EXISTS') setFavorited(true);
      else if (err.errorCode === 'FAVORITE_NOT_FOUND') setFavorited(false);
      else {
        setFavorited(prev);
        notify.apiError(err);
      }
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

  const handleMessage = () => {
    if (!isAuthenticated) {
      requireLogin('Đăng nhập để nhắn tin cho chủ trọ.');
      return;
    }
    setChatMessage(DEFAULT_FIRST_MESSAGE);
    setChatOpen(true);
  };

  // Backend bắt buộc tin nhắn đầu tiên (10-2000 ký tự). Hội thoại cho tin này đã có thì backend trả lại
  // hội thoại cũ và gắn thêm tin nhắn vào đó.
  const submitFirstMessage = async () => {
    setChatSubmitting(true);
    try {
      const data = await contactApi.createConversation({
        listingId: Number(id),
        initialMessage: chatMessage.trim(),
      });
      setChatOpen(false);
      // Trang tin nhắn theo khu vực của vai trò: chủ trọ/admin ở /quan-ly, còn lại ở /tai-khoan.
      const base = [ROLES.LANDLORD, ROLES.ADMIN].includes(role) ? '/quan-ly/tin-nhan' : '/tai-khoan/tin-nhan';
      navigate(data?.id ? `${base}?conversationId=${data.id}` : base);
    } catch (err) {
      notify.apiError(err);
    } finally {
      setChatSubmitting(false);
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

  const openCreateReview = () => {
    setEditingReview(null);
    setReviewRating(5);
    setReviewContent('');
    setReviewContentServerError('');
    setReviewOpen(true);
  };

  const closeReviewMenu = () => {
    setReviewMenuAnchor(null);
    setReviewMenuReview(null);
  };

  const openEditReview = () => {
    const review = reviewMenuReview;
    closeReviewMenu();
    if (!review) return;
    setEditingReview(review);
    setReviewRating(review.rating);
    setReviewContent(review.content || '');
    setReviewContentServerError('');
    setReviewOpen(true);
  };

  const openDeleteReview = () => {
    const review = reviewMenuReview;
    closeReviewMenu();
    if (review) setDeletingReview(review);
  };

  const submitReview = async () => {
    const validationError = getReviewContentError(reviewRating, reviewContent);
    if (validationError) {
      setReviewContentServerError(validationError);
      return;
    }
    setReviewSubmitting(true);
    try {
      const payload = {
        rating: reviewRating,
        content: normalizeReviewContent(reviewContent),
      };
      if (editingReview) {
        await reviewApi.update(editingReview.id, payload);
        notify.success('Đã cập nhật đánh giá.');
      } else {
        await reviewApi.create(id, payload);
        notify.success('Cảm ơn bạn đã đánh giá.');
        setReviewEligibility((current) => current
          ? { ...current, eligible: false, alreadyReviewed: true }
          : current);
        setListing((current) => current
          ? { ...current, canReview: false, reviewedByMe: true }
          : current);
      }
      setReviewOpen(false);
      setEditingReview(null);
      setReviewContent('');
      setReviewContentServerError('');
      await loadReviews();
    } catch (err) {
      const contentError = err.fieldErrors?.find((item) => item.field === 'content')?.message;
      setReviewContentServerError(contentError || '');
      notify.apiError(contentError ? { ...err, message: contentError } : err);
    } finally {
      setReviewSubmitting(false);
    }
  };

  const deleteOwnReview = async () => {
    const review = deletingReview;
    if (!review) return;
    setReviewDeleting(true);
    try {
      await reviewApi.remove(review.id);
      notify.success('Đã xóa đánh giá. Bạn có thể đánh giá lại tin này.');
      setDeletingReview(null);
      setListing((current) => current
        ? { ...current, canReview: true, reviewedByMe: false }
        : current);
      if (role === ROLES.TENANT) {
        try {
          setReviewEligibility(await reviewApi.getEligibility(id));
        } catch {
          setReviewEligibility(null);
        }
      }
      await loadReviews();
    } catch (err) {
      notify.apiError(err, 'Xóa đánh giá thất bại');
    } finally {
      setReviewDeleting(false);
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
    landlord, contactName, contactPhone,
    viewCount, favoriteCount, commentCount, latitude, longitude, locationSource, electricityPrice, waterPrice,
    roomCount, toiletCount, currentOccupants, maxOccupants, availableFrom, furnitureStatus,
    curfewType, toiletType, petAllowed, parkingAvailable, genderRequirement, categoryCode,
    canReview, reviewedByMe, publishedAt, status,
  } = listing;

  const effectiveCanReview = reviewEligibility?.eligible ?? canReview;
  const effectiveReviewedByMe = reviewEligibility?.alreadyReviewed ?? reviewedByMe;

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
              {/* WARD = tọa độ tâm phường: chỉ hiện khu vực, không cắm ghim như vị trí thật */}
              {locationSource === 'WARD' && (
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                  Vị trí gần đúng theo khu vực {wardName ? `phường ${wardName}` : 'phường'} — liên hệ chủ trọ để biết địa chỉ chính xác.
                </Typography>
              )}
              <Box
                component="iframe"
                title="Bản đồ vị trí"
                src={locationSource === 'WARD'
                  ? `https://www.openstreetmap.org/export/embed.html?bbox=${longitude - 0.016}%2C${latitude - 0.012}%2C${longitude + 0.016}%2C${latitude + 0.012}&layer=mapnik`
                  : `https://www.openstreetmap.org/export/embed.html?bbox=${longitude - 0.008}%2C${latitude - 0.006}%2C${longitude + 0.008}%2C${latitude + 0.006}&layer=mapnik&marker=${latitude}%2C${longitude}`}
                sx={{ width: '100%', height: 300, border: 0, borderRadius: 2 }}
                loading="lazy"
              />
            </>
          )}

          {/* Đánh giá */}
          <Typography variant="h6" component="h2" sx={{ fontWeight: 700, mt: 3, mb: 0.5 }}>
            Đánh giá {totalReviews > 0 ? `(${totalReviews})` : ''}
          </Typography>
          {/* Đánh giá thuộc về phòng: gồm cả đánh giá từ các lần đăng tin trước của cùng phòng (RE-2). */}
          {(reviews.summary?.rentalUnitName || listing?.rentalUnit?.name) && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Đánh giá của phòng &quot;{reviews.summary?.rentalUnitName || listing?.rentalUnit?.name}&quot; qua mọi lần đăng tin
            </Typography>
          )}
          {reviewsLoading ? (
            <LoadingSkeleton variant="comment" count={2} />
          ) : totalReviews === 0 ? (
            <EmptyState size="sm" title="Chưa có đánh giá nào cho phòng này" />
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
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                            {r.author?.fullName}
                          </Typography>
                          <Rating value={r.rating} readOnly size="small" />
                          <Typography variant="caption" color="text.secondary">
                            {fromNow(r.createdAt)}
                          </Typography>
                        </Stack>
                        {r.rentedViaListingCreatedAt && String(r.listingId) !== String(listing?.id) && (
                          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                            Thuê qua tin đăng tháng {dayjs(r.rentedViaListingCreatedAt).format('MM/YYYY')}
                          </Typography>
                        )}
                        {r.content && <RichTextViewer content={r.content} variant="body2" />}
                      </Box>
                      {isAuthenticated && String(r.author?.id) === String(user?.id) && (
                        <IconButton
                          size="small"
                          aria-label="Tùy chọn đánh giá"
                          aria-haspopup="menu"
                          onClick={(event) => {
                            setReviewMenuAnchor(event.currentTarget);
                            setReviewMenuReview(r);
                          }}
                          sx={{ alignSelf: 'flex-start' }}
                        >
                          <MoreVertIcon fontSize="small" />
                        </IconButton>
                      )}
                    </Stack>
                  </Box>
                ))}
              </Stack>
              <Menu
                anchorEl={reviewMenuAnchor}
                open={Boolean(reviewMenuAnchor)}
                onClose={closeReviewMenu}
                anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
                transformOrigin={{ vertical: 'top', horizontal: 'right' }}
              >
                <MenuItem onClick={openEditReview}>
                  <ListItemIcon><EditOutlinedIcon fontSize="small" /></ListItemIcon>
                  Sửa
                </MenuItem>
                <MenuItem onClick={openDeleteReview} sx={{ color: 'error.main' }}>
                  <ListItemIcon><DeleteOutlineIcon color="error" fontSize="small" /></ListItemIcon>
                  Xóa
                </MenuItem>
              </Menu>
            </Box>
          )}
          {isAuthenticated && (
            <Box sx={{ mt: 1 }}>
              {effectiveReviewedByMe ? (
                <Typography variant="body2" color="text.secondary">
                  Bạn đã đánh giá tin này. Dùng nút ba chấm bên cạnh đánh giá của bạn để sửa hoặc xóa.
                </Typography>
              ) : effectiveCanReview ? (
                <Button
                  variant="outlined"
                  onClick={openCreateReview}
                >
                  Viết đánh giá
                </Button>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Chỉ người đang thuê hoặc đã trả phòng mới được đánh giá tin này.
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

              <Divider sx={{ my: 2 }} />

              {/* Hành động */}
              <Stack spacing={1}>
                {(role === ROLES.TENANT || !isAuthenticated) && status === 'ACTIVE' && (
                  <Button
                    variant="contained"
                    color="success"
                    startIcon={<HomeWorkOutlinedIcon />}
                    fullWidth
                    onClick={() => {
                      if (!isAuthenticated) {
                        requireLogin('Đăng nhập bằng tài khoản người thuê để gửi yêu cầu thuê phòng.');
                      } else {
                        setRentalOpen(true);
                      }
                    }}
                  >
                    Thuê phòng
                  </Button>
                )}
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
          </Box>
        </Grid>
      </Grid>

      <RentalRequestDialog
        open={rentalOpen}
        listing={listing}
        onClose={() => setRentalOpen(false)}
      />

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

      {/* Dialog viết/sửa đánh giá */}
      <Dialog
        open={reviewOpen}
        onClose={() => {
          if (reviewSubmitting) return;
          setReviewContentServerError('');
          setEditingReview(null);
          setReviewOpen(false);
        }}
        maxWidth="xs"
        fullWidth
      >
        <DialogTitle>{editingReview ? 'Sửa đánh giá' : 'Viết đánh giá'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }} alignItems="flex-start">
            <RatingStars value={reviewRating} onChange={(v) => setReviewRating(v || 1)} showValue={false} />
            <TextField
              label="Chia sẻ trải nghiệm của bạn"
              value={reviewContent}
              onChange={(e) => {
                setReviewContent(e.target.value);
                setReviewContentServerError('');
              }}
              multiline
              minRows={3}
              fullWidth
              inputProps={{ maxLength: 2000 }}
              error={!!(reviewContentServerError || getReviewContentError(reviewRating, reviewContent))}
              helperText={reviewContentServerError
                || getReviewContentError(reviewRating, reviewContent)
                || (reviewRating <= 2
                  ? 'Với đánh giá thấp, vui lòng nêu rõ lý do (ít nhất 10 ký tự).'
                  : 'Không bắt buộc. Nếu nhập, nội dung cần từ 10 đến 2000 ký tự.')}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button
            color="inherit"
            onClick={() => {
              setReviewContentServerError('');
              setEditingReview(null);
              setReviewOpen(false);
            }}
            disabled={reviewSubmitting}
          >
            Hủy
          </Button>
          <Button
            variant="contained"
            onClick={submitReview}
            disabled={reviewSubmitting || !!getReviewContentError(reviewRating, reviewContent)}
          >
            {reviewSubmitting ? 'Đang lưu…' : editingReview ? 'Lưu thay đổi' : 'Gửi đánh giá'}
          </Button>
        </DialogActions>
      </Dialog>

      <ConfirmDialog
        open={!!deletingReview}
        title="Xóa đánh giá"
        message="Bạn chắc chắn muốn xóa đánh giá này? Sau khi xóa, bạn có thể gửi đánh giá mới cho tin này."
        confirmText="Xóa"
        severity="error"
        loading={reviewDeleting}
        onConfirm={deleteOwnReview}
        onCancel={() => !reviewDeleting && setDeletingReview(null)}
      />

      {/* Dialog tin nhắn đầu tiên gửi chủ trọ */}
      <Dialog open={chatOpen} onClose={() => !chatSubmitting && setChatOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Nhắn tin cho chủ trọ</DialogTitle>
        <DialogContent>
          <TextField
            label="Tin nhắn"
            value={chatMessage}
            onChange={(e) => setChatMessage(e.target.value)}
            multiline
            minRows={3}
            fullWidth
            autoFocus
            sx={{ mt: 1 }}
            inputProps={{ maxLength: FIRST_MESSAGE_MAX }}
            error={chatMessage.length > 0 && chatMessage.trim().length < FIRST_MESSAGE_MIN}
            helperText={chatMessage.trim().length < FIRST_MESSAGE_MIN
              ? `Tối thiểu ${FIRST_MESSAGE_MIN} ký tự`
              : `${chatMessage.length}/${FIRST_MESSAGE_MAX}`}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button color="inherit" onClick={() => setChatOpen(false)} disabled={chatSubmitting}>
            Hủy
          </Button>
          <Button
            variant="contained"
            onClick={submitFirstMessage}
            disabled={chatSubmitting || chatMessage.trim().length < FIRST_MESSAGE_MIN}
          >
            {chatSubmitting ? 'Đang gửi…' : 'Gửi tin nhắn'}
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
};

export default ListingDetailPage;
