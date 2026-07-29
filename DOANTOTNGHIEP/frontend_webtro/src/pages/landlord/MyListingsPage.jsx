import { useCallback, useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import {
  Box, Card, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, Avatar, Stack,
  Typography, Chip, IconButton, Menu, MenuItem, ListItemIcon, TextField, InputAdornment, Button,
  TablePagination, Tooltip, ToggleButtonGroup, ToggleButton,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import EditIcon from '@mui/icons-material/Edit';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import VisibilityIcon from '@mui/icons-material/Visibility';
import LockIcon from '@mui/icons-material/Lock';
import AutorenewIcon from '@mui/icons-material/Autorenew';
import CampaignIcon from '@mui/icons-material/Campaign';
import BarChartIcon from '@mui/icons-material/BarChart';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SendIcon from '@mui/icons-material/Send';
import AddBoxIcon from '@mui/icons-material/AddBox';
import listingApi from '@/api/listingApi';
import PageHeader from '@/components/dashboard/PageHeader';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';
import ConfirmDialog from '@/components/common/ConfirmDialog';
import StatusChip from '@/components/common/StatusChip';
import notify from '@/utils/toast';
import { formatPrice, formatDate } from '@/utils/format';
import { LISTING_STATUS_META } from '@/constants';

const STATUS_TABS = ['', 'ACTIVE', 'PENDING', 'DRAFT', 'REJECTED', 'HIDDEN', 'EXPIRED', 'CLOSED', 'LOCKED', 'NEED_REVIEW'];

const ACTION_META = {
  EDIT: { label: 'Sửa tin', icon: <EditIcon fontSize="small" /> },
  SUBMIT: { label: 'Gửi duyệt', icon: <SendIcon fontSize="small" /> },
  HIDE: { label: 'Ẩn tin', icon: <VisibilityOffIcon fontSize="small" /> },
  UNHIDE: { label: 'Hiện lại', icon: <VisibilityIcon fontSize="small" /> },
  CLOSE: { label: 'Đóng tin', icon: <LockIcon fontSize="small" /> },
  RENEW: { label: 'Gia hạn', icon: <AutorenewIcon fontSize="small" /> },
  PROMOTE: { label: 'Đẩy tin', icon: <CampaignIcon fontSize="small" /> },
  VIEW_STATS: { label: 'Xem thống kê', icon: <BarChartIcon fontSize="small" /> },
  DELETE: { label: 'Xóa tin', icon: <DeleteOutlineIcon fontSize="small" />, danger: true },
};

const CLOSE_REASONS = [
  { value: 'RENTED_OUT', label: 'Đã cho thuê' },
  { value: 'NO_LONGER_AVAILABLE', label: 'Không còn cho thuê' },
  { value: 'OTHER', label: 'Lý do khác' },
];

const MyListingsPage = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [statusCounts, setStatusCounts] = useState({});
  const [status, setStatus] = useState(searchParams.get('status') || '');
  const [keyword, setKeyword] = useState('');
  const [search, setSearch] = useState('');

  const [menuAnchor, setMenuAnchor] = useState(null);
  const [menuRow, setMenuRow] = useState(null);
  const [confirm, setConfirm] = useState(null); // { action, row }
  const [acting, setActing] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const params = { page, size, sort: 'updatedAt,desc' };
      if (status) params.status = status;
      if (search) params.keyword = search;
      const data = await listingApi.getMyListings(params);
      setItems(data?.items || []);
      setTotal(data?.totalElements || 0);
      setStatusCounts(data?.statusCounts || {});
    } catch (e) {
      notify.apiError(e, 'Không tải được danh sách tin');
    } finally {
      setLoading(false);
    }
  }, [page, size, status, search]);

  useEffect(() => { load(); }, [load]);

  const changeStatus = (val) => {
    setStatus(val);
    setPage(0);
    setSearchParams(val ? { status: val } : {}, { replace: true });
  };

  const openMenu = (e, row) => { setMenuAnchor(e.currentTarget); setMenuRow(row); };
  const closeMenu = () => { setMenuAnchor(null); setMenuRow(null); };

  const runAction = async (action, row, payload) => {
    setActing(true);
    try {
      if (action === 'HIDE') await listingApi.hide(row.id);
      else if (action === 'UNHIDE') await listingApi.unhide(row.id);
      else if (action === 'RENEW') await listingApi.renew(row.id);
      else if (action === 'SUBMIT') await listingApi.submit(row.id);
      else if (action === 'CLOSE') await listingApi.close(row.id, payload);
      else if (action === 'DELETE') await listingApi.remove(row.id);
      notify.success('Thao tác thành công');
      setConfirm(null);
      load();
    } catch (e) {
      notify.apiError(e, 'Thao tác thất bại');
    } finally {
      setActing(false);
    }
  };

  const handleAction = (action, row) => {
    closeMenu();
    if (action === 'EDIT') { navigate(`/quan-ly/tin-dang/${row.id}/sua`); return; }
    if (action === 'VIEW_STATS') { navigate(`/quan-ly/tin-dang/${row.id}/thong-ke`); return; }
    if (action === 'PROMOTE') { navigate(`/quan-ly/goi-dich-vu?listingId=${row.id}`); return; }
    if (action === 'CLOSE' || action === 'DELETE') { setConfirm({ action, row }); return; }
    runAction(action, row);
  };

  return (
    <Box>
      <PageHeader
        title="Tin đăng của tôi"
        subtitle={total ? `${total} tin` : 'Quản lý toàn bộ tin đăng của bạn'}
        action={<Button variant="contained" startIcon={<AddBoxIcon />} component={RouterLink} to="/quan-ly/tin-dang/tao">Đăng tin mới</Button>}
      />

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ mb: 2 }} alignItems={{ md: 'center' }} justifyContent="space-between">
        <ToggleButtonGroup value={status} exclusive size="small" onChange={(_, v) => v !== null && changeStatus(v)} sx={{ flexWrap: 'wrap' }}>
          {STATUS_TABS.map((s) => (
            <ToggleButton key={s || 'all'} value={s}>
              {s ? LISTING_STATUS_META[s]?.label : 'Tất cả'}
              {s && statusCounts[s] != null ? ` (${statusCounts[s]})` : ''}
            </ToggleButton>
          ))}
        </ToggleButtonGroup>
        <TextField
          size="small" placeholder="Tìm theo tiêu đề…" value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') { setPage(0); setSearch(keyword.trim()); } }}
          InputProps={{ startAdornment: <InputAdornment position="start"><SearchIcon /></InputAdornment> }}
          sx={{ minWidth: 240 }}
        />
      </Stack>

      <Card>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table sx={{ minWidth: 720 }}>
            <TableHead>
              <TableRow>
                <TableCell>Tin đăng</TableCell>
                <TableCell>Trạng thái</TableCell>
                <TableCell align="center">Xem</TableCell>
                <TableCell align="center">Lưu</TableCell>
                <TableCell align="center">Liên hệ</TableCell>
                <TableCell>Hết hạn</TableCell>
                <TableCell align="right">Hành động</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow><TableCell colSpan={7} sx={{ py: 3 }}><LoadingSkeleton variant="table" columns={7} count={6} /></TableCell></TableRow>
              ) : items.length === 0 ? (
                <TableRow><TableCell colSpan={7} sx={{ border: 0 }}>
                  <EmptyState
                    title="Không có tin đăng"
                    description="Chưa có tin nào khớp bộ lọc hiện tại."
                    action={<Button variant="contained" startIcon={<AddBoxIcon />} component={RouterLink} to="/quan-ly/tin-dang/tao">Đăng tin mới</Button>}
                  />
                </TableCell></TableRow>
              ) : items.map((row) => (
                <TableRow key={row.id} hover>
                  <TableCell>
                    <Stack direction="row" spacing={1.5} alignItems="center" sx={{ minWidth: 0 }}>
                      <Avatar variant="rounded" src={row.thumbnailUrl} sx={{ width: 56, height: 42 }} />
                      <Box sx={{ minWidth: 0 }}>
                        <Typography
                          component={RouterLink} to={`/tin/${row.slug ? `${row.slug}-` : ''}${row.id}`}
                          variant="subtitle2" sx={{ fontWeight: 600, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' }, display: 'block', maxWidth: 320 }} noWrap
                        >
                          {row.title}
                        </Typography>
                        <Typography variant="caption" color="primary" sx={{ fontWeight: 600 }}>{formatPrice(row.price)}</Typography>
                        {row.promoted && <Chip size="small" color="secondary" label="Nổi bật" sx={{ ml: 1, height: 18 }} />}
                        {row.status === 'REJECTED' && row.rejectReason && (
                          <Tooltip title={row.rejectReason}>
                            <Typography variant="caption" color="error" sx={{ display: 'block', maxWidth: 320 }} noWrap>Lý do: {row.rejectReason}</Typography>
                          </Tooltip>
                        )}
                      </Box>
                    </Stack>
                  </TableCell>
                  <TableCell><StatusChip status={row.status} /></TableCell>
                  <TableCell align="center">{row.viewCount ?? 0}</TableCell>
                  <TableCell align="center">{row.favoriteCount ?? 0}</TableCell>
                  <TableCell align="center">{row.contactCount ?? 0}</TableCell>
                  <TableCell>
                    {row.expiredAt ? (
                      <Typography variant="caption" color={row.expiringSoon ? 'error' : 'text.secondary'}>
                        {formatDate(row.expiredAt)}{row.daysRemaining != null ? ` (${row.daysRemaining} ngày)` : ''}
                      </Typography>
                    ) : <Typography variant="caption" color="text.disabled">—</Typography>}
                  </TableCell>
                  <TableCell align="right">
                    <IconButton size="small" onClick={(e) => openMenu(e, row)} disabled={!(row.availableActions || []).length}>
                      <MoreVertIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div"
          count={total}
          page={page}
          onPageChange={(_, p) => setPage(p)}
          rowsPerPage={size}
          onRowsPerPageChange={(e) => { setSize(parseInt(e.target.value, 10)); setPage(0); }}
          rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage="Số dòng"
        />
      </Card>

      <Menu anchorEl={menuAnchor} open={Boolean(menuAnchor)} onClose={closeMenu}>
        {(menuRow?.availableActions || []).map((action) => {
          const meta = ACTION_META[action];
          if (!meta) return null;
          return (
            <MenuItem key={action} onClick={() => handleAction(action, menuRow)} sx={meta.danger ? { color: 'error.main' } : undefined}>
              <ListItemIcon sx={meta.danger ? { color: 'error.main' } : undefined}>{meta.icon}</ListItemIcon>
              {meta.label}
            </MenuItem>
          );
        })}
      </Menu>

      <ConfirmDialog
        open={confirm?.action === 'DELETE'}
        title="Xóa tin đăng"
        message={`Xóa tin "${confirm?.row?.title || ''}"? Tin sẽ bị gỡ khỏi hệ thống.`}
        confirmText="Xóa tin"
        severity="error"
        loading={acting}
        onConfirm={() => runAction('DELETE', confirm.row)}
        onCancel={() => setConfirm(null)}
      />

      <ConfirmDialog
        open={confirm?.action === 'CLOSE'}
        title="Đóng tin đăng"
        message="Cho biết lý do đóng tin này:"
        confirmText="Đóng tin"
        severity="warning"
        loading={acting}
        requireReason
        reasonLabel="Lý do đóng tin"
        reasonOptions={CLOSE_REASONS}
        onConfirm={({ reason, note }) => runAction('CLOSE', confirm.row, { closeReason: reason, note })}
        onCancel={() => setConfirm(null)}
      />
    </Box>
  );
};

export default MyListingsPage;
