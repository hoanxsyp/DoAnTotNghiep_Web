import {
  Paper, Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  TablePagination, Skeleton, Box, Typography, Button, Checkbox,
} from '@mui/material';
import InboxIcon from '@mui/icons-material/Inbox';
import ErrorOutlineIcon from '@mui/icons-material/ErrorOutline';

/**
 * Bảng dữ liệu quản trị dùng chung (docs/04 §9). Xử lý đủ 4 trạng thái: loading (skeleton đúng số
 * cột), error (kèm nút thử lại), empty (icon + thông điệp), success. Có phân trang server-side.
 * Chỉ nhận dữ liệu + callback qua props (luật F3) — không tự gọi API.
 *
 * @param {Array<{key,label,render?,align?,width?}>} columns  cấu hình cột; `render(row)` tùy biến ô
 * @param {Array} rows            danh sách bản ghi (một trang)
 * @param {Function} rowKey       (row) => key duy nhất
 * @param {boolean} loading       đang tải trang
 * @param {object|null} error     lỗi đã chuẩn hóa (có `.message`) hoặc null
 * @param {Function} onRetry      gọi lại khi bấm "Thử lại"
 * @param {string} emptyText      thông điệp khi rỗng
 * pagination: page,size,total,onPageChange,onSizeChange (bỏ trống nếu không phân trang)
 * selection (tùy chọn): selected[], onToggle(id), onToggleAll(ids)
 */
const AdminDataTable = ({
  columns,
  rows = [],
  rowKey = (r) => r.id,
  loading = false,
  error = null,
  onRetry,
  emptyText = 'Không có dữ liệu',
  emptyAction,
  page = 0,
  size = 20,
  total = 0,
  onPageChange,
  onSizeChange,
  selectable = false,
  selected = [],
  onToggle,
  onToggleAll,
}) => {
  const colSpan = columns.length + (selectable ? 1 : 0);
  const allIds = rows.map(rowKey);
  const allChecked = selectable && rows.length > 0 && allIds.every((id) => selected.includes(id));
  const someChecked = selectable && allIds.some((id) => selected.includes(id)) && !allChecked;

  const renderBody = () => {
    if (loading) {
      return Array.from({ length: 6 }).map((_, i) => (
        <TableRow key={`sk-${i}`}>
          {selectable && <TableCell padding="checkbox"><Skeleton variant="rectangular" width={18} height={18} /></TableCell>}
          {columns.map((col) => (
            <TableCell key={col.key} align={col.align}>
              <Skeleton width="80%" />
            </TableCell>
          ))}
        </TableRow>
      ));
    }

    if (error) {
      return (
        <TableRow>
          <TableCell colSpan={colSpan}>
            <Box sx={{ py: 6, textAlign: 'center' }}>
              <ErrorOutlineIcon color="error" sx={{ fontSize: 48, mb: 1 }} />
              <Typography color="text.secondary" gutterBottom>
                {error.message || 'Không tải được dữ liệu'}
              </Typography>
              {onRetry && (
                <Button variant="outlined" onClick={onRetry} sx={{ mt: 1 }}>
                  Thử lại
                </Button>
              )}
            </Box>
          </TableCell>
        </TableRow>
      );
    }

    if (!rows.length) {
      return (
        <TableRow>
          <TableCell colSpan={colSpan}>
            <Box sx={{ py: 6, textAlign: 'center' }}>
              <InboxIcon sx={{ fontSize: 48, mb: 1, color: 'text.disabled' }} />
              <Typography color="text.secondary" gutterBottom>{emptyText}</Typography>
              {emptyAction && <Box sx={{ mt: 1 }}>{emptyAction}</Box>}
            </Box>
          </TableCell>
        </TableRow>
      );
    }

    return rows.map((row) => {
      const id = rowKey(row);
      return (
        <TableRow key={id} hover selected={selectable && selected.includes(id)}>
          {selectable && (
            <TableCell padding="checkbox">
              <Checkbox
                size="small"
                checked={selected.includes(id)}
                onChange={() => onToggle?.(id)}
              />
            </TableCell>
          )}
          {columns.map((col) => (
            <TableCell key={col.key} align={col.align} sx={{ width: col.width }}>
              {col.render ? col.render(row) : row[col.key] ?? '—'}
            </TableCell>
          ))}
        </TableRow>
      );
    });
  };

  return (
    <Paper variant="outlined">
      <TableContainer sx={{ overflowX: 'auto' }}>
        <Table size="small" sx={{ minWidth: 720 }}>
          <TableHead>
            <TableRow>
              {selectable && (
                <TableCell padding="checkbox">
                  <Checkbox
                    size="small"
                    indeterminate={someChecked}
                    checked={allChecked}
                    onChange={() => onToggleAll?.(allIds)}
                    disabled={!rows.length}
                  />
                </TableCell>
              )}
              {columns.map((col) => (
                <TableCell key={col.key} align={col.align} sx={{ fontWeight: 700, width: col.width }}>
                  {col.label}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>{renderBody()}</TableBody>
        </Table>
      </TableContainer>
      {onPageChange && !error && (
        <TablePagination
          component="div"
          count={total}
          page={page}
          rowsPerPage={size}
          onPageChange={(_, p) => onPageChange(p)}
          onRowsPerPageChange={(e) => onSizeChange?.(parseInt(e.target.value, 10))}
          rowsPerPageOptions={[10, 20, 50, 100]}
          labelRowsPerPage="Số dòng"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} / ${count}`}
        />
      )}
    </Paper>
  );
};

export default AdminDataTable;
