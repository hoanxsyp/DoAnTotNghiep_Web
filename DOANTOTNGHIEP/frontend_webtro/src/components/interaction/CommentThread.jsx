import { useMemo, useState } from 'react';
import {
  Box,
  Stack,
  Avatar,
  Typography,
  Chip,
  Button,
  IconButton,
  TextField,
  Divider,
  Menu,
  MenuItem,
  Tooltip,
} from '@mui/material';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import ReplyIcon from '@mui/icons-material/Reply';
import RichTextViewer from '@/components/common/RichTextViewer';
import SentimentChip from '@/components/common/SentimentChip';
import EmptyState from '@/components/common/EmptyState';
import LoadingSkeleton from '@/components/common/LoadingSkeleton';
import { fromNow } from '@/utils/format';

/**
 * Bình luận 2 cấp (docs/04 mục 6 #13, [§3.11]).
 *
 * - Không lồng sâu hơn 2 cấp (tránh vỡ layout mobile).
 * - Badge "Chủ trọ" cho userId === ownerId (CMT-03).
 * - Nút Sửa/Xóa chỉ hiện với chính chủ khi canEdit (nhận từ API).
 * - Chủ trọ KHÔNG thấy nút xóa bình luận người khác — chỉ báo cáo/phản hồi [§3.11].
 * - Nội dung render qua RichTextViewer (chống XSS).
 *
 * Props: comments (arr), listingId, ownerId, currentUser (obj), onReply(parentId, content),
 *        onEdit(id, content), onDelete(id), onReport(comment), loading, hasMore, onLoadMore
 *
 * Mỗi comment: { id, userId, userName, userAvatar, content, createdAt, editedAt,
 *   canEdit, canDelete, parentId, replies[], sentimentLabel, sentimentScore, sentimentConfidence }
 */

function ReplyBox({ placeholder = 'Viết bình luận…', onSubmit, onCancel, submitting }) {
  const [text, setText] = useState('');
  const trimmed = text.trim();
  const tooShort = trimmed.length > 0 && trimmed.length < 3;

  const handleSubmit = () => {
    if (trimmed.length < 3) return;
    Promise.resolve(onSubmit?.(trimmed)).then((ok) => {
      if (ok !== false) setText('');
    });
  };

  return (
    <Box sx={{ mt: 1 }}>
      <TextField
        value={text}
        onChange={(e) => setText(e.target.value)}
        placeholder={placeholder}
        multiline
        minRows={2}
        fullWidth
        error={tooShort}
        helperText={tooShort ? 'Nội dung tối thiểu 3 ký tự' : ''}
        inputProps={{ maxLength: 1000 }}
      />
      <Stack direction="row" spacing={1} sx={{ mt: 1 }} justifyContent="flex-end">
        {onCancel && (
          <Button size="small" color="inherit" onClick={onCancel}>
            Hủy
          </Button>
        )}
        <Button
          size="small"
          variant="contained"
          disabled={trimmed.length < 3 || submitting}
          onClick={handleSubmit}
        >
          {submitting ? 'Đang gửi…' : 'Gửi'}
        </Button>
      </Stack>
    </Box>
  );
}

function CommentItem({
  comment,
  ownerId,
  currentUser,
  depth = 0,
  onReply,
  onEdit,
  onDelete,
  onReport,
}) {
  const [replying, setReplying] = useState(false);
  const [editing, setEditing] = useState(false);
  const [menuEl, setMenuEl] = useState(null);

  const isOwnerComment = ownerId != null && comment.userId === ownerId;
  const isMine = currentUser && comment.userId === currentUser.id;

  const replies = comment.replies || [];

  return (
    <Box sx={{ py: 1.5 }}>
      <Stack direction="row" spacing={1.5}>
        <Avatar src={comment.userAvatar} sx={{ width: 36, height: 36 }}>
          {comment.userName?.charAt(0)?.toUpperCase()}
        </Avatar>

        <Box sx={{ flex: 1, minWidth: 0 }}>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
              {comment.userName || 'Người dùng'}
            </Typography>
            {isOwnerComment && <Chip size="small" color="primary" label="Chủ trọ" />}
            <Typography variant="caption" color="text.secondary">
              {fromNow(comment.createdAt)}
              {comment.editedAt ? ' · đã sửa' : ''}
            </Typography>
            {comment.sentimentLabel && (
              <SentimentChip
                label={comment.sentimentLabel}
                score={comment.sentimentScore}
                confidence={comment.sentimentConfidence}
              />
            )}
          </Stack>

          {editing ? (
            <ReplyBox
              placeholder="Chỉnh sửa bình luận…"
              onSubmit={(content) =>
                Promise.resolve(onEdit?.(comment.id, content)).then((ok) => {
                  if (ok !== false) setEditing(false);
                  return ok;
                })
              }
              onCancel={() => setEditing(false)}
            />
          ) : (
            <Box sx={{ mt: 0.25 }}>
              <RichTextViewer content={comment.content} variant="body2" />
            </Box>
          )}

          {!editing && (
            <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
              {depth === 0 && onReply && (
                <Button
                  size="small"
                  color="inherit"
                  startIcon={<ReplyIcon fontSize="small" />}
                  onClick={() => setReplying((v) => !v)}
                >
                  Trả lời
                </Button>
              )}
              {(isMine || onReport) && (
                <>
                  <IconButton
                    size="small"
                    aria-label="Tùy chọn bình luận"
                    onClick={(e) => setMenuEl(e.currentTarget)}
                  >
                    <MoreVertIcon fontSize="small" />
                  </IconButton>
                  <Menu anchorEl={menuEl} open={!!menuEl} onClose={() => setMenuEl(null)}>
                    {isMine && comment.canEdit && (
                      <MenuItem
                        onClick={() => {
                          setMenuEl(null);
                          setEditing(true);
                        }}
                      >
                        Sửa
                      </MenuItem>
                    )}
                    {isMine && comment.canDelete && (
                      <MenuItem
                        onClick={() => {
                          setMenuEl(null);
                          onDelete?.(comment.id);
                        }}
                      >
                        Xóa
                      </MenuItem>
                    )}
                    {!isMine && onReport && (
                      <MenuItem
                        onClick={() => {
                          setMenuEl(null);
                          onReport?.(comment);
                        }}
                      >
                        Báo cáo
                      </MenuItem>
                    )}
                  </Menu>
                </>
              )}
            </Stack>
          )}

          {replying && (
            <ReplyBox
              placeholder="Viết câu trả lời…"
              onSubmit={(content) =>
                Promise.resolve(onReply?.(comment.id, content)).then((ok) => {
                  if (ok !== false) setReplying(false);
                  return ok;
                })
              }
              onCancel={() => setReplying(false)}
            />
          )}

          {replies.length > 0 && (
            <Box sx={{ mt: 1, pl: 2, borderLeft: '2px solid', borderColor: 'divider' }}>
              {replies.map((child) => (
                <CommentItem
                  key={child.id}
                  comment={child}
                  ownerId={ownerId}
                  currentUser={currentUser}
                  depth={depth + 1}
                  onEdit={onEdit}
                  onDelete={onDelete}
                  onReport={onReport}
                />
              ))}
            </Box>
          )}
        </Box>
      </Stack>
    </Box>
  );
}

export default function CommentThread({
  comments = [],
  listingId,
  ownerId,
  currentUser,
  onReply,
  onEdit,
  onDelete,
  onReport,
  onCreate,
  loading = false,
  hasMore = false,
  onLoadMore,
}) {
  // Hỗ trợ cả dạng phẳng (parentId) lẫn dạng lồng (replies[]).
  const roots = useMemo(() => {
    if (comments.some((c) => c.replies)) return comments.filter((c) => !c.parentId);
    const byParent = {};
    comments.forEach((c) => {
      if (c.parentId) {
        byParent[c.parentId] = byParent[c.parentId] || [];
        byParent[c.parentId].push(c);
      }
    });
    return comments
      .filter((c) => !c.parentId)
      .map((c) => ({ ...c, replies: byParent[c.id] || [] }));
  }, [comments]);

  return (
    <Box>
      {onCreate && currentUser && (
        <ReplyBox
          placeholder="Đặt câu hỏi hoặc bình luận về tin này…"
          onSubmit={(content) => onCreate(content)}
        />
      )}

      {loading && roots.length === 0 ? (
        <LoadingSkeleton variant="comment" count={3} />
      ) : roots.length === 0 ? (
        <EmptyState
          size="sm"
          title="Chưa có bình luận nào"
          description="Hãy là người đầu tiên đặt câu hỏi về tin đăng này."
        />
      ) : (
        <Stack divider={<Divider flexItem />} sx={{ mt: 1 }}>
          {roots.map((c) => (
            <CommentItem
              key={c.id}
              comment={c}
              ownerId={ownerId}
              currentUser={currentUser}
              onReply={onReply}
              onEdit={onEdit}
              onDelete={onDelete}
              onReport={onReport}
            />
          ))}
        </Stack>
      )}

      {hasMore && (
        <Box sx={{ textAlign: 'center', mt: 1 }}>
          <Button onClick={onLoadMore} disabled={loading}>
            {loading ? 'Đang tải…' : 'Xem thêm bình luận'}
          </Button>
        </Box>
      )}
    </Box>
  );
}
