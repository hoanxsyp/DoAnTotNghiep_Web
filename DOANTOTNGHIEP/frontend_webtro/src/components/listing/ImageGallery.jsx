import { useCallback, useEffect, useRef, useState } from 'react';
import { Box, IconButton, Dialog, Typography, Stack } from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import CloseIcon from '@mui/icons-material/Close';
import ImageNotSupportedOutlinedIcon from '@mui/icons-material/ImageNotSupportedOutlined';

/**
 * Xem ảnh chi tiết tin: ảnh chính + thumbnail + lightbox đơn giản
 * (docs/04 mục 6 #7).
 *
 * - Bấm ảnh -> viewer full-screen: ←/→ chuyển, Esc đóng, đếm "3/8", swipe mobile.
 * - Ảnh lazy trừ ảnh đầu (loading="eager" cho LCP).
 * - alt bắt buộc.
 *
 * Props: images (arr string|{url,thumbnailUrl,alt}), alt (str), showThumbnails (bool),
 *        enableFullscreen (bool), aspectRatio (str='16/9')
 */
const srcOf = (img) => (typeof img === 'string' ? img : img?.url || img?.thumbnailUrl);
const thumbOf = (img) => (typeof img === 'string' ? img : img?.thumbnailUrl || img?.url);

export default function ImageGallery({
  images = [],
  alt = 'Ảnh tin đăng',
  showThumbnails = true,
  enableFullscreen = true,
  aspectRatio = '16 / 9',
}) {
  const [index, setIndex] = useState(0);
  const [open, setOpen] = useState(false);
  const touchX = useRef(null);

  const count = images.length;
  const safeIndex = Math.min(index, Math.max(0, count - 1));

  const go = useCallback(
    (dir) => setIndex((i) => (count ? (i + dir + count) % count : 0)),
    [count],
  );

  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => {
      if (e.key === 'ArrowLeft') go(-1);
      else if (e.key === 'ArrowRight') go(1);
      else if (e.key === 'Escape') setOpen(false);
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, go]);

  const onTouchStart = (e) => (touchX.current = e.touches[0].clientX);
  const onTouchEnd = (e) => {
    if (touchX.current == null) return;
    const dx = e.changedTouches[0].clientX - touchX.current;
    if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1);
    touchX.current = null;
  };

  if (!count) {
    return (
      <Box
        sx={{
          width: '100%',
          aspectRatio,
          bgcolor: 'action.hover',
          borderRadius: 2,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: 'text.disabled',
        }}
      >
        <ImageNotSupportedOutlinedIcon fontSize="large" />
      </Box>
    );
  }

  return (
    <Box>
      {/* Ảnh chính */}
      <Box
        sx={{
          position: 'relative',
          width: '100%',
          aspectRatio,
          borderRadius: 2,
          overflow: 'hidden',
          bgcolor: 'action.hover',
          cursor: enableFullscreen ? 'zoom-in' : 'default',
        }}
        onClick={() => enableFullscreen && setOpen(true)}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        <Box
          component="img"
          src={srcOf(images[safeIndex])}
          alt={`${alt} ${safeIndex + 1}/${count}`}
          loading={safeIndex === 0 ? 'eager' : 'lazy'}
          sx={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
        />

        {count > 1 && (
          <>
            <IconButton
              aria-label="Ảnh trước"
              onClick={(e) => {
                e.stopPropagation();
                go(-1);
              }}
              sx={navBtnSx('left')}
            >
              <ChevronLeftIcon />
            </IconButton>
            <IconButton
              aria-label="Ảnh sau"
              onClick={(e) => {
                e.stopPropagation();
                go(1);
              }}
              sx={navBtnSx('right')}
            >
              <ChevronRightIcon />
            </IconButton>
            <Typography variant="caption" sx={counterSx}>
              {safeIndex + 1}/{count}
            </Typography>
          </>
        )}
      </Box>

      {/* Thumbnails */}
      {showThumbnails && count > 1 && (
        <Stack direction="row" spacing={1} sx={{ mt: 1, overflowX: 'auto', pb: 0.5 }}>
          {images.map((img, i) => (
            <Box
              key={i}
              component="img"
              src={thumbOf(img)}
              alt={`${alt} thu nhỏ ${i + 1}`}
              loading="lazy"
              onClick={() => setIndex(i)}
              sx={{
                width: 64,
                height: 64,
                flexShrink: 0,
                objectFit: 'cover',
                borderRadius: 1,
                cursor: 'pointer',
                border: '2px solid',
                borderColor: i === safeIndex ? 'primary.main' : 'transparent',
                opacity: i === safeIndex ? 1 : 0.7,
              }}
            />
          ))}
        </Stack>
      )}

      {/* Lightbox full-screen */}
      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        fullScreen
        PaperProps={{ sx: { bgcolor: 'rgba(0,0,0,0.95)' } }}
      >
        <Box
          sx={{
            position: 'relative',
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
          onTouchStart={onTouchStart}
          onTouchEnd={onTouchEnd}
        >
          <IconButton
            aria-label="Đóng"
            onClick={() => setOpen(false)}
            sx={{ position: 'absolute', top: 12, right: 12, color: '#fff' }}
          >
            <CloseIcon />
          </IconButton>

          <Box
            component="img"
            src={srcOf(images[safeIndex])}
            alt={`${alt} ${safeIndex + 1}/${count}`}
            sx={{ maxWidth: '95vw', maxHeight: '90vh', objectFit: 'contain' }}
          />

          {count > 1 && (
            <>
              <IconButton
                aria-label="Ảnh trước"
                onClick={() => go(-1)}
                sx={{ position: 'absolute', left: 12, color: '#fff' }}
              >
                <ChevronLeftIcon fontSize="large" />
              </IconButton>
              <IconButton
                aria-label="Ảnh sau"
                onClick={() => go(1)}
                sx={{ position: 'absolute', right: 12, color: '#fff' }}
              >
                <ChevronRightIcon fontSize="large" />
              </IconButton>
              <Typography
                sx={{ position: 'absolute', bottom: 20, color: '#fff', bgcolor: 'rgba(0,0,0,0.5)', px: 1.5, py: 0.5, borderRadius: 5 }}
              >
                {safeIndex + 1} / {count}
              </Typography>
            </>
          )}
        </Box>
      </Dialog>
    </Box>
  );
}

const navBtnSx = (side) => ({
  position: 'absolute',
  top: '50%',
  transform: 'translateY(-50%)',
  [side]: 8,
  bgcolor: 'rgba(0,0,0,0.4)',
  color: '#fff',
  '&:hover': { bgcolor: 'rgba(0,0,0,0.6)' },
});

const counterSx = {
  position: 'absolute',
  bottom: 8,
  right: 8,
  bgcolor: 'rgba(0,0,0,0.55)',
  color: '#fff',
  px: 1,
  py: 0.25,
  borderRadius: 5,
};
