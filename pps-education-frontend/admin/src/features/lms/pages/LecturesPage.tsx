import React, { useState } from "react";
import { FileText, Upload, Video } from "lucide-react";
import { Lecture } from "@/types";
import { mockCourses, mockLectures } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

export default function LecturesPage() {
  const [lectures, setLectures] = useState<Lecture[]>(mockLectures);
  const [showUploadForm, setShowUploadForm] = useState(false);
  const [title, setTitle] = useState("");
  const [fileType, setFileType] = useState<"VIDEO" | "PDF">("VIDEO");
  const [courseId, setCourseId] = useState("CRS-01");

  const handleUpload = (e: React.FormEvent) => {
    e.preventDefault();
    if (!title) return;

    const newLec: Lecture = {
      id: `LEC-00${lectures.length + 1}`,
      title,
      description: "Học liệu bổ trợ giảng dạy tích hợp CDN tải mượt.",
      fileType,
      cdnUrl: `https://pps-cdn.vn/lectures/custom_${Date.now()}.${fileType.toLowerCase()}`,
      courseId,
      views: 0
    };

    setLectures((prev) => [...prev, newLec]);
    setTitle("");
    setShowUploadForm(false);
    alert("Đã upload học liệu bổ trợ thành công!\nTệp tin được phân phối trực tiếp qua máy chủ CDN bảo đảm xem không giật lag (UC-23).");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Tài Liệu & Khảo Thí LMS (E-Learning)</h1>
        <p className="text-xs text-slate-500 mt-1">Phân phối học liệu video chất lượng qua CDN (UC-23).</p>
      </div>

      <div className="flex items-center justify-between">
        <h3 className="text-sm font-bold text-slate-800 font-display">Kho bài giảng số</h3>
        <Button variant="primary" onClick={() => setShowUploadForm(!showUploadForm)}>
          <Upload className="w-4 h-4" />
          <span>Tải học liệu lên CDN</span>
        </Button>
      </div>

      {showUploadForm && (
        <form onSubmit={handleUpload} className="bg-slate-50 p-4 rounded-xl border border-slate-200 grid grid-cols-1 md:grid-cols-4 gap-4 animate-in fade-in duration-150">
          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Tiêu đề học liệu</label>
            <input
              type="text"
              required
              placeholder="Ví dụ: Unit 2 Luyện từ vựng..."
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
            />
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Định dạng file</label>
            <select value={fileType} onChange={(e) => setFileType(e.target.value as "VIDEO" | "PDF")} className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none">
              <option value="VIDEO">Video bổ trợ (.mp4)</option>
              <option value="PDF">Tài liệu lý thuyết (.pdf)</option>
            </select>
          </div>

          <div className="space-y-1">
            <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Gán khóa học</label>
            <select value={courseId} onChange={(e) => setCourseId(e.target.value)} className="w-full bg-white border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none">
              {mockCourses.map((crs) => (
                <option key={crs.id} value={crs.id}>
                  {crs.name}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1 flex items-end">
            <button type="submit" className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 px-3 rounded-lg flex items-center justify-center gap-1">
              Xác nhận lưu học liệu
            </button>
          </div>
        </form>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {lectures.map((lec) => (
          <Card key={lec.id} className="flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2">
                {lec.fileType === "VIDEO" ? (
                  <span className="p-2 rounded-lg bg-red-50 text-brand-red">
                    <Video className="w-5 h-5 shrink-0" />
                  </span>
                ) : (
                  <span className="p-2 rounded-lg bg-amber-50 text-brand-orange">
                    <FileText className="w-5 h-5 shrink-0" />
                  </span>
                )}

                <div>
                  <h4 className="text-xs font-bold text-slate-800 leading-normal">{lec.title}</h4>
                  <span className="text-[10px] text-slate-400 font-mono mt-0.5 block">{lec.id}</span>
                </div>
              </div>

              <p className="text-[11px] text-slate-500 italic mt-3 bg-slate-50 p-2 rounded border leading-relaxed break-all">CDN Target: {lec.cdnUrl}</p>
            </div>

            <div className="border-t border-slate-100 pt-3 flex items-center justify-between">
              <span className="text-[10px] font-semibold text-slate-500">Lượt tải/Xem: {lec.views} views</span>
              <span className="text-[10px] font-bold text-brand-orange">CDN-OPTIMIZED</span>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}
