import { useEffect, useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { Alert, Button, Chip, Paper, Skeleton, Stack, Typography } from '@mui/material';
import listingApi from '@/api/listingApi';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import StatusChip from '@/components/common/StatusChip';
import ImageGallery from '@/components/listing/ImageGallery';
import { formatArea, formatPrice } from '@/utils/format';

const ListingDetail = ({ id }) => {
  const [listing, setListing] = useState(null);
  const [listingError, setListingError] = useState(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    listingApi.getDetail(id, { countView: false })
      .then((data) => { if (active) setListing(data); })
      .catch((error) => { if (active) setListingError(error.message); });
    return () => { active = false; };
  }, [id, attempt]);

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
          <Button component={RouterLink} to="/admin/tin-dang">Danh sách tin đăng</Button>
        }
      />
      <Stack spacing={2}>
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
  return <ListingDetail key={id} id={id} />;
};

export default ListingDetailPage;
