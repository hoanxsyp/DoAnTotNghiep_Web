import { useEffect, useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField,
} from '@mui/material';
import AddressSelector from '@/components/filter/AddressSelector';
import rentalUnitApi from '@/api/rentalUnitApi';
import notify from '@/utils/toast';

const EMPTY = { name: '', unitLabel: '', provinceId: null, districtId: null, wardId: null, addressDetail: '' };

/**
 * Hộp thoại tạo/sửa phòng. Phòng đã có đánh giá thì khoá địa chỉ và số phòng (RU-4), chỉ quản trị
 * viên sửa được (`canOverrideLock`). Đổi địa chỉ phòng sẽ đồng bộ sang mọi tin của phòng.
 */
export default function RentalUnitFormDialog({ open, unit, canOverrideLock = false, onClose, onSaved }) {
  const editing = !!unit;
  const locked = editing && unit.addressLocked && !canOverrideLock;
  const [form, setForm] = useState(EMPTY);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!open) return;
    setError('');
    setForm(unit ? {
      name: unit.name || '',
      unitLabel: unit.unitLabel || '',
      provinceId: unit.provinceId ?? null,
      districtId: unit.districtId ?? null,
      wardId: unit.wardId ?? null,
      addressDetail: unit.addressDetail || '',
    } : EMPTY);
  }, [open, unit]);

  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const valid = form.name.trim() && form.wardId && form.addressDetail.trim().length >= 5;

  const save = async () => {
    setSaving(true);
    setError('');
    const payload = {
      name: form.name.trim(),
      unitLabel: form.unitLabel.trim(),
      provinceId: form.provinceId,
      districtId: form.districtId,
      wardId: form.wardId,
      addressDetail: form.addressDetail.trim(),
    };
    try {
      const saved = editing
        ? await rentalUnitApi.update(unit.id, payload)
        : await rentalUnitApi.create(payload);
      notify.success(editing ? 'Đã cập nhật phòng' : 'Đã tạo phòng');
      onSaved?.(saved);
    } catch (e) {
      setError(e?.message || 'Lưu phòng thất bại');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Dialog open={open} onClose={saving ? undefined : onClose} fullWidth maxWidth="sm">
      <DialogTitle>{editing ? 'Sửa phòng' : 'Thêm phòng'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {locked && (
            <Alert severity="warning">
              Phòng đã có đánh giá nên không đổi được địa chỉ và số phòng. Bạn vẫn đổi được tên phòng.
            </Alert>
          )}
          {editing && !locked && (
            <Alert severity="info">
              Đổi địa chỉ sẽ cập nhật mọi tin của phòng; tin đang hiển thị sẽ phải chờ duyệt lại.
            </Alert>
          )}
          {error && <Alert severity="error">{error}</Alert>}
          <TextField
            label="Tên phòng" required fullWidth value={form.name} inputProps={{ maxLength: 150 }}
            onChange={(e) => set({ name: e.target.value })}
            helperText="Ví dụ: Phòng 101 - Nhà trọ Hồng Loan"
          />
          <AddressSelector
            required
            disabled={locked}
            value={{ provinceId: form.provinceId, districtId: form.districtId, wardId: form.wardId }}
            onChange={(v) => set(v)}
          />
          <TextField
            label="Số nhà, tên đường" required fullWidth multiline minRows={2} disabled={locked}
            value={form.addressDetail} inputProps={{ maxLength: 255 }}
            onChange={(e) => set({ addressDetail: e.target.value })}
          />
          <TextField
            label="Số phòng (nếu có)" fullWidth disabled={locked} value={form.unitLabel} inputProps={{ maxLength: 50 }}
            onChange={(e) => set({ unitLabel: e.target.value })}
            helperText="Dùng để phân biệt các phòng cùng địa chỉ"
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={saving}>Huỷ</Button>
        <Button variant="contained" onClick={save} disabled={saving || !valid}>
          {saving ? 'Đang lưu…' : 'Lưu'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
