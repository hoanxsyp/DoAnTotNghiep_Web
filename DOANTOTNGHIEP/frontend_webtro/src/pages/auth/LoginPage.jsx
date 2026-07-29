import { useEffect, useState } from 'react';
import { useNavigate, useLocation, Link as RouterLink } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Typography, TextField, Button, Link, Alert, Stack, FormControlLabel, Checkbox,
  InputAdornment, IconButton, CircularProgress,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { login } from '@/redux/authSlice';
import authApi from '@/api/authApi';
import { ROLES } from '@/constants';
import { notify } from '@/utils/toast';

const schema = yup.object({
  emailOrPhone: yup.string().trim().required('Vui lòng nhập email hoặc số điện thoại'),
  password: yup.string().required('Vui lòng nhập mật khẩu'),
});

/** Đích đến sau đăng nhập (docs/04 mục 5.1.5): from -> admin -> landlord -> trang chủ. */
const resolveDestination = (user, from) => {
  if (from) return from;
  const roles = user?.roles ?? [];
  if (roles.includes(ROLES.ADMIN) || roles.includes(ROLES.MODERATOR)) return '/admin/dashboard';
  if (roles.includes(ROLES.LANDLORD)) return '/quan-ly/tong-quan';
  return '/';
};

const LoginPage = () => {
  const dispatch = useDispatch();
  const navigate = useNavigate();
  const location = useLocation();
  const from = location.state?.from;

  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState(null);
  const [needVerify, setNeedVerify] = useState(false);
  const [resending, setResending] = useState(false);

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: yupResolver(schema), defaultValues: { emailOrPhone: '', password: '' } });

  useEffect(() => {
    document.title = 'Đăng nhập — Webtro';
  }, []);

  const onSubmit = async (values) => {
    setFormError(null);
    setNeedVerify(false);
    const result = await dispatch(login(values));
    if (login.fulfilled.match(result)) {
      notify.success('Đăng nhập thành công.');
      navigate(resolveDestination(result.payload, from), { replace: true });
      return;
    }
    const err = result.payload || {};
    if (err.errorCode === 'ACCOUNT_NOT_VERIFIED') {
      setNeedVerify(true);
      setFormError('Tài khoản chưa được xác thực. Vui lòng kiểm tra email hoặc gửi lại email xác thực.');
    } else if (err.errorCode === 'ACCOUNT_LOCKED' || err.status === 403) {
      setFormError('Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.');
    } else if (err.status === 429) {
      const mins = err.retryAfter ? Math.ceil(Number(err.retryAfter) / 60) : null;
      setFormError(
        `Bạn đã đăng nhập sai quá nhiều lần. Vui lòng thử lại sau${mins ? ` ${mins} phút` : ''}.`,
      );
    } else if (err.status === 401 || err.errorCode === 'INVALID_CREDENTIALS') {
      setFormError('Email hoặc mật khẩu không đúng.');
    } else {
      setFormError(err.message || 'Đăng nhập thất bại, vui lòng thử lại.');
    }
  };

  const handleResend = async () => {
    const emailOrPhone = getValues('emailOrPhone');
    if (!emailOrPhone) {
      notify.warning('Vui lòng nhập email trước khi gửi lại.');
      return;
    }
    setResending(true);
    try {
      await authApi.resendVerification({ email: emailOrPhone });
      notify.success('Đã gửi lại email xác thực. Vui lòng kiểm tra hộp thư.');
    } catch (e) {
      notify.apiError(e);
    } finally {
      setResending(false);
    }
  };

  return (
    <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 800 }}>
        Đăng nhập
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Chào mừng bạn quay lại
      </Typography>

      {formError && (
        <Alert
          severity={needVerify ? 'warning' : 'error'}
          sx={{ mb: 2 }}
          action={
            needVerify ? (
              <Button color="inherit" size="small" onClick={handleResend} disabled={resending}>
                {resending ? 'Đang gửi…' : 'Gửi lại email'}
              </Button>
            ) : undefined
          }
        >
          {formError}
        </Alert>
      )}

      <Stack spacing={2}>
        <TextField
          label="Email hoặc số điện thoại"
          fullWidth
          autoFocus
          autoComplete="username"
          error={!!errors.emailOrPhone}
          helperText={errors.emailOrPhone?.message}
          disabled={isSubmitting}
          {...register('emailOrPhone')}
        />
        <TextField
          label="Mật khẩu"
          type={showPassword ? 'text' : 'password'}
          fullWidth
          autoComplete="current-password"
          error={!!errors.password}
          helperText={errors.password?.message}
          disabled={isSubmitting}
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
          {...register('password')}
        />

        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <FormControlLabel control={<Checkbox size="small" defaultChecked />} label="Ghi nhớ" />
          <Link component={RouterLink} to="/quen-mat-khau" underline="hover" variant="body2">
            Quên mật khẩu?
          </Link>
        </Stack>

        <Button
          type="submit"
          variant="contained"
          size="large"
          fullWidth
          disabled={isSubmitting}
          startIcon={isSubmitting ? <CircularProgress size={16} color="inherit" /> : null}
        >
          {isSubmitting ? 'Đang đăng nhập…' : 'Đăng nhập'}
        </Button>
      </Stack>

      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        Chưa có tài khoản?{' '}
        <Link component={RouterLink} to="/dang-ky" underline="hover" fontWeight={600}>
          Đăng ký
        </Link>
      </Typography>
    </Box>
  );
};

export default LoginPage;
