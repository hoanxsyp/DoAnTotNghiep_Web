import { useMemo, useState } from 'react';
import { Box, Typography, Link, Button } from '@mui/material';

/**
 * Nhận diện URL http/https. Cố ý KHÔNG bắt scheme khác:
 * javascript:, data:, vbscript: là vector XSS kinh điển.
 */
const URL_REGEX = /(https?:\/\/[^\s<>"']+)/g;

/**
 * Render nội dung do người dùng nhập một cách AN TOÀN (docs/04 mục 6.2).
 *
 * BẢO MẬT — đọc kỹ trước khi sửa:
 * 1. TUYỆT ĐỐI KHÔNG dùng dangerouslySetInnerHTML ở đây hay bất kỳ đâu [§11.1] (luật F5).
 * 2. Chuỗi đưa vào JSX dưới dạng {text} -> React tự escape mọi HTML entity.
 *    "<script>alert(1)</script>" sẽ HIỂN THỊ NGUYÊN VĂN, không thực thi.
 * 3. Không dùng markdown/HTML parser -> không có bề mặt tấn công.
 * 4. Link chỉ chấp nhận http/https. Không bao giờ render javascript:.
 */
export default function RichTextViewer({
  content = '',
  maxLines,
  expandable = false,
  variant = 'body1',
}) {
  const [expanded, setExpanded] = useState(false);

  // Tách text thành đoạn (theo \n), trong mỗi đoạn tách URL ra để bọc <Link>.
  const paragraphs = useMemo(() => {
    if (!content) return [];
    return String(content)
      .split(/\r?\n/)
      .map((line) => line.split(URL_REGEX).filter(Boolean));
  }, [content]);

  if (!content) return null;

  const clamp = maxLines && !expanded;

  return (
    <Box>
      <Box
        sx={
          clamp
            ? {
                display: '-webkit-box',
                WebkitLineClamp: maxLines,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }
            : undefined
        }
      >
        {paragraphs.map((parts, i) => (
          // eslint-disable-next-line react/no-array-index-key
          <Typography key={i} variant={variant} sx={{ minHeight: '1.6em', wordBreak: 'break-word' }}>
            {parts.map((part, j) =>
              URL_REGEX.test(part) ? (
                <Link
                  // eslint-disable-next-line react/no-array-index-key
                  key={j}
                  href={part}
                  target="_blank"
                  rel="nofollow noopener noreferrer"
                >
                  {part}
                </Link>
              ) : (
                // {part} -> React escape tự động. Đây chính là lớp chống XSS.
                part
              ),
            )}
          </Typography>
        ))}
      </Box>

      {expandable && maxLines && (
        <Button size="small" onClick={() => setExpanded((v) => !v)} sx={{ mt: 1 }}>
          {expanded ? 'Thu gọn' : 'Xem thêm'}
        </Button>
      )}
    </Box>
  );
}
