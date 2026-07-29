import { Grid } from '@mui/material';
import ListingCard from '@/components/listing/ListingCard';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import EmptyState from '@/components/common/EmptyState';

/**
 * Lưới ListingCard responsive (docs/04 mục 6, component #2).
 *
 * - Cột mặc định {xs:1, sm:2, md:3, lg:4} (khớp yêu cầu phần này); có thể override qua `columns`.
 * - loading -> render LoadingSkeleton (đúng số ô), rỗng -> EmptyState.
 * - Gom logic loading/empty vào 1 nơi, các trang không lặp lại.
 *
 * Props: items/listings (arr), loading (bool), skeletonCount (num), emptyState (node),
 *        columns (obj {xs,sm,md,lg,xl}), cardVariant, showFavorite, showStatus, onFavoriteToggle,
 *        renderActions (fn(listing) -> node)
 */
const DEFAULT_COLUMNS = { xs: 1, sm: 2, md: 3, lg: 4 };

const gridSize = (cols, key) => (cols[key] ? Math.round(12 / cols[key]) : undefined);

export default function ListingGrid({
  items,
  listings,
  loading = false,
  skeletonCount = 6,
  emptyState,
  columns = DEFAULT_COLUMNS,
  cardVariant = 'vertical',
  showFavorite = true,
  showStatus = false,
  onFavoriteToggle,
  renderActions,
}) {
  const data = items ?? listings ?? [];
  const cols = { ...DEFAULT_COLUMNS, ...columns };

  const itemGridProps = {
    xs: gridSize(cols, 'xs'),
    sm: gridSize(cols, 'sm'),
    md: gridSize(cols, 'md'),
    lg: gridSize(cols, 'lg'),
    ...(cols.xl ? { xl: gridSize(cols, 'xl') } : {}),
  };

  if (loading) {
    return (
      <Grid container spacing={2}>
        {Array.from({ length: skeletonCount }).map((_, i) => (
          // eslint-disable-next-line react/no-array-index-key
          <Grid item {...itemGridProps} key={i}>
            <LoadingSkeleton variant="listing-card" />
          </Grid>
        ))}
      </Grid>
    );
  }

  if (!data.length) {
    return (
      emptyState || (
        <EmptyState
          title="Không tìm thấy tin đăng nào"
          description="Hãy thử điều chỉnh bộ lọc hoặc mở rộng khu vực tìm kiếm."
        />
      )
    );
  }

  return (
    <Grid container spacing={2}>
      {data.map((listing) => (
        <Grid item {...itemGridProps} key={listing.id}>
          <ListingCard
            listing={listing}
            variant={cardVariant}
            showFavorite={showFavorite}
            showStatus={showStatus}
            onFavoriteToggle={onFavoriteToggle}
            actions={renderActions ? renderActions(listing) : undefined}
          />
        </Grid>
      ))}
    </Grid>
  );
}
