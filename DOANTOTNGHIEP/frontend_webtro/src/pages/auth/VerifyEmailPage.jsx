import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import {
  Box, Typography, Button, CircularProgress, Stack, Dialog, DialogTitle, DialogContent,
  DialogActions, TextField,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import HighlightOffIcon from '@mui/icons-material/HighlightOff';
import authApi from '@/api/authApi';
import useAuth from '@/hooks/useAuth';
import { notify } from '@/utils/toast';

/** Xác thực email (docs/04 mục 5.1.9) — tự động chạy khi vào trang, chống gọi 2 lần (StrictMode). */
const VerifyEmailPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const { isAuthenticated } = useAuth();

  const [status, setStatus] = useState(token ? 'loading' : 'error'); // loading | success | error
  const [resendOpen, setResendOpen] = useState(false);
  const [resendEmail, setResendEmail] = useState('');
  const calledRef = useRef(false);

  useEffect(() => {
    document.title = 'Xác thực email — Webtro';
  }, []);

  useEffect(() => {
    if (!token || calledRef.current) return;
    calledRef.current = true;
    authApi
      .verifyEmail({ token })
      .then(() => setStatus('success'))
      .catch((err) => {
        // Bấm lại link cũ khi đã xác thực -> coi như thành công.
        if (err.errorCode === 'ALREADY_VERIFIED') setStatus('success');
        else setStatus('error');
      });
  }, [token]);

  const handleResend = async () => {
    try {
      await authApi.resendVerification({ email: resendEmail });
      notify.success('Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.');
      setResendOpen(false);
    } catch (e) {
      notify.apiError(e);
    }
  };

  return (
    <Box sx={{ textAlign: 'center' }}>
      {status === 'loading' && (
        <>
          <CircularProgress sx={{ mb: 2 }} />
          <Typography variant="h6">Đang xác thực email của bạn…</Typography>
        </>
      )}

      {status === 'success' && (
        <>
          <CheckCircleOutlineIcon sx={{ fontSize: 72, color: 'success.main' }} />
          <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 1 }}>
            Xác thực thành công!
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Tài khoản của bạn đã được kích hoạt.
          </Typography>
          <Button
            variant="contained"
            component={RouterLink}
            to={isAuthenticated ? '/' : '/dang-nhap'}
            sx={{ mt: 3 }}
          >
            {isAuthenticated ? 'Về trang chủ' : 'Đăng nhập ngay'}
          </Button>
        </>
      )}

      {status === 'error' && (
        <>
          <HighlightOffIcon sx={{ fontSize: 72, color: 'error.main' }} />
          <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 1 }}>
            Xác thực thất bại
          </Typography>
          <Typography color="text.secondary" sx={{ mt: 1 }}>
            Link không hợp lệ hoặc đã hết hạn.
          </Typography>
          <Stack spacing={1.5} sx={{ mt: 3 }}>
            <Button variant="contained" onClick={() => setResendOpen(true)}>
              Gửi lại email
            </Button>
            <Button component={RouterLink} to="/">
              Về trang chủ
            </Button>
          </Stack>
        </>
      )}

      <Dialog open={resendOpen} onClose={() => setResendOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Gửi lại email xác thực</DialogTitle>
        <DialogContent>
          <TextField
            label="Email"
            type="email"
            fullWidth
            autoFocus
            value={resendEmail}
            onChange={(e) => setResendEmail(e.target.value)}
            sx={{ mt: 1 }}
          />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={() => setResendOpen(false)} color="inherit">
            Hủy
          </Button>
          <Button variant="contained" onClick={handleResend} disabled={!resendEmail.trim()}>
            Gửi
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default VerifyEmailPage;
