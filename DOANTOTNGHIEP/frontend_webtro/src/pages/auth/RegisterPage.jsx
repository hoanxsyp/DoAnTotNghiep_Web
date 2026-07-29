import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Typography, TextField, Button, Link, Alert, Stack, ToggleButton, ToggleButtonGroup,
  InputAdornment, IconButton, FormControlLabel, Checkbox, LinearProgress, FormHelperText,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import SearchIcon from '@mui/icons-material/Search';
import HomeWorkIcon from '@mui/icons-material/HomeWork';
import MarkEmailReadOutlinedIcon from '@mui/icons-material/MarkEmailReadOutlined';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import authApi from '@/api/authApi';
import { ROLES } from '@/constants';
import { notify } from '@/utils/toast';

const VN_PHONE = /^(0|\+84)(3[2-9]|5[25689]|7[06-9]|8[1-9]|9[0-46-9])\d{7}$/;
const SAFE_NAME = /^[^<>{}[\]\\/"'`;$|&*=+#@%^~]+$/u;

const schema = yup.object({
  role: yup.string().oneOf([ROLES.TENANT, ROLES.LANDLORD]).required('Vui lòng chọn vai trò'),
  fullName: yup
    .string()
    .trim()
    .required('Vui lòng nhập họ và tên')
    .min(2, 'Họ tên quá ngắn')
    .max(100, 'Họ tên tối đa 100 ký tự')
    .matches(SAFE_NAME, 'Họ tên chứa ký tự không hợp lệ'),
  email: yup
    .string()
    .trim()
    .lowercase()
    .required('Vui lòng nhập email')
    .email('Email không đúng định dạng')
    .max(150, 'Email tối đa 150 ký tự'),
  phone: yup
    .string()
    .trim()
    .required('Vui lòng nhập số điện thoại')
    .matches(VN_PHONE, 'Số điện thoại không hợp lệ (VD: 0901234567)'),
  password: yup
    .string()
    .required('Vui lòng nhập mật khẩu')
    .min(8, 'Mật khẩu tối thiểu 8 ký tự')
    .matches(/[a-zA-Z]/, 'Mật khẩu phải có ít nhất một chữ cái')
    .matches(/\d/, 'Mật khẩu phải có ít nhất một chữ số'),
  confirmPassword: yup
    .string()
    .required('Vui lòng nhập lại mật khẩu')
    .oneOf([yup.ref('password')], 'Mật khẩu nhập lại không khớp'),
  acceptTerms: yup.boolean().oneOf([true], 'Bạn cần đồng ý với điều khoản sử dụng'),
});

const maskEmail = (email = '') => {
  const [name, domain] = email.split('@');
  if (!domain) return email;
  const head = name.slice(0, 1);
  return `${head}***@${domain}`;
};

const ChecklistRow = ({ ok, text }) => (
  <Stack direction="row" spacing={0.5} alignItems="center" sx={{ color: ok ? 'success.main' : 'text.disabled' }}>
    {ok ? <CheckIcon sx={{ fontSize: 16 }} /> : <CloseIcon sx={{ fontSize: 16 }} />}
    <Typography variant="caption">{text}</Typography>
  </Stack>
);

const RegisterPage = () => {
  const [showPassword, setShowPassword] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [successEmail, setSuccessEmail] = useState(null);
  const [resendCooldown, setResendCooldown] = useState(0);

  const {
    register,
    handleSubmit,
    control,
    watch,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: {
      role: ROLES.TENANT,
      fullName: '',
      email: '',
      phone: '',
      password: '',
      confirmPassword: '',
      acceptTerms: false,
    },
  });

  useEffect(() => {
    document.title = 'Đăng ký — Webtro';
  }, []);

  useEffect(() => {
    if (resendCooldown <= 0) return undefined;
    const t = setTimeout(() => setResendCooldown((n) => n - 1), 1000);
    return () => clearTimeout(t);
  }, [resendCooldown]);

  const role = watch('role');
  const password = watch('password') || '';
  const hasLen = password.length >= 8;
  const hasAlpha = /[a-zA-Z]/.test(password);
  const hasDigit = /\d/.test(password);
  const strength = [hasLen, hasAlpha, hasDigit].filter(Boolean).length;
  const strengthLabel = ['Yếu', 'Yếu', 'Trung bình', 'Mạnh'][strength] || 'Yếu';
  const strengthColor = strength >= 3 ? 'success' : strength === 2 ? 'warning' : 'error';

  const onSubmit = async (values) => {
    setSubmitError(null);
    try {
      await authApi.register({
        fullName: values.fullName,
        email: values.email,
        phone: values.phone,
        password: values.password,
        role: values.role,
      });
      setSuccessEmail(values.email);
      setResendCooldown(60);
    } catch (err) {
      if (err.errorCode === 'EMAIL_CONFLICT') {
        setError('email', { message: 'Email này đã được sử dụng.' });
      } else if (err.errorCode === 'PHONE_CONFLICT') {
        setError('phone', { message: 'Số điện thoại này đã được sử dụng.' });
      } else if (Array.isArray(err.fieldErrors) && err.fieldErrors.length) {
        err.fieldErrors.forEach((fe) => setError(fe.field, { message: fe.message }));
      } else if (err.status === 429) {
        setSubmitError('Bạn đã đăng ký quá nhiều lần. Vui lòng thử lại sau.');
      } else {
        setSubmitError(err.message || 'Đăng ký thất bại, vui lòng thử lại.');
      }
    }
  };

  const handleResend = async () => {
    try {
      await authApi.resendVerification({ email: successEmail });
      notify.success('Đã gửi lại email xác thực.');
      setResendCooldown(60);
    } catch (e) {
      notify.apiError(e);
    }
  };

  if (successEmail) {
    return (
      <Box sx={{ textAlign: 'center' }}>
        <MarkEmailReadOutlinedIcon sx={{ fontSize: 64, color: 'primary.main' }} />
        <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 1 }}>
          Kiểm tra email của bạn
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Đã gửi email xác thực tới <b>{maskEmail(successEmail)}</b>. Vui lòng kiểm tra hộp thư để
          kích hoạt tài khoản.
        </Typography>
        <Stack spacing={1.5} sx={{ mt: 3 }}>
          <Button variant="outlined" onClick={handleResend} disabled={resendCooldown > 0}>
            {resendCooldown > 0 ? `Gửi lại (${resendCooldown}s)` : 'Gửi lại email'}
          </Button>
          <Button variant="contained" component={RouterLink} to="/dang-nhap">
            Về trang đăng nhập
          </Button>
        </Stack>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 800 }}>
        Tạo tài khoản
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Miễn phí · Chỉ mất 1 phút
      </Typography>

      {submitError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {submitError}
        </Alert>
      )}

      <Stack spacing={2}>
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Bạn muốn dùng tài khoản để: *
          </Typography>
          <Controller
            name="role"
            control={control}
            render={({ field }) => (
              <ToggleButtonGroup
                exclusive
                value={field.value}
                onChange={(_, v) => v && field.onChange(v)}
                fullWidth
                color="primary"
              >
                <ToggleButton value={ROLES.TENANT} sx={{ flexDirection: 'column', py: 1.5, gap: 0.5 }}>
                  <SearchIcon />
                  <Typography variant="body2" fontWeight={600}>
                    Tìm phòng
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    (Người thuê)
                  </Typography>
                </ToggleButton>
                <ToggleButton value={ROLES.LANDLORD} sx={{ flexDirection: 'column', py: 1.5, gap: 0.5 }}>
                  <HomeWorkIcon />
                  <Typography variant="body2" fontWeight={600}>
                    Cho thuê
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    (Chủ trọ)
                  </Typography>
                </ToggleButton>
              </ToggleButtonGroup>
            )}
          />
          {errors.role && <FormHelperText error>{errors.role.message}</FormHelperText>}
        </Box>

        <TextField
          label="Họ và tên"
          fullWidth
          error={!!errors.fullName}
          helperText={errors.fullName?.message}
          {...register('fullName')}
        />
        <TextField
          label="Email"
          type="email"
          fullWidth
          error={!!errors.email}
          helperText={errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Số điện thoại"
          fullWidth
          error={!!errors.phone}
          helperText={errors.phone?.message}
          {...register('phone')}
        />
        <Box>
          <TextField
            label="Mật khẩu"
            type={showPassword ? 'text' : 'password'}
            fullWidth
            error={!!errors.password}
            helperText={errors.password?.message}
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
          {password && (
            <Box sx={{ mt: 1 }}>
              <LinearProgress
                variant="determinate"
                value={(strength / 3) * 100}
                color={strengthColor}
                sx={{ borderRadius: 1, height: 6 }}
              />
              <Typography variant="caption" color="text.secondary">
                Độ mạnh: {strengthLabel}
              </Typography>
              <Stack spacing={0.25} sx={{ mt: 0.5 }}>
                <ChecklistRow ok={hasLen} text="Tối thiểu 8 ký tự" />
                <ChecklistRow ok={hasAlpha} text="Có chữ cái" />
                <ChecklistRow ok={hasDigit} text="Có chữ số" />
              </Stack>
            </Box>
          )}
        </Box>
        <TextField
          label="Nhập lại mật khẩu"
          type={showPassword ? 'text' : 'password'}
          fullWidth
          error={!!errors.confirmPassword}
          helperText={errors.confirmPassword?.message}
          {...register('confirmPassword')}
        />

        {role === ROLES.LANDLORD && (
          <Alert severity="info">
            Chủ trọ cần bổ sung thông tin liên hệ sau khi đăng ký để được đăng tin công khai.
          </Alert>
        )}

        <Box>
          <Controller
            name="acceptTerms"
            control={control}
            render={({ field }) => (
              <FormControlLabel
                control={<Checkbox checked={field.value} onChange={field.onChange} />}
                label={
                  <Typography variant="body2">
                    Tôi đồng ý với{' '}
                    <Link component={RouterLink} to="/dieu-khoan" target="_blank" underline="hover">
                      Điều khoản sử dụng
                    </Link>
                  </Typography>
                }
              />
            )}
          />
          {errors.acceptTerms && <FormHelperText error>{errors.acceptTerms.message}</FormHelperText>}
        </Box>

        <Button type="submit" variant="contained" size="large" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Đang đăng ký…' : 'Đăng ký'}
        </Button>
      </Stack>

      <Typography variant="body2" sx={{ mt: 3, textAlign: 'center' }}>
        Đã có tài khoản?{' '}
        <Link component={RouterLink} to="/dang-nhap" underline="hover" fontWeight={600}>
          Đăng nhập
        </Link>
      </Typography>
    </Box>
  );
};

export default RegisterPage;
