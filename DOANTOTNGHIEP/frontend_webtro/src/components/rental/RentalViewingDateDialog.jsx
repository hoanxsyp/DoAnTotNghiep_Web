import { useEffect, useState } from 'react';
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material';
import dayjs from 'dayjs';
import rentalApi from '@/api/rentalApi';
import notify from '@/utils/toast';

export default function RentalViewingDateDialog({ open, request, onClose, onSuccess }) {
  const [viewingDate, setViewingDate] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) setViewingDate(request?.desiredViewingDate || '');
  }, [open, request]);

  const submit = async () => {
    if (!request?.id || !viewingDate) return;
    setSubmitting(true);
    try {
      const data = await rentalApi.updateViewingDate(request.id, viewingDate);
      notify.success('Đã cập nhật ngày muốn xem phòng.');
      onSuccess?.(data);
      onClose?.();
    } catch (error) {
      notify.apiError(error, 'Không cập nhật được ngày muốn xem');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={!!open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Đổi ngày muốn xem phòng</DialogTitle>
      <DialogContent sx={{ pt: 1 }}>
        <TextField
          label="Ngày muốn xem phòng"
          type="date"
          value={viewingDate}
          onChange={(event) => setViewingDate(event.target.value)}
          inputProps={{ min: dayjs().format('YYYY-MM-DD') }}
          InputLabelProps={{ shrink: true }}
          required
          fullWidth
          sx={{ mt: 1 }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button color="inherit" onClick={onClose} disabled={submitting}>Đóng</Button>
        <Button variant="contained" onClick={submit} disabled={submitting || !viewingDate}>
          {submitting ? 'Đang lưu…' : 'Lưu ngày xem'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
