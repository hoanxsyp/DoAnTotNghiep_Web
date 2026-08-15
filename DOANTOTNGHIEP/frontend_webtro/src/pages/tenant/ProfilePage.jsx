import { useEffect, useRef, useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import dayjs from 'dayjs';
import {
  Box, Grid, Card, CardContent, Stack, Avatar, Button, TextField, MenuItem,
  Typography, Divider, Chip, IconButton, Tooltip,
} from '@mui/material';
import PhotoCameraIcon from '@mui/icons-material/PhotoCamera';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import VerifiedIcon from '@mui/icons-material/Verified';
import { DatePicker } from '@mui/x-date-pickers/DatePicker';
import { useDispatch } from 'react-redux';
import userApi from '@/api/userApi';
import { bootstrapAuth } from '@/redux/authSlice';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const GENDERS = [
  { value: 'MALE', label: 'Nam' },
  { value: 'FEMALE', label: 'Nữ' },
  { value: 'OTHER', label: 'Khác' },
  { value: 'UNKNOWN', label: 'Không muốn nêu' },
];

const schema = yup.object({
  fullName: yup
    .string().trim().required('Vui lòng nhập họ tên')
    .min(2, 'Họ tên tối thiểu 2 ký tự').max(100, 'Họ tên tối đa 100 ký tự'),
  gender: yup.string().oneOf(['MALE', 'FEMALE', 'OTHER', 'UNKNOWN']).nullable(),
  dateOfBirth: yup.date().nullable().max(new Date(), 'Ngày sinh không hợp lệ'),
  address: yup.string().max(255, 'Địa chỉ tối đa 255 ký tự'),
  bio: yup.string().max(500, 'Giới thiệu tối đa 500 ký tự'),
});

const ProfilePage = () => {
  const dispatch = useDispatch();
  const fileRef = useRef(null);
  const [loading, setLoading] = useState(true);
  const [profile, setProfile] = useState(null);
  const [avatarUrl, setAvatarUrl] = useState(null);
  const [uploading, setUploading] = useState(false);

  const {
    control, handleSubmit, reset, formState: { errors, isSubmitting, isDirty },
  } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { fullName: '', gender: 'UNKNOWN', dateOfBirth: null, address: '', bio: '' },
  });

  const load = async () => {
    setLoading(true);
    try {
      const data = await userApi.getMe();
      setProfile(data);
      setAvatarUrl(data?.avatarUrl || null);
      reset({
        fullName: data?.fullName || '',
        gender: data?.gender || 'UNKNOWN',
        dateOfBirth: data?.dateOfBirth ? dayjs(data.dateOfBirth) : null,
        address: data?.address || '',
        bio: data?.bio || '',
      });
    } catch (e) {
      notify.apiError(e, 'Không tải được hồ sơ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  const onSubmit = async (values) => {
    try {
      const payload = {
        fullName: values.fullName.trim(),
        gender: values.gender || undefined,
        dateOfBirth: values.dateOfBirth ? dayjs(values.dateOfBirth).format('YYYY-MM-DD') : undefined,
        address: values.address?.trim() || undefined,
        bio: values.bio?.trim() || undefined,
      };
      const updated = await userApi.updateMe(payload);
      setProfile(updated);
      notify.success('Đã cập nhật hồ sơ');
      dispatch(bootstrapAuth());
    } catch (e) {
      notify.apiError(e, 'Cập nhật hồ sơ thất bại');
    }
  };

  const handlePickFile = () => fileRef.current?.click();

  const handleAvatarChange = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
      notify.error('Ảnh phải là JPG, PNG hoặc WEBP');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      notify.error('Ảnh đại diện tối đa 5MB');
      return;
    }
    const formData = new FormData();
    formData.append('file', file);
    setUploading(true);
    try {
      const res = await userApi.updateAvatar(formData);
      setAvatarUrl(res?.avatarUrl || null);
      notify.success('Đã cập nhật ảnh đại diện');
      dispatch(bootstrapAuth());
    } catch (err) {
      notify.apiError(err, 'Tải ảnh đại diện thất bại');
    } finally {
      setUploading(false);
    }
  };

  const handleDeleteAvatar = async () => {
    setUploading(true);
    try {
      await userApi.deleteAvatar();
      setAvatarUrl(null);
      notify.success('Đã xóa ảnh đại diện');
      dispatch(bootstrapAuth());
    } catch (err) {
      notify.apiError(err, 'Xóa ảnh đại diện thất bại');
    } finally {
      setUploading(false);
    }
  };

  if (loading) {
    return (
      <Box>
        <PageHeader title="Hồ sơ cá nhân" subtitle="Xem và cập nhật thông tin của bạn" />
        <LoadingSkeleton variant="form" />
      </Box>
    );
  }

  const landlord = profile?.landlordProfile;

  return (
    <Box>
      <PageHeader title="Hồ sơ cá nhân" subtitle="Xem và cập nhật thông tin của bạn" />
      <Grid container spacing={3}>
        {/* Cột trái: avatar + thông tin tài khoản */}
        <Grid item xs={12} md={4}>
          <Card>
            <CardContent>
              <Stack alignItems="center" spacing={1.5}>
                <Box sx={{ position: 'relative' }}>
                  <Avatar src={avatarUrl || undefined} sx={{ width: 104, height: 104, fontSize: 40 }}>
                    {profile?.fullName?.charAt(0)?.toUpperCase()}
                  </Avatar>
                  <Tooltip title="Đổi ảnh đại diện">
                    <IconButton
                      onClick={handlePickFile}
                      disabled={uploading}
                      sx={{ position: 'absolute', bottom: 0, right: 0, bgcolor: 'primary.main', color: '#fff', '&:hover': { bgcolor: 'primary.dark' } }}
                    >
                      <PhotoCameraIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Box>
                <input ref={fileRef} type="file" hidden accept="image/jpeg,image/png,image/webp" onChange={handleAvatarChange} />
                <Typography variant="h6" sx={{ fontWeight: 700 }}>{profile?.fullName}</Typography>
                {avatarUrl && (
                  <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={handleDeleteAvatar} disabled={uploading}>
                    Xóa ảnh
                  </Button>
                )}
              </Stack>

              <Divider sx={{ my: 2 }} />
              <Stack spacing={1}>
                <InfoRow label="Email" value={profile?.email} verified={profile?.emailVerified} />
                <InfoRow label="Số điện thoại" value={profile?.phone || 'Chưa cập nhật'} verified={profile?.phoneVerified} />
                <InfoRow label="Vai trò" value={roleLabel(profile?.role)} />
                <InfoRow label="Tham gia" value={formatDateTime(profile?.createdAt, 'DD/MM/YYYY')} />
              </Stack>

              {landlord && (
                <>
                  <Divider sx={{ my: 2 }} />
                  <Typography variant="subtitle2" sx={{ fontWeight: 700, mb: 1 }}>Hồ sơ chủ trọ</Typography>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Chip
                        size="small"
                        color={landlord.verified ? 'success' : 'default'}
                        icon={landlord.verified ? <VerifiedIcon /> : undefined}
                        label={landlord.verified ? 'Đã xác thực' : 'Chưa xác thực'}
                      />
                      <Chip size="small" variant="outlined" label={`Uy tín ${landlord.trustScore ?? '—'}`} />
                    </Stack>
                    <InfoRow label="Tin đang hiển thị" value={landlord.activeListings ?? 0} />
                    <InfoRow label="Điểm đánh giá" value={landlord.averageRating != null ? `${landlord.averageRating}/5` : 'Chưa có'} />
                  </Stack>
                </>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Cột phải: form chỉnh sửa */}
        <Grid item xs={12} md={8}>
          <Card component="form" onSubmit={handleSubmit(onSubmit)} noValidate>
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>Thông tin cá nhân</Typography>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <Controller name="fullName" control={control} render={({ field }) => (
                    <TextField {...field} label="Họ và tên" fullWidth required
                      error={!!errors.fullName} helperText={errors.fullName?.message} />
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="gender" control={control} render={({ field }) => (
                    <TextField {...field} select label="Giới tính" fullWidth
                      error={!!errors.gender} helperText={errors.gender?.message}>
                      {GENDERS.map((g) => <MenuItem key={g.value} value={g.value}>{g.label}</MenuItem>)}
                    </TextField>
                  )} />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <Controller name="dateOfBirth" control={control} render={({ field }) => (
                    <DatePicker
                      label="Ngày sinh"
                      value={field.value}
                      onChange={field.onChange}
                      format="DD/MM/YYYY"
                      disableFuture
                      slotProps={{ textField: { fullWidth: true, error: !!errors.dateOfBirth, helperText: errors.dateOfBirth?.message } }}
                    />
                  )} />
                </Grid>
                <Grid item xs={12}>
                  <Controller name="address" control={control} render={({ field }) => (
                    <TextField {...field} label="Địa chỉ" fullWidth
                      error={!!errors.address} helperText={errors.address?.message} />
                  )} />
                </Grid>
                <Grid item xs={12}>
                  <Controller name="bio" control={control} render={({ field }) => (
                    <TextField {...field} label="Giới thiệu bản thân" fullWidth multiline minRows={3}
                      error={!!errors.bio} helperText={errors.bio?.message || 'Tối đa 500 ký tự'} />
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

const roleLabel = (r) => ({
  ROLE_TENANT: 'Người thuê', ROLE_LANDLORD: 'Chủ trọ',
  ROLE_MODERATOR: 'Kiểm duyệt', ROLE_ADMIN: 'Quản trị',
}[r] || r || '—');

const InfoRow = ({ label, value, verified }) => (
  <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
    <Typography variant="body2" color="text.secondary">{label}</Typography>
    <Stack direction="row" spacing={0.5} alignItems="center" sx={{ minWidth: 0 }}>
      <Typography variant="body2" sx={{ fontWeight: 600, textAlign: 'right' }} noWrap>{value}</Typography>
      {verified === true && <VerifiedIcon color="success" sx={{ fontSize: 16 }} />}
    </Stack>
  </Stack>
);

export default ProfilePage;
