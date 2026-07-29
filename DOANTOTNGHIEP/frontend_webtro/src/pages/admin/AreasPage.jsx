import { useState, useEffect } from 'react';
import {
  Box, Stack, Chip, IconButton, Button, Switch, Dialog, DialogTitle,
  DialogContent, DialogActions, TextField, InputAdornment,
  CircularProgress, Tooltip, Grid, Card, CardHeader, CardContent, List,
  ListItemButton, ListItemText, Typography,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import SearchIcon from '@mui/icons-material/Search';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import usePagedResource from '@/hooks/usePagedResource';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import AdminDataTable from '@/components/admin/AdminDataTable';

/**
 * Quản lý khu vực hành chính (docs/04 §10.5, ADM-06). Bảng tỉnh/thành + bật/tắt + sửa tên; xem
 * quận/huyện và phường/xã theo cấp; nhập khu vực từ file. Quyền CATALOG_MANAGE.
 */
const AreasPage = () => {
  const {
    items, total, loading, error, page, size, setPage, setSize, setParams, reload,
  } = usePagedResource(adminApi.getProvinces, { initialSize: 20 });

  const [keyword, setKeyword] = useState('');
  const [editing, setEditing] = useState(null);
  const [editName, setEditName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [file, setFile] = useState(null);

  // Cascading drilldown
  const [selectedProvince, setSelectedProvince] = useState(null);
  const [districts, setDistricts] = useState([]);
  const [selectedDistrict, setSelectedDistrict] = useState(null);
  const [wards, setWards] = useState([]);
  const [loadingChild, setLoadingChild] = useState(false);

  useEffect(() => {
    if (!selectedProvince) { setDistricts([]); setSelectedDistrict(null); return; }
    setLoadingChild(true);
    adminApi.getDistricts(selectedProvince.id)
      .then((d) => setDistricts(d || []))
      .catch((err) => notify.apiError(err))
      .finally(() => setLoadingChild(false));
  }, [selectedProvince]);

  useEffect(() => {
    if (!selectedDistrict) { setWards([]); return; }
    setLoadingChild(true);
    adminApi.getWards(selectedDistrict.id)
      .then((w) => setWards(w || []))
      .catch((err) => notify.apiError(err))
      .finally(() => setLoadingChild(false));
  }, [selectedDistrict]);

  const toggle = async (row) => {
    try { await adminApi.toggleProvince(row.id); reload(); } catch (err) { notify.apiError(err); }
  };

  const saveEdit = async () => {
    setSubmitting(true);
    try {
      await adminApi.updateProvince(editing.id, { name: editName.trim() });
      notify.success('Đã cập nhật tỉnh/thành');
      setEditing(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const handleImport = async () => {
    if (!file) return;
    setSubmitting(true);
    try {
      const fd = new FormData();
      fd.append('file', file);
      await adminApi.importAreas(fd);
      notify.success('Đã nhập dữ liệu khu vực');
      setImportOpen(false);
      setFile(null);
      reload();
    } catch (err) {
      notify.apiError(err);
    } finally {
      setSubmitting(false);
    }
  };

  const columns = [
    { key: 'code', label: 'Mã', width: 90, render: (r) => r.code || '—' },
    { key: 'name', label: 'Tỉnh / Thành phố', render: (r) => r.name },
    { key: 'districtCount', label: 'Số quận/huyện', align: 'center', render: (r) => r.districtCount ?? '—' },
    { key: 'listingCount', label: 'Số tin', align: 'center', render: (r) => r.listingCount ?? 0 },
    {
      key: 'active', label: 'Hiển thị', align: 'center', render: (r) => (
        <Switch size="small" checked={r.active ?? true} onChange={() => toggle(r)} />
      ),
    },
    {
      key: 'actions', label: '', align: 'right', render: (r) => (
        <Stack direction="row" justifyContent="flex-end">
          <Tooltip title="Xem quận/huyện"><IconButton size="small" onClick={() => { setSelectedProvince(r); setSelectedDistrict(null); }}><SearchIcon fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Sửa tên"><IconButton size="small" onClick={() => { setEditing(r); setEditName(r.name || ''); }}><EditIcon fontSize="small" /></IconButton></Tooltip>
        </Stack>
      ),
    },
  ];

  return (
    <Box>
      <AdminPageHeader
        title="Quản lý khu vực"
        subtitle={`${total} tỉnh/thành`}
        actions={<Button variant="contained" startIcon={<UploadFileIcon />} onClick={() => setImportOpen(true)}>Nhập từ file</Button>}
      />

      <Stack direction="row" spacing={1.5} sx={{ mb: 2 }}>
        <TextField
          size="small"
          placeholder="Tìm tỉnh/thành"
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
        emptyText="Chưa có dữ liệu khu vực"
        page={page}
        size={size}
        total={total}
        onPageChange={setPage}
        onSizeChange={setSize}
      />

      {selectedProvince && (
        <Grid container spacing={2} sx={{ mt: 1 }}>
          <Grid item xs={12} md={6}>
            <Card variant="outlined">
              <CardHeader title={`Quận/Huyện của ${selectedProvince.name}`} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} />
              <CardContent sx={{ pt: 0, maxHeight: 320, overflowY: 'auto' }}>
                {loadingChild && !districts.length ? (
                  <Box sx={{ py: 2, textAlign: 'center' }}><CircularProgress size={22} /></Box>
                ) : !districts.length ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Không có dữ liệu</Typography>
                ) : (
                  <List dense disablePadding>
                    {districts.map((d) => (
                      <ListItemButton key={d.id} selected={selectedDistrict?.id === d.id} onClick={() => setSelectedDistrict(d)}>
                        <ListItemText primary={d.name} secondary={d.wardCount != null ? `${d.wardCount} phường/xã` : undefined} />
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </CardContent>
            </Card>
          </Grid>
          <Grid item xs={12} md={6}>
            <Card variant="outlined">
              <CardHeader title={selectedDistrict ? `Phường/Xã của ${selectedDistrict.name}` : 'Chọn một quận/huyện'} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} />
              <CardContent sx={{ pt: 0, maxHeight: 320, overflowY: 'auto' }}>
                {!selectedDistrict ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Chọn quận/huyện để xem phường/xã</Typography>
                ) : loadingChild ? (
                  <Box sx={{ py: 2, textAlign: 'center' }}><CircularProgress size={22} /></Box>
                ) : !wards.length ? (
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>Không có dữ liệu</Typography>
                ) : (
                  <List dense disablePadding>
                    {wards.map((w) => (
                      <ListItemButton key={w.id}>
                        <ListItemText primary={w.name} />
                        {w.active === false && <Chip size="small" label="Ẩn" />}
                      </ListItemButton>
                    ))}
                  </List>
                )}
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      <Dialog open={Boolean(editing)} onClose={() => !submitting && setEditing(null)} maxWidth="xs" fullWidth>
        <DialogTitle>Sửa tỉnh/thành</DialogTitle>
        <DialogContent>
          <TextField autoFocus fullWidth label="Tên tỉnh/thành" value={editName} onChange={(e) => setEditName(e.target.value)} sx={{ mt: 1 }} />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditing(null)} disabled={submitting}>Hủy</Button>
          <Button variant="contained" onClick={saveEdit} disabled={submitting || !editName.trim()} startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : null}>Lưu</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={importOpen} onClose={() => !submitting && setImportOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Nhập khu vực từ file</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Chọn file danh sách tỉnh/quận/phường (định dạng theo chuẩn hệ thống).
          </Typography>
          <Button variant="outlined" component="label" startIcon={<UploadFileIcon />} fullWidth>
            {file ? file.name : 'Chọn file'}
            <input hidden type="file" accept=".csv,.json,.xlsx" onChange={(e) => setFile(e.target.files?.[0] || null)} />
          </Button>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setImportOpen(false)} disabled={submitting}>Hủy</Button>
          <Button variant="contained" onClick={handleImport} disabled={submitting || !file} startIcon={submitting ? <CircularProgress size={16} color="inherit" /> : null}>Nhập</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default AreasPage;
