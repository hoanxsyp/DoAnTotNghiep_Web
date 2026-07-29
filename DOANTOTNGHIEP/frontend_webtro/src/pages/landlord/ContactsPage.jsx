import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Box, Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Avatar, Stack,
  Typography, Chip, TextField, MenuItem, TablePagination, Button, Tooltip, IconButton,
} from '@mui/material';
import ChatIcon from '@mui/icons-material/Chat';
import PhoneIcon from '@mui/icons-material/Phone';
import contactApi from '@/api/contactApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import notify from '@/utils/toast';
import { formatDateTime } from '@/utils/format';

const TYPE_META = {
  VIEW_PHONE: { label: 'Xem SĐT', color: 'default' },
  SEND_FORM: { label: 'Gửi form', color: 'info' },
  START_CHAT: { label: 'Nhắn tin', color: 'primary' },
};

const ContactsPage = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [type, setType] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'createdAt,desc' };
      if (type) params.type = type;
      const data = await contactApi.getLandlordContacts(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
      setSummary(data?.summary || null);
    } catch (e) {
      notify.apiError(e, 'Không tải được danh sách người liên hệ');
    } finally {
      setLoading(false);
    }
  }, [page, size, type]);

  useEffect(() => { load(); }, [load]);

  return (
    <Box>
      <PageHeader
        title="Người đã liên hệ"
        subtitle="Danh sách người thuê đã liên hệ tin đăng của bạn"
        action={(
          <TextField select size="small" label="Hình thức" value={type} onChange={(e) => { setPage(0); setType(e.target.value); }} sx={{ minWidth: 160 }}>
            <MenuItem value="">Tất cả</MenuItem>
            {Object.entries(TYPE_META).map(([k, v]) => <MenuItem key={k} value={k}>{v.label}</MenuItem>)}
          </TextField>
        )}
      />

      {summary && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap' }}>
          <Chip label={`Tổng: ${summary.totalContacts}`} color="primary" variant="outlined" />
          <Chip label={`Xem SĐT: ${summary.viewPhone}`} variant="outlined" />
          <Chip label={`Gửi form: ${summary.sendForm}`} variant="outlined" />
          <Chip label={`Nhắn tin: ${summary.startChat}`} variant="outlined" />
        </Stack>
      )}

      <Card>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table sx={{ minWidth: 720 }}>
            <TableHead>
              <TableRow>
                <TableCell>Người liên hệ</TableCell>
                <TableCell>Tin đăng</TableCell>
                <TableCell>Hình thức</TableCell>
                <TableCell>Nội dung</TableCell>
                <TableCell>Thời gian</TableCell>
                <TableCell align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={6} sx={{ py: 3 }}><LoadingSkeleton variant="table" columns={6} count={6} /></TableCell></TableRow>
              ) : items.length === 0 ? (
                <TableRow><TableCell colSpan={6} sx={{ border: 0 }}>
                  <EmptyState title="Chưa có ai liên hệ" description="Khi có người thuê liên hệ tin của bạn, họ sẽ xuất hiện ở đây." />
                </TableCell></TableRow>
              ) : items.map((c) => {
                const tm = TYPE_META[c.type] || { label: c.type, color: 'default' };
                return (
                  <TableRow key={c.contactLogId} hover>
                    <TableCell>
                      <Stack direction="row" spacing={1.5} alignItems="center">
                        <Avatar src={c.tenant?.avatarUrl}>{c.tenant?.fullName?.charAt(0)}</Avatar>
                        <Box>
                          <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>{c.tenant?.fullName}</Typography>
                          {c.tenant?.phone && (
                            <Typography variant="caption" color="text.secondary">
                              <PhoneIcon sx={{ fontSize: 12, verticalAlign: 'middle' }} /> {c.tenant.phone}
                            </Typography>
                          )}
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell><Typography variant="body2" sx={{ maxWidth: 200 }} noWrap>{c.listingTitle}</Typography></TableCell>
                    <TableCell><Chip size="small" color={tm.color} label={c.typeLabel || tm.label} /></TableCell>
                    <TableCell><Typography variant="body2" color="text.secondary" sx={{ maxWidth: 240 }} noWrap>{c.message || '—'}</Typography></TableCell>
                    <TableCell><Typography variant="caption">{formatDateTime(c.createdAt)}</Typography></TableCell>
                    <TableCell align="right">
                      {c.conversationId && (
                        <Tooltip title="Mở cuộc trò chuyện">
                          <IconButton size="small" color="primary" onClick={() => navigate(`/quan-ly/tin-nhan?conversationId=${c.conversationId}`)}>
                            <ChatIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={total} page={page} onPageChange={(_, p) => setPage(p)}
          rowsPerPage={size} onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[20, 50, 100]} labelRowsPerPage="Số dòng"
        />
      </Card>
    </Box>
  );
};

export default ContactsPage;
