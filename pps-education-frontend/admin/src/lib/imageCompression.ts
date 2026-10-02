/**
 * Thu nhỏ ảnh đại diện ngay trên trình duyệt trước khi upload: ảnh chụp từ điện thoại thường 3-10MB,
 * trong khi avatar chỉ hiển thị vài trăm px — gửi nguyên file trên mạng di động chậm làm upload treo rất lâu.
 * Không giải mã được (VD HEIC trên trình duyệt không hỗ trợ) hoặc ảnh động GIF thì giữ nguyên file gốc.
 */
const SKIP_BELOW_BYTES = 300 * 1024;

export async function compressImageForUpload(file: File, maxDimension = 1024, quality = 0.85): Promise<File> {
  if (!file.type.startsWith("image/") || file.type === "image/gif" || file.size <= SKIP_BELOW_BYTES) return file;
  if (typeof createImageBitmap !== "function") return file;

  let bitmap: ImageBitmap;
  try {
    // imageOrientation "from-image": xoay theo EXIF để ảnh chụp dọc không bị nằm ngang sau khi vẽ lại.
    bitmap = await createImageBitmap(file, { imageOrientation: "from-image" });
  } catch {
    return file;
  }

  try {
    const scale = Math.min(1, maxDimension / Math.max(bitmap.width, bitmap.height));
    const width = Math.round(bitmap.width * scale);
    const height = Math.round(bitmap.height * scale);
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d");
    if (!ctx) return file;
    // Nền trắng để ảnh PNG trong suốt không bị đen khi chuyển sang JPEG.
    ctx.fillStyle = "#fff";
    ctx.fillRect(0, 0, width, height);
    ctx.drawImage(bitmap, 0, 0, width, height);

    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (!blob || blob.size >= file.size) return file;
    const baseName = file.name.replace(/\.[^.]+$/, "") || "avatar";
    return new File([blob], `${baseName}.jpg`, { type: "image/jpeg", lastModified: Date.now() });
  } catch {
    return file;
  } finally {
    bitmap.close();
  }
}
