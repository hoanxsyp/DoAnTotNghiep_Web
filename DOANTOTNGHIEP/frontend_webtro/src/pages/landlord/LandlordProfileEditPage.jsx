import { useEffect, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Grid, Card, CardContent, TextField, Button, Stack, Typography, Chip, FormControlLabel,
  Switch, Alert, Divider,
} from '@mui/material';
import VerifiedIcon from '@mui/icons-material/Verified';
import userApi from '@/api/userApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import notify from '@/utils/toast';

const schema = yup.object({
  contactName: yup.string().trim().required('Vui lòng nhập tên liên hệ').min(2, 'Tối thiểu 2 ký tự').max(100),
  contactPhone: yup.string().trim().required('Vui lòng nhập số điện thoại').matches(/^0\d{9}$/, 'Số điện thoại không hợp lệ'),
  contactZalo: yup.string().trim().matches(/^(0\d{9})?$/, 'Zalo không hợp lệ'),
  businessName: yup.string().max(150),
  businessAddress: yup.string().max(255),
  description: yup.string().max(1000),
});

const VERIFY_META = {
  VERIFIED: { label: 'Đã xác thực', color: 'success' },
  PENDING: { label: 'Đang chờ duyệt', color: 'warning' },
  REJECTED: { label: 'Bị từ chối', color: 'error' },
  NOT_SUBMITTED: { label: 'Chưa xác thực', color: 'default' },
};

const LandlordProfileEditPage = () => {
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [requesting, setRequesting] = useState(false);

  const {
    control, handleSubmit, reset, formState: { errors, isSubmitting, isDirty },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { contactName: '', contactPhone: '', contactZalo: '', businessName: '', businessAddress: '', description: '', chatEnabled: true },
  });

  const load = async () => {
    setLoading(true);
    try {
      const data = await userApi.getLandlordProfile();
      setProfile(data);
      reset({
        contactName: data?.contactName || '',
        contactPhone: data?.contactPhone || '',
        contactZalo: data?.contactZalo || '',
        businessName: data?.businessName || '',
        businessAddress: data?.businessAddress || '',
        description: data?.description || '',
        chatEnabled: data?.chatEnabled ?? true,
      });
    } catch (e) {
      notify.apiError(e, 'Không tải được hồ sơ chủ trọ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const onSubmit = async (values) => {
    try {
      const payload = {
        contactName: values.contactName.trim(),
        contactPhone: values.contactPhone.trim(),
        contactZalo: values.contactZalo?.trim() || undefined,
        businessName: values.businessName?.trim() || undefined,
        businessAddress: values.businessAddress?.trim() || undefined,
        description: values.description?.trim() || undefined,
        chatEnabled: values.chatEnabled,
      };
      const updated = await userApi.updateLandlordProfile(payload);
      setProfile(updated);
      notify.success('Đã cập nhật hồ sơ chủ trọ');
    } catch (e) {
      notify.apiError(e, 'Cập nhật hồ sơ thất bại');
    }
  };

  const handleRequestVerify = async () => {
    setRequesting(true);
    try {
      await userApi.requestLandlordVerification({});
      notify.success('Đã gửi yêu cầu xác thực. Quản trị viên sẽ xem xét trong 1–2 ngày làm việc.');
      load();
    } catch (e) {
      notify.apiError(e, 'Gửi yêu cầu xác thực thất bại');
    } finally {
      setRequesting(false);
    }
  };

  if (loading) {
    return <Box><PageHeader title="Hồ sơ chủ trọ" /><LoadingSkeleton variant="form" /></Box>;
  }

  const vStatus = profile?.verificationStatus || 'NOT_SUBMITTED';
  const vMeta = VERIFY_META[vStatus] || VERIFY_META.NOT_SUBMITTED;

  return (
    <Box>
      <PageHeader title="Hồ sơ chủ trọ" subtitle="Thông tin liên hệ hiển thị trên tin đăng của bạn" />

      {profile?.postingSuspended && (
        <Alert severity="error" sx={{ mb: 3 }}>
          Tài khoản đang tạm bị hạn chế đăng tin do có {profile.warningCountLast30Days} cảnh báo vi phạm trong 30 ngày qua.
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>Trạng thái xác thực</Typography>
              <Chip
                color={vMeta.color}
                icon={vStatus === 'VERIFIED' ? <VerifiedIcon /> : undefined}
                label={vMeta.label}
              />
              <Divider sx={{ my: 2 }} />
              <Stack spacing={1}>
                <Row label="Điểm uy tín" value={profile?.trustScore ?? '—'} />
                <Row label="Tổng số tin" value={profile?.totalListings ?? 0} />
                <Row label="Tin đang hiển thị" value={profile?.activeListings ?? 0} />
              </Stack>
              {vStatus !== 'VERIFIED' && vStatus !== 'PENDING' && (
                <Button fullWidth variant="contained" sx={{ mt: 2 }} onClick={handleRequestVerify} disabled={requesting}>
                  {requesting ? 'Đang gửi…' : 'Gửi yêu cầu xác thực'}
                </Button>
              )}
              {vStatus === 'PENDING' && (
                <Alert severity="info" sx={{ mt: 2 }}>Yêu cầu xác thực đang được xem xét.</Alert>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={8}>
          <Card component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Thông tin liên hệ</Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Controller name="contactName" control={control} render={({ field }) => (
                    <TextField {...field} label="Tên liên hệ" fullWidth required error={!!errors.contactName} helperText={errors.contactName?.message} />
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="contactPhone" control={control} render={({ field }) => (
                    <TextField {...field} label="Số điện thoại" fullWidth required error={!!errors.contactPhone} helperText={errors.contactPhone?.message} />
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="contactZalo" control={control} render={({ field }) => (
                    <TextField {...field} label="Zalo (tùy chọn)" fullWidth error={!!errors.contactZalo} helperText={errors.contactZalo?.message} />
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="businessName" control={control} render={({ field }) => (
                    <TextField {...field} label="Tên cơ sở / nhà trọ" fullWidth error={!!errors.businessName} helperText={errors.businessName?.message} />
                  )} />
                </Grid>
                <Grid item xs={12}>
                  <Controller name="businessAddress" control={control} render={({ field }) => (
                    <TextField {...field} label="Địa chỉ cơ sở" fullWidth error={!!errors.businessAddress} helperText={errors.businessAddress?.message} />
                  )} />
                </Grid>
                <Grid item xs={12}>
                  <Controller name="description" control={control} render={({ field }) => (
                    <TextField {...field} label="Giới thiệu" fullWidth multiline minRows={3} error={!!errors.description} helperText={errors.description?.message || 'Tối đa 1000 ký tự'} />
                  )} />
                </Grid>
                <Grid item xs={12}>
                  <Controller name="chatEnabled" control={control} render={({ field }) => (
                    <FormControlLabel
                      control={<Switch checked={field.value} onChange={field.onChange} />}
                      label="Cho phép người thuê nhắn tin trực tiếp (nếu tắt, chỉ hiển thị số điện thoại)"
                    />
                  )} />
                </Grid>
              </Grid>
              <Stack direction="row" justifyContent="flex-end" spacing={1.5} sx={{ mt: 3 }}>
                <Button type="button" onClick={() => load()} disabled={isSubmitting}>Khôi phục</Button>
                <Button type="submit" variant="contained" disabled={isSubmitting || !isDirty}>
                  {isSubmitting ? 'Đang lưu…' : 'Lưu thay đổi'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

const Row = ({ label, value }) => (
  <Stack direction="row" justifyContent="space-between">
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Typography variant="body2" sx={{ fontWeight: 600 }}>{value}</Typography>
  </Stack>
);

export default LandlordProfileEditPage;
