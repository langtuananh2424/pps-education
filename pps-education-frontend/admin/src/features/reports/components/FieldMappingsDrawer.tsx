import React, { useEffect, useState } from "react";
import Button from "@/components/ui/Button";
import Select from "@/components/ui/Select";
import { X, Tag } from "lucide-react";
import {
  ReportTemplateResponse,
  ReportTemplateFieldMappingResponse,
  FieldMappingItemRequest,
  AvailableReportFieldResponse,
  updateFieldMappings,
  getAvailableReportFields,
} from "@/features/academic/api";
import { ApiError } from "@/lib/apiClient";

interface Props {
  template: ReportTemplateResponse;
  onClose: () => void;
  onSuccess: () => void;
}

type FieldType = "FIELD" | "FORMULA" | "TABLE";

const FIELD_TYPE_LABELS: Record<FieldType, string> = {
  FIELD: "Trường dữ liệu",
  FORMULA: "Công thức",
  TABLE: "Bảng động",
};

const FIELD_TYPE_COLORS: Record<FieldType, string> = {
  FIELD: "bg-blue-100 text-blue-700",
  FORMULA: "bg-amber-100 text-amber-700",
  TABLE: "bg-purple-100 text-purple-700",
};

interface MappingRow {
  placeholderKey: string;
  dataPath: string;
  fieldType: FieldType;
  description: string;
  isNew: boolean;
}

const FALLBACK_DATA_PATHS: Record<string, { value: string; label: string }[]> = {
  DAILY_REPORT: [
    { value: "CLASS_NAME", label: "Tên lớp học (CLASS_NAME)" },
    { value: "CLASS_DATE", label: "Ngày học (CLASS_DATE)" },
    { value: "TEACHER_NAME", label: "Tên giáo viên (TEACHER_NAME)" },
    { value: "LESSON_TOPIC", label: "Nội dung bài học (LESSON_TOPIC)" },
    { value: "TOTAL_STUDENTS", label: "Tổng số học sinh (TOTAL_STUDENTS)" },
    { value: "ABSENT_COUNT", label: "Số HS vắng mặt (ABSENT_COUNT)" },
    { value: "GENERATED_DATE", label: "Ngày xuất báo cáo (GENERATED_DATE)" },
    { value: "[[TABLE:STUDENTS]]", label: "Bảng học sinh ([[TABLE:STUDENTS]])" },
  ],
  STUDENT_PROFILE: [
    { value: "STUDENT_NAME", label: "Họ và tên học sinh (STUDENT_NAME)" },
    { value: "STUDENT_CODE", label: "Mã học sinh (STUDENT_CODE)" },
    { value: "DATE_OF_BIRTH", label: "Ngày sinh (DATE_OF_BIRTH)" },
    { value: "GENDER", label: "Giới tính (GENDER)" },
    { value: "PRIMARY_SITE", label: "Cơ sở / Điểm trường (PRIMARY_SITE)" },
    { value: "STATUS", label: "Trạng thái học sinh (STATUS)" },
    { value: "ENROLLMENT_DATE", label: "Ngày nhập học (ENROLLMENT_DATE)" },
    { value: "PRIMARY_PARENT_NAME", label: "Họ tên phụ huynh chính (PRIMARY_PARENT_NAME)" },
    { value: "PRIMARY_PARENT_PHONE", label: "SĐT phụ huynh (PRIMARY_PARENT_PHONE)" },
    { value: "PRIMARY_PARENT_EMAIL", label: "Email phụ huynh (PRIMARY_PARENT_EMAIL)" },
    { value: "FINANCIAL_GUARDIAN_NAME", label: "Người bảo trợ tài chính (FINANCIAL_GUARDIAN_NAME)" },
    { value: "GENERATED_DATE", label: "Ngày xuất báo cáo (GENERATED_DATE)" },
  ],
  STUDENT_COMMENT: [
    { value: "STUDENT_NAME", label: "Họ và tên học sinh (STUDENT_NAME)" },
    { value: "STUDENT_CODE", label: "Mã học sinh (STUDENT_CODE)" },
    { value: "STUDENT_COMMENT", label: "Nội dung nhận xét (STUDENT_COMMENT)" },
    { value: "GENERATED_DATE", label: "Ngày xuất báo cáo (GENERATED_DATE)" },
  ],
  TRANSCRIPT: [
    { value: "STUDENT_NAME", label: "Họ tên học sinh (STUDENT_NAME)" },
    { value: "STUDENT_CODE", label: "Mã học sinh (STUDENT_CODE)" },
    { value: "CLASS_NAME", label: "Tên lớp (CLASS_NAME)" },
    { value: "SCHOOL_NAME", label: "Tên trường / Cơ sở (SCHOOL_NAME)" },
    { value: "ACADEMIC_YEAR", label: "Năm học (ACADEMIC_YEAR)" },
    { value: "PRIMARY_TEACHER_NAME", label: "Giáo viên chính (PRIMARY_TEACHER_NAME)" },
    { value: "GENERATED_DATE", label: "Ngày xuất báo cáo (GENERATED_DATE)" },
    { value: "LISTENING_MID1", label: "Điểm Nghe - Giữa kỳ 1 (LISTENING_MID1)" },
    { value: "READING_MID1", label: "Điểm Đọc - Giữa kỳ 1 (READING_MID1)" },
    { value: "SPEAKING_MID1", label: "Điểm Nói - Giữa kỳ 1 (SPEAKING_MID1)" },
    { value: "WRITING_MID1", label: "Điểm Viết - Giữa kỳ 1 (WRITING_MID1)" },
    { value: "GRAMMAR_MID1", label: "Điểm Ngữ pháp - Giữa kỳ 1 (GRAMMAR_MID1)" },
    { value: "OVERALL_MID1", label: "Điểm Tổng kết - Giữa kỳ 1 (OVERALL_MID1)" },
    { value: "LEVEL_MID1", label: "Xếp loại - Giữa kỳ 1 (LEVEL_MID1)" },
    { value: "COMMENT_MID1", label: "Nhận xét - Giữa kỳ 1 (COMMENT_MID1)" },

    { value: "LISTENING_END1", label: "Điểm Nghe - Cuối kỳ 1 (LISTENING_END1)" },
    { value: "READING_END1", label: "Điểm Đọc - Cuối kỳ 1 (READING_END1)" },
    { value: "SPEAKING_END1", label: "Điểm Nói - Cuối kỳ 1 (SPEAKING_END1)" },
    { value: "WRITING_END1", label: "Điểm Viết - Cuối kỳ 1 (WRITING_END1)" },
    { value: "GRAMMAR_END1", label: "Điểm Ngữ pháp - Cuối kỳ 1 (GRAMMAR_END1)" },
    { value: "OVERALL_END1", label: "Điểm Tổng kết - Cuối kỳ 1 (OVERALL_END1)" },
    { value: "LEVEL_END1", label: "Xếp loại - Cuối kỳ 1 (LEVEL_END1)" },
    { value: "COMMENT_END1", label: "Nhận xét - Cuối kỳ 1 (COMMENT_END1)" },
  ],
  GRADE_REPORT: [
    { value: "CLASS_NAME", label: "Tên lớp học (CLASS_NAME)" },
    { value: "ACADEMIC_YEAR", label: "Năm học (ACADEMIC_YEAR)" },
    { value: "PRIMARY_TEACHER_NAME", label: "Giáo viên chính (PRIMARY_TEACHER_NAME)" },
    { value: "GENERATED_DATE", label: "Ngày xuất báo cáo (GENERATED_DATE)" },

    { value: "LISTENING_MID1", label: "Điểm Nghe - Giữa kỳ 1 (LISTENING_MID1)" },
    { value: "READING_MID1", label: "Điểm Đọc - Giữa kỳ 1 (READING_MID1)" },
    { value: "SPEAKING_MID1", label: "Điểm Nói - Giữa kỳ 1 (SPEAKING_MID1)" },
    { value: "WRITING_MID1", label: "Điểm Viết - Giữa kỳ 1 (WRITING_MID1)" },
    { value: "GRAMMAR_MID1", label: "Điểm Ngữ pháp - Giữa kỳ 1 (GRAMMAR_MID1)" },
    { value: "OVERALL_MID1", label: "Điểm Tổng kết - Giữa kỳ 1 (OVERALL_MID1)" },
    { value: "LEVEL_MID1", label: "Xếp loại - Giữa kỳ 1 (LEVEL_MID1)" },
    { value: "COMMENT_MID1", label: "Nhận xét - Giữa kỳ 1 (COMMENT_MID1)" },

    { value: "LISTENING_END1", label: "Điểm Nghe - Cuối kỳ 1 (LISTENING_END1)" },
    { value: "READING_END1", label: "Điểm Đọc - Cuối kỳ 1 (READING_END1)" },
    { value: "SPEAKING_END1", label: "Điểm Nói - Cuối kỳ 1 (SPEAKING_END1)" },
    { value: "WRITING_END1", label: "Điểm Viết - Cuối kỳ 1 (WRITING_END1)" },
    { value: "GRAMMAR_END1", label: "Điểm Ngữ pháp - Cuối kỳ 1 (GRAMMAR_END1)" },
    { value: "OVERALL_END1", label: "Điểm Tổng kết - Cuối kỳ 1 (OVERALL_END1)" },
    { value: "LEVEL_END1", label: "Xếp loại - Cuối kỳ 1 (LEVEL_END1)" },
    { value: "COMMENT_END1", label: "Nhận xét - Cuối kỳ 1 (COMMENT_END1)" },
  ],
};

const ALIAS_MAP: Record<string, string> = {
  // Nói / Speaking
  NOI_GIUA_KI_1: "SPEAKING_MID1",
  NOI_GK1: "SPEAKING_MID1",
  SPEAKING_GK1: "SPEAKING_MID1",
  NOI_CUOI_KI_1: "SPEAKING_END1",
  NOI_CK1: "SPEAKING_END1",
  SPEAKING_CK1: "SPEAKING_END1",

  // Đọc / Reading
  DOC_GIUA_KI_1: "READING_MID1",
  DOC_GK1: "READING_MID1",
  READING_GK1: "READING_MID1",
  DOC_CUOI_KI_1: "READING_END1",
  DOC_CK1: "READING_END1",
  READING_CK1: "READING_END1",

  // Nghe / Listening
  NGHE_GIUA_KI_1: "LISTENING_MID1",
  NGHE_GK1: "LISTENING_MID1",
  LISTENING_GK1: "LISTENING_MID1",
  NGHE_CUOI_KI_1: "LISTENING_END1",
  NGHE_CK1: "LISTENING_END1",
  LISTENING_CK1: "LISTENING_END1",

  // Viết / Writing
  VIET_GIUA_KI_1: "WRITING_MID1",
  VIET_GK1: "WRITING_MID1",
  WRITING_GK1: "WRITING_MID1",
  VIET_CUOI_KI_1: "WRITING_END1",
  VIET_CK1: "WRITING_END1",
  WRITING_CK1: "WRITING_END1",

  // Ngữ pháp / Grammar
  NGU_PHAP_GIUA_KI_1: "GRAMMAR_MID1",
  NGUPHAP_GIUA_KI_1: "GRAMMAR_MID1",
  NGU_PHAP_GK1: "GRAMMAR_MID1",
  GRAMMAR_GK1: "GRAMMAR_MID1",
  NGU_PHAP_CUOI_KI_1: "GRAMMAR_END1",
  NGU_PHAP_CK1: "GRAMMAR_END1",
  GRAMMAR_CK1: "GRAMMAR_END1",

  // Tổng kết & Nhận xét
  TONG_KET_GIUA_KI_1: "OVERALL_MID1",
  OVERALL_GK1: "OVERALL_MID1",
  TONG_KET_CUOI_KI_1: "OVERALL_END1",
  OVERALL_CK1: "OVERALL_END1",
  NHAN_XET_GIUA_KI_1: "COMMENT_MID1",
  NHAN_XET_GK1: "COMMENT_MID1",
  NHAN_XET_CUOI_KI_1: "COMMENT_END1",
  NHAN_XET_CK1: "COMMENT_END1",
};

export default function FieldMappingsDrawer({ template, onClose, onSuccess }: Props) {
  const [rows, setRows] = useState<MappingRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [apiFieldsMap, setApiFieldsMap] = useState<Record<string, AvailableReportFieldResponse[]>>({});

  useEffect(() => {
    getAvailableReportFields()
      .then(setApiFieldsMap)
      .catch(() => undefined);
  }, []);

  const backendFields = apiFieldsMap[template.templateType];
  const suggestedList: { value: string; label: string }[] = backendFields
    ? backendFields.map((f) => ({ value: f.key, label: `${f.label} (${f.key})` }))
    : FALLBACK_DATA_PATHS[template.templateType] ?? [];

  useEffect(() => {
    // Khởi tạo rows từ placeholderKeys + fieldMappings đã có
    const existingMap = new Map<string, ReportTemplateFieldMappingResponse>(
      (template.fieldMappings || []).map((m) => [m.placeholderKey, m])
    );

    const allKeys = template.placeholderKeys || [];
    const initialRows: MappingRow[] = allKeys.map((key) => {
      const existing = existingMap.get(key);
      const cleanKey = key.replace(/[\[\]]/g, "").trim();
      const aliasMatch = ALIAS_MAP[cleanKey.toUpperCase()];
      const matched = suggestedList.find((s) => s.value === cleanKey || s.value === key || s.value === aliasMatch);
      const defaultPath = existing?.dataPath ?? (aliasMatch ? aliasMatch : matched ? matched.value : cleanKey);

      return {
        placeholderKey: key,
        dataPath: defaultPath,
        fieldType: (existing?.fieldType as FieldType) ?? (key.startsWith("[[TABLE:") ? "TABLE" : "FIELD"),
        description: existing?.description ?? "",
        isNew: !existing,
      };
    });

    // Nếu không có placeholder keys nào (ví dụ file PDF cũ), hiển thị các mapping hiện tại
    if (initialRows.length === 0 && (template.fieldMappings || []).length > 0) {
      (template.fieldMappings || []).forEach((m) =>
        initialRows.push({
          placeholderKey: m.placeholderKey,
          dataPath: m.dataPath ?? "",
          fieldType: m.fieldType as FieldType,
          description: m.description ?? "",
          isNew: false,
        })
      );
    }

    setRows(initialRows);
  }, [template]);

  const updateRow = (index: number, field: keyof MappingRow, value: string) => {
    setRows((prev) => prev.map((r, i) => i === index ? { ...r, [field]: value } : r));
  };

  const handleSave = async () => {
    setLoading(true);
    setError(null);
    try {
      const mappings: FieldMappingItemRequest[] = rows
        .filter((r) => r.dataPath.trim() || r.fieldType === "FORMULA")
        .map((r) => ({
          placeholderKey: r.placeholderKey,
          dataPath: r.dataPath.trim() || null,
          fieldType: r.fieldType,
          description: r.description.trim() || undefined,
        }));
      await updateFieldMappings(template.id, { mappings });
      onSuccess();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Lưu cấu hình thất bại.");
    } finally {
      setLoading(false);
    }
  };

  const hasPlaceholders = rows.length > 0;

  return (
    <div className="fixed inset-0 z-50 flex justify-end">
      <div className="absolute inset-0 bg-slate-900/40" onClick={onClose} />
      <div className="relative w-[640px] bg-white h-full shadow-2xl flex flex-col">
        {/* Header */}
        <div className="p-6 border-b border-slate-200 flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-bold text-slate-800">Cấu hình Mapping</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              <span className="font-medium text-slate-700">{template.name}</span> — Ánh xạ placeholder → dữ liệu thực tế
            </p>
          </div>
          <button className="text-slate-400 hover:text-slate-600 p-1" onClick={onClose}>
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Body */}
        <div className="p-6 flex-1 overflow-y-auto space-y-4">
          {error && (
            <div className="text-sm text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-lg">{error}</div>
          )}

          {/* Hướng dẫn */}
          <div className="bg-blue-50 border border-blue-100 rounded-lg p-3 text-xs text-blue-700 space-y-1">
            <p className="font-semibold text-blue-800 mb-1">Hướng dẫn điền Data Path:</p>
            <p>• Chọn từ <strong>Dropdown danh sách trường hỗ trợ sẵn</strong> bên dưới hoặc tự điền giá trị tùy chỉnh.</p>
            <p>• <strong>Công thức:</strong> Hệ thống tự tính, không cần điền data path.</p>
            <p>• <strong>Loại báo cáo:</strong> <span className="font-semibold">{template.templateType}</span></p>
          </div>

          {!hasPlaceholders && (
            <div className="text-center py-8 text-slate-400">
              <Tag className="w-8 h-8 mx-auto mb-2 opacity-40" />
              <p className="text-sm">Không tìm thấy placeholder nào trong file này.</p>
              <p className="text-xs mt-1">Hãy chắc chắn file dùng cú pháp <code>[TEN_BIEN]</code></p>
            </div>
          )}

          {rows.map((row, index) => {
            const hasExactOption = suggestedList.some((s) => s.value === row.dataPath);
            const displayOptions = hasExactOption
              ? suggestedList
              : row.dataPath
              ? [{ value: row.dataPath, label: `${row.dataPath} (Tự động nhận diện)` }, ...suggestedList]
              : suggestedList;

            return (
              <div key={row.placeholderKey} className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <code className="text-sm font-semibold text-slate-800 bg-white border border-slate-300 px-2 py-0.5 rounded">
                      {row.placeholderKey}
                    </code>
                    {row.isNew && (
                      <span className="text-xs text-emerald-600 bg-emerald-50 border border-emerald-200 px-1.5 py-0.5 rounded">Mới</span>
                    )}
                  </div>
                  <Select
                    value={row.fieldType}
                    onChange={(e) => updateRow(index, "fieldType", e.target.value)}
                    className={`text-xs font-semibold px-2 py-1 rounded border-0 cursor-pointer ${FIELD_TYPE_COLORS[row.fieldType]}`}
                  >
                    {(Object.entries(FIELD_TYPE_LABELS) as [FieldType, string][]).map(([k, v]) => (
                      <option key={k} value={k}>{v}</option>
                    ))}
                  </Select>
                </div>

                <div>
                  <label className="block text-xs text-slate-500 mb-1">Data Path (nguồn dữ liệu)</label>
                  {row.fieldType === "FORMULA" ? (
                    <input
                      type="text"
                      disabled
                      value=""
                      placeholder="Công thức tự động — không cần điền"
                      className="w-full border border-slate-300 rounded-md text-xs p-2 bg-slate-100 text-slate-400"
                    />
                  ) : (
                    <div className="space-y-1.5">
                      <Select
                        value={row.dataPath}
                        onChange={(e) => {
                          if (e.target.value !== "CUSTOM") {
                            updateRow(index, "dataPath", e.target.value);
                          } else {
                            updateRow(index, "dataPath", "");
                          }
                        }}
                        className="w-full border border-slate-300 rounded-md text-xs p-2 bg-white focus:outline-none focus:ring-1 focus:ring-brand-orange"
                      >
                        <option value="">-- Chọn trường dữ liệu --</option>
                        {displayOptions.map((s) => (
                          <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                        <option value="CUSTOM">✏️ Tự nhập giá trị tùy chỉnh...</option>
                      </Select>

                      {(!hasExactOption || row.dataPath === "") && (
                        <input
                          type="text"
                          value={row.dataPath}
                          onChange={(e) => updateRow(index, "dataPath", e.target.value)}
                          placeholder="Ví dụ: CLASS_NAME hoặc READING_MID1"
                          className="w-full border border-slate-300 rounded-md text-xs p-2 focus:outline-none focus:ring-1 focus:ring-brand-orange bg-white"
                        />
                      )}
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-xs text-slate-500 mb-1">Ghi chú (tuỳ chọn)</label>
                  <input
                    type="text"
                    value={row.description}
                    onChange={(e) => updateRow(index, "description", e.target.value)}
                    placeholder="Mô tả ý nghĩa của biến này..."
                    className="w-full border border-slate-300 rounded-md text-xs p-2 focus:outline-none focus:ring-1 focus:ring-brand-orange"
                  />
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div className="p-6 border-t border-slate-200 flex items-center justify-between gap-3">
          <p className="text-xs text-slate-500">
            {rows.length} placeholder{rows.length !== 1 ? "s" : ""} được phát hiện
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={onClose} disabled={loading}>Huỷ</Button>
            <Button onClick={handleSave} disabled={loading || rows.length === 0}>
              {loading ? "Đang lưu..." : "Lưu cấu hình"}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
