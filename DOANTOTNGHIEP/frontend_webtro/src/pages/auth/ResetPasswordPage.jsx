import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Typography, TextField, Button, Alert, Stack, InputAdornment, IconButton,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import authApi from '@/api/authApi';
import { notify } from '@/utils/toast';

const schema = yup.object({
  newPassword: yup
    .string()
    .required('Vui lòng nhập mật khẩu mới')
    .min(8, 'Mật khẩu tối thiểu 8 ký tự')
    .matches(/[a-zA-Z]/, 'Mật khẩu phải có ít nhất một chữ cái')
    .matches(/\d/, 'Mật khẩu phải có ít nhất một chữ số'),
  confirmPassword: yup
    .string()
    .required('Vui lòng nhập lại mật khẩu')
    .oneOf([yup.ref('newPassword')], 'Mật khẩu nhập lại không khớp'),
});

const ResetPasswordPage = () => {
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [invalidToken, setInvalidToken] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  });

  useEffect(() => {
    document.title = 'Đặt lại mật khẩu — Webtro';
  }, []);

  const submit = async (values) => {
    try {
      await authApi.resetPassword({ token, newPassword: values.newPassword });
      notify.success('Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.');
      navigate('/dang-nhap', { replace: true });
    } catch (err) {
      if (err.errorCode === 'TOKEN_INVALID' || err.errorCode === 'TOKEN_EXPIRED') {
        setInvalidToken(true);
      } else {
        notify.apiError(err);
      }
    }
  };

  if (!token || invalidToken) {
    return (
      <Box sx={{ textAlign: 'center' }}>
        <LinkOffIcon sx={{ fontSize: 64, color: 'error.main' }} />
        <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 1 }}>
          Link không hợp lệ
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Link đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.
        </Typography>
        <Button variant="contained" component={RouterLink} to="/quen-mat-khau" sx={{ mt: 3 }}>
          Yêu cầu link mới
        </Button>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={handleSubmit(submit)} noValidate>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 800 }}>
        Đặt lại mật khẩu
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Tạo mật khẩu mới cho tài khoản của bạn.
      </Typography>

      <Stack spacing={2}>
        <TextField
          label="Mật khẩu mới"
          type={showPassword ? 'text' : 'password'}
          fullWidth
          autoFocus
          error={!!errors.newPassword}
          helperText={errors.newPassword?.message}
          InputProps={{
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
                  onClick={() => setShowPassword((v) => !v)}
                  edge="end"
                >
                  {showPassword ? <VisibilityOff /> : <Visibility />}
                </IconButton>
              </InputAdornment>
            ),
          }}
          {...register('newPassword')}
        />
        <TextField
          label="Nhập lại mật khẩu mới"
          type={showPassword ? 'text' : 'password'}
          fullWidth
          error={!!errors.confirmPassword}
          helperText={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />

        <Alert severity="info">
          Sau khi đặt lại, bạn cần đăng nhập lại bằng mật khẩu mới trên mọi thiết bị.
        </Alert>

        <Button type="submit" variant="contained" size="large" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Đang xử lý…' : 'Đặt lại mật khẩu'}
        </Button>
      </Stack>
    </Box>
  );
};

export default ResetPasswordPage;
