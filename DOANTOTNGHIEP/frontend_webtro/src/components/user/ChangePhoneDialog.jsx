import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material';
import userApi from '@/api/userApi';
import notify from '@/utils/toast';

const PHONE_REGEX = /^0\d{9}$/;

/**
 * Đổi số điện thoại tài khoản (PATCH /users/me/contact). Số này hiện làm SĐT liên hệ trên tin của
 * người không có hồ sơ chủ trọ; với chủ trọ, backend đồng bộ luôn SĐT trên hồ sơ chủ trọ.
 */
export default function ChangePhoneDialog({ open, currentPhone, onClose, onSuccess }) {
  const [phone, setPhone] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) setPhone(currentPhone || '');
  }, [open, currentPhone]);

  const value = phone.trim();
  const invalid = value !== '' && !PHONE_REGEX.test(value);
  const unchanged = value === (currentPhone || '');

  const submit = async () => {
    if (!value || invalid || unchanged) return;
    setSubmitting(true);
    try {
      await userApi.updateContact({ contactPhone: value });
      notify.success('Đã đổi số điện thoại');
      onSuccess?.();
      onClose?.();
    } catch (error) {
      notify.apiError(error, 'Không đổi được số điện thoại');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={!!open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Đổi số điện thoại</DialogTitle>
      <DialogContent sx={{ pt: 1 }}>
        <TextField
          label="Số điện thoại mới"
          value={phone}
          onChange={(event) => setPhone(event.target.value)}
          onKeyDown={(event) => event.key === 'Enter' && submit()}
          error={invalid}
          helperText={invalid ? 'Số điện thoại không hợp lệ' : 'Gồm 10 chữ số, bắt đầu bằng 0'}
          inputProps={{ inputMode: 'tel', maxLength: 10 }}
          required
          fullWidth
          autoFocus
          sx={{ mt: 1 }}
        />
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          Số này được hiển thị làm số liên hệ trên tin đăng của bạn.
        </Typography>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button color="inherit" onClick={onClose} disabled={submitting}>Đóng</Button>
        <Button variant="contained" onClick={submit} disabled={submitting || !value || invalid || unchanged}>
          {submitting ? 'Đang lưu…' : 'Lưu số mới'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
