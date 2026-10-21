import { useEffect, useState } from 'react';
import {
  Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, Stack, TextField, Typography,
} from '@mui/material';
import dayjs from 'dayjs';
import rentalApi from '@/api/rentalApi';
import notify from '@/utils/toast';

export default function RentalRequestDialog({ open, listing, onClose, onSuccess }) {
  const [viewingDate, setViewingDate] = useState('');
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) setViewingDate(dayjs().add(1, 'day').format('YYYY-MM-DD'));
  }, [open]);

  const submit = async () => {
    const listingId = listing?.listingId ?? listing?.id;
    if (!viewingDate || !listingId) return;
    setSubmitting(true);
    try {
      const data = await rentalApi.create(listingId, viewingDate);
      notify.success('Đã gửi yêu cầu thuê phòng tới chủ trọ.');
      onSuccess?.(data);
      onClose?.();
    } catch (error) {
      notify.apiError(error, 'Không gửi được yêu cầu thuê phòng');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={!!open} onClose={submitting ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>Xác nhận thuê phòng</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 0.5 }}>
          <Typography>
            Bạn có chắc muốn gửi yêu cầu thuê phòng “{listing?.title || ''}” tới chủ trọ?
          </Typography>
          <TextField
            label="Ngày muốn đi xem phòng"
            type="date"
            value={viewingDate}
            onChange={(event) => setViewingDate(event.target.value)}
            inputProps={{ min: dayjs().format('YYYY-MM-DD') }}
            InputLabelProps={{ shrink: true }}
            required
            fullWidth
          />
          <Alert severity="info">
            Việc xem phòng và ký hợp đồng được hai bên thực hiện trực tiếp, không thực hiện trên website.
          </Alert>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button color="inherit" onClick={onClose} disabled={submitting}>Không</Button>
        <Button variant="contained" onClick={submit} disabled={submitting || !viewingDate}>
          {submitting ? 'Đang gửi…' : 'Có'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
