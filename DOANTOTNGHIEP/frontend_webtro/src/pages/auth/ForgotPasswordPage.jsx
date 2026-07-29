import { useEffect, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import { Box, Typography, TextField, Button, Link, Stack } from '@mui/material';
import MarkEmailReadOutlinedIcon from '@mui/icons-material/MarkEmailReadOutlined';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import authApi from '@/api/authApi';
import { notify } from '@/utils/toast';

const schema = yup.object({
  email: yup
    .string()
    .trim()
    .required('Vui lòng nhập email')
    .email('Email không đúng định dạng'),
});

const maskEmail = (email = '') => {
  const [name, domain] = email.split('@');
  if (!domain) return email;
  return `${name.slice(0, 1)}***@${domain}`;
};

const ForgotPasswordPage = () => {
  const [sentTo, setSentTo] = useState(null);
  const [cooldown, setCooldown] = useState(0);

  const {
    register,
    handleSubmit,
    getValues,
    formState: { errors, isSubmitting },
  } = useForm({ resolver: yupResolver(schema), defaultValues: { email: '' } });

  useEffect(() => {
    document.title = 'Quên mật khẩu — Webtro';
  }, []);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const t = setTimeout(() => setCooldown((n) => n - 1), 1000);
    return () => clearTimeout(t);
  }, [cooldown]);

  const submit = async (values) => {
    try {
      // BE luôn trả 200 dù email không tồn tại (chống dò tài khoản) — vẫn hiện màn thành công.
      await authApi.forgotPassword({ email: values.email });
    } catch (err) {
      if (err.status === 429) {
        notify.error('Bạn đã yêu cầu quá nhiều lần. Vui lòng thử lại sau ít phút.');
        return;
      }
      notify.apiError(err);
      return;
    }
    setSentTo(values.email);
    setCooldown(60);
  };

  const handleResend = () => submit({ email: getValues('email') || sentTo });

  if (sentTo) {
    return (
      <Box sx={{ textAlign: 'center' }}>
        <MarkEmailReadOutlinedIcon sx={{ fontSize: 64, color: 'primary.main' }} />
        <Typography variant="h5" component="h1" sx={{ fontWeight: 800, mt: 1 }}>
          Kiểm tra email
        </Typography>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Nếu email tồn tại trong hệ thống, chúng tôi đã gửi link đặt lại mật khẩu tới:
          <br />
          <b>{maskEmail(sentTo)}</b>
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Link có hiệu lực trong 30 phút.
        </Typography>
        <Stack spacing={1.5} sx={{ mt: 3 }}>
          <Button variant="outlined" onClick={handleResend} disabled={cooldown > 0}>
            {cooldown > 0 ? `Gửi lại (${cooldown}s)` : 'Gửi lại'}
          </Button>
          <Button component={RouterLink} to="/dang-nhap" startIcon={<ArrowBackIcon />}>
            Về trang đăng nhập
          </Button>
        </Stack>
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={handleSubmit(submit)} noValidate>
      <Typography variant="h5" component="h1" sx={{ fontWeight: 800 }}>
        Quên mật khẩu
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Nhập email đã đăng ký, chúng tôi sẽ gửi link đặt lại mật khẩu.
      </Typography>

      <Stack spacing={2}>
        <TextField
          label="Email"
          type="email"
          fullWidth
          autoFocus
          error={!!errors.email}
          helperText={errors.email?.message}
          {...register('email')}
        />
        <Button type="submit" variant="contained" size="large" fullWidth disabled={isSubmitting}>
          {isSubmitting ? 'Đang gửi…' : 'Gửi link đặt lại'}
        </Button>
      </Stack>

      <Box sx={{ mt: 3, textAlign: 'center' }}>
        <Link component={RouterLink} to="/dang-nhap" underline="hover" variant="body2">
          ← Về trang đăng nhập
        </Link>
      </Box>
    </Box>
  );
};

export default ForgotPasswordPage;
