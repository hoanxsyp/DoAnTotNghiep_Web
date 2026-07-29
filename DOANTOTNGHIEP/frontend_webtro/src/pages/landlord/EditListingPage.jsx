import { useEffect, useState } from 'react';
import { Box, Button } from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import listingApi from '@/api/listingApi';
import ListingWizard from '@/components/listing/ListingWizard';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';

/** Map chi tiết tin (docs/03 mục 4.4.4) sang initialData cho ListingWizard. */
const mapToInitialData = (d) => ({
  values: {
    categoryId: d.categoryId ?? d.category?.id ?? '',
    title: d.title ?? '',
    description: d.description ?? '',
    price: d.price ?? '',
    area: d.area ?? '',
    depositAmount: d.depositAmount ?? '',
    electricityPrice: d.electricityPrice ?? '',
    waterPrice: d.waterPrice ?? '',
    provinceId: d.provinceId ?? d.province?.id ?? null,
    districtId: d.districtId ?? d.district?.id ?? null,
    wardId: d.wardId ?? d.ward?.id ?? null,
    addressDetail: d.addressDetail ?? '',
    roomCount: d.roomCount ?? '',
    toiletCount: d.toiletCount ?? 1,
    maxOccupants: d.maxOccupants ?? 1,
    currentOccupants: d.currentOccupants ?? '',
    genderRequirement: d.genderRequirement ?? 'ANY',
    petAllowed: !!d.petAllowed,
    parkingAvailable: !!d.parkingAvailable,
    curfewType: d.curfewType ?? 'UNKNOWN',
    furnitureStatus: d.furnitureStatus ?? 'NONE',
    toiletType: d.toiletType ?? 'PRIVATE',
    availableFrom: d.availableFrom ? dayjs(d.availableFrom) : null,
    amenityIds: (d.amenities || []).map((a) => a.id ?? a).filter(Boolean),
    contactName: d.contactName ?? '',
    contactPhone: d.contactPhone ?? '',
  },
  images: (d.images || []).map((img) => ({ id: img.id, url: img.mediumUrl || img.url || img.originalUrl, name: img.id, isPrimary: img.isPrimary })),
  primaryImageId: (d.images || []).find((img) => img.isPrimary)?.id ?? null,
});

const EditListingPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(true);
  const [initialData, setInitialData] = useState(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    listingApi.getDetail(id)
      .then((d) => { if (alive) setInitialData(mapToInitialData(d)); })
      .catch((e) => { if (alive) { setError(true); notify.apiError(e, 'Không tải được tin đăng'); } })
      .finally(() => alive && setLoading(false));
    return () => { alive = false; };
  }, [id]);

  return (
    <Box>
      <PageHeader title="Chỉnh sửa tin" subtitle="Cập nhật thông tin tin đăng của bạn" />
      {loading ? (
        <LoadingSkeleton variant="form" />
      ) : error ? (
        <EmptyState title="Không tìm thấy tin đăng" action={<Button variant="contained" onClick={() => navigate('/quan-ly/tin-dang')}>Về danh sách tin</Button>} />
      ) : (
        <ListingWizard mode="edit" listingId={id} initialData={initialData} onDone={() => navigate('/quan-ly/tin-dang')} />
      )}
    </Box>
  );
};

export default EditListingPage;
