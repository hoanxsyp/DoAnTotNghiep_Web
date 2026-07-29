import { useEffect, useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  TextField,
  MenuItem,
  Stack,
} from '@mui/material';

/**
 * Dialog xác nhận dùng lại (docs/04 mục 6, component #14).
 *
 * Props:
 * - open, title, message (node), severity 'info'|'warning'|'error'
 * - confirmText, cancelText, onConfirm(payload), onCancel, loading
 * - requireReason (bool): thêm Select lý do + ô mô tả bắt buộc (khóa/từ chối/hoàn tiền)
 * - reasonLabel, reasonOptions (arr {value,label})
 * - requireTypeToConfirm (str): buộc gõ đúng chuỗi để bật nút xác nhận (xóa tài khoản...)
 *
 * onConfirm nhận { reason, note } khi requireReason.
 */
export default function ConfirmDialog({
  open,
  title = 'Xác nhận',
  message,
  severity = 'warning',
  confirmText = 'Xác nhận',
  cancelText = 'Hủy',
  onConfirm,
  onCancel,
  loading = false,
  requireReason = false,
  reasonLabel = 'Lý do',
  reasonOptions = [],
  requireTypeToConfirm,
}) {
  const [reason, setReason] = useState('');
  const [note, setNote] = useState('');
  const [typed, setTyped] = useState('');
  const [touched, setTouched] = useState(false);

  // Reset state mỗi khi mở lại
  useEffect(() => {
    if (open) {
      setReason('');
      setNote('');
      setTyped('');
      setTouched(false);
    }
  }, [open]);

  const hasReasonOptions = reasonOptions && reasonOptions.length > 0;
  const reasonMissing = requireReason && ((hasReasonOptions && !reason) || !note.trim());
  const typeMismatch = requireTypeToConfirm && typed !== requireTypeToConfirm;
  const confirmDisabled = loading || reasonMissing || typeMismatch;

  const confirmColor = severity === 'error' ? 'error' : severity === 'info' ? 'primary' : 'warning';

  const handleConfirm = () => {
    setTouched(true);
    if (confirmDisabled) return;
    onConfirm?.(requireReason ? { reason: hasReasonOptions ? reason : undefined, note: note.trim() } : undefined);
  };

  return (
    <Dialog open={!!open} onClose={loading ? undefined : onCancel} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {typeof message === 'string' ? (
          <DialogContentText>{message}</DialogContentText>
        ) : (
          message
        )}

        {requireReason && (
          <Stack spacing={2} sx={{ mt: 2 }}>
            {hasReasonOptions && (
              <TextField
                select
                label={reasonLabel}
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                error={touched && !reason}
                helperText={touched && !reason ? 'Vui lòng chọn lý do' : ''}
              >
                {reasonOptions.map((opt) => (
                  <MenuItem key={opt.value} value={opt.value}>
                    {opt.label}
                  </MenuItem>
                ))}
              </TextField>
            )}
            <TextField
              label="Nội dung chi tiết"
              value={note}
              onChange={(e) => setNote(e.target.value)}
              multiline
              minRows={2}
              error={touched && !note.trim()}
              helperText={touched && !note.trim() ? 'Vui lòng nhập nội dung' : ''}
            />
          </Stack>
        )}

        {requireTypeToConfirm && (
          <TextField
            sx={{ mt: 2 }}
            label={`Gõ "${requireTypeToConfirm}" để xác nhận`}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            error={touched && typeMismatch}
            helperText={touched && typeMismatch ? 'Chuỗi xác nhận chưa đúng' : ''}
          />
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onCancel} disabled={loading} color="inherit">
          {cancelText}
        </Button>
        <Button
          onClick={handleConfirm}
          variant="contained"
          color={confirmColor}
          disabled={confirmDisabled}
        >
          {loading ? 'Đang xử lý…' : confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
