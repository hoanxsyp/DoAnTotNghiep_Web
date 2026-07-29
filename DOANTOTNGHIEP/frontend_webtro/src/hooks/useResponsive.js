import { useTheme, useMediaQuery } from '@mui/material';

/**
 * Hook tiện lợi cho responsive [§11.7]. Trả về cờ theo breakpoint MUI để component tự điều chỉnh
 * (ví dụ sidebar thu thành drawer ở mobile).
 */
export const useResponsive = () => {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const isTablet = useMediaQuery(theme.breakpoints.between('md', 'lg'));
  const isDesktop = useMediaQuery(theme.breakpoints.up('lg'));

  return { isMobile, isTablet, isDesktop };
};

export default useResponsive;
