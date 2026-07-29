import { Outlet } from 'react-router-dom';
import { Box } from '@mui/material';
import PublicHeader from '@/components/layout/PublicHeader';
import PublicFooter from '@/components/layout/PublicFooter';
import ChatbotWidget from '@/components/chatbot/ChatbotWidget';

/**
 * Layout cho trang công khai (khách + đã đăng nhập): header điều hướng, nội dung, footer, và
 * widget chatbot nổi ở góc [§9.3].
 */
const PublicLayout = () => {
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', minHeight: '100vh' }}>
      <PublicHeader />
      <Box component="main" sx={{ flexGrow: 1 }}>
        <Outlet />
      </Box>
      <PublicFooter />
      <ChatbotWidget />
    </Box>
  );
};

export default PublicLayout;
