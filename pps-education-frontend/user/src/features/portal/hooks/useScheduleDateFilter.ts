import { useEffect, useState } from "react";
import { AcademicTermResponse, listAcademicTerms } from "../api";

/** So sánh trực tiếp chuỗi ISO "YYYY-MM-DD" (đủ đúng thứ tự, không cần parse Date). */
export function inDateRange(date: string, from: string, to: string): boolean {
  if (from && date < from) return false;
  if (to && date > to) return false;
  return true;
}

interface UseScheduleDateFilterResult {
  fromDate: string;
  toDate: string;
  terms: AcademicTermResponse[];
  selectedTermId: number | null;
  isActive: boolean;
  setFromDate: (v: string) => void;
  setToDate: (v: string) => void;
  setDateRange: (fromDate: string, toDate: string) => void;
  selectTerm: (termId: number | null) => void;
  reset: () => void;
}

/**
 * Lọc theo khoảng thời gian + học kỳ cho tab Lịch học & Chuyên cần (dùng chung ScheduleTab/
 * StudentScheduleTab). Bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-12.
 *
 * "Học kỳ" chỉ là 1 preset của khoảng thời gian — academic_terms KHÔNG có FK trực tiếp tới
 * classes/class_sessions (độc lập, chỉ gắn theo site, xem docs/sdd-groups/06-hoc-thuat.md mục
 * c-bis: "1 lớp tồn tại xuyên suốt nhiều kỳ"). Chọn 1 kỳ = tự set fromDate/toDate theo
 * term.startDate/endDate; nếu người dùng sửa tay lại 1 trong 2 ngày sau đó, tự bỏ chọn kỳ (ngày
 * không còn khớp nguyên kỳ nữa, tránh hiển thị sai là đang xem "cả kỳ X" trong khi đã đổi).
 * Lọc dữ liệu vẫn làm ở client (ScheduleTab đã tải hết buổi học/điểm danh của lớp trong 1 lần gọi,
 * không cần gọi lại API mỗi khi đổi filter).
 */
export function useScheduleDateFilter(siteId: number | null): UseScheduleDateFilterResult {
  const [fromDate, setFromDateState] = useState("");
  const [toDate, setToDateState] = useState("");
  const [terms, setTerms] = useState<AcademicTermResponse[]>([]);
  const [selectedTermId, setSelectedTermId] = useState<number | null>(null);

  useEffect(() => {
    setTerms([]);
    setSelectedTermId(null);
    setFromDateState("");
    setToDateState("");
    if (!siteId) return;
    listAcademicTerms(siteId)
      .then((list) => setTerms([...list].sort((a, b) => b.startDate.localeCompare(a.startDate))))
      .catch(() => undefined);
  }, [siteId]);

  const setFromDate = (v: string) => {
    setFromDateState(v);
    setSelectedTermId(null);
  };
  const setToDate = (v: string) => {
    setToDateState(v);
    setSelectedTermId(null);
  };
  /** DateRangePicker báo cả 2 ngày cùng lúc (mỗi lần chọn 1 ngày trên lịch) — set gộp, tránh 2 lần setState rời rạc. */
  const setDateRange = (from: string, to: string) => {
    setFromDateState(from);
    setToDateState(to);
    setSelectedTermId(null);
  };

  const selectTerm = (termId: number | null) => {
    if (termId == null) {
      setSelectedTermId(null);
      setFromDateState("");
      setToDateState("");
      return;
    }
    const term = terms.find((t) => t.id === termId);
    if (!term) return;
    setSelectedTermId(termId);
    setFromDateState(term.startDate);
    setToDateState(term.endDate);
  };

  return {
    fromDate,
    toDate,
    terms,
    selectedTermId,
    isActive: !!fromDate || !!toDate,
    setFromDate,
    setToDate,
    setDateRange,
    selectTerm,
    reset: () => selectTerm(null)
  };
}
