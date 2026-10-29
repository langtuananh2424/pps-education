import React, { useRef, useState } from "react";
import { Download, Upload } from "lucide-react";
import { useTranslation } from "react-i18next";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import { ApiError } from "@/lib/apiClient";
import { uploadMedia } from "../api";

/**
 * Giới hạn phía client, khớp/chặt hơn backend (MediaStorageService: ảnh ≤10MB, audio ≤50MB) và nginx
 * (zone pps_upload 30 req/phút/IP, burst 20): chặn sớm thay vì để server từ chối từng file, và không
 * bắn dồn dập gây 429 hàng loạt. Đây chỉ là lớp tiện dụng — phòng thủ thật vẫn ở backend + nginx.
 */
const CONCURRENCY = 2;
const MAX_FILES_PER_BATCH = 100;
const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const MAX_AUDIO_BYTES = 50 * 1024 * 1024;
const RATE_LIMIT_RETRIES = 6;
const RATE_LIMIT_BACKOFF_MS = 4000;

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

/** Gặp 429 (nginx rate limit) thì chờ rồi thử lại, thay vì báo lỗi ngay. */
async function uploadWithBackoff(file: File): Promise<{ url: string }> {
  for (let attempt = 0; ; attempt++) {
    try {
      return await uploadMedia(file, "LMS_QUESTION");
    } catch (err) {
      if (!(err instanceof ApiError) || err.status !== 429 || attempt >= RATE_LIMIT_RETRIES) throw err;
      await sleep(RATE_LIMIT_BACKOFF_MS * (attempt + 1));
    }
  }
}

interface UploadRow {
  fileName: string;
  /** Đường dẫn tương đối (vd lms/questions/images/<uuid>.png) — dán thẳng vào cột Excel, host do môi trường tự ghép. */
  relativePath: string | null;
  error: string | null;
}

/** Bỏ host khỏi URL tuyệt đối để file Excel không dính cứng vào 1 domain/bucket (xem ImportMediaUrlResolver phía backend). */
function toRelativePath(url: string): string {
  try {
    return new URL(url).pathname.replace(/^\/+/, "");
  } catch {
    return url;
  }
}

function csvCell(value: string): string {
  return /[",\r\n]/.test(value) ? `"${value.replace(/"/g, '""')}"` : value;
}

/** Popup upload hàng loạt ảnh/âm thanh câu hỏi, trả bảng "tên file gốc → đường dẫn tương đối" để tải CSV dán vào Excel import. */
export default function BulkMediaUploadModal({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation("lms-question-authoring");
  const inputRef = useRef<HTMLInputElement>(null);
  const [rows, setRows] = useState<UploadRow[]>([]);
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState({ done: 0, total: 0 });

  async function handleFiles(fileList: FileList | null) {
    const picked = Array.from(fileList ?? []);
    if (picked.length === 0) return;
    const files = picked.slice(0, MAX_FILES_PER_BATCH);
    const skipped: UploadRow[] = picked.slice(MAX_FILES_PER_BATCH).map((f) => ({
      fileName: f.name,
      relativePath: null,
      error: t("bulkUpload.tooManyFiles", { max: MAX_FILES_PER_BATCH }),
    }));
    setUploading(true);
    setProgress({ done: 0, total: files.length });
    const results: UploadRow[] = new Array(files.length);
    let next = 0;
    let done = 0;
    async function worker() {
      while (next < files.length) {
        const index = next++;
        const file = files[index];
        const isAudio = file.type.startsWith("audio/");
        const isImage = file.type.startsWith("image/");
        if (!isAudio && !isImage) {
          results[index] = { fileName: file.name, relativePath: null, error: t("bulkUpload.unsupportedType") };
          done++;
          setProgress({ done, total: files.length });
          continue;
        }
        if (file.size > (isAudio ? MAX_AUDIO_BYTES : MAX_IMAGE_BYTES)) {
          results[index] = {
            fileName: file.name,
            relativePath: null,
            error: t("bulkUpload.tooLarge", { maxMb: (isAudio ? MAX_AUDIO_BYTES : MAX_IMAGE_BYTES) / 1024 / 1024 }),
          };
          done++;
          setProgress({ done, total: files.length });
          continue;
        }
        try {
          const { url } = await uploadWithBackoff(file);
          results[index] = { fileName: file.name, relativePath: toRelativePath(url), error: null };
        } catch (err) {
          results[index] = {
            fileName: file.name,
            relativePath: null,
            error: err instanceof ApiError ? err.message : t("bulkUpload.failed"),
          };
        }
        done++;
        setProgress({ done, total: files.length });
      }
    }
    await Promise.all(Array.from({ length: Math.min(CONCURRENCY, files.length) }, worker));
    setRows((prev) => [...prev, ...results, ...skipped]);
    setUploading(false);
    if (inputRef.current) inputRef.current.value = "";
  }

  const succeeded = rows.filter((r) => r.relativePath);

  function downloadCsv() {
    const lines = [`${t("bulkUpload.csvHeaderName")},${t("bulkUpload.csvHeaderPath")}`];
    for (const r of succeeded) lines.push(`${csvCell(r.fileName)},${csvCell(r.relativePath!)}`);
    // BOM để Excel đọc đúng UTF-8 (tên file tiếng Việt).
    const blob = new Blob(["﻿" + lines.join("\r\n")], { type: "text/csv;charset=utf-8" });
    const href = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = href;
    a.download = "upload-urls.csv";
    a.click();
    URL.revokeObjectURL(href);
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={t("bulkUpload.title")}
      description={t("bulkUpload.description")}
      size="lg"
      footer={
        <>
          <Button variant="secondary" size="sm" onClick={onClose}>
            {t("bulkUpload.close")}
          </Button>
          <Button variant="primary" size="sm" onClick={downloadCsv} disabled={succeeded.length === 0}>
            <Download className="w-3.5 h-3.5" />
            {t("bulkUpload.downloadCsv")}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <input
          ref={inputRef}
          type="file"
          multiple
          accept="image/*,audio/*"
          className="hidden"
          onChange={(e) => handleFiles(e.target.files)}
        />
        <Button variant="secondary" size="sm" disabled={uploading} onClick={() => inputRef.current?.click()}>
          <Upload className="w-3.5 h-3.5" />
          {uploading ? t("bulkUpload.uploading", progress) : t("bulkUpload.chooseFiles")}
        </Button>

        {rows.length > 0 && (
          <div className="border border-slate-200 rounded-lg overflow-hidden">
            <div className="max-h-80 overflow-y-auto">
              <table className="w-full text-[13px]">
                <thead className="bg-slate-50 text-slate-500 sticky top-0">
                  <tr>
                    <th className="text-left font-bold px-3 py-2">{t("bulkUpload.csvHeaderName")}</th>
                    <th className="text-left font-bold px-3 py-2">{t("bulkUpload.csvHeaderPath")}</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((r, i) => (
                    <tr key={i} className="border-t border-slate-100">
                      <td className="px-3 py-2 break-all">{r.fileName}</td>
                      <td className={`px-3 py-2 break-all font-mono ${r.error ? "text-red-600" : ""}`}>
                        {r.relativePath ?? r.error}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="px-3 py-2 text-[12px] text-slate-500 border-t border-slate-100">
              {t("bulkUpload.summary", { ok: succeeded.length, total: rows.length })}
            </p>
          </div>
        )}
      </div>
    </Modal>
  );
}
