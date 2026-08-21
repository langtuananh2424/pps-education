import React, { useEffect, useRef, useState } from "react";
import { Bell, MessageSquareCode, ShieldAlert } from "lucide-react";
import { useTranslation } from "react-i18next";
import { ApiError } from "@/lib/apiClient";
import { formatDateTimeHm } from "@/lib/format";
import { clampLines } from "@/lib/textClamp";
import { listComments, listMyComments, listMyNotifications, NotificationResponse, StudentCommentResponse } from "../api";

interface HomeTabProps {
  studentName: string;
  classId: number | null;
  /**
   * UC-64 (2026-07-29) — chỉ set khi xem qua Cổng Phụ huynh, dùng
   * `/portal/parent/children/{studentId}/classes/{classId}/comments`. Không set (Học sinh tự
   * xem) thì dùng self-service `/students/me/classes/{classId}/comments` (listMyComments,
   * suy studentId từ JWT) — cả 2 endpoint giờ đã có, không còn ẩn khối nhận xét nữa.
   */
  parentStudentId?: number;
}

export default function HomeTab({ studentName, classId, parentStudentId }: HomeTabProps) {
  const { t, i18n } = useTranslation("portal-progress");
  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [comments, setComments] = useState<StudentCommentResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Mobile: carousel "Thông báo" tự trượt ngang (theo yêu cầu người dùng, 2026-07-31) — desktop giữ
  // nguyên danh sách dọc. cardRefs dùng scrollIntoView theo từng thẻ thay vì tự tính pixel scroll
  // (bề rộng thẻ co giãn theo % nên tính tay dễ sai). pausedRef (không phải state) để interval đọc
  // giá trị mới nhất ngay lập tức khi người dùng chạm/kéo tay, không cần re-tạo interval mỗi lần.
  const [carouselIndex, setCarouselIndex] = useState(0);
  const cardRefs = useRef<(HTMLDivElement | null)[]>([]);
  const pausedRef = useRef(false);

  useEffect(() => {
    if (notifications.length <= 1) return;
    const timer = setInterval(() => {
      if (pausedRef.current) return;
      setCarouselIndex((prev) => {
        const next = (prev + 1) % notifications.length;
        cardRefs.current[next]?.scrollIntoView({ behavior: "smooth", inline: "start", block: "nearest" });
        return next;
      });
    }, 4000);
    return () => clearInterval(timer);
  }, [notifications.length]);

  const pauseCarousel = () => {
    pausedRef.current = true;
  };
  const resumeCarouselSoon = () => {
    setTimeout(() => {
      pausedRef.current = false;
    }, 3000);
  };

  useEffect(() => {
    setLoading(true);
    const commentsPromise = classId == null ? Promise.resolve([]) : parentStudentId != null ? listComments(parentStudentId, classId) : listMyComments(classId);
    Promise.all([listMyNotifications(), commentsPromise])
      .then(([notif, cmt]) => {
        setNotifications(notif.content);
        setComments(cmt);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t("homeLoadError")))
      .finally(() => setLoading(false));
  }, [classId, parentStudentId]);

  const warned = comments.filter((c) => c.isWarning);
  const regular = comments.filter((c) => !c.isWarning);

  if (loading) return <p className="text-sm text-muted font-bold">{t("loading")}</p>;

  return (
    <div className="space-y-6">
      <div className="bg-[linear-gradient(to_right,#17a6a0,#0e8c86,#1e2a45)] text-white p-8 rounded-[20px] border border-teal-deep/30 shadow-[0_8px_30px_rgba(23,166,160,0.12)] relative overflow-hidden">
        <div className="absolute right-[-40px] bottom-[-40px] w-48 h-48 bg-white/5 rounded-full pointer-events-none" />
        <div className="relative z-10 space-y-3">
          <span className="bg-[#ff7a59] text-white px-3 py-1 rounded-full text-xs font-bold uppercase tracking-wider">{t("home.newsBadge")}</span>
          <h1 className="text-3xl md:text-4xl font-extrabold tracking-tight leading-tight">
            {t("home.welcomeMessage")} <span className="text-[#f4b740]">{studentName}</span>!
          </h1>
        </div>
      </div>

      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className={`grid grid-cols-1 gap-6 ${classId != null ? "lg:grid-cols-3" : ""}`}>
        <div className={`${classId != null ? "lg:col-span-2" : ""} bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4`}>
          <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
            <Bell className="text-teal" /> {t("home.notificationsTitle")}
          </h2>
          {notifications.length === 0 ? (
            <p className="text-xs text-muted font-bold italic">{t("home.noNotifications")}</p>
          ) : (
            <>
              {/* Mobile: carousel trượt ngang TỰ ĐỘNG (theo yêu cầu người dùng, 2026-07-31) — dừng lại
                  khi người dùng đang chạm/kéo tay, tự chạy tiếp sau 3s không thao tác. Không dùng mẹo
                  bleed lề (-mx/px) — đã từng gây lệch mép với card phía trên, để carousel nằm nguyên
                  trong padding p-6 sẵn có của card, chỉ cho từng thẻ "ngó" ra 1 phần (w-[85%]). */}
              <div className="md:hidden">
                <div
                  onTouchStart={pauseCarousel}
                  onTouchEnd={resumeCarouselSoon}
                  onMouseDown={pauseCarousel}
                  onMouseUp={resumeCarouselSoon}
                  className="flex gap-3 overflow-x-auto scrollbar-hide snap-x snap-mandatory"
                >
                  {notifications.map((n, i) => (
                    <div
                      key={n.id}
                      ref={(el) => {
                        cardRefs.current[i] = el;
                      }}
                      className="shrink-0 w-[85%] snap-start bg-slate-50/50 border border-line/60 p-4 rounded-[16px] space-y-1"
                    >
                      <div className="flex justify-between items-center">
                        <span className="text-[10px] font-bold text-teal uppercase">{n.notificationType}</span>
                        <span className="text-xs text-muted font-semibold">{formatDateTimeHm(n.createdAt, i18n.language)}</span>
                      </div>
                      <h4 className="font-extrabold text-ink text-sm">{n.title}</h4>
                      <p className="text-xs text-muted font-semibold" style={clampLines(3)}>
                        {n.content}
                      </p>
                    </div>
                  ))}
                </div>
                {notifications.length > 1 && (
                  <div className="flex items-center justify-center gap-1.5 mt-3">
                    {notifications.map((_, i) => (
                      <span key={i} className={`h-1.5 rounded-full transition-all ${i === carouselIndex ? "w-4 bg-teal" : "w-1.5 bg-line"}`} />
                    ))}
                  </div>
                )}
              </div>

              <div className="hidden md:block space-y-3">
                {notifications.map((n) => (
                  <div key={n.id} className="bg-slate-50/50 border border-line/60 p-4 rounded-[16px] space-y-1">
                    <div className="flex justify-between items-center">
                      <span className="text-[10px] font-bold text-teal uppercase">{n.notificationType}</span>
                      <span className="text-xs text-muted font-semibold">{formatDateTimeHm(n.createdAt, i18n.language)}</span>
                    </div>
                    <h4 className="font-extrabold text-ink text-sm">{n.title}</h4>
                    <p className="text-xs text-muted font-semibold">{n.content}</p>
                  </div>
                ))}
              </div>
            </>
          )}
        </div>

        {classId != null && (
        <div className="lg:col-span-1 space-y-6">
          {warned.length > 0 && (
            <div className="bg-[#ff7a59]/5 border border-[#ff7a59]/20 p-5 rounded-[20px] space-y-3">
              <h3 className="text-base font-extrabold text-[#ff7a59] flex items-center gap-2">
                <ShieldAlert size={20} /> {t("home.warningsTitle")}
              </h3>
              {warned.map((c) => (
                <div key={c.id} className="bg-white border border-[#ff7a59]/15 p-4 rounded-xl space-y-1">
                  <p className="text-xs text-ink leading-relaxed font-bold">{c.content}</p>
                  <p className="text-[10px] text-muted font-bold text-right">{c.commentDate}</p>
                </div>
              ))}
            </div>
          )}

          <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
            <h3 className="text-lg font-extrabold text-ink flex items-center gap-2">
              <MessageSquareCode className="text-teal" /> {t("home.recentCommentsTitle")}
            </h3>
            <div className="space-y-4">
              {/* Chỉ hiện 5 nhận xét gần nhất (theo yêu cầu người dùng, 2026-08-12) — comments đã được
                  backend sắp xếp desc theo commentDate (findBySchoolClassIdAndStudentIdAndStatusOrderByCommentDateDesc),
                  không có giới hạn số dòng ở API nên phải cắt bớt ở FE để trang chủ không phình to theo thời gian. */}
              {regular.slice(0, 5).map((c) => (
                <div key={c.id} className="border-b border-line last:border-0 pb-4 last:pb-0 space-y-1">
                  <div className="flex justify-between items-center">
                    <span className="px-2 py-0.5 bg-teal/10 text-teal-deep text-[10px] font-extrabold rounded-full border border-teal/20">
                      {t("home.dailyBadge")}
                    </span>
                    <span className="text-xs text-muted font-semibold">{c.commentDate}</span>
                  </div>
                  <p className="text-xs text-muted leading-relaxed italic font-semibold">"{c.content}"</p>
                </div>
              ))}
              {regular.length === 0 && <p className="text-xs text-muted font-bold italic">{t("home.noComments")}</p>}
            </div>
          </div>
        </div>
        )}
      </div>
    </div>
  );
}
