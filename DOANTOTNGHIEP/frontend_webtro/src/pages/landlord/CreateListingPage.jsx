import { Box } from '@mui/material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import ListingWizard from '@/components/listing/ListingWizard';
import PageHeader from '@/components/dashboard/PageHeader';

const CreateListingPage = () => {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  return (
    <Box>
      <PageHeader title="Đăng tin mới" subtitle="Điền thông tin theo các bước bên dưới" />
      <ListingWizard
        mode="create"
        initialUnitId={params.get('rentalUnitId') || undefined}
        onDone={() => navigate('/quan-ly/tin-dang')}
      />
    </Box>
  );
};

export default CreateListingPage;
