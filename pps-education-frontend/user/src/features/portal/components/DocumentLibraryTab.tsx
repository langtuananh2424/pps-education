import React, { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Download, FileText, FolderOpen, Music, Play, Search, Video, X } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { CurriculumDocumentResponse, getPortalClass, listMyDocuments } from "../api";
import { clampLines } from "@/lib/textClamp";

const documentTypeIcons: Record<CurriculumDocumentResponse["documentType"], React.ReactNode> = {
  VIDEO: <Video size={12} />,
  PDF: <FileText size={12} />,
  AUDIO: <Music size={12} />,
  SLIDE: <FileText size={12} />,
  IMAGE: <FileText size={12} />,
  OTHER: <FileText size={12} />
};

/** Nền pastel gradient của thẻ tài liệu theo loại (kiểu "glass", 2026-09-19, đã xác nhận với người dùng — đồng bộ với thẻ BTVN). */
const documentTypeTints: Record<CurriculumDocumentResponse["documentType"], string> = {
  PDF: "from-teal/15 via-white/70 to-white/50",
  SLIDE: "from-teal/15 via-white/70 to-white/50",
  VIDEO: "from-coral/15 via-white/70 to-white/50",
  AUDIO: "from-plum/15 via-white/70 to-white/50",
  IMAGE: "from-gold/20 via-white/70 to-white/50",
  OTHER: "from-gold/20 via-white/70 to-white/50"
};

type CategoryFilter = "ALL" | "DOCUMENT" | "MEDIA" | "OTHER";

/** Nhóm theo documentType THẬT (VIDEO/PDF/AUDIO/SLIDE/IMAGE/OTHER) — không bịa thêm phân loại (VD "Flashcards"/"E-Book") ngoài dữ liệu backend đang có.
 *  Nhãn hiển thị tra qua t("documents.categoryFilters.<key>") — xem locale portal-account.json. */
const CATEGORY_FILTERS: { key: CategoryFilter; types: CurriculumDocumentResponse["documentType"][] }[] = [
  { key: "ALL", types: [] },
  { key: "DOCUMENT", types: ["PDF", "SLIDE"] },
  { key: "MEDIA", types: ["VIDEO", "AUDIO"] },
  { key: "OTHER", types: ["IMAGE", "OTHER"] }
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
  const { t } = useTranslation("portal-account");
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
      .catch((err) => setError(err instanceof ApiError ? err.message : t("documents.loadFailed")))
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

  if (loading) return <p className="text-sm text-muted font-bold">{t("documents.loading")}</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-[linear-gradient(to_right,#17a6a0,#0e8c86)] p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.08)] flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-2xl bg-white/15 flex items-center justify-center text-white shrink-0">
            <FolderOpen size={22} />
          </div>
          <div>
            <h2 className="text-lg md:text-xl font-black text-white">{t("documents.heroTitle")}</h2>
            <p className="text-sm text-white/80 font-bold mt-0.5">{t("documents.heroSubtitle")}</p>
          </div>
        </div>

        <div className="relative w-full md:w-72">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" />
          <input
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t("documents.searchPlaceholder")}
            className="w-full bg-white border-none rounded-xl pl-8 pr-3 py-2.5 text-sm font-bold text-ink focus:outline-none focus:ring-2 focus:ring-white/50 shadow-sm"
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
              className={`shrink-0 snap-start px-4 py-2 rounded-xl text-sm font-bold transition-all cursor-pointer ${
                categoryFilter === c.key
                  ? "bg-teal text-white shadow-md"
                  : "bg-white/70 backdrop-blur-md border border-white/80 text-ink shadow-sm hover:bg-white/90"
              }`}
            >
              {t(`documents.categoryFilters.${c.key}`)} ({count})
            </button>
          );
        })}
      </div>

      {filteredDocuments.length === 0 ? (
        <p className="text-sm text-muted font-bold italic text-center py-10">{t("documents.noResults")}</p>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
          {filteredDocuments.map((doc) => {
            const youtubeVideoId = doc.documentType === "VIDEO" ? extractYouTubeVideoId(doc.fileUrl) : null;
            const thumbnailUrl = doc.coverImageUrl ?? (youtubeVideoId ? getYouTubeThumbnailUrl(youtubeVideoId) : null);

            return (
              <div
                key={doc.id}
                className={`flex flex-col rounded-3xl p-2.5 bg-gradient-to-br ${documentTypeTints[doc.documentType]} backdrop-blur-md border border-white/80 shadow-[0_8px_30px_rgba(30,42,69,0.06)] hover:border-teal/50 hover:ring-4 hover:ring-teal/10 hover:shadow-[0_12px_36px_rgba(30,42,69,0.12)] hover:-translate-y-0.5 transition-all overflow-hidden`}
              >
                <div className="relative w-full h-32 rounded-2xl bg-white/60 flex items-center justify-center text-teal overflow-hidden">
                  {thumbnailUrl ? <img src={thumbnailUrl} alt="" className="w-full h-32 object-cover" /> : <FileText size={28} />}
                  <span className="absolute top-2 left-2 flex items-center gap-1 bg-ink/70 text-white text-[10px] font-extrabold uppercase px-2 py-1 rounded-lg">
                    {documentTypeIcons[doc.documentType]} {t(`documents.type.${doc.documentType}`)}
                  </span>
                  {youtubeVideoId && (
                    <div className="absolute inset-0 flex items-center justify-center bg-ink/10">
                      <div className="w-9 h-9 rounded-full bg-white/90 flex items-center justify-center text-teal-deep shadow">
                        <Play size={16} className="ml-0.5" />
                      </div>
                    </div>
                  )}
                </div>
                <div className="px-2 pt-3 pb-2 flex-1 min-w-0 space-y-1">
                  <p title={doc.title} style={clampLines(2)} className="font-black text-ink text-base leading-snug">
                    {doc.title}
                  </p>
                  {doc.description && (
                    <p title={doc.description} style={clampLines(2)} className="text-xs text-muted font-semibold">
                      {doc.description}
                    </p>
                  )}
                </div>
                <div className="px-2 pb-2">
                  {youtubeVideoId ? (
                    <button
                      type="button"
                      onClick={() => setActiveYouTubeDoc({ title: doc.title, videoId: youtubeVideoId })}
                      className="w-full flex items-center justify-center gap-1.5 bg-white/80 hover:bg-white border border-white rounded-full py-2 text-xs font-extrabold text-ink transition-colors cursor-pointer"
                    >
                      <Play size={13} className="text-teal" /> {t("documents.watchVideo")}
                    </button>
                  ) : (
                    <a
                      href={doc.fileUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="w-full flex items-center justify-center gap-1.5 bg-white/80 hover:bg-white border border-white rounded-full py-2 text-xs font-extrabold text-ink transition-colors"
                    >
                      <Download size={13} className="text-teal" /> {t("documents.readAndDownload", { type: t(`documents.type.${doc.documentType}`) })}
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
                aria-label={t("documents.close")}
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
