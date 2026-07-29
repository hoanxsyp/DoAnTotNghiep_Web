import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Stack, Chip, IconButton, Button, Switch, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, MenuItem, CircularProgress, Tooltip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import adminApi from '@/api/adminApi';
import { AMENITY_GROUP_META } from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const GROUPS = Object.keys(AMENITY_GROUP_META);

const schema = yup.object({
  name: yup.string().trim().required('Vui lòng nhập tên tiện ích').max(100),
  group: yup.string().oneOf(GROUPS, 'Chọn nhóm').required('Chọn nhóm'),
  icon: yup.string().trim().max(50).nullable(),
});

/**
 * Quản lý tiện ích (docs/04 §10.5, ADM-07). CRUD + bật/tắt + phân theo 4 nhóm AmenityGroup
 * (canonical §5). Quyền CATALOG_MANAGE. Form React Hook Form + Yup.
 */
const AmenitiesPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, reload,
  } = usePagedResource(adminApi.getAmenities, { initialSize: 50 });

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { control, handleSubmit, reset, formState: { errors } } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { name: '', group: GROUPS[0], icon: '' },
  });

  const openCreate = () => { setEditing(null); reset({ name: '', group: GROUPS[0], icon: '' }); setDialogOpen(true); };
  const openEdit = (row) => { setEditing(row); reset({ name: row.name || '', group: row.group || row.amenityGroup || GROUPS[0], icon: row.icon || '' }); setDialogOpen(true); };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      if (editing) await adminApi.updateAmenity(editing.id, values);
      else await adminApi.createAmenity(values);
      notify.success(editing ? 'Đã cập nhật tiện ích' : 'Đã tạo tiện ích');
      setDialogOpen(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const toggle = async (row) => {
    try { await adminApi.toggleAmenity(row.id); reload(); } catch (err) { notify.apiError(err); }
  };

  const handleDelete = async () => {
    setSubmitting(true);
    try {
      await adminApi.deleteAmenity(deleting.id);
      notify.success('Đã xóa tiện ích');
      setDeleting(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'name', label: 'Tên tiện ích', render: (r) => r.name },
    { key: 'group', label: 'Nhóm', render: (r) => <Chip size="small" variant="outlined" label={AMENITY_GROUP_META[r.group || r.amenityGroup] || r.group || r.amenityGroup} /> },
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
        title="Quản lý tiện ích"
        subtitle={`${total} tiện ích`}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm tiện ích</Button>}
      />

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Chưa có tiện ích nào"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Dialog open={dialogOpen} onClose={() => !submitting && setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{editing ? 'Sửa tiện ích' : 'Thêm tiện ích'}</DialogTitle>
        <Box component="form" onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Controller name="name" control={control} render={({ field }) => (
                <TextField {...field} label="Tên tiện ích" fullWidth error={!!errors.name} helperText={errors.name?.message} />
              )} />
              <Controller name="group" control={control} render={({ field }) => (
                <TextField {...field} select label="Nhóm tiện ích" fullWidth error={!!errors.group} helperText={errors.group?.message}>
                  {GROUPS.map((g) => <MenuItem key={g} value={g}>{AMENITY_GROUP_META[g]}</MenuItem>)}
                </TextField>
              )} />
              <Controller name="icon" control={control} render={({ field }) => (
                <TextField {...field} label="Icon (tùy chọn)" fullWidth error={!!errors.icon} helperText={errors.icon?.message} />
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
        title="Xóa tiện ích"
        message={`Xóa tiện ích "${deleting?.name}"?`}
        confirmText="Xóa"
        confirmColor="error"
        loading={submitting}
        onClose={() => setDeleting(null)}
        onConfirm={handleDelete}
      />
    </Box>
  );
};

export default AmenitiesPage;
