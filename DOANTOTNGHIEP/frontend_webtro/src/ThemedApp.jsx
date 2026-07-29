import { useEffect } from 'react';
import { useDispatch, useSelector } from 'react-redux';
import { ThemeProvider, CssBaseline } from '@mui/material';
import { getTheme } from '@/theme/theme';
import { selectThemeMode } from '@/redux/uiSlice';
import { bootstrapAuth } from '@/redux/authSlice';
import { setAuthFailureHandler } from '@/api/axiosClient';
import { clearSession } from '@/redux/authSlice';

/**
 * Bọc ứng dụng bằng theme (sáng/tối theo uiSlice) và chạy bootstrapAuth một lần lúc mở app để
 * khôi phục phiên qua refresh token (canonical mục 4.3). Cũng đăng ký handler đăng xuất cứng khi
 * refresh thất bại.
 */
const ThemedApp = ({ children }) => {
  const dispatch = useDispatch();
  const mode = useSelector(selectThemeMode);
  const theme = getTheme(mode);

  useEffect(() => {
    // Khi interceptor refresh thất bại -> dọn phiên (đưa app về trạng thái chưa đăng nhập).
    setAuthFailureHandler(() => dispatch(clearSession()));
    dispatch(bootstrapAuth());
  }, [dispatch]);

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      {children}
    </ThemeProvider>
  );
};

export default ThemedApp;
