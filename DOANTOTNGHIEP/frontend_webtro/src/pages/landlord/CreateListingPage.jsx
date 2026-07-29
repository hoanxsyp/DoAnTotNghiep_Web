import { Box } from '@mui/material';
import { useNavigate } from 'react-router-dom';
import ListingWizard from '@/components/listing/ListingWizard';
import PageHeader from '@/components/dashboard/PageHeader';

const CreateListingPage = () => {
  const navigate = useNavigate();
  return (
    <Box>
      <PageHeader title="Đăng tin mới" subtitle="Điền thông tin theo các bước bên dưới" />
      <ListingWizard mode="create" onDone={() => navigate('/quan-ly/tin-dang')} />
    </Box>
  );
};

export default CreateListingPage;
