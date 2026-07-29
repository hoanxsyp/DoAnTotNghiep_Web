import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  TablePagination,
  Checkbox,
  Paper,
  Toolbar,
  Typography,
  Stack,
  Button,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';

/**
 * Bảng dữ liệu dùng lại (docs/04 mục 6 #15).
 *
 * - Sort server-side (đổi ?sort=), phân trang (TablePagination, size 10/20/50/100 — trần 100),
 *   loading, error+retry, chọn nhiều + thanh hành động hàng loạt.
 * - mobileCardRenderer -> ở xs render card thay bảng.
 *
 * Props: columns [{field, headerName, sortable, width, align, renderCell, hideBelow}],
 *        rows (arr), loading, error, onRetry, page, size, total, onPageChange, onSizeChange,
 *        sort (str "field,dir"), onSortChange (fn(field)), selectable, selected (arr id),
 *        onSelectionChange, bulkActions [{label,icon,onClick,color}], emptyState (node),
 *        mobileCardRenderer (fn(row)->node), stickyHeader, getRowId (fn)
 */
export default function DataTable({
  columns = [],
  rows = [],
  loading = false,
  error,
  onRetry,
  page = 0,
  size = 20,
  total = 0,
  onPageChange,
  onSizeChange,
  sort,
  onSortChange,
  selectable = false,
  selected = [],
  onSelectionChange,
  bulkActions = [],
  emptyState,
  mobileCardRenderer,
  stickyHeader = false,
  getRowId = (row) => row.id,
}) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('sm'));

  const [sortField, sortDir] = (sort || '').split(',');

  const handleSort = (field) => {
    if (!onSortChange) return;
    const isAsc = sortField === field && sortDir === 'asc';
    onSortChange(`${field},${isAsc ? 'desc' : 'asc'}`);
  };

  const allSelected = rows.length > 0 && selected.length === rows.length;
  const someSelected = selected.length > 0 && selected.length < rows.length;

  const toggleAll = () =>
    onSelectionChange?.(allSelected ? [] : rows.map((r) => getRowId(r)));
  const toggleOne = (id) =>
    onSelectionChange?.(
      selected.includes(id) ? selected.filter((s) => s !== id) : [...selected, id],
    );

  const visibleColumns = columns.filter(
    (c) => !(isMobile && c.hideBelow && c.hideBelow === 'sm'),
  );

  const bulkToolbar = selectable && selected.length > 0 && (
    <Toolbar sx={{ bgcolor: 'primary.main', color: 'primary.contrastText', borderRadius: 1, mb: 1, minHeight: 56 }}>
      <Typography sx={{ flex: 1 }}>Đã chọn {selected.length}</Typography>
      <Stack direction="row" spacing={1}>
        {bulkActions.map((a) => (
          <Button
            key={a.label}
            size="small"
            color="inherit"
            startIcon={a.icon}
            onClick={() => a.onClick(selected)}
          >
            {a.label}
          </Button>
        ))}
      </Stack>
    </Toolbar>
  );

  const pagination = (
    <TablePagination
      component="div"
      count={total}
      page={page}
      rowsPerPage={size}
      onPageChange={(_, p) => onPageChange?.(p)}
      onRowsPerPageChange={(e) => onSizeChange?.(Number(e.target.value))}
      rowsPerPageOptions={[10, 20, 50, 100]}
      labelRowsPerPage="Số dòng mỗi trang"
      labelDisplayedRows={({ from, to, count }) => `${from}–${to} trên ${count}`}
    />
  );

  // Lỗi
  if (error && !loading) {
    return (
      <EmptyState
        title="Không tải được dữ liệu"
        description={error?.message || 'Đã có lỗi xảy ra, vui lòng thử lại.'}
        action={
          onRetry ? (
            <Button variant="contained" onClick={onRetry}>
              Thử lại
            </Button>
          ) : undefined
        }
      />
    );
  }

  // Mobile: render card
  if (isMobile && mobileCardRenderer) {
    return (
      <Box>
        {bulkToolbar}
        {loading ? (
          <LoadingSkeleton variant="list-item" count={size > 6 ? 6 : size} />
        ) : rows.length === 0 ? (
          emptyState || <EmptyState title="Không có dữ liệu" />
        ) : (
          <Stack spacing={1.5}>{rows.map((row) => mobileCardRenderer(row))}</Stack>
        )}
        {pagination}
      </Box>
    );
  }

  return (
    <Box>
      {bulkToolbar}
      <TableContainer component={Paper} variant="outlined" sx={{ overflowX: 'auto' }}>
        <Table stickyHeader={stickyHeader} size="small">
          <TableHead>
            <TableRow>
              {selectable && (
                <TableCell padding="checkbox">
                  <Checkbox
                    indeterminate={someSelected}
                    checked={allSelected}
                    onChange={toggleAll}
                    inputProps={{ 'aria-label': 'Chọn tất cả' }}
                  />
                </TableCell>
              )}
              {visibleColumns.map((col) => (
                <TableCell
                  key={col.field}
                  align={col.align}
                  sx={{ width: col.width, whiteSpace: 'nowrap' }}
                  sortDirection={sortField === col.field ? sortDir : false}
                >
                  {col.sortable ? (
                    <TableSortLabel
                      active={sortField === col.field}
                      direction={sortField === col.field ? sortDir || 'asc' : 'asc'}
                      onClick={() => handleSort(col.field)}
                    >
                      {col.headerName}
                    </TableSortLabel>
                  ) : (
                    col.headerName
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={visibleColumns.length + (selectable ? 1 : 0)} sx={{ border: 0 }}>
                  <LoadingSkeleton variant="table" count={size > 8 ? 8 : size} columns={visibleColumns.length} />
                </TableCell>
              </TableRow>
            ) : rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={visibleColumns.length + (selectable ? 1 : 0)} sx={{ border: 0 }}>
                  {emptyState || <EmptyState title="Không có dữ liệu" size="sm" />}
                </TableCell>
              </TableRow>
            ) : (
              rows.map((row) => {
                const id = getRowId(row);
                const isSel = selected.includes(id);
                return (
                  <TableRow key={id} hover selected={isSel}>
                    {selectable && (
                      <TableCell padding="checkbox">
                        <Checkbox checked={isSel} onChange={() => toggleOne(id)} />
                      </TableCell>
                    )}
                    {visibleColumns.map((col) => (
                      <TableCell key={col.field} align={col.align}>
                        {col.renderCell ? col.renderCell(row) : row[col.field]}
                      </TableCell>
                    ))}
                  </TableRow>
                );
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {pagination}
    </Box>
  );
}
