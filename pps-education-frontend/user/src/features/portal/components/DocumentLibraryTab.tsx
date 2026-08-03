import React, { useEffect, useMemo, useState } from "react";
import { Download, FileText, FolderOpen, Music, Play, Search, Video, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { CurriculumDocumentResponse, getPortalClass, listMyDocuments } from "../api";
import { clampLines } from "@/lib/textClamp";

const documentTypeLabels: Record<CurriculumDocumentResponse["documentType"], string> = {
  VIDEO: "Video",
  PDF: "PDF",
  AUDIO: "Audio",
  SLIDE: "Slide",
  IMAGE: "Ảnh",
  OTHER: "Khác"
};

const documentTypeIcons: Record<CurriculumDocumentResponse["documentType"], React.ReactNode> = {
  VIDEO: <Video size={12} />,
  PDF: <FileText size={12} />,
  AUDIO: <Music size={12} />,
  SLIDE: <FileText size={12} />,
  IMAGE: <FileText size={12} />,
  OTHER: <FileText size={12} />
};

type CategoryFilter = "ALL" | "DOCUMENT" | "MEDIA" | "OTHER";

/** Nhóm theo documentType THẬT (VIDEO/PDF/AUDIO/SLIDE/IMAGE/OTHER) — không bịa thêm phân loại (VD "Flashcards"/"E-Book") ngoài dữ liệu backend đang có. */
const CATEGORY_FILTERS: { key: CategoryFilter; label: string; types: CurriculumDocumentResponse["documentType"][] }[] = [
  { key: "ALL", label: "Tất cả tài liệu", types: [] },
  { key: "DOCUMENT", label: "Sách & Tài liệu", types: ["PDF", "SLIDE"] },
  { key: "MEDIA", label: "Video & Audio", types: ["VIDEO", "AUDIO"] },
  { key: "OTHER", label: "Hình ảnh & Khác", types: ["IMAGE", "OTHER"] }
];

/** Nhận diện link YouTube (watch/youtu.be/shorts/embed) và trả về videoId, null nếu không phải YouTube. */
function extractYouTubeVideoId(url: string): string | null {
  try {
    const parsed = new URL(url);
    const host = parsed.hostname.replace(/^www\./, "").replace(/^m\./, "");
    if (host === "youtu.be") return parsed.pathname.slice(1);
    if (host === "youtube.com") {
      if (parsed.pathname === "/watch") return parsed.searchParams.get("v");
      if (parsed.pathname.startsWith("/embed/")) return parsed.pathname.slice("/embed/".length);
      if (parsed.pathname.startsWith("/shorts/")) return parsed.pathname.slice("/shorts/".length);
    }
    return null;
  } catch {
    return null;
  }
}

function getYouTubeThumbnailUrl(videoId: string): string {
  return `https://img.youtube.com/vi/${videoId}/hqdefault.jpg`;
}

interface DocumentLibraryTabProps {
  classId: number;
}

/**
 * UC-60 (phía học sinh xem) — Kho dữ liệu tham khảo, tách ra thành tab riêng
 * ở sidebar Portal thay vì nằm lồng trong E-Learning & LMS (nay đã bỏ hẳn tab
 * đó, đã xác nhận với người dùng 2026-07-27) — DÙNG LẠI nguyên
 * listMyDocuments(curriculumId), không phải API/dữ liệu mới.
 */
export default function DocumentLibraryTab({ classId }: DocumentLibraryTabProps) {
  const [documents, setDocuments] = useState<CurriculumDocumentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const [categoryFilter, setCategoryFilter] = useState<CategoryFilter>("ALL");
  const [activeYouTubeDoc, setActiveYouTubeDoc] = useState<{ title: string; videoId: string } | null>(null);

  useEffect(() => {
    setLoading(true);
    getPortalClass(classId)
      .then((cls) => listMyDocuments(cls.curriculumId))
      .then(setDocuments)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được kho dữ liệu."))
      .finally(() => setLoading(false));
  }, [classId]);

  const filteredDocuments = useMemo(() => {
    const category = CATEGORY_FILTERS.find((c) => c.key === categoryFilter)!;
    return documents.filter((doc) => {
      const matchesCategory = category.types.length === 0 || category.types.includes(doc.documentType);
      const matchesSearch = doc.title.toLowerCase().includes(searchQuery.toLowerCase());
      return matchesCategory && matchesSearch;
    });
  }, [documents, categoryFilter, searchQuery]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-gradient-to-r from-teal to-teal-deep p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.08)] flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl bg-white/15 flex items-center justify-center text-white shrink-0">
            <FolderOpen size={22} />
          </div>
          <div>
            <h2 className="text-lg md:text-xl font-black text-white">Kho Dữ Liệu &amp; Thư Viện Tài Liệu Tham Khảo</h2>
            <p className="text-xs text-white/80 font-bold mt-0.5">Tài liệu chung theo khung chương trình để tự học thêm.</p>
          </div>
        </div>

        <div className="relative w-full md:w-72">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Tìm kiếm tài liệu, sách..."
            className="w-full bg-white border-none rounded-xl pl-8 pr-3 py-2.5 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-white/50 shadow-sm"
          />
        </div>
      </div>

      {/* Mobile: 1 hàng cuộn ngang kiểu carousel thay vì tự vỡ thành lưới 2x2 (theo yêu cầu người dùng,
          2026-07-31). Không dùng mẹo bleed "-mx-4 px-4" (kéo tràn lề rồi bù lại padding) — nút đầu tiên
          sẽ không thẳng hàng với card gradient phía trên (đã gặp, khoảng cách lệch nhau); để nguyên
          trong container hiện có thì mép trái tự khớp đúng với card phía trên. Desktop (md+) giữ nguyên
          flex-wrap như cũ. */}
      <div className="flex items-center gap-2 overflow-x-auto scrollbar-hide snap-x snap-proximity md:flex-wrap">
        {CATEGORY_FILTERS.map((c) => {
          const count = c.types.length === 0 ? documents.length : documents.filter((doc) => c.types.includes(doc.documentType)).length;
          return (
            <button
              key={c.key}
              onClick={() => setCategoryFilter(c.key)}
              className={`shrink-0 snap-start px-3.5 py-2 rounded-xl text-xs font-bold transition-colors ${
                categoryFilter === c.key ? "bg-teal text-white shadow-sm" : "bg-white border border-line/80 text-muted hover:bg-sky-2"
              }`}
            >
              {c.label} ({count})
            </button>
          );
        })}
      </div>

      {filteredDocuments.length === 0 ? (
        <p className="text-xs text-muted font-bold italic text-center py-10">Không tìm thấy tài liệu nào phù hợp.</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocuments.map((doc) => {
            const youtubeVideoId = doc.documentType === "VIDEO" ? extractYouTubeVideoId(doc.fileUrl) : null;
            const thumbnailUrl = doc.coverImageUrl ?? (youtubeVideoId ? getYouTubeThumbnailUrl(youtubeVideoId) : null);

            return (
              <div key={doc.id} className="flex flex-col rounded-[16px] border border-line/80 bg-white overflow-hidden shadow-sm">
                <div className="relative w-full h-32 bg-teal/10 flex items-center justify-center text-teal overflow-hidden">
                  {thumbnailUrl ? <img src={thumbnailUrl} alt="" className="w-full h-32 object-cover" /> : <FileText size={28} />}
                  <span className="absolute top-2 left-2 flex items-center gap-1 bg-ink/70 text-white text-[10px] font-extrabold uppercase px-2 py-1 rounded-lg">
                    {documentTypeIcons[doc.documentType]} {documentTypeLabels[doc.documentType]}
                  </span>
                  {youtubeVideoId && (
                    <div className="absolute inset-0 flex items-center justify-center bg-ink/10">
                      <div className="w-9 h-9 rounded-full bg-white/90 flex items-center justify-center text-teal-deep shadow">
                        <Play size={16} className="ml-0.5" />
                      </div>
                    </div>
                  )}
                </div>
                <div className="p-3.5 flex-1 min-w-0 space-y-1">
                  <p title={doc.title} style={clampLines(2)} className="font-extrabold text-ink text-sm leading-snug">
                    {doc.title}
                  </p>
                  {doc.description && (
                    <p title={doc.description} style={clampLines(2)} className="text-[10px] text-muted font-bold">
                      {doc.description}
                    </p>
                  )}
                </div>
                <div className="p-3.5 pt-0">
                  {youtubeVideoId ? (
                    <button
                      type="button"
                      onClick={() => setActiveYouTubeDoc({ title: doc.title, videoId: youtubeVideoId })}
                      className="w-full flex items-center justify-center gap-1.5 bg-slate-50 hover:bg-slate-100 border border-line/80 rounded-xl py-2 text-xs font-extrabold text-ink transition-colors"
                    >
                      <Play size={13} className="text-teal" /> Xem video
                    </button>
                  ) : (
                    <a
                      href={doc.fileUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="w-full flex items-center justify-center gap-1.5 bg-slate-50 hover:bg-slate-100 border border-line/80 rounded-xl py-2 text-xs font-extrabold text-ink transition-colors"
                    >
                      <Download size={13} className="text-teal" /> Đọc &amp; Tải về {documentTypeLabels[doc.documentType]}
                    </a>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}

      {activeYouTubeDoc && (
        <div className="fixed inset-0 bg-ink/40 backdrop-blur-sm z-[100] flex items-center justify-center p-4" onClick={() => setActiveYouTubeDoc(null)}>
          <div className="bg-white rounded-[24px] max-w-2xl w-full max-h-[92vh] overflow-y-auto shadow-2xl p-6 space-y-4" onClick={(e) => e.stopPropagation()}>
            <div className="flex items-start justify-between gap-3">
              <h3 className="text-lg font-extrabold text-ink">{activeYouTubeDoc.title}</h3>
              <button
                onClick={() => setActiveYouTubeDoc(null)}
                className="w-8 h-8 shrink-0 rounded-full bg-sky-2 hover:bg-sky flex items-center justify-center text-ink transition-colors"
                aria-label="Đóng"
              >
                <X size={16} />
              </button>
            </div>
            <div className="aspect-video w-full rounded-[12px] overflow-hidden bg-ink">
              <iframe
                src={`https://www.youtube.com/embed/${activeYouTubeDoc.videoId}?rel=0`}
                title={activeYouTubeDoc.title}
                className="w-full h-full"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowFullScreen
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
