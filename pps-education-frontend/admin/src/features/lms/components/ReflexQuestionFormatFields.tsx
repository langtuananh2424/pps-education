import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Camera, ImagePlus, Sparkles } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Select from "@/components/ui/Select";
import Tabs from "@/components/ui/Tabs";
import {
  ReflexQuestionFormat,
  ReflexQuestionFormatOption,
  ReviewVideoSourceType,
  captureReflexPicture,
  draftReflexPictureBrief,
  getReflexQuestionFormats,
  uploadMedia
} from "../api";

const inputClass = "w-full bg-slate-50 border border-slate-200 text-xs p-2.5 rounded-lg focus:outline-none";
const labelClass = "text-[10px] uppercase font-bold text-slate-500 block mb-1";

/** V200 — phần dạng đề + dữ liệu tranh của 1 câu hỏi Video phản xạ (dùng chung form tạo bộ / thêm / sửa câu hỏi). */
export interface ReflexFormatValue {
  questionFormat: ReflexQuestionFormat | "";
  pictureImageUrl: string;
  pictureBrief: string;
}

export const EMPTY_REFLEX_FORMAT: ReflexFormatValue = { questionFormat: "", pictureImageUrl: "", pictureBrief: "" };

/** Trường gửi lên API: bỏ trống dạng đề = hệ thống suy theo thời lượng như câu hỏi cũ. */
export function toFormatRequest(v: ReflexFormatValue) {
  if (!v.questionFormat) return {};
  return {
    questionFormat: v.questionFormat,
    ...(v.questionFormat === "PICTURE"
      ? { pictureImageUrl: v.pictureImageUrl || undefined, pictureBrief: v.pictureBrief.trim() || undefined }
      : {})
  };
}

const optionsCache = new Map<number, Promise<ReflexQuestionFormatOption[]>>();

/** Danh sách dạng đề theo chương trình — nguồn chân lý ở backend (cùng bảng dùng để chấm), cache theo chương trình. */
export function useReflexFormatOptions(curriculumId: number | null) {
  const [options, setOptions] = useState<ReflexQuestionFormatOption[] | null>(null);
  useEffect(() => {
    if (!curriculumId) {
      setOptions([]);
      return;
    }
    let cancelled = false;
    let request = optionsCache.get(curriculumId);
    if (!request) {
      request = getReflexQuestionFormats(curriculumId).catch((err) => {
        optionsCache.delete(curriculumId);
        throw err;
      });
      optionsCache.set(curriculumId, request);
    }
    request.then((o) => !cancelled && setOptions(o)).catch(() => !cancelled && setOptions([]));
    return () => {
      cancelled = true;
    };
  }, [curriculumId]);
  return options;
}

/**
 * V200 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-29) — chọn dạng đề cho câu hỏi Video phản xạ. Chọn
 * dạng đề thì điền sẵn thời gian ghi âm mà rubric hiệu chuẩn theo ({@code onRecommendedSeconds}). Dạng tả tranh mở
 * popup soạn tranh: chụp khung hình tại mốc câu hỏi (hoặc tải ảnh) → AI viết nháp → giáo viên duyệt mô tả.
 */
export default function ReflexQuestionFormatFields({
  curriculumId,
  videoUrl,
  sourceType,
  timestampSeconds,
  value,
  onChange
}: {
  curriculumId: number | null;
  videoUrl?: string;
  sourceType?: ReviewVideoSourceType;
  timestampSeconds: string;
  value: ReflexFormatValue;
  /** @param recommendedSeconds chỉ có khi vừa đổi dạng đề: thời gian ghi âm rubric hiệu chuẩn theo — parent điền vào cùng lượt cập nhật. */
  onChange: (v: ReflexFormatValue, recommendedSeconds?: number) => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const options = useReflexFormatOptions(curriculumId);
  const [pictureOpen, setPictureOpen] = useState(false);

  if (options === null) {
    return <p className="text-[11px] text-slate-400">{t("lectures.reflexFormat.loading")}</p>;
  }
  if (options.length === 0) {
    return <p className="text-[11px] text-slate-400 italic">{t("lectures.reflexFormat.notSupported")}</p>;
  }

  const handleFormatChange = (format: ReflexQuestionFormat | "") => {
    const option = options.find((o) => o.format === format);
    onChange({ ...value, questionFormat: format }, option?.recommendedSeconds);
    if (format === "PICTURE" && !value.pictureBrief) setPictureOpen(true);
  };

  return (
    <div className="space-y-1.5">
      <div>
        <label className={labelClass}>{t("lectures.reflexFormat.label")}</label>
        <Select
          value={value.questionFormat}
          onChange={(e) => handleFormatChange(e.target.value as ReflexQuestionFormat | "")}
          className={inputClass}
        >
          <option value="">{t("lectures.reflexFormat.auto")}</option>
          {options.map((o) => (
            <option key={o.format} value={o.format}>
              {t(`lectures.reflexFormat.formats.${o.format}`)} — {o.label} ({t("lectures.reflexFormat.seconds", { count: o.recommendedSeconds })})
            </option>
          ))}
        </Select>
      </div>

      {value.questionFormat === "PICTURE" && (
        <div className="flex items-start gap-2 bg-sky-50 border border-sky-100 rounded-lg p-2">
          {value.pictureImageUrl && (
            <img src={value.pictureImageUrl} alt="" className="w-20 h-14 object-cover rounded border border-slate-200 shrink-0" />
          )}
          <div className="flex-1 min-w-0 text-[11px]">
            {value.pictureBrief ? (
              <p className="text-slate-700 whitespace-pre-line">{value.pictureBrief}</p>
            ) : (
              <p className="text-rose-600 font-semibold">{t("lectures.reflexFormat.briefMissing")}</p>
            )}
          </div>
          <Button type="button" variant="secondary" size="sm" onClick={() => setPictureOpen(true)}>
            <ImagePlus className="w-3.5 h-3.5" /> {t("lectures.reflexPicture.openButton")}
          </Button>
        </div>
      )}

      {pictureOpen && (
        <ReflexPictureModal
          videoUrl={videoUrl}
          sourceType={sourceType}
          timestampSeconds={timestampSeconds}
          initialImageUrl={value.pictureImageUrl}
          initialBrief={value.pictureBrief}
          onClose={() => setPictureOpen(false)}
          onSave={(imageUrl, brief) => {
            onChange({ ...value, questionFormat: "PICTURE", pictureImageUrl: imageUrl, pictureBrief: brief });
            setPictureOpen(false);
          }}
        />
      )}
    </div>
  );
}

/**
 * Popup soạn tranh 2 tab. Tab "Ảnh": chụp khung hình trong video tại mốc (chỉnh được mốc chụp mà KHÔNG đổi mốc hiện câu
 * hỏi — phòng khi tranh hiện lệch mốc) hoặc tải ảnh lên (bắt buộc với video YouTube). Tab "Mô tả": AI viết nháp 2–3
 * dòng, giáo viên đối chiếu với ảnh và sửa rồi lưu. Chỉ mô tả CHỮ được dùng khi chấm; ảnh không gửi vào AI chấm.
 */
function ReflexPictureModal({
  videoUrl,
  sourceType,
  timestampSeconds,
  initialImageUrl,
  initialBrief,
  onClose,
  onSave
}: {
  videoUrl?: string;
  sourceType?: ReviewVideoSourceType;
  timestampSeconds: string;
  initialImageUrl: string;
  initialBrief: string;
  onClose: () => void;
  onSave: (imageUrl: string, brief: string) => void;
}) {
  const { t } = useTranslation("lms-review-video");
  const [tab, setTab] = useState<"image" | "brief">(initialImageUrl ? "brief" : "image");
  const [captureAt, setCaptureAt] = useState(timestampSeconds || "0");
  const [imageUrl, setImageUrl] = useState(initialImageUrl);
  const [brief, setBrief] = useState(initialBrief);
  const [busy, setBusy] = useState<"capture" | "upload" | "draft" | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const canCapture = sourceType === "R2_VIDEO" && !!videoUrl;

  const requestDraft = async (url: string) => {
    setBusy("draft");
    setError(null);
    setNotice(null);
    try {
      const draft = await draftReflexPictureBrief(url);
      if (!draft.aiAvailable) setNotice(t("lectures.reflexPicture.aiUnavailable"));
      else if (!draft.pictureFound) setNotice(t("lectures.reflexPicture.noPictureFound"));
      else setBrief(draft.brief);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t("lectures.reflexPicture.draftFailed"));
    } finally {
      setBusy(null);
    }
  };

  /** Có ảnh mới → sang tab Mô tả; mô tả đang trống thì nhờ AI viết nháp luôn. */
  const applyImage = (url: string) => {
    setImageUrl(url);
    setTab("brief");
    if (!brief.trim()) void requestDraft(url);
  };

  const handleCapture = async () => {
    if (!videoUrl) return;
    setBusy("capture");
    setError(null);
    try {
      const { imageUrl: url } = await captureReflexPicture(videoUrl, Math.max(0, Number(captureAt) || 0));
      setBusy(null);
      applyImage(url);
    } catch (err) {
      setBusy(null);
      setError(err instanceof ApiError ? err.message : t("lectures.reflexPicture.captureFailed"));
    }
  };

  const handleUpload = async (file: File | undefined) => {
    if (!file) return;
    setBusy("upload");
    setError(null);
    try {
      const { url } = await uploadMedia(file, "REVIEW_VIDEO");
      setBusy(null);
      applyImage(url);
    } catch (err) {
      setBusy(null);
      setError(err instanceof ApiError ? err.message : t("lectures.reflexPicture.uploadFailed"));
    }
  };

  const handleSave = () => {
    if (!brief.trim()) {
      setError(t("lectures.reflexFormat.briefMissing"));
      return;
    }
    onSave(imageUrl, brief.trim());
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={t("lectures.reflexPicture.title")}
      description={t("lectures.reflexPicture.description")}
      size="lg"
      footer={
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={onClose}>
            {t("lectures.common.cancel")}
          </Button>
          <Button type="button" variant="primary" size="sm" onClick={handleSave} disabled={busy !== null}>
            {t("lectures.reflexPicture.saveButton")}
          </Button>
        </div>
      }
    >
      <div className="space-y-3">
        <Tabs
          items={[
            { id: "image", label: t("lectures.reflexPicture.imageTab") },
            { id: "brief", label: t("lectures.reflexPicture.briefTab") }
          ]}
          activeId={tab}
          onChange={(id) => setTab(id as "image" | "brief")}
        />
        {error && <div className="text-[11px] text-rose-600 bg-rose-50 border border-rose-100 p-2 rounded-lg">{error}</div>}
        {notice && <div className="text-[11px] text-amber-700 bg-amber-50 border border-amber-100 p-2 rounded-lg">{notice}</div>}

        {tab === "image" ? (
          <div className="space-y-3">
            {canCapture ? (
              <div className="flex items-end gap-2">
                <div className="w-40">
                  <label className={labelClass}>{t("lectures.reflexPicture.captureAtLabel")}</label>
                  <input type="number" min={0} value={captureAt} onChange={(e) => setCaptureAt(e.target.value)} className={inputClass} />
                </div>
                <Button type="button" variant="primary" size="sm" onClick={handleCapture} disabled={busy !== null}>
                  <Camera className="w-3.5 h-3.5" />
                  {busy === "capture" ? t("lectures.reflexPicture.capturing") : t("lectures.reflexPicture.captureButton")}
                </Button>
              </div>
            ) : (
              <p className="text-[11px] text-slate-500 bg-slate-50 border border-slate-200 p-2 rounded-lg">
                {t("lectures.reflexPicture.captureUnavailable")}
              </p>
            )}
            <p className="text-[11px] text-slate-500">{t("lectures.reflexPicture.captureHint")}</p>
            <div>
              <label className={labelClass}>{t("lectures.reflexPicture.uploadLabel")}</label>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={busy !== null}
                onChange={(e) => handleUpload(e.target.files?.[0])}
                className="text-xs"
              />
              {busy === "upload" && <span className="text-[11px] text-slate-500 ml-2">{t("lectures.reflexPicture.uploading")}</span>}
            </div>
            {imageUrl && <img src={imageUrl} alt="" className="max-h-72 rounded-lg border border-slate-200" />}
          </div>
        ) : (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              {imageUrl ? (
                <img src={imageUrl} alt="" className="w-full rounded-lg border border-slate-200" />
              ) : (
                <p className="text-[11px] text-slate-400 italic">{t("lectures.reflexPicture.noImage")}</p>
              )}
            </div>
            <div className="space-y-2">
              <label className={labelClass}>{t("lectures.reflexPicture.briefLabel")}</label>
              <textarea
                value={brief}
                onChange={(e) => setBrief(e.target.value)}
                rows={5}
                placeholder={t("lectures.reflexPicture.briefPlaceholder")}
                className={inputClass}
              />
              <p className="text-[11px] text-amber-700">{t("lectures.reflexPicture.reviewWarning")}</p>
              <Button type="button" variant="secondary" size="sm" disabled={!imageUrl || busy !== null} onClick={() => imageUrl && requestDraft(imageUrl)}>
                <Sparkles className="w-3.5 h-3.5" />
                {busy === "draft" ? t("lectures.reflexPicture.drafting") : t("lectures.reflexPicture.draftButton")}
              </Button>
            </div>
          </div>
        )}
      </div>
    </Modal>
  );
}
