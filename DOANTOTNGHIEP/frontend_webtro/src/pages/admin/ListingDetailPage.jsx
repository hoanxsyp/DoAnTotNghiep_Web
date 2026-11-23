import { useEffect, useState } from 'react';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom';
import { Alert, Button, Chip, Paper, Rating, Skeleton, Stack, Typography } from '@mui/material';
import adminApi from '@/api/adminApi';
import listingApi from '@/api/listingApi';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatusChip from '@/components/common/StatusChip';
import ImageGallery from '@/components/listing/ImageGallery';
import { formatArea, formatDateTime, formatPrice } from '@/utils/format';

const ListingDetail = ({ id, commentId, reviewId }) => {
  const [listing, setListing] = useState(null);
  const [listingError, setListingError] = useState(null);
  const [content, setContent] = useState(null);
  const [contentError, setContentError] = useState(null);
  const [attempt, setAttempt] = useState(0);
  const contentId = commentId || reviewId;
  const contentLabel = commentId ? 'Bình luận' : 'Đánh giá';

  useEffect(() => {
    let active = true;
    listingApi.getDetail(id, { countView: false })
      .then((data) => { if (active) setListing(data); })
      .catch((error) => { if (active) setListingError(error.message); });
    return () => { active = false; };
  }, [id, attempt]);

  useEffect(() => {
    if (!contentId) return undefined;
    let active = true;
    const getContent = commentId ? adminApi.getComment : adminApi.getReview;
    getContent(contentId)
      .then((item) => {
        if (!active) return;
        if (String(item.listingId) === id) {
          setContent(item);
        } else {
          setContentError('Nội dung này thuộc một tin đăng khác.');
        }
      })
      .catch((error) => { if (active) setContentError(error.message); });
    return () => { active = false; };
  }, [commentId, contentId, id]);

  const retry = () => {
    setListingError(null);
    setAttempt((previous) => previous + 1);
  };

  return (
    <>
      <AdminPageHeader
        title={listing?.title || 'Chi tiết tin đăng'}
        subtitle={`Tin #${id}`}
        actions={
          <Button
            component={RouterLink}
            to={commentId ? '/admin/binh-luan' : reviewId ? '/admin/danh-gia' : '/admin/tin-dang'}
          >
            {commentId ? 'Danh sách bình luận' : reviewId ? 'Danh sách đánh giá' : 'Danh sách tin đăng'}
          </Button>
        }
      />
      <Stack spacing={2}>
        {contentId && (
          <Paper variant="outlined" sx={{ p: 2, borderColor: 'primary.main' }}>
            <Stack spacing={1}>
              <Typography component="h2" variant="h6">{contentLabel} #{contentId}</Typography>
              {contentError && <Alert severity="warning">{contentError}</Alert>}
              {!content && !contentError && <Skeleton />}
              {content && (
                <>
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                    <Typography variant="body2">
                      {content.author?.fullName || '—'} · {formatDateTime(content.createdAt)}
                    </Typography>
                    <Chip size="small" label={content.statusLabel || content.status} />
                  </Stack>
                  {content.rating != null && <Rating value={content.rating} size="small" readOnly />}
                  <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                    {content.content || 'Không có nội dung văn bản.'}
                  </Typography>
                  {content.hiddenReason && (
                    <Typography variant="body2" color="text.secondary">Lý do ẩn · {content.hiddenReason}</Typography>
                  )}
                </>
              )}
            </Stack>
          </Paper>
        )}
        {listingError && (
          <Alert severity="error" action={<Button color="inherit" onClick={retry}>Thử lại</Button>}>
            {listingError}
          </Alert>
        )}
        {!listing && !listingError && <Skeleton variant="rounded" height={250} />}
        {listing && (
          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={2} alignItems="flex-start">
            <Paper variant="outlined" sx={{ p: 2, width: { xs: '100%', lg: '45%' } }}>
              <ImageGallery images={listing.images || []} alt={listing.title} />
            </Paper>
            <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 0, width: '100%' }}>
              <Stack spacing={1.5}>
                <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" alignItems="center">
                  <StatusChip status={listing.status} />
                  {listing.categoryName && <Chip size="small" label={listing.categoryName} />}
                </Stack>
                <Typography fontWeight={700}>{formatPrice(listing.price)} · {formatArea(listing.area)}</Typography>
                <Typography>{listing.fullAddress || listing.addressDetail}</Typography>
                {listing.landlord?.fullName && (
                  <Typography variant="body2" color="text.secondary">Chủ tin · {listing.landlord.fullName}</Typography>
                )}
                <Typography component="h2" variant="h6">Mô tả</Typography>
                <Typography sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{listing.description}</Typography>
                {!!listing.amenities?.length && (
                  <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
                    {listing.amenities.map((amenity) => (
                      <Chip key={amenity.id} size="small" variant="outlined" label={amenity.name} />
                    ))}
                  </Stack>
                )}
              </Stack>
            </Paper>
          </Stack>
        )}
      </Stack>
    </>
  );
};

const ListingDetailPage = () => {
  const { id } = useParams();
  const [params] = useSearchParams();
  const commentId = params.get('commentId');
  const reviewId = params.get('reviewId');
  return <ListingDetail key={`${id}-${commentId}-${reviewId}`} id={id} commentId={commentId} reviewId={reviewId} />;
};

export default ListingDetailPage;
