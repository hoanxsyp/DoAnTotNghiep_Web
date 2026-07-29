import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Card, CardContent, Grid, TextField, Button, Stack, InputAdornment, IconButton, Typography,
} from '@mui/material';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import authApi from '@/api/authApi';
import PageHeader from '@/components/dashboard/PageHeader';
import notify from '@/utils/toast';

const schema = yup.object({
  currentPassword: yup.string().required('Vui lòng nhập mật khẩu hiện tại'),
  newPassword: yup
    .string().required('Vui lòng nhập mật khẩu mới')
    .min(8, 'Mật khẩu tối thiểu 8 ký tự')
    .matches(/[A-Za-z]/, 'Mật khẩu phải có chữ')
    .matches(/[0-9]/, 'Mật khẩu phải có số'),
  confirmPassword: yup
    .string().required('Vui lòng xác nhận mật khẩu')
    .oneOf([yup.ref('newPassword')], 'Mật khẩu xác nhận không khớp'),
});

const ChangePasswordPage = () => {
  const [show, setShow] = useState({ cur: false, next: false, confirm: false });
  const {
    control, handleSubmit, reset, formState: { errors, isSubmitting },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = async (values) => {
    try {
      await authApi.changePassword({
        currentPassword: values.currentPassword,
        newPassword: values.newPassword,
      });
      notify.success('Đổi mật khẩu thành công');
      reset();
    } catch (e) {
      notify.apiError(e, 'Đổi mật khẩu thất bại');
    }
  };

  const toggle = (key) => setShow((s) => ({ ...s, [key]: !s[key] }));
  const eye = (visible, onClick) => (
    <InputAdornment position="end">
      <IconButton onClick={onClick} edge="end" tabIndex={-1}>
        {visible ? <VisibilityOff /> : <Visibility />}
      </IconButton>
    </InputAdornment>
  );

  return (
    <Box>
      <PageHeader title="Đổi mật khẩu" subtitle="Bảo mật tài khoản của bạn bằng mật khẩu mạnh" />
      <Grid container justifyContent="center">
        <Grid item xs={12} md={7} lg={6}>
          <Card component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <CardContent>
              <Stack spacing={2.5}>
                <Controller name="currentPassword" control={control} render={({ field }) => (
                  <TextField {...field} type={show.cur ? 'text' : 'password'} label="Mật khẩu hiện tại" fullWidth
                    error={!!errors.currentPassword} helperText={errors.currentPassword?.message}
                    InputProps={{ endAdornment: eye(show.cur, () => toggle('cur')) }} />
                )} />
                <Controller name="newPassword" control={control} render={({ field }) => (
                  <TextField {...field} type={show.next ? 'text' : 'password'} label="Mật khẩu mới" fullWidth
                    error={!!errors.newPassword} helperText={errors.newPassword?.message || 'Tối thiểu 8 ký tự, gồm chữ và số'}
                    InputProps={{ endAdornment: eye(show.next, () => toggle('next')) }} />
                )} />
                <Controller name="confirmPassword" control={control} render={({ field }) => (
                  <TextField {...field} type={show.confirm ? 'text' : 'password'} label="Xác nhận mật khẩu mới" fullWidth
                    error={!!errors.confirmPassword} helperText={errors.confirmPassword?.message}
                    InputProps={{ endAdornment: eye(show.confirm, () => toggle('confirm')) }} />
                )} />
                <Typography variant="caption" color="text.secondary">
                  Sau khi đổi mật khẩu, bạn có thể cần đăng nhập lại trên các thiết bị khác.
                </Typography>
                <Stack direction="row" justifyContent="flex-end">
                  <Button type="submit" variant="contained" disabled={isSubmitting}>
                    {isSubmitting ? 'Đang xử lý…' : 'Đổi mật khẩu'}
                  </Button>
                </Stack>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ChangePasswordPage;
