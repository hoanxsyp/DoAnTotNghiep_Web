import { useState, useEffect } from 'react';
import {
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions,
  Button, TextField, CircularProgress, MenuItem,
} from '@mui/material';

/**
 * Hộp thoại xác nhận hành động quản trị. Hỗ trợ tùy chọn nhập lý do (bắt buộc với từ chối/khóa/ẩn
 * theo state machine canonical §5.1) và chọn mức độ. Nhận callback qua props (luật F3).
 *
 * @param {boolean} open
 * @param {string} title
 * @param {string} message
 * @param {string} confirmText nhãn nút xác nhận
 * @param {string} confirmColor màu nút xác nhận (error/primary/warning...)
 * @param {boolean} requireReason  hiện ô lý do và bắt buộc nhập
 * @param {string} reasonLabel
 * @param {Array<{value,label}>} selectOptions  nếu có -> hiện dropdown (vd severity, reason code)
 * @param {string} selectLabel
 * @param {boolean} loading  đang gửi
 * @param {Function} onClose
 * @param {Function} onConfirm  ({ reason, selectValue }) => void
 */
const ConfirmDialog = ({
  open,
  title,
  message,
  confirmText = 'Xác nhận',
  confirmColor = 'primary',
  requireReason = false,
  reasonLabel = 'Lý do',
  reasonRequired = true,
  selectOptions = null,
  selectLabel = 'Mức độ',
  loading = false,
  onClose,
  onConfirm,
}) => {
  const [reason, setReason] = useState('');
  const [selectValue, setSelectValue] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (open) {
      setReason('');
      setSelectValue(selectOptions?.[0]?.value ?? '');
      setTouched(false);
    }
  }, [open, selectOptions]);

  const reasonError = requireReason && reasonRequired && touched && !reason.trim();

  const handleConfirm = () => {
    setTouched(true);
    if (requireReason && reasonRequired && !reason.trim()) return;
    onConfirm({ reason: reason.trim(), selectValue });
  };

  return (
    <Dialog open={open} onClose={loading ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        {message && <DialogContentText sx={{ mb: 2 }}>{message}</DialogContentText>}
        {selectOptions && (
          <TextField
            select
            fullWidth
            label={selectLabel}
            value={selectValue}
            onChange={(e) => setSelectValue(e.target.value)}
            sx={{ mb: 2 }}
          >
            {selectOptions.map((opt) => (
              <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>
            ))}
          </TextField>
        )}
        {requireReason && (
          <TextField
            fullWidth
            multiline
            minRows={2}
            label={reasonLabel}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            onBlur={() => setTouched(true)}
            error={reasonError}
            helperText={reasonError ? 'Vui lòng nhập lý do' : ' '}
          />
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>Hủy</Button>
        <Button
          variant="contained"
          color={confirmColor}
          onClick={handleConfirm}
          disabled={loading}
          startIcon={loading ? <CircularProgress size={16} color="inherit" /> : null}
        >
          {confirmText}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

export default ConfirmDialog;
