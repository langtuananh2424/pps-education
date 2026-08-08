import React, { useEffect, useMemo, useState } from "react";
import { ArrowRightLeft, Search } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";
import {
  AcademicYearResponse,
  ClassResponse,
  CurriculumResponse,
  PromoteClassResponse,
  listAcademicYears,
  listCurriculums,
  promoteClass
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ClassPromotionModalProps {
  classes: ClassResponse[];
  onClose: () => void;
  onPromoted: (newClass: ClassResponse) => void;
}

/**
 * UC-18b (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-08-07) —
 * chuyển lớp hàng loạt cuối năm học: chọn lớp nguồn, tạo lớp mới (giữ
 * nguyên site/loại hình lớp), chuyển toàn bộ học sinh đang học ACTIVE sang
 * lớp mới. Xem ClassService#promoteClass.
 */
export default function ClassPromotionModal({ classes, onClose, onPromoted }: ClassPromotionModalProps) {
  const [sourceQuery, setSourceQuery] = useState("");
  const [sourceClassId, setSourceClassId] = useState<number | null>(null);
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [academicYears, setAcademicYears] = useState<AcademicYearResponse[]>([]);
  const [result, setResult] = useState<PromoteClassResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [form, setForm] = useState({
    classCode: "",
    name: "",
    curriculumId: "",
    academicYearId: "",
    startDate: "",
    endDate: "",
    maxStudents: "",
    minStudents: ""
  });
  const [touched, setTouched] = useState({ classCode: false, name: false, curriculumId: false, academicYearId: false, startDate: false, maxStudents: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);

  useEffect(() => {
    listCurriculums().then(setCurriculums).catch(() => undefined);
    listAcademicYears().then(setAcademicYears).catch(() => undefined);
  }, []);

  const sourceClass = classes.find((c) => c.id === sourceClassId) ?? null;
  const filteredSourceClasses = useMemo(() => {
    if (!sourceQuery.trim()) return classes;
    const q = sourceQuery.toLowerCase();
    return classes.filter((c) => c.name.toLowerCase().includes(q) || c.classCode.toLowerCase().includes(q));
  }, [classes, sourceQuery]);
  const eligibleCurriculums = curriculums.filter((c) => c.siteId == null || (sourceClass && c.siteId === sourceClass.siteId));

  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));
  const invalid = {
    classCode: (touched.classCode || submitAttempted) && !form.classCode.trim(),
    name: (touched.name || submitAttempted) && !form.name.trim(),
    curriculumId: (touched.curriculumId || submitAttempted) && !form.curriculumId,
    academicYearId: (touched.academicYearId || submitAttempted) && !form.academicYearId,
    startDate: (touched.startDate || submitAttempted) && !form.startDate,
    maxStudents: (touched.maxStudents || submitAttempted) && !form.maxStudents
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!sourceClassId || !form.classCode.trim() || !form.name.trim() || !form.curriculumId || !form.academicYearId || !form.startDate || !form.maxStudents) {
      setError("Vui lòng chọn lớp nguồn và điền đủ các trường bắt buộc được đánh dấu đỏ.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const response = await promoteClass(sourceClassId, {
        classCode: form.classCode.trim(),
        name: form.name.trim(),
        curriculumId: Number(form.curriculumId),
        academicYearId: Number(form.academicYearId),
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        maxStudents: Number(form.maxStudents),
        minStudents: form.minStudents ? Number(form.minStudents) : undefined
      });
      setResult(response);
      onPromoted(response.newClass);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Chuyển lớp hàng loạt thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return (
      <Modal open onClose={onClose} title="Chuyển lớp hàng loạt — Kết quả" size="lg">
        <div className="space-y-4">
          <div className="bg-emerald-50 border border-emerald-100 rounded-lg p-3 text-xs text-emerald-700">
            Đã tạo lớp <span className="font-bold">{result.newClass.classCode}</span> — {result.newClass.name}. Chuyển thành công{" "}
            <span className="font-bold">{result.movedStudentCount}</span> học sinh.
          </div>

          {result.skippedStudentCount > 0 && (
            <div>
              <p className="text-[10px] uppercase font-bold text-slate-500 mb-2">Học sinh bị bỏ lại ({result.skippedStudentCount})</p>
              <div className="space-y-1.5 max-h-64 overflow-y-auto">
                {result.skippedStudents.map((s) => (
                  <div key={s.studentId} className="border border-amber-100 bg-amber-50 rounded-lg p-2.5 text-xs">
                    <div className="font-bold text-slate-800">
                      {s.studentCode} — {s.studentFullName}
                    </div>
                    <div className="text-[10px] text-amber-700 mt-0.5">{s.reason}</div>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="flex justify-end pt-2">
            <Button type="button" variant="primary" onClick={onClose}>
              Đóng
            </Button>
          </div>
        </div>
      </Modal>
    );
  }

  return (
    <Modal open onClose={onClose} title="Chuyển lớp hàng loạt cuối năm học" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div>
          <label className={labelClass}>Lớp nguồn *</label>
          {!sourceClass ? (
            <div className="border border-slate-200 rounded-lg">
              <div className="relative p-2 border-b border-slate-100">
                <Search className="absolute left-5 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-400" />
                <input
                  value={sourceQuery}
                  onChange={(e) => setSourceQuery(e.target.value)}
                  placeholder="Tìm theo tên / mã lớp..."
                  className="w-full bg-slate-50 border border-slate-200 text-xs pl-8 pr-3 py-2 rounded-lg focus:outline-none"
                />
              </div>
              <div className="max-h-40 overflow-y-auto divide-y divide-slate-100">
                {filteredSourceClasses.length === 0 ? (
                  <p className="text-xs text-slate-400 p-3 text-center">Không tìm thấy lớp phù hợp.</p>
                ) : (
                  filteredSourceClasses.map((c) => (
                    <button
                      key={c.id}
                      type="button"
                      onClick={() => setSourceClassId(c.id)}
                      className="w-full text-left px-3 py-2 text-xs hover:bg-slate-50"
                    >
                      <span className="font-mono font-bold text-brand-red">{c.classCode}</span> — {c.name}
                      <span className="text-slate-400"> · {c.siteName}</span>
                    </button>
                  ))
                )}
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-between border border-slate-200 rounded-lg p-3 text-xs bg-slate-50">
              <div>
                <span className="font-mono font-bold text-brand-red">{sourceClass.classCode}</span> — {sourceClass.name}
                <div className="text-[10px] text-slate-500 mt-0.5">
                  {sourceClass.siteName} · {sourceClass.classType === "LINKED" ? "Liên kết" : "Mở tại trung tâm"}
                </div>
              </div>
              <button type="button" onClick={() => setSourceClassId(null)} className="text-brand-red font-bold text-[11px] hover:underline shrink-0">
                Đổi lớp
              </button>
            </div>
          )}
        </div>

        <fieldset disabled={!sourceClass} className="space-y-3 disabled:opacity-50">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Mã lớp mới *</label>
              <input
                value={form.classCode}
                onChange={(e) => setForm({ ...form, classCode: e.target.value })}
                onBlur={() => markTouched("classCode")}
                className={`${invalid.classCode ? inputErrorClass : inputClass} font-mono`}
              />
              {invalid.classCode && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Mã lớp mới.</p>}
            </div>
            <div>
              <label className={labelClass}>Tên lớp mới *</label>
              <input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                onBlur={() => markTouched("name")}
                className={invalid.name ? inputErrorClass : inputClass}
              />
              {invalid.name && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Tên lớp mới.</p>}
            </div>

            <div className="col-span-2">
              <label className={labelClass}>Khung chương trình mới *</label>
              <Select
                value={form.curriculumId}
                onChange={(e) => setForm({ ...form, curriculumId: e.target.value })}
                onBlur={() => markTouched("curriculumId")}
                className={invalid.curriculumId ? inputErrorClass : inputClass}
              >
                <option value="">-- Chọn khung chương trình --</option>
                {eligibleCurriculums.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c.code} — {c.name}
                  </option>
                ))}
              </Select>
              {invalid.curriculumId && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Khung chương trình mới.</p>}
            </div>

            <div>
              <label className={labelClass}>Năm học mới *</label>
              <Select
                value={form.academicYearId}
                onChange={(e) => setForm({ ...form, academicYearId: e.target.value })}
                onBlur={() => markTouched("academicYearId")}
                className={invalid.academicYearId ? inputErrorClass : inputClass}
              >
                <option value="">-- Chọn năm học --</option>
                {academicYears.map((y) => (
                  <option key={y.id} value={y.id}>
                    {y.code} — {y.name}
                  </option>
                ))}
              </Select>
              {invalid.academicYearId && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Năm học mới.</p>}
            </div>
            <div>
              <label className={labelClass}>Sĩ số tối đa *</label>
              <input
                type="number"
                min={1}
                value={form.maxStudents}
                onChange={(e) => setForm({ ...form, maxStudents: e.target.value })}
                onBlur={() => markTouched("maxStudents")}
                className={invalid.maxStudents ? inputErrorClass : inputClass}
              />
              {invalid.maxStudents && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Sĩ số tối đa.</p>}
            </div>

            <div>
              <label className={labelClass}>Ngày bắt đầu lớp mới *</label>
              <DatePicker
                value={form.startDate}
                onChange={(v) => {
                  setForm({ ...form, startDate: v });
                  markTouched("startDate");
                }}
                max={form.endDate || undefined}
                hasError={invalid.startDate}
              />
              {invalid.startDate && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày bắt đầu.</p>}
              <p className="text-[10px] text-slate-400 mt-1">Cũng là ngày hiệu lực chuyển ghi danh học sinh sang lớp mới.</p>
            </div>
            <div>
              <label className={labelClass}>Ngày kết thúc (dự kiến)</label>
              <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
            </div>
            <div>
              <label className={labelClass}>Sĩ số tối thiểu</label>
              <input type="number" min={0} value={form.minStudents} onChange={(e) => setForm({ ...form, minStudents: e.target.value })} className={inputClass} />
            </div>
          </div>

          <p className="text-[10px] text-slate-400">
            Lớp mới giữ nguyên Điểm trường và Loại hình lớp của lớp nguồn. Chỉ học sinh đang học (trạng thái ACTIVE) được chuyển sang;
            học sinh Bảo lưu/Đình chỉ/Đã tốt nghiệp/Thôi học bị bỏ lại. Giáo viên KHÔNG được copy sang — gán lại thủ công sau.
          </p>
        </fieldset>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting || !sourceClass}>
            <ArrowRightLeft className="w-3.5 h-3.5" />
            {submitting ? "Đang chuyển..." : "Chuyển lớp hàng loạt"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
