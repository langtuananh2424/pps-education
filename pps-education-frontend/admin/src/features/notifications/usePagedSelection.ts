import { useMemo, useState } from "react";

/**
 * Phân trang phía client + tick chọn nhiều dòng cho các hàng chờ duyệt (BE trả cả danh sách,
 * không có Page<T>). Tập đã chọn giữ nguyên khi chuyển trang; id không còn trong danh sách
 * (đã duyệt/từ chối xong) tự bị loại khỏi tập chọn.
 */
export function usePagedSelection<T extends { id: number }>(items: T[]) {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [selectedRaw, setSelectedRaw] = useState<Set<number>>(new Set());

  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const currentPage = Math.min(page, totalPages - 1);
  const pageItems = useMemo(
    () => items.slice(currentPage * pageSize, (currentPage + 1) * pageSize),
    [items, currentPage, pageSize]
  );
  const selectedIds = useMemo(() => {
    const existing = new Set(items.map((it) => it.id));
    return Array.from(selectedRaw).filter((id) => existing.has(id));
  }, [items, selectedRaw]);
  const selectedSet = useMemo(() => new Set(selectedIds), [selectedIds]);

  const allPageSelected = pageItems.length > 0 && pageItems.every((it) => selectedSet.has(it.id));
  const somePageSelected = pageItems.some((it) => selectedSet.has(it.id));

  const toggleOne = (id: number) =>
    setSelectedRaw((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  /** Tick/bỏ tick toàn bộ dòng của trang hiện tại. */
  const togglePage = () =>
    setSelectedRaw((prev) => {
      const next = new Set(prev);
      if (allPageSelected) pageItems.forEach((it) => next.delete(it.id));
      else pageItems.forEach((it) => next.add(it.id));
      return next;
    });

  const clearSelection = () => setSelectedRaw(new Set());

  return {
    page: currentPage,
    pageSize,
    setPage,
    setPageSize: (size: number) => {
      setPageSize(size);
      setPage(0);
    },
    pageItems,
    selectedIds,
    selectedSet,
    allPageSelected,
    somePageSelected,
    toggleOne,
    togglePage,
    clearSelection
  };
}
