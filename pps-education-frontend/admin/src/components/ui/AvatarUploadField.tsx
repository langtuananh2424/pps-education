import React, { useRef, useState } from "react";
import { Camera, Loader2 } from "lucide-react";
import { cn } from "@/lib/cn";

interface AvatarUploadFieldProps {
  /** URL ảnh hiện tại (nếu đã có) — trống thì hiện chữ cái đầu tên thay thế. */
  value: string | null | undefined;
  onChange: (url: string) => void;
  /** Gọi API upload thật (module do trang gọi tự quy định) — trả về URL public sau khi lên R2 thành công. */
  onUpload: (file: File) => Promise<{ url: string }>;
  /** Dùng để hiện chữ cái đầu khi chưa có ảnh. */
  fallbackName: string;
  size?: "md" | "lg";
  disabled?: boolean;
}

const sizeClasses = { md: "w-16 h-16 text-lg", lg: "w-24 h-24 text-2xl" };

/** Ảnh đại diện dạng tròn có thể bấm để đổi ảnh — dùng chung cho hồ sơ Nhân sự/Phụ huynh/Học sinh (V48/UC-63). */
export default function AvatarUploadField({ value, onChange, onUpload, fallbackName, size = "lg", disabled }: AvatarUploadFieldProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const initial = fallbackName.trim().split(" ").slice(-1)[0]?.[0]?.toUpperCase() || "U";

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = "";
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const { url } = await onUpload(file);
      onChange(url);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Upload ảnh thất bại.");
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="flex items-center gap-3">
      <div className="relative shrink-0">
        <input ref={inputRef} type="file" accept="image/*" className="hidden" onChange={handleFileChange} disabled={disabled || uploading} />
        <div
          className={cn(
            "rounded-full bg-brand-gradient flex items-center justify-center font-display font-bold text-white shadow-soft overflow-hidden border-2 border-white",
            sizeClasses[size]
          )}
        >
          {value ? <img src={value} alt="" className="w-full h-full object-cover" /> : initial}
        </div>
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          disabled={disabled || uploading}
          title="Đổi ảnh đại diện"
          className="absolute bottom-0 right-0 w-6 h-6 rounded-full bg-brand-red hover:opacity-90 text-white flex items-center justify-center shadow-md border-2 border-white disabled:opacity-50"
        >
          {uploading ? <Loader2 className="w-3 h-3 animate-spin" /> : <Camera className="w-3 h-3" />}
        </button>
      </div>
      {error && <p className="text-sm text-rose-600 font-semibold">{error}</p>}
    </div>
  );
}
