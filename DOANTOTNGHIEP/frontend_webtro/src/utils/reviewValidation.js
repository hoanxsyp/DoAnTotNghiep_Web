export const REVIEW_CONTENT_MIN_LENGTH = 10;
export const REVIEW_CONTENT_MAX_LENGTH = 2000;

export const normalizeReviewContent = (content) => {
  const trimmed = content?.trim() || '';
  return trimmed || null;
};

export const getReviewContentError = (rating, content) => {
  const normalized = normalizeReviewContent(content);
  if (!normalized) {
    return rating <= 2 ? 'Vui lòng nhập nội dung đánh giá ít nhất 10 ký tự.' : '';
  }
  if (normalized.length < REVIEW_CONTENT_MIN_LENGTH || normalized.length > REVIEW_CONTENT_MAX_LENGTH) {
    return 'Nội dung đánh giá phải từ 10 đến 2000 ký tự.';
  }
  return '';
};
