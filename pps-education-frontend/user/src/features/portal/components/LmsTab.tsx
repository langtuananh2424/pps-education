import React, { useEffect, useState } from "react";
import { BookOpen, Download, FileText, Play } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import { LessonMaterialResponse, LessonResponse, listLessonMaterials, listLessonsByClass } from "../api";
import LmsPracticeDemo from "./LmsPracticeDemo";

interface LmsTabProps {
  classId: number;
}

export default function LmsTab({ classId }: LmsTabProps) {
  const [lessons, setLessons] = useState<LessonResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [openLessonId, setOpenLessonId] = useState<number | null>(null);
  const [materials, setMaterials] = useState<LessonMaterialResponse[]>([]);

  useEffect(() => {
    setLoading(true);
    listLessonsByClass(classId)
      .then((res) => setLessons(res.filter((l) => l.status === "PUBLISHED").sort((a, b) => a.lessonOrder - b.lessonOrder)))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được bài giảng."))
      .finally(() => setLoading(false));
  }, [classId]);

  const handleOpen = (lessonId: number) => {
    if (openLessonId === lessonId) {
      setOpenLessonId(null);
      return;
    }
    setOpenLessonId(lessonId);
    listLessonMaterials(lessonId)
      .then(setMaterials)
      .catch(() => setMaterials([]));
  };

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  return (
    <div className="space-y-6">
      {error && <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">{error}</div>}

      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-4">
        <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
          <BookOpen className="text-teal" /> Kho bài giảng
        </h2>
        {lessons.length === 0 ? (
          <p className="text-xs text-muted font-bold italic">Lớp này chưa có bài giảng nào được công bố.</p>
        ) : (
          <div className="space-y-3">
            {lessons.map((lesson) => (
              <div key={lesson.id} className="border border-line/80 rounded-[16px] overflow-hidden">
                <button
                  onClick={() => handleOpen(lesson.id)}
                  className="w-full flex items-center gap-4 p-4 bg-sky-2 hover:bg-sky text-left"
                >
                  <div className="w-10 h-10 rounded-full bg-teal/10 border border-teal/20 flex items-center justify-center text-teal shrink-0">
                    <Play size={16} />
                  </div>
                  <div className="flex-1">
                    <p className="font-extrabold text-ink text-sm">{lesson.title}</p>
                    <p className="text-[10px] text-muted font-bold">{lesson.durationMinutes ? `${lesson.durationMinutes} phút` : lesson.lessonType}</p>
                  </div>
                </button>
                {openLessonId === lesson.id && (
                  <div className="p-4 space-y-2 bg-white">
                    {materials.length === 0 ? (
                      <p className="text-xs text-muted font-bold italic">Chưa có tài liệu đính kèm.</p>
                    ) : (
                      materials.map((m) => (
                        <a
                          key={m.id}
                          href={m.fileUrl}
                          target="_blank"
                          rel="noreferrer"
                          className="flex items-center gap-2 text-xs font-bold text-teal-deep hover:underline"
                        >
                          {m.isDownloadable ? <Download size={14} /> : <FileText size={14} />}
                          {m.title}
                        </a>
                      ))
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      <LmsPracticeDemo />
    </div>
  );
}
