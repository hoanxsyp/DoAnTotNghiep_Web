import { useState, useEffect, useCallback } from 'react';
import { Box, Alert, Button } from '@mui/material';
import adminApi from '@/api/adminApi';
import { notify } from '@/utils/toast';
import AdminPageHeader from '@/components/admin/AdminPageHeader';
import ConfigEditor from '@/components/admin/ConfigEditor';
import PageLoader from '@/components/common/PageLoader';

const GROUP_TITLES = {
  modules: 'Bật/tắt module AI',
  sentiment: 'Phân tích cảm xúc',
  recommendation: 'Gợi ý tin đăng',
  price: 'Dự đoán giá',
  chatbot: 'Trợ lý ảo',
  trustWeights: 'Trọng số điểm uy tín',
};

/**
 * Cấu hình AI (docs/04 §10.10, API 4.19.5/4.19.6). Bật/tắt module, chỉnh ngưỡng cảm xúc + trọng số
 * uy tín. Quyền AI_CONFIG_MANAGE (chỉ Admin). Lưu bắt buộc kèm lý do (audit).
 */
const AiConfigPage = () => {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    adminApi.getAiConfig()
      .then(setData)
      .catch((err) => { setError(err); notify.apiError(err); })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => { load(); }, [load]);

  const groups = data
    ? Object.entries(data)
        .filter(([, v]) => Array.isArray(v) && v.length)
        .map(([key, items]) => ({ title: GROUP_TITLES[key] || key, items }))
    : [];

  const handleSave = async (configs, reason) => {
    setSaving(true);
    try {
      await adminApi.updateAiConfig({ configs, reason });
      notify.success('Đã lưu cấu hình AI');
      load();
    } catch (err) {
      notify.apiError(err);
      throw err;
    } finally {
      setSaving(false);
    }
  };

  return (
    <Box>
      <AdminPageHeader title="Cấu hình AI" subtitle="Bật/tắt module và điều chỉnh ngưỡng, trọng số" />
      {loading ? (
        <PageLoader />
      ) : error ? (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={load}>Thử lại</Button>}>
          {error.message}
        </Alert>
      ) : (
        <ConfigEditor groups={groups} onSave={handleSave} saving={saving} requireReason />
      )}
    </Box>
  );
};

export default AiConfigPage;
