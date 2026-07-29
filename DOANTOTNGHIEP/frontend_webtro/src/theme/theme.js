import { createTheme } from '@mui/material/styles';

/**
 * Theme MUI cho Webtro (canonical mục 12 + docs/04_THIET_KE_GIAO_DIEN.md).
 *
 * Bảng màu: teal (#0F766E) làm primary — gợi cảm giác tin cậy, gần gũi, hợp lĩnh vực nhà ở, khác
 * biệt với đỏ/cam của các sàn BĐS lớn. Font Inter hỗ trợ đầy đủ tiếng Việt (kể cả "ự", "ỹ", "ặ").
 */
const buildTheme = (mode) =>
  createTheme({
    palette: {
      mode,
      primary: {
        main: '#0F766E',
        light: '#14B8A6',
        dark: '#0B5A54',
        contrastText: '#FFFFFF',
      },
      secondary: {
        main: '#F59E0B',
        light: '#FBBF24',
        dark: '#D97706',
        contrastText: '#1F2937',
      },
      success: { main: '#16A34A' },
      warning: { main: '#F59E0B' },
      error: { main: '#DC2626' },
      info: { main: '#0EA5E9' },
      ...(mode === 'light'
        ? {
            background: { default: '#F1F5F9', paper: '#FFFFFF' },
            text: { primary: '#0F172A', secondary: '#475569' },
            divider: '#E2E8F0',
          }
        : {
            background: { default: '#0B1120', paper: '#111827' },
            text: { primary: '#F1F5F9', secondary: '#94A3B8' },
            divider: '#1E293B',
          }),
    },
    typography: {
      fontFamily: "'Inter', 'Segoe UI', 'Roboto', 'Helvetica Neue', sans-serif",
      h1: { fontSize: '2.25rem', fontWeight: 700, lineHeight: 1.2 },
      h2: { fontSize: '1.875rem', fontWeight: 700, lineHeight: 1.25 },
      h3: { fontSize: '1.5rem', fontWeight: 600, lineHeight: 1.3 },
      h4: { fontSize: '1.25rem', fontWeight: 600 },
      h5: { fontSize: '1.125rem', fontWeight: 600 },
      h6: { fontSize: '1rem', fontWeight: 600 },
      button: { textTransform: 'none', fontWeight: 600 },
    },
    shape: { borderRadius: 10 },
    spacing: 8,
    breakpoints: {
      values: { xs: 0, sm: 600, md: 900, lg: 1200, xl: 1536 },
    },
    components: {
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: { root: { borderRadius: 8, paddingInline: 18 } },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: 12,
            border: '1px solid',
            borderColor: mode === 'light' ? '#E2E8F0' : '#1E293B',
          },
        },
      },
      MuiPaper: { defaultProps: { elevation: 0 } },
      MuiTextField: { defaultProps: { size: 'small', fullWidth: true } },
      MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
      MuiTableCell: {
        styleOverrides: { head: { fontWeight: 700, whiteSpace: 'nowrap' } },
      },
    },
  });

export const lightTheme = buildTheme('light');
export const darkTheme = buildTheme('dark');

export const getTheme = (mode) => (mode === 'dark' ? darkTheme : lightTheme);

export default lightTheme;
