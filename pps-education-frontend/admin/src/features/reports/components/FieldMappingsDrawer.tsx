import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
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

/** Nhãn dịch qua i18next namespace "reports-templates" — xem src/i18n/locales/{vi,en}/reports-templates.json. */
function fieldTypeLabel(t: (key: string) => string, fieldType: FieldType): string {
  return t(`fieldType.${fieldType}`);
}

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

/** Danh sách khoá gợi ý data path theo loại mẫu báo cáo — nhãn hiển thị dịch qua i18next
 *  namespace "reports-templates" (khoá `dataPathOptions.<templateType>.<key>`), xem hàm
 *  `fallbackDataPathOptions` bên dưới. */
const FALLBACK_DATA_PATH_KEYS: Record<string, string[]> = {
  DAILY_REPORT: [
    "CLASS_NAME",
    "CLASS_DATE",
    "TEACHER_NAME",
    "LESSON_TOPIC",
    "TOTAL_STUDENTS",
    "ABSENT_COUNT",
    "GENERATED_DATE",
    "[[TABLE:STUDENTS]]",
  ],
  STUDENT_PROFILE: [
    "STUDENT_NAME",
    "STUDENT_CODE",
    "DATE_OF_BIRTH",
    "GENDER",
    "PRIMARY_SITE",
    "STATUS",
    "ENROLLMENT_DATE",
    "PRIMARY_PARENT_NAME",
    "PRIMARY_PARENT_PHONE",
    "PRIMARY_PARENT_EMAIL",
    "FINANCIAL_GUARDIAN_NAME",
    "GENERATED_DATE",
  ],
  STUDENT_COMMENT: [
    "STUDENT_NAME",
    "STUDENT_CODE",
    "STUDENT_COMMENT",
    "GENERATED_DATE",
  ],
  TRANSCRIPT: [
    "STUDENT_NAME",
    "STUDENT_CODE",
    "CLASS_NAME",
    "SCHOOL_NAME",
    "ACADEMIC_YEAR",
    "PRIMARY_TEACHER_NAME",
    "GENERATED_DATE",
    "LISTENING_MID1",
    "READING_MID1",
    "SPEAKING_MID1",
    "WRITING_MID1",
    "GRAMMAR_MID1",
    "OVERALL_MID1",
    "LEVEL_MID1",
    "COMMENT_MID1",
    "LISTENING_END1",
    "READING_END1",
    "SPEAKING_END1",
    "WRITING_END1",
    "GRAMMAR_END1",
    "OVERALL_END1",
    "LEVEL_END1",
    "COMMENT_END1",
  ],
  GRADE_REPORT: [
    "CLASS_NAME",
    "ACADEMIC_YEAR",
    "PRIMARY_TEACHER_NAME",
    "GENERATED_DATE",
    "LISTENING_MID1",
    "READING_MID1",
    "SPEAKING_MID1",
    "WRITING_MID1",
    "GRAMMAR_MID1",
    "OVERALL_MID1",
    "LEVEL_MID1",
    "COMMENT_MID1",
    "LISTENING_END1",
    "READING_END1",
    "SPEAKING_END1",
    "WRITING_END1",
    "GRAMMAR_END1",
    "OVERALL_END1",
    "LEVEL_END1",
    "COMMENT_END1",
  ],
};

/** Chuyển 1 giá trị placeholder (VD "[[TABLE:STUDENTS]]") thành khoá i18n hợp lệ (VD "TABLE_STUDENTS"). */
function dataPathI18nKey(value: string): string {
  return value.replace(/[^A-Za-z0-9_]/g, "_").replace(/^_+|_+$/g, "");
}

function fallbackDataPathOptions(
  t: (key: string) => string,
  templateType: string
): { value: string; label: string }[] {
  const keys = FALLBACK_DATA_PATH_KEYS[templateType] ?? [];
  return keys.map((value) => ({
    value,
    label: `${t(`dataPathOptions.${templateType}.${dataPathI18nKey(value)}`)} (${value})`,
  }));
}

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
  const { t } = useTranslation("reports-templates");
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
    : fallbackDataPathOptions(t, template.templateType);

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
      setError(err instanceof ApiError ? err.message : t("fieldMappingsDrawer.saveError"));
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
            <h2 className="text-lg font-bold text-slate-800">{t("fieldMappingsDrawer.title")}</h2>
            <p className="text-sm text-slate-500 mt-0.5">
              <span className="font-medium text-slate-700">{template.name}</span> {t("fieldMappingsDrawer.subtitleSuffix")}
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
            <p className="font-semibold text-blue-800 mb-1">{t("fieldMappingsDrawer.guideTitle")}</p>
            <p>• <strong>{t("fieldMappingsDrawer.guideLine1Bold")}</strong> {t("fieldMappingsDrawer.guideLine1Suffix")}</p>
            <p>• <strong>{t("fieldMappingsDrawer.guideLine2Bold")}</strong> {t("fieldMappingsDrawer.guideLine2Suffix")}</p>
            <p>• <strong>{t("fieldMappingsDrawer.guideLine3Bold")}</strong> <span className="font-semibold">{template.templateType}</span></p>
          </div>

          {!hasPlaceholders && (
            <div className="text-center py-8 text-slate-400">
              <Tag className="w-8 h-8 mx-auto mb-2 opacity-40" />
              <p className="text-sm">{t("fieldMappingsDrawer.noPlaceholders.line1")}</p>
              <p className="text-xs mt-1">{t("fieldMappingsDrawer.noPlaceholders.line2Prefix")} <code>[TEN_BIEN]</code></p>
            </div>
          )}

          {rows.map((row, index) => {
            const hasExactOption = suggestedList.some((s) => s.value === row.dataPath);
            const displayOptions = hasExactOption
              ? suggestedList
              : row.dataPath
              ? [{ value: row.dataPath, label: t("fieldMappingsDrawer.customOptionAutoDetected", { value: row.dataPath }) }, ...suggestedList]
              : suggestedList;

            return (
              <div key={row.placeholderKey} className="bg-slate-50 border border-slate-200 rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <code className="text-sm font-semibold text-slate-800 bg-white border border-slate-300 px-2 py-0.5 rounded">
                      {row.placeholderKey}
                    </code>
                    {row.isNew && (
                      <span className="text-xs text-emerald-600 bg-emerald-50 border border-emerald-200 px-1.5 py-0.5 rounded">{t("fieldMappingsDrawer.newBadge")}</span>
                    )}
                  </div>
                  <Select
                    value={row.fieldType}
                    onChange={(e) => updateRow(index, "fieldType", e.target.value)}
                    className={`text-xs font-semibold px-2 py-1 rounded border-0 cursor-pointer ${FIELD_TYPE_COLORS[row.fieldType]}`}
                  >
                    {(["FIELD", "FORMULA", "TABLE"] as FieldType[]).map((k) => (
                      <option key={k} value={k}>{fieldTypeLabel(t, k)}</option>
                    ))}
                  </Select>
                </div>

                <div>
                  <label className="block text-xs text-slate-500 mb-1">{t("fieldMappingsDrawer.dataPathLabel")}</label>
                  {row.fieldType === "FORMULA" ? (
                    <input
                      type="text"
                      disabled
                      value=""
                      placeholder={t("fieldMappingsDrawer.formulaPlaceholder")}
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
                        <option value="">{t("fieldMappingsDrawer.dataPathSelectPlaceholder")}</option>
                        {displayOptions.map((s) => (
                          <option key={s.value} value={s.value}>{s.label}</option>
                        ))}
                        <option value="CUSTOM">{t("fieldMappingsDrawer.customOption")}</option>
                      </Select>

                      {(!hasExactOption || row.dataPath === "") && (
                        <input
                          type="text"
                          value={row.dataPath}
                          onChange={(e) => updateRow(index, "dataPath", e.target.value)}
                          placeholder={t("fieldMappingsDrawer.customInputPlaceholder")}
                          className="w-full border border-slate-300 rounded-md text-xs p-2 focus:outline-none focus:ring-1 focus:ring-brand-orange bg-white"
                        />
                      )}
                    </div>
                  )}
                </div>

                <div>
                  <label className="block text-xs text-slate-500 mb-1">{t("fieldMappingsDrawer.notesLabel")}</label>
                  <input
                    type="text"
                    value={row.description}
                    onChange={(e) => updateRow(index, "description", e.target.value)}
                    placeholder={t("fieldMappingsDrawer.notesPlaceholder")}
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
            {t("fieldMappingsDrawer.placeholderCount", { count: rows.length })}
          </p>
          <div className="flex gap-3">
            <Button variant="secondary" onClick={onClose} disabled={loading}>{t("fieldMappingsDrawer.cancel")}</Button>
            <Button onClick={handleSave} disabled={loading || rows.length === 0}>
              {loading ? t("fieldMappingsDrawer.saving") : t("fieldMappingsDrawer.saveButton")}
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
