import React, { useRef, useState } from "react";
import { Loader2, Paperclip, RotateCcw } from "lucide-react";
import { useTranslation } from "react-i18next";

interface FileUploadFieldProps {
  /** URL hiện tại (nếu đã có file/đang sửa) — hiển thị read-only, không cho gõ tay. */
  value: string;
  onChange: (url: string) => void;
  /** Gọi API upload thật (module/content-type do trang gọi tự quy định) — trả về URL public sau khi lên R2 thành công. */
  onUpload: (file: File) => Promise<{ url: string }>;
  /** Cho biết kích thước file byte vừa chọn — dùng để tự điền field fileSizeBytes nếu form cần. */
  onFileSize?: (bytes: number) => void;
  accept?: string;
  placeholder?: string;
  disabled?: boolean;
}

/** Ô "Chọn file" dùng chung cho mọi màn upload tài liệu thật lên R2 (thay ô nhập URL tay cũ). */
export default function FileUploadField({ value, onChange, onUpload, onFileSize, accept, placeholder, disabled }: FileUploadFieldProps) {
  const { t } = useTranslation("layout");
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handlePick = () => inputRef.current?.click();

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const { url } = await onUpload(file);
      onChange(url);
      onFileSize?.(file.size);
    } catch (err) {
      setError(err instanceof Error ? err.message : t("upload.file.failed"));
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="space-y-1.5">
      <input ref={inputRef} type="file" accept={accept} className="hidden" onChange={handleFileChange} disabled={disabled || uploading} />
      {value ? (
        <div className="flex items-center gap-2 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
          <Paperclip className="w-3.5 h-3.5 text-slate-400 shrink-0" />
          <a href={value} target="_blank" rel="noreferrer" className="text-[11px] text-brand-orange font-semibold truncate flex-1 hover:underline">
            {value}
          </a>
          <button
            type="button"
            onClick={handlePick}
            disabled={disabled || uploading}
            className="text-[10px] font-bold text-slate-500 hover:text-slate-800 flex items-center gap-1 shrink-0 disabled:opacity-50"
          >
            {uploading ? <Loader2 className="w-3 h-3 animate-spin" /> : <RotateCcw className="w-3 h-3" />}
            {t("upload.file.changeFile")}
          </button>
        </div>
      ) : (
        <button
          type="button"
          onClick={handlePick}
          disabled={disabled || uploading}
          className="w-full flex items-center justify-center gap-1.5 border border-dashed border-slate-300 text-slate-500 hover:border-brand-orange hover:text-brand-orange text-xs font-bold py-2.5 rounded-lg disabled:opacity-50"
        >
          {uploading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Paperclip className="w-3.5 h-3.5" />}
          {uploading ? t("upload.file.uploading") : placeholder ?? t("upload.file.choose")}
        </button>
      )}
      {error && <p className="text-[10px] text-rose-600 font-semibold">{error}</p>}
    </div>
  );
}
