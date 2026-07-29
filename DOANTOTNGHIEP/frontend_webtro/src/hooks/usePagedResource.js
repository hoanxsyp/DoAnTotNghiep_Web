import { useState, useEffect, useCallback, useRef } from 'react';

/**
 * Hook lấy dữ liệu phân trang cho các trang quản trị: gộp `page`/`size`/`sort` với bộ lọc, gọi
 * `fetcher(params)`, và quản lý trạng thái loading/error/data. Đối xứng với envelope phân trang
 * canonical §7.1 (`items`, `totalElements`, `page`...). Trang chỉ gọi qua api/ (luật F1) — `fetcher`
 * chính là một hàm của adminApi.
 *
 * @param {Function} fetcher (params) => Promise<{ items, totalElements, ... }>
 * @param {object} options { initialParams, initialSize, initialSort }
 */
const usePagedResource = (fetcher, { initialParams = {}, initialSize = 20, initialSort } = {}) => {
  const [params, setParams] = useState(initialParams);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(initialSize);
  const [sort, setSort] = useState(initialSort);
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [reloadFlag, setReloadFlag] = useState(0);
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const reload = useCallback(() => setReloadFlag((n) => n + 1), []);

  // Đổi bộ lọc -> quay về trang đầu.
  const updateParams = useCallback((next) => {
    setParams((prev) => ({ ...prev, ...next }));
    setPage(0);
  }, []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    const query = { page, size, ...params };
    if (sort) query.sort = sort;
    fetcherRef.current(query)
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [page, size, sort, params, reloadFlag]);

  const items = Array.isArray(data) ? data : data?.items ?? [];
  const total = data?.totalElements ?? items.length;

  return {
    items,
    data,
    total,
    loading,
    error,
    page,
    size,
    sort,
    params,
    setPage,
    setSize,
    setSort,
    setParams: updateParams,
    reload,
  };
};

export default usePagedResource;
