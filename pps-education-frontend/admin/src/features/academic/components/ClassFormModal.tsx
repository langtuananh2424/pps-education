import React, { useEffect, useState } from "react";
import { Plus } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { listSites, SiteResponse } from "@/features/facility/api";
import { ClassResponse, CreateClassRequest, createClass, CurriculumResponse, listCurriculums } from "../api";
import Modal from "@/components/ui/Modal";
import Button from "@/components/ui/Button";
import DatePicker from "@/components/ui/DatePicker";
import Select from "@/components/ui/Select";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const inputErrorClass = "w-full bg-rose-50/40 border border-rose-400 text-xs p-2.5 rounded-lg focus:outline-none focus:ring-1 focus:ring-rose-300";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

interface ClassFormModalProps {
  onClose: () => void;
  onCreated: (created: ClassResponse) => void;
}

/** UC-18 Main Flow bước 3-4: khởi tạo record lớp học thực tế. */
export default function ClassFormModal({ onClose, onCreated }: ClassFormModalProps) {
  const [sites, setSites] = useState<SiteResponse[]>([]);
  const [curriculums, setCurriculums] = useState<CurriculumResponse[]>([]);
  const [form, setForm] = useState({
    classCode: "",
    name: "",
    siteId: "",
    curriculumId: "",
    classType: "OPEN" as CreateClassRequest["classType"],
    maxStudents: "",
    minStudents: "",
    startDate: "",
    endDate: "",
    academicYear: "",
    semester: ""
  });
  const [touched, setTouched] = useState({ classCode: false, name: false, siteId: false, curriculumId: false, maxStudents: false, startDate: false });
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listSites().then(setSites).catch(() => undefined);
    listCurriculums().then(setCurriculums).catch(() => undefined);
  }, []);

  const markTouched = (field: keyof typeof touched) => setTouched((t) => ({ ...t, [field]: true }));
  const invalid = {
    classCode: (touched.classCode || submitAttempted) && !form.classCode.trim(),
    name: (touched.name || submitAttempted) && !form.name.trim(),
    siteId: (touched.siteId || submitAttempted) && !form.siteId,
    curriculumId: (touched.curriculumId || submitAttempted) && !form.curriculumId,
    maxStudents: (touched.maxStudents || submitAttempted) && !form.maxStudents,
    startDate: (touched.startDate || submitAttempted) && !form.startDate
  };

  const selectedSite = sites.find((s) => String(s.id) === form.siteId) ?? null;
  const eligibleSites = form.classType === "LINKED" ? sites.filter((s) => s.siteType === "PARTNER") : sites;
  const eligibleCurriculums = curriculums.filter((c) => c.siteId == null || (selectedSite && c.siteId === selectedSite.id));

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitAttempted(true);
    if (!form.classCode.trim() || !form.name.trim() || !form.siteId || !form.curriculumId || !form.maxStudents || !form.startDate) {
      setError("Vui lòng điền đủ các trường bắt buộc được đánh dấu đỏ.");
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const request: CreateClassRequest = {
        classCode: form.classCode.trim(),
        name: form.name.trim(),
        siteId: Number(form.siteId),
        curriculumId: Number(form.curriculumId),
        classType: form.classType,
        maxStudents: Number(form.maxStudents),
        minStudents: form.minStudents ? Number(form.minStudents) : undefined,
        startDate: form.startDate,
        endDate: form.endDate || undefined,
        academicYear: form.academicYear.trim() || undefined,
        semester: form.semester.trim() || undefined
      };
      const created = await createClass(request);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Tạo lớp học thất bại.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Modal open onClose={onClose} title="Thêm lớp học mới (UC-18)" size="lg">
      <form onSubmit={handleSubmit} className="space-y-5">
        {error && <div className="text-xs text-rose-600 bg-rose-50 border border-rose-100 p-2.5 rounded-lg">{error}</div>}

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Mã lớp *</label>
            <input
              value={form.classCode}
              onChange={(e) => setForm({ ...form, classCode: e.target.value })}
              onBlur={() => markTouched("classCode")}
              className={`${invalid.classCode ? inputErrorClass : inputClass} font-mono`}
            />
            {invalid.classCode && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Mã lớp.</p>}
          </div>
          <div>
            <label className={labelClass}>Tên lớp *</label>
            <input
              value={form.name}
              onChange={(e) => setForm({ ...form, name: e.target.value })}
              onBlur={() => markTouched("name")}
              className={invalid.name ? inputErrorClass : inputClass}
            />
            {invalid.name && <p className="text-[10px] text-rose-600 mt-1">Vui lòng nhập Tên lớp.</p>}
          </div>

          <div>
            <label className={labelClass}>Loại hình lớp *</label>
            <Select
              value={form.classType}
              onChange={(e) => setForm({ ...form, classType: e.target.value as CreateClassRequest["classType"], siteId: "" })}
              className={inputClass}
            >
              <option value="OPEN">Lớp mở tại trung tâm</option>
              <option value="LINKED">Lớp liên kết trường</option>
            </Select>
            {form.classType === "LINKED" && (
              <p className="text-[10px] text-slate-400 mt-1">Bắt buộc chọn Điểm trường loại "Trường liên kết" (UC-18 A2).</p>
            )}
          </div>
          <div>
            <label className={labelClass}>Điểm trường {form.classType === "LINKED" ? "liên kết" : ""} *</label>
            <Select
              value={form.siteId}
              onChange={(e) => setForm({ ...form, siteId: e.target.value, curriculumId: "" })}
              onBlur={() => markTouched("siteId")}
              className={invalid.siteId ? inputErrorClass : inputClass}
            >
              <option value="">-- Chọn điểm trường --</option>
              {eligibleSites.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.name} ({s.siteType === "PARTNER" ? "Liên kết" : "Trực thuộc"})
                </option>
              ))}
            </Select>
            {invalid.siteId && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Điểm trường.</p>}
          </div>

          <div className="col-span-2">
            <label className={labelClass}>Khung chương trình *</label>
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
            {invalid.curriculumId && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Khung chương trình.</p>}
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
            <label className={labelClass}>Sĩ số tối thiểu</label>
            <input type="number" min={0} value={form.minStudents} onChange={(e) => setForm({ ...form, minStudents: e.target.value })} className={inputClass} />
          </div>

          <div>
            <label className={labelClass}>Ngày khai giảng *</label>
            <DatePicker
              value={form.startDate}
              onChange={(v) => {
                setForm({ ...form, startDate: v });
                markTouched("startDate");
              }}
              max={form.endDate || undefined}
              hasError={invalid.startDate}
            />
            {invalid.startDate && <p className="text-[10px] text-rose-600 mt-1">Vui lòng chọn Ngày khai giảng.</p>}
          </div>
          <div>
            <label className={labelClass}>Ngày kết thúc (dự kiến)</label>
            <DatePicker value={form.endDate} onChange={(v) => setForm({ ...form, endDate: v })} min={form.startDate || undefined} />
          </div>

          <div>
            <label className={labelClass}>Năm học</label>
            <input value={form.academicYear} onChange={(e) => setForm({ ...form, academicYear: e.target.value })} placeholder="VD: 2026-2027" className={inputClass} />
          </div>
          <div>
            <label className={labelClass}>Học kỳ</label>
            <input value={form.semester} onChange={(e) => setForm({ ...form, semester: e.target.value })} placeholder="VD: HK1" className={inputClass} />
          </div>
        </div>

        <div className="flex justify-end gap-2 pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            Hủy
          </Button>
          <Button type="submit" variant="primary" disabled={submitting}>
            <Plus className="w-3.5 h-3.5" />
            {submitting ? "Đang tạo..." : "Tạo lớp học"}
          </Button>
        </div>
      </form>
    </Modal>
  );
}
