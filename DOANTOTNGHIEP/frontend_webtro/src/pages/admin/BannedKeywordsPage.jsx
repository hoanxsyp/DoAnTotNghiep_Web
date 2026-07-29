import { useState } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { yupResolver } from '@hookform/resolvers/yup';
import * as yup from 'yup';
import {
  Box, Stack, Chip, IconButton, Button, Switch, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, MenuItem, InputAdornment,
  CircularProgress, Tooltip,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import SearchIcon from '@mui/icons-material/Search';
import adminApi from '@/api/adminApi';
import {
  BANNED_KEYWORD_SEVERITY_META, BANNED_KEYWORD_SCOPE_META, metaChip,
} from '@/config/adminMeta';
import { notify } from '@/utils/toast';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';
import ConfirmDialog from '@/components/admin/ConfirmDialog';

const SEVERITIES = Object.keys(BANNED_KEYWORD_SEVERITY_META);
const SCOPES = Object.keys(BANNED_KEYWORD_SCOPE_META);

const schema = yup.object({
  keyword: yup.string().trim().required('Vui lòng nhập từ khóa').max(100),
  severity: yup.string().oneOf(SEVERITIES).required(),
  scope: yup.string().oneOf(SCOPES).required(),
});

/**
 * Quản lý từ khóa cấm (docs/04 §10, canonical §5.3 §11.10). CRUD + bật/tắt, phân theo mức độ
 * (MILD/SEVERE) và phạm vi (LISTING/COMMENT/BOTH). Quyền SYSTEM_CONFIG_MANAGE.
 */
const BannedKeywordsPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getBannedKeywords, { initialSize: 50 });

  const [keyword, setKeyword] = useState('');
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState(null);
  const [deleting, setDeleting] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const { control, handleSubmit, reset, formState: { errors } } = useForm({
    resolver: yupResolver(schema),
    defaultValues: { keyword: '', severity: 'MILD', scope: 'BOTH' },
  });

  const openCreate = () => { setEditing(null); reset({ keyword: '', severity: 'MILD', scope: 'BOTH' }); setDialogOpen(true); };
  const openEdit = (row) => { setEditing(row); reset({ keyword: row.keyword || '', severity: row.severity || 'MILD', scope: row.scope || 'BOTH' }); setDialogOpen(true); };

  const onSubmit = async (values) => {
    setSubmitting(true);
    try {
      if (editing) await adminApi.updateBannedKeyword(editing.id, values);
      else await adminApi.createBannedKeyword(values);
      notify.success(editing ? 'Đã cập nhật từ khóa' : 'Đã thêm từ khóa');
      setDialogOpen(false);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const toggle = async (row) => {
    try { await adminApi.toggleBannedKeyword(row.id); reload(); } catch (err) { notify.apiError(err); }
  };

  const handleDelete = async () => {
    setSubmitting(true);
    try {
      await adminApi.deleteBannedKeyword(deleting.id);
      notify.success('Đã xóa từ khóa');
      setDeleting(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'keyword', label: 'Từ khóa', render: (r) => r.keyword },
    {
      key: 'severity', label: 'Mức độ', render: (r) => {
        const m = metaChip(BANNED_KEYWORD_SEVERITY_META, r.severity);
        return <Chip size="small" label={m.label} color={m.color} />;
      },
    },
    { key: 'scope', label: 'Phạm vi', render: (r) => <Chip size="small" variant="outlined" label={BANNED_KEYWORD_SCOPE_META[r.scope] || r.scope} /> },
    {
      key: 'active', label: 'Áp dụng', align: 'center', render: (r) => (
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
        title="Quản lý từ khóa cấm"
        subtitle={`${total} từ khóa`}
        actions={<Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm từ khóa</Button>}
      />

      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm từ khóa"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && setParams({ keyword: keyword.trim() || undefined })}
          sx={{ flexGrow: 1, maxWidth: 360 }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> }}
        />
        <Button variant="outlined" onClick={() => setParams({ keyword: keyword.trim() || undefined })}>Tìm</Button>
      </Stack>

      <AdminDataTable
        columns={columns}
        rows={items}
        loading={loading}
        error={error}
        onRetry={reload}
        emptyText="Chưa có từ khóa cấm nào"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      <Dialog open={dialogOpen} onClose={() => !submitting && setDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>{editing ? 'Sửa từ khóa cấm' : 'Thêm từ khóa cấm'}</DialogTitle>
        <Box component="form" onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Stack spacing={2} sx={{ mt: 0.5 }}>
              <Controller name="keyword" control={control} render={({ field }) => (
                <TextField {...field} label="Từ khóa" fullWidth error={!!errors.keyword} helperText={errors.keyword?.message} />
              )} />
              <Controller name="severity" control={control} render={({ field }) => (
                <TextField {...field} select label="Mức độ" fullWidth>
                  {SEVERITIES.map((s) => <MenuItem key={s} value={s}>{BANNED_KEYWORD_SEVERITY_META[s].label}</MenuItem>)}
                </TextField>
              )} />
              <Controller name="scope" control={control} render={({ field }) => (
                <TextField {...field} select label="Phạm vi áp dụng" fullWidth>
                  {SCOPES.map((s) => <MenuItem key={s} value={s}>{BANNED_KEYWORD_SCOPE_META[s]}</MenuItem>)}
                </TextField>
              )} />
            </Stack>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setDialogOpen(false)} disabled={submitting}>Hủy</Button>
            <Button type="submit" variant="contained" disabled={submitting} startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : null}>
              {editing ? 'Lưu' : 'Thêm'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      <ConfirmDialog
        open={Boolean(deleting)}
        title="Xóa từ khóa cấm"
        message={`Xóa từ khóa "${deleting?.keyword}"?`}
        confirmText="Xóa"
        confirmColor="error"
        loading={submitting}
        onClose={() => setDeleting(null)}
        onConfirm={handleDelete}
      />
    </Box>
  );
};

export default BannedKeywordsPage;
