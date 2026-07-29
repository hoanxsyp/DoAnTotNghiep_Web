import { useState, useEffect, useCallback } from 'react';
import { Box, Alert, Button, TextField, MenuItem } from '@mui/material';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import ConfigEditor from '@/components/admin/ConfigEditor';
import PageLoader from '@/components/common/PageLoader';

const GROUP_OPTIONS = [
  { value: 'ALL', label: 'Tất cả nhóm' },
  { value: 'LISTING', label: 'Tin đăng' },
  { value: 'MODERATION', label: 'Kiểm duyệt' },
  { value: 'INTERACTION', label: 'Tương tác' },
  { value: 'PROMOTION', label: 'Gói dịch vụ' },
  { value: 'SECURITY', label: 'Bảo mật' },
  { value: 'SPAM', label: 'Chống spam' },
];

/**
 * Cấu hình hệ thống theo nhóm (docs/04 §10, API 4.20.1/4.20.2). Mọi ngưỡng nghiệp vụ (không hardcode)
 * — thời hạn tin, upload, kiểm duyệt, rate limit... Quyền SYSTEM_CONFIG_MANAGE (chỉ Admin). Nhóm
 * TRUST/AI nằm ở trang Cấu hình AI. Lưu bắt buộc kèm lý do (audit).
 */
const SystemConfigPage = () => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [group, setGroup] = useState('ALL');

  const load = useCallback((g) => {
    setLoading(true);
    setError(null);
    adminApi.getSystemConfigs({ group: g })
      .then(setData)
      .catch((err) => { setError(err); notify.apiError(err); })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(group); }, [load, group]);

  const groups = (data?.groups || []).map((g) => ({ title: g.label || g.group, items: g.configs || [] }));

  const handleSave = async (configs, reason) => {
    setSaving(true);
    try {
      await adminApi.updateSystemConfigs({ configs, reason });
      notify.success('Đã lưu cấu hình hệ thống');
      load(group);
    } catch (err) {
      notify.apiError(err);
      throw err;
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <AdminPageHeader
        title="Cấu hình hệ thống"
        subtitle="Ngưỡng nghiệp vụ theo nhóm — nhóm uy tín/AI ở trang Cấu hình AI"
        actions={
          <TextField select size="small" label="Nhóm" value={group} onChange={(e) => setGroup(e.target.value)} sx={{ minWidth: 180 }}>
            {GROUP_OPTIONS.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
          </TextField>
        }
      />
      {loading ? (
        <PageLoader />
      ) : error ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => load(group)}>Thử lại</Button>}>
          {error.message}
        </Alert>
      ) : (
        <ConfigEditor groups={groups} onSave={handleSave} saving={saving} requireReason />
      )}
    </Box>
  );
};

export default SystemConfigPage;
