import { useCallback, useRef, useState } from 'react';
import {
  Box,
  Typography,
  Button,
  Stack,
  IconButton,
  Chip,
  LinearProgress,
  Tooltip,
} from '@mui/material';
import CloudUploadOutlinedIcon from '@mui/icons-material/CloudUploadOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import StarIcon from '@mui/icons-material/Star';
import StarBorderIcon from '@mui/icons-material/StarBorder';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import notify from '@/utils/toast';

/**
 * Kéo thả nhiều ảnh (docs/04 mục 6 #6, [§11.9]).
 *
 * - Kéo thả (HTML5 DnD, KHÔNG thêm dependency), sắp xếp lại bằng kéo thả, chọn ảnh đại diện.
 * - Mỗi ảnh: preview, thanh tiến trình (khi có uploadFn), nút xóa, nhãn "Ảnh đại diện".
 * - Validate accept + maxSizeMb + maxFiles TRƯỚC khi thêm.
 * - privateMode -> nhãn 🔒 (giấy tờ chủ trọ).
 *
 * Props: value (arr), onChange (fn), maxFiles (num=10), maxSizeMb (num=5),
 *        accept (arr mime), primaryId, onPrimaryChange (fn), sortable (bool),
 *        uploadFn (fn(file)->Promise<{id,url}>), privateMode (bool)
 *
 * Item ảnh: { id, url, name, file?, isPrimary?, uploading?, progress?, error? }
 */
const DEFAULT_ACCEPT = ['image/jpeg', 'image/png', 'image/webp'];

let tempSeq = 0;
const nextTempId = () => `tmp-${Date.now()}-${(tempSeq += 1)}`;

export default function ImageUploader({
  value = [],
  onChange,
  maxFiles = 10,
  maxSizeMb = 5,
  accept = DEFAULT_ACCEPT,
  primaryId,
  onPrimaryChange,
  sortable = true,
  uploadFn,
  privateMode = false,
}) {
  const inputRef = useRef(null);
  const [dragOver, setDragOver] = useState(false);
  const dragIndex = useRef(null);

  const currentPrimary =
    primaryId ?? value.find((i) => i.isPrimary)?.id ?? value[0]?.id ?? null;

  const validate = useCallback(
    (file) => {
      if (accept.length && !accept.includes(file.type)) {
        return `"${file.name}" không đúng định dạng (chỉ JPG, PNG, WEBP).`;
      }
      if (file.size > maxSizeMb * 1024 * 1024) {
        return `"${file.name}" vượt quá ${maxSizeMb}MB.`;
      }
      return null;
    },
    [accept, maxSizeMb],
  );

  const addFiles = useCallback(
    async (fileList) => {
      const files = Array.from(fileList || []);
      if (!files.length) return;

      const slots = maxFiles - value.length;
      if (slots <= 0) {
        notify.warning(`Chỉ được tải tối đa ${maxFiles} ảnh.`);
        return;
      }

      const accepted = [];
      for (const file of files.slice(0, slots)) {
        const err = validate(file);
        if (err) {
          notify.error(err);
          continue;
        }
        accepted.push(file);
      }
      if (files.length > slots) {
        notify.warning(`Chỉ thêm được ${slots} ảnh nữa (tối đa ${maxFiles}).`);
      }
      if (!accepted.length) return;

      const newItems = accepted.map((file) => ({
        id: nextTempId(),
        url: URL.createObjectURL(file),
        name: file.name,
        file,
        uploading: !!uploadFn,
        progress: uploadFn ? 0 : 100,
      }));

      let merged = [...value, ...newItems];
      onChange?.(merged);

      // Nếu chưa có primary, đặt ảnh đầu tiên làm đại diện.
      if (!currentPrimary && merged.length) {
        onPrimaryChange?.(merged[0].id);
      }

      if (uploadFn) {
        for (const item of newItems) {
          try {
            const res = await uploadFn(item.file, (p) => {
              merged = merged.map((it) =>
                it.id === item.id ? { ...it, progress: p } : it,
              );
              onChange?.(merged);
            });
            merged = merged.map((it) =>
              it.id === item.id
                ? { ...it, id: res?.id ?? it.id, url: res?.url ?? it.url, uploading: false, progress: 100 }
                : it,
            );
            onChange?.(merged);
          } catch (e) {
            notify.apiError(e, `Tải "${item.name}" thất bại`);
            merged = merged.map((it) =>
              it.id === item.id ? { ...it, uploading: false, error: true } : it,
            );
            onChange?.(merged);
          }
        }
      }
    },
    [value, maxFiles, validate, uploadFn, onChange, currentPrimary, onPrimaryChange],
  );

  const removeItem = (id) => {
    const target = value.find((i) => i.id === id);
    if (target?.url?.startsWith('blob:')) URL.revokeObjectURL(target.url);
    const next = value.filter((i) => i.id !== id);
    onChange?.(next);
    if (currentPrimary === id) onPrimaryChange?.(next[0]?.id ?? null);
  };

  const setPrimary = (id) => onPrimaryChange?.(id);

  // --- Sắp xếp bằng kéo thả ---
  const handleDragStart = (index) => (dragIndex.current = index);
  const handleDropReorder = (index) => {
    const from = dragIndex.current;
    dragIndex.current = null;
    if (from == null || from === index) return;
    const next = [...value];
    const [moved] = next.splice(from, 1);
    next.splice(index, 0, moved);
    onChange?.(next);
  };

  const handleZoneDrop = (e) => {
    e.preventDefault();
    setDragOver(false);
    if (e.dataTransfer?.files?.length) addFiles(e.dataTransfer.files);
  };

  return (
    <Box>
      {/* Vùng kéo thả */}
      <Box
        onDragOver={(e) => {
          e.preventDefault();
          setDragOver(true);
        }}
        onDragLeave={() => setDragOver(false)}
        onDrop={handleZoneDrop}
        onClick={() => inputRef.current?.click()}
        sx={{
          border: '2px dashed',
          borderColor: dragOver ? 'primary.main' : 'divider',
          bgcolor: dragOver ? 'action.hover' : 'transparent',
          borderRadius: 2,
          p: 3,
          textAlign: 'center',
          cursor: 'pointer',
          transition: 'border-color .2s, background-color .2s',
        }}
      >
        <CloudUploadOutlinedIcon sx={{ fontSize: 40, color: 'text.secondary' }} />
        <Typography sx={{ mt: 1 }}>Kéo thả ảnh vào đây hoặc bấm để chọn</Typography>
        <Typography variant="caption" color="text.secondary">
          Tối đa {maxFiles} ảnh · mỗi ảnh ≤ {maxSizeMb}MB · JPG, PNG, WEBP
        </Typography>
        <input
          ref={inputRef}
          type="file"
          hidden
          multiple
          accept={accept.join(',')}
          onChange={(e) => {
            addFiles(e.target.files);
            e.target.value = '';
          }}
        />
      </Box>

      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mt: 1 }}>
        <Typography variant="caption" color="text.secondary">
          {value.length}/{maxFiles} ảnh
        </Typography>
        {privateMode && (
          <Chip size="small" icon={<LockOutlinedIcon />} label="Riêng tư" variant="outlined" />
        )}
      </Stack>

      {/* Lưới preview */}
      {value.length > 0 && (
        <Box
          sx={{
            mt: 1.5,
            display: 'grid',
            gap: 1.5,
            gridTemplateColumns: {
              xs: 'repeat(2, 1fr)',
              sm: 'repeat(3, 1fr)',
              md: 'repeat(4, 1fr)',
            },
          }}
        >
          {value.map((item, index) => {
            const isPrimary = item.id === currentPrimary;
            return (
              <Box
                key={item.id}
                draggable={sortable && !item.uploading}
                onDragStart={() => handleDragStart(index)}
                onDragOver={(e) => e.preventDefault()}
                onDrop={() => handleDropReorder(index)}
                sx={{
                  position: 'relative',
                  aspectRatio: '1 / 1',
                  borderRadius: 1.5,
                  overflow: 'hidden',
                  border: '2px solid',
                  borderColor: isPrimary ? 'primary.main' : 'divider',
                  cursor: sortable ? 'grab' : 'default',
                  '&:hover .img-actions': { opacity: 1 },
                }}
              >
                <Box
                  component="img"
                  src={item.url}
                  alt={item.name || 'Ảnh tải lên'}
                  sx={{
                    width: '100%',
                    height: '100%',
                    objectFit: 'cover',
                    display: 'block',
                    ...(item.error && { filter: 'grayscale(1)', opacity: 0.5 }),
                  }}
                />

                {item.uploading && (
                  <LinearProgress
                    variant="determinate"
                    value={item.progress || 0}
                    sx={{ position: 'absolute', bottom: 0, left: 0, right: 0 }}
                  />
                )}

                {isPrimary && (
                  <Chip
                    size="small"
                    color="primary"
                    icon={<StarIcon />}
                    label="Đại diện"
                    sx={{ position: 'absolute', top: 4, left: 4 }}
                  />
                )}

                <Stack
                  className="img-actions"
                  direction="row"
                  spacing={0.5}
                  sx={{
                    position: 'absolute',
                    top: 4,
                    right: 4,
                    opacity: { xs: 1, md: 0 },
                    transition: 'opacity .2s',
                  }}
                >
                  {!isPrimary && (
                    <Tooltip title="Đặt làm ảnh đại diện">
                      <IconButton
                        size="small"
                        onClick={() => setPrimary(item.id)}
                        sx={{ bgcolor: 'rgba(0,0,0,0.5)', color: '#fff', '&:hover': { bgcolor: 'rgba(0,0,0,0.7)' } }}
                      >
                        <StarBorderIcon fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                  <Tooltip title="Xóa ảnh">
                    <IconButton
                      size="small"
                      onClick={() => removeItem(item.id)}
                      sx={{ bgcolor: 'rgba(0,0,0,0.5)', color: '#fff', '&:hover': { bgcolor: 'error.main' } }}
                    >
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
              </Box>
            );
          })}
        </Box>
      )}

      <Button sx={{ mt: 1.5 }} onClick={() => inputRef.current?.click()} disabled={value.length >= maxFiles}>
        Chọn từ thư viện
      </Button>
    </Box>
  );
}
