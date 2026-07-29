import React from 'react';
import ReactDOM from 'react-dom/client';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import 'dayjs/locale/vi';
import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

import store from '@/redux/store';
import router from '@/routes/router';
import ThemedApp from '@/ThemedApp';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <Provider store={store}>
      <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="vi">
        <ThemedApp>
          <RouterProvider router={router} />
          <ToastContainer
            position="top-right"
            autoClose={4000}
            newestOnTop
            closeOnClick
            pauseOnHover
            theme="colored"
          />
        </ThemedApp>
      </LocalizationProvider>
    </Provider>
  </React.StrictMode>,
);
