import { useState, useEffect } from 'react';
import {
  Box, Card, CardHeader, CardContent, Stack, Switch, TextField, Typography,
  Button, Divider, FormControlLabel, Dialog, DialogTitle, DialogContent,
  DialogActions, CircularProgress,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import RestartAltIcon from '@mui/icons-material/RestartAlt';

/**
 * Trình sửa cấu hình dùng chung cho AiConfig / SystemConfig. Nhận `groups` = [{ title, items }] với
 * mỗi item { key, value, type, label, defaultValue, min, max }. Theo dõi thay đổi cục bộ, chỉ gửi
 * các key đã đổi. Nếu `requireReason`, hỏi lý do trước khi lưu (audit bắt buộc). Nhận dữ liệu +
 * callback qua props (luật F3) — không tự gọi API.
 *
 * @param {Function} onSave (configs[{key,value}], reason) => Promise
 */
const ConfigEditor = ({ groups = [], onSave, saving = false, requireReason = true }) => {
  const [values, setValues] = useState({});
  const [reasonOpen, setReasonOpen] = useState(false);
  const [reason, setReason] = useState('');

  // Khởi tạo bản đồ giá trị từ groups mỗi khi dữ liệu nguồn đổi.
  useEffect(() => {
    const map = {};
    groups.forEach((g) => g.items.forEach((it) => { map[it.key] = it.value; }));
    setValues(map);
  }, [groups]);

  const original = {};
  groups.forEach((g) => g.items.forEach((it) => { original[it.key] = it.value; }));

  const changedKeys = Object.keys(values).filter((k) => values[k] !== original[k]);
  const dirty = changedKeys.length > 0;

  const setValue = (key, v) => setValues((prev) => ({ ...prev, [key]: v }));

  const resetAll = () => {
    const map = {};
    groups.forEach((g) => g.items.forEach((it) => { map[it.key] = it.value; }));
    setValues(map);
  };

  const collectConfigs = () => changedKeys.map((k) => ({ key: k, value: values[k] }));

  const doSave = async (withReason) => {
    await onSave(collectConfigs(), withReason);
    setReasonOpen(false);
    setReason('');
  };

  const handleSaveClick = () => {
    if (requireReason) setReasonOpen(true);
    else doSave(undefined);
  };

  const renderControl = (item) => {
    const v = values[item.key];
    if (item.type === 'BOOLEAN') {
      return (
        <FormControlLabel
          control={<Switch checked={!!v} onChange={(e) => setValue(item.key, e.target.checked)} />}
          label={v ? 'Bật' : 'Tắt'}
        />
      );
    }
    const isNumber = item.type === 'INT' || item.type === 'DECIMAL';
    return (
      <TextField
        size="small"
        type={isNumber ? 'number' : 'text'}
        value={v ?? ''}
        onChange={(e) => setValue(item.key, isNumber ? (e.target.value === '' ? '' : Number(e.target.value)) : e.target.value)}
        inputProps={isNumber ? { min: item.min, max: item.max, step: item.type === 'DECIMAL' ? 'any' : 1 } : undefined}
        helperText={isNumber && (item.min != null || item.max != null) ? `Khoảng: ${item.min ?? '−∞'} – ${item.max ?? '∞'}` : ' '}
        sx={{ width: 180 }}
      />
    );
  };

  return (
    <Box>
      <Stack spacing={2}>
        {groups.map((group) => (
          <Card key={group.title} variant="outlined">
            <CardHeader title={group.title} titleTypographyProps={{ variant: 'subtitle1', fontWeight: 700 }} />
            <CardContent sx={{ pt: 0 }}>
              <Stack divider={<Divider flexItem />} spacing={0}>
                {group.items.map((item) => (
                  <Box key={item.key} sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2, py: 1.25, flexWrap: 'wrap' }}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body2" fontWeight={600}>{item.label || item.key}</Typography>
                      <Typography variant="caption" color="text.secondary" fontFamily="monospace">{item.key}</Typography>
                    </Box>
                    {renderControl(item)}
                  </Box>
                ))}
              </Stack>
            </CardContent>
          </Card>
        ))}
      </Stack>

      <Box sx={{
        position: 'sticky', bottom: 0, mt: 2, py: 1.5,
        display: 'flex', justifyContent: 'flex-end', gap: 1,
        bgcolor: 'background.default', borderTop: (t) => `1px solid ${t.palette.divider}`,
      }}>
        <Typography variant="body2" color="text.secondary" sx={{ mr: 'auto', alignSelf: 'center' }}>
          {dirty ? `${changedKeys.length} thay đổi chưa lưu` : 'Chưa có thay đổi'}
        </Typography>
        <Button startIcon={<RestartAltIcon />} onClick={resetAll} disabled={!dirty || saving}>Hoàn tác</Button>
        <Button variant="contained" startIcon={<SaveIcon />} onClick={handleSaveClick} disabled={!dirty || saving}>Lưu thay đổi</Button>
      </Box>

      <Dialog open={reasonOpen} onClose={() => !saving && setReasonOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Xác nhận thay đổi cấu hình</DialogTitle>
        <DialogContent>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            Thay đổi được ghi vào nhật ký kiểm toán. Nhập lý do (10–500 ký tự).
          </Typography>
          <TextField
            fullWidth multiline minRows={2} label="Lý do"
            value={reason} onChange={(e) => setReason(e.target.value)}
            error={reason.length > 0 && reason.trim().length < 10}
            helperText={reason.length > 0 && reason.trim().length < 10 ? 'Tối thiểu 10 ký tự' : ' '}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReasonOpen(false)} disabled={saving}>Hủy</Button>
          <Button
            variant="contained"
            onClick={() => doSave(reason.trim())}
            disabled={saving || reason.trim().length < 10}
            startIcon={saving ? <CircularProgress size={16} color="inherit" /> : null}
          >
            Lưu
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default ConfigEditor;
