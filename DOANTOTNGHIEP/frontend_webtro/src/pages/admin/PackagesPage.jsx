import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Chip, IconButton, Button, Switch, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, MenuItem, InputAdornment,
  CircularProgress, Tooltip, Grid,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import { formatCurrency } from '@/utils/format';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';

const PURPOSE_META = {
  PUSH_TOP: 'Đẩy lên đầu',
  HIGHLIGHT: 'Làm nổi bật',
  BOTH: 'Đẩy đầu + nổi bật',
};
const PURPOSES = Object.keys(PURPOSE_META);

const schema = yup.object({
  name: yup.string().trim().required('Vui lòng nhập tên gói').max(150),
  purpose: yup.string().oneOf(PURPOSES).required(),
  price: yup.number().typeError('Phải là số').min(0, 'Không âm').required('Nhập giá'),
  durationDays: yup.number().typeError('Phải là số').integer('Số nguyên').min(1, 'Tối thiểu 1 ngày').required('Nhập số ngày'),
  priority: yup.number().typeError('Phải là số').integer().min(0).max(100, 'Tối đa 100').required('Nhập độ ưu tiên'),
  description: yup.string().trim().max(500).nullable(),
});

/**
 * Quản lý gói dịch vụ đẩy tin (docs/04 §10.6, ADM-08). CRUD + bật/tắt. Quyền PACKAGE_MANAGE — chỉ
 * Admin (Moderator không thấy nhóm Tài chính). Form React Hook Form + Yup.
 */
const PackagesPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, reload,
  } = usePagedResource(adminApi.getPackages, { initialSize: 50 });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { control, handleSubmit, reset, formState: { errors } } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { name: '', purpose: 'PUSH_TOP', price: 0, durationDays: 7, priority: 10, description: '' },
  });

  const openCreate = () => {
    setEditing(null);
    reset({ name: '', purpose: 'PUSH_TOP', price: 0, durationDays: 7, priority: 10, description: '' });
    setDialogOpen(true);
  };
  const openEdit = (row) => {
    setEditing(row);
    reset({
      name: row.name || '', purpose: row.purpose || 'PUSH_TOP', price: row.price ?? 0,
      durationDays: row.durationDays ?? 7, priority: row.priority ?? 10, description: row.description || '',
    });
    setDialogOpen(true);
  };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      if (editing) await adminApi.updatePackage(editing.id, values);
      else await adminApi.createPackage(values);
      notify.success(editing ? 'Đã cập nhật gói' : 'Đã tạo gói');
      setDialogOpen(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const toggleActive = async (row) => {
    try {
      await adminApi.updatePackage(row.id, { active: !(row.active ?? true) });
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const columns = [
    { key: 'name', label: 'Tên gói', render: (r) => r.name },
    { key: 'purpose', label: 'Mục đích', render: (r) => <Chip size="small" variant="outlined" label={PURPOSE_META[r.purpose] || r.purpose} /> },
    { key: 'price', label: 'Giá', align: 'right', render: (r) => formatCurrency(r.price) },
    { key: 'durationDays', label: 'Số ngày', align: 'center', render: (r) => r.durationDays },
    { key: 'priority', label: 'Ưu tiên', align: 'center', render: (r) => r.priority },
    {
      key: 'active', label: 'Kích hoạt', align: 'center', render: (r) => (
        <Switch size="small" checked={r.active ?? true} onChange={() => toggleActive(r)} />
      ),
    },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Tooltip title="Sửa"><IconButton size="small" onClick={() => openEdit(r)}><EditIcon fontSize="small" /></IconButton></Tooltip>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Quản lý gói dịch vụ"
        subtitle={`${total} gói`}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm gói</Button>}
      />

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Chưa có gói dịch vụ nào"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Dialog open={dialogOpen} onClose={() => !submitting && setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editing ? 'Sửa gói dịch vụ' : 'Thêm gói dịch vụ'}</DialogTitle>
        <Box component="form" onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Grid container spacing={2} sx={{ mt: 0 }}>
              <Grid item xs={12}>
                <Controller name="name" control={control} render={({ field }) => (
                  <TextField {...field} label="Tên gói" fullWidth error={!!errors.name} helperText={errors.name?.message} />
                )} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller name="purpose" control={control} render={({ field }) => (
                  <TextField {...field} select label="Mục đích" fullWidth>
                    {PURPOSES.map((p) => <MenuItem key={p} value={p}>{PURPOSE_META[p]}</MenuItem>)}
                  </TextField>
                )} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller name="price" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Giá" fullWidth error={!!errors.price} helperText={errors.price?.message}
                    InputProps={{ endAdornment: <InputAdornment position="end">đ</InputAdornment> }} />
                )} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller name="durationDays" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Số ngày hiệu lực" fullWidth error={!!errors.durationDays} helperText={errors.durationDays?.message} />
                )} />
              </Grid>
              <Grid item xs={12} sm={6}>
                <Controller name="priority" control={control} render={({ field }) => (
                  <TextField {...field} type="number" label="Độ ưu tiên (0-100)" fullWidth error={!!errors.priority} helperText={errors.priority?.message} />
                )} />
              </Grid>
              <Grid item xs={12}>
                <Controller name="description" control={control} render={({ field }) => (
                  <TextField {...field} label="Mô tả" fullWidth multiline minRows={2} error={!!errors.description} helperText={errors.description?.message} />
                )} />
              </Grid>
            </Grid>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={submitting}>Hủy</Button>
            <Button type="submit" variant="contained" disabled={submitting} startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : null}>
              {editing ? 'Lưu' : 'Tạo'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>
    </Box>
  );
};

export default PackagesPage;
