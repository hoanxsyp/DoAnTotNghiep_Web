import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Stack, Chip, IconButton, Button, Switch, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, CircularProgress, Tooltip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import adminApi from '@/api/adminApi';
import { CATEGORY_CODES } from '@/constants';
import { notify } from '@/utils/toast';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const schema = yup.object({
  name: yup.string().trim().required('Vui lòng nhập tên danh mục').max(100, 'Tối đa 100 ký tự'),
  code: yup.string().trim().required('Vui lòng nhập mã').max(50, 'Tối đa 50 ký tự'),
  displayOrder: yup.number().typeError('Phải là số').min(0, 'Không âm').required('Nhập thứ tự'),
  iconUrl: yup.string().trim().max(255).nullable(),
});

/**
 * Quản lý danh mục tin (docs/04 §10.5, ADM-05). CRUD + bật/tắt hiển thị + thứ tự. Quyền
 * CATALOG_MANAGE. Form dùng React Hook Form + Yup, lỗi hiển thị field-level, nút submit có loading.
 */
const CategoriesPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, reload,
  } = usePagedResource(adminApi.getCategories, { initialSize: 50 });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { control, handleSubmit, reset, formState: { errors } } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { name: '', code: '', displayOrder: 0, iconUrl: '' },
  });

  const openCreate = () => {
    setEditing(null);
    reset({ name: '', code: '', displayOrder: (items?.length || 0), iconUrl: '' });
    setDialogOpen(true);
  };
  const openEdit = (row) => {
    setEditing(row);
    reset({ name: row.name || '', code: row.code || '', displayOrder: row.displayOrder ?? 0, iconUrl: row.iconUrl || '' });
    setDialogOpen(true);
  };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      if (editing) await adminApi.updateCategory(editing.id, values);
      else await adminApi.createCategory(values);
      notify.success(editing ? 'Đã cập nhật danh mục' : 'Đã tạo danh mục');
      setDialogOpen(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const toggle = async (row) => {
    try {
      await adminApi.toggleCategory(row.id);
      reload();
    } catch (err) {
      notify.apiError(err);
    }
  };

  const handleDelete = async () => {
    setSubmitting(true);
    try {
      await adminApi.deleteCategory(deleting.id);
      notify.success('Đã xóa danh mục');
      setDeleting(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'displayOrder', label: 'Thứ tự', align: 'center', width: 80, render: (r) => r.displayOrder ?? '—' },
    { key: 'name', label: 'Tên danh mục', render: (r) => r.name },
    { key: 'code', label: 'Mã', render: (r) => <Chip size="small" variant="outlined" label={r.code} /> },
    { key: 'listingCount', label: 'Số tin', align: 'center', render: (r) => r.listingCount ?? 0 },
    {
      key: 'active', label: 'Hiển thị', align: 'center', render: (r) => (
        <Switch size="small" checked={r.active ?? true} onChange={() => toggle(r)} />
      ),
    },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Stack direction="row" justifyContent="flex-end">
          <Tooltip title="Sửa"><IconButton size="small" onClick={() => openEdit(r)}><EditIcon fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Xóa"><IconButton size="small" color="error" onClick={() => setDeleting(r)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
        </Stack>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Quản lý danh mục"
        subtitle={`${total} danh mục`}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm danh mục</Button>}
      />

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Chưa có danh mục nào"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Dialog open={dialogOpen} onClose={() => !submitting && setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{editing ? 'Sửa danh mục' : 'Thêm danh mục'}</DialogTitle>
        <Box component="form" onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Controller name="name" control={control} render={({ field }) => (
                <TextField {...field} label="Tên danh mục" fullWidth error={!!errors.name} helperText={errors.name?.message} />
              )} />
              <Controller name="code" control={control} render={({ field }) => (
                <TextField {...field} label="Mã (CategoryCode)" fullWidth error={!!errors.code} helperText={errors.code?.message || 'Ví dụ: BOARDING_HOUSE'} placeholder={Object.keys(CATEGORY_CODES)[0]} />
              )} />
              <Controller name="displayOrder" control={control} render={({ field }) => (
                <TextField {...field} type="number" label="Thứ tự hiển thị" fullWidth error={!!errors.displayOrder} helperText={errors.displayOrder?.message} />
              )} />
              <Controller name="iconUrl" control={control} render={({ field }) => (
                <TextField {...field} label="Icon URL (tùy chọn)" fullWidth error={!!errors.iconUrl} helperText={errors.iconUrl?.message} />
              )} />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={submitting}>Hủy</Button>
            <Button type="submit" variant="contained" disabled={submitting} startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : null}>
              {editing ? 'Lưu' : 'Tạo'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Xóa danh mục"
        message={`Xóa danh mục "${deleting?.name}"? Không xóa được nếu còn tin thuộc danh mục.`}
        confirmText="Xóa"
        confirmColor="error"
        loading={submitting}
        onClose={() => setDeleting(null)}
        onConfirm={handleDelete}
      />
    </Box>
  );
};

export default CategoriesPage;
