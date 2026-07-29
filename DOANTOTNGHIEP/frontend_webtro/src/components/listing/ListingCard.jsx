import { Link as RouterLink } from 'react-router-dom';
import {
  Card,
  CardActionArea,
  CardContent,
  Box,
  Typography,
  Stack,
  Chip,
  IconButton,
  Tooltip,
} from '@mui/material';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import PlaceOutlinedIcon from '@mui/icons-material/PlaceOutlined';
import SquareFootOutlinedIcon from '@mui/icons-material/SquareFootOutlined';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined';
import StatusChip from '@/components/common/StatusChip';
import TrustScoreBadge from '@/components/listing/TrustScoreBadge';
import { formatPrice, formatArea, fromNow } from '@/utils/format';

/**
 * Thẻ tin đăng (docs/04 mục 6, component #1).
 *
 * - Ảnh đại diện lazy, aspect-ratio 16:9 chống CLS (canonical mục 8 "đặt aspect-ratio giữ chỗ").
 * - Tiêu đề, giá (formatPrice), diện tích, khu vực.
 * - Nhãn "Nổi bật" khi promoted (PROMO-02).
 * - StatusChip khi showStatus (tin của chủ trọ).
 * - TrustScoreBadge khi trust thấp (trustLevel != NORMAL hoặc lowTrustWarning).
 * - Nút tim (onFavoriteToggle). unavailable -> grayscale + overlay [§3.9].
 * - Click -> /tin/{slug}-{id}. Bọc <Link> thật để mở tab mới được.
 *
 * Props: listing (obj), variant 'vertical'|'horizontal'|'compact', showFavorite (bool),
 *        showStatus (bool), unavailable (bool), onFavoriteToggle (fn), actions (node)
 */
export default function ListingCard({
  listing,
  variant = 'vertical',
  showFavorite = true,
  showStatus = false,
  unavailable = false,
  onFavoriteToggle,
  actions,
}) {
  if (!listing) return null;

  const {
    id,
    slug,
    title,
    price,
    area,
    districtName,
    provinceName,
    shortAddress,
    thumbnailUrl,
    primaryImageUrl,
    trustScore,
    trustLevel,
    trustLabel,
    lowTrustWarning,
    favoritedByMe,
    promoted,
    promotedLabel,
    status,
    createdAt,
    publishedAt,
  } = listing;

  const to = `/tin/${slug ? `${slug}-` : ''}${id}`;
  const image = thumbnailUrl || primaryImageUrl;
  const area_ = area != null ? formatArea(area) : null;
  const location = shortAddress || [districtName, provinceName].filter(Boolean).join(', ');
  const timeText = fromNow(publishedAt || createdAt);

  // Trust: chỉ hiện badge khi rủi ro. trustLevel từ API là nguồn chuẩn; fallback theo cờ.
  const level = trustLevel || (lowTrustWarning ? 'RISKY' : 'NORMAL');
  const showTrust = level !== 'NORMAL' || lowTrustWarning === true;

  const isHorizontal = variant === 'horizontal';
  const isCompact = variant === 'compact';

  const favoriteBtn = showFavorite && (
    <Tooltip title={favoritedByMe ? 'Bỏ lưu tin' : 'Lưu tin'}>
      <IconButton
        size="small"
        aria-label={favoritedByMe ? 'Bỏ lưu tin' : 'Lưu tin'}
        onClick={(e) => {
          e.preventDefault();
          e.stopPropagation();
          onFavoriteToggle?.(listing);
        }}
        sx={{
          position: 'absolute',
          top: 6,
          right: 6,
          bgcolor: 'rgba(0,0,0,0.45)',
          color: '#fff',
          '&:hover': { bgcolor: 'rgba(0,0,0,0.65)' },
          zIndex: 2,
        }}
      >
        {favoritedByMe ? (
          <FavoriteIcon fontSize="small" color="error" />
        ) : (
          <FavoriteBorderIcon fontSize="small" />
        )}
      </IconButton>
    </Tooltip>
  );

  const imageBox = (
    <Box
      sx={{
        position: 'relative',
        width: isHorizontal ? { xs: 120, sm: 160 } : '100%',
        flexShrink: 0,
        aspectRatio: isHorizontal ? '1 / 1' : '16 / 9',
        bgcolor: 'action.hover',
        overflow: 'hidden',
        ...(unavailable && { filter: 'grayscale(1)' }),
      }}
    >
      {image ? (
        <Box
          component="img"
          src={image}
          alt={title || 'Ảnh tin đăng'}
          loading="lazy"
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />
      ) : (
        <Box
          sx={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'text.disabled',
          }}
        >
          <ImageNotSupportedOutlinedIcon fontSize="large" />
        </Box>
      )}

      {promoted && (
        <Chip
          size="small"
          color="secondary"
          icon={<LocalFireDepartmentIcon />}
          label={promotedLabel || 'Nổi bật'}
          sx={{ position: 'absolute', top: 6, left: 6, fontWeight: 600, zIndex: 2 }}
        />
      )}

      {favoriteBtn}

      {unavailable && (
        <Box
          sx={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            bgcolor: 'rgba(0,0,0,0.35)',
          }}
        >
          <Chip label="Không còn nhận" color="default" />
        </Box>
      )}
    </Box>
  );

  const content = (
    <CardContent
      sx={{
        p: isCompact ? 1.25 : 1.5,
        flex: 1,
        minWidth: 0,
        '&:last-child': { pb: isCompact ? 1.25 : 1.5 },
      }}
    >
      <Typography
        variant={isCompact ? 'body2' : 'subtitle1'}
        sx={{
          fontWeight: 600,
          lineHeight: 1.35,
          display: '-webkit-box',
          WebkitLineClamp: 2,
          WebkitBoxOrient: 'vertical',
          overflow: 'hidden',
        }}
      >
        {title}
      </Typography>

      <Typography
        variant={isCompact ? 'subtitle2' : 'h6'}
        color="primary"
        sx={{ fontWeight: 700, mt: 0.5 }}
      >
        {formatPrice(price)}
        <Typography component="span" variant="caption" color="text.secondary">
          {price != null ? '/tháng' : ''}
        </Typography>
      </Typography>

      <Stack
        direction="row"
        spacing={1.5}
        alignItems="center"
        sx={{ mt: 0.5, color: 'text.secondary', flexWrap: 'wrap' }}
      >
        {area_ && (
          <Stack direction="row" spacing={0.25} alignItems="center">
            <SquareFootOutlinedIcon sx={{ fontSize: 16 }} />
            <Typography variant="caption">{area_}</Typography>
          </Stack>
        )}
        {location && (
          <Stack direction="row" spacing={0.25} alignItems="center" sx={{ minWidth: 0 }}>
            <PlaceOutlinedIcon sx={{ fontSize: 16 }} />
            <Typography variant="caption" noWrap sx={{ maxWidth: 180 }}>
              {location}
            </Typography>
          </Stack>
        )}
      </Stack>

      {(showStatus || showTrust) && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
          {showStatus && status && <StatusChip status={status} type="listing" />}
          {showTrust && <TrustScoreBadge score={trustScore} level={level} variant="inline" />}
        </Stack>
      )}

      {timeText && (
        <Typography variant="caption" color="text.disabled" sx={{ display: 'block', mt: 0.5 }}>
          {timeText}
        </Typography>
      )}
    </CardContent>
  );

  return (
    <Card
      sx={{
        height: isHorizontal ? 'auto' : '100%',
        display: 'flex',
        flexDirection: 'column',
        position: 'relative',
        transition: 'box-shadow .2s, transform .2s',
        '&:hover': { boxShadow: 4 },
      }}
    >
      <CardActionArea
        component={RouterLink}
        to={to}
        sx={{
          display: 'flex',
          flexDirection: isHorizontal ? 'row' : 'column',
          alignItems: 'stretch',
          flex: 1,
        }}
      >
        {imageBox}
        {content}
      </CardActionArea>

      {actions && (
        <Box sx={{ p: 1, pt: 0, display: 'flex', gap: 1, flexWrap: 'wrap' }}>{actions}</Box>
      )}
    </Card>
  );
}
