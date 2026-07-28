import React, { useState } from "react";
import { Award, Calendar, ChevronDown, Clock, Download, ExternalLink, FileCheck, MessageSquareText, ShieldCheck, Sparkles, TrendingUp, Video } from "lucide-react";

type AttitudeType = "Tốt" | "Khá" | "TB Khá" | "TB" | "Yếu" | "Kém";

interface SessionFeedbackLog {
  id: string;
  studentCode: string;
  sessionName: string;
  date: string;
  timeSlot: string;
  attitude: AttitudeType;
  prevGrammarHW: string;
  prevSpeakingHW: string;
  teacherComment: string;
  nextLectureLabel: string;
  nextLectureLink: string;
  nextHomework: string;
  notes: string | null;
}

interface DailyLearningProgressTabProps {
  studentName: string;
  studentCode: string;
}

/**
 * UC-21 (phía học sinh xem lại) — bản chuyển thể từ thiết kế Google AI Studio
 * (form nhập liệu của giáo viên) thành màn CHỈ XEM cho học sinh: bỏ hết
 * select/input chỉnh sửa (thái độ, % BTVN, chọn link/bài tập) vì học sinh
 * không được phép tự sửa nhận xét/thái độ do giáo viên chấm — ô "Thái độ học
 * tập" vẫn giữ NGUYÊN kiểu dáng như 1 dropdown (có mũi tên ChevronDown) để
 * khớp đúng UI gốc, nhưng là <div> tĩnh, không có onChange/focus được.
 *
 * DỮ LIỆU MẪU — CHƯA nối API thật. Endpoint hiện có
 * (GET /api/classes/{classId}/comments?studentId=) không có @PreAuthorize và
 * không kiểm tra quyền sở hữu (bất kỳ ai đăng nhập cũng đọc được nhận xét của
 * BẤT KỲ studentId nào) — nối thẳng vào Portal học sinh sẽ lộ dữ liệu nhận
 * xét của học sinh khác. Cần Backend bổ sung endpoint self-service riêng
 * (VD GET /api/students/me/classes/{classId}/comments, suy studentId từ JWT
 * như StudentPortalController đã làm cho sessions/exercises/grades) trước
 * khi nối data thật — đã xác nhận với người dùng 2026-07-27.
 */
const MOCK_LOGS: SessionFeedbackLog[] = [
  {
    id: "1",
    studentCode: "HS-20260710",
    sessionName: "Buổi 15: Thuyết trình con vật yêu thích (My Favorite Pet)",
    date: "2026-07-27",
    timeSlot: "09:47:00 - 09:52:00",
    attitude: "Tốt",
    prevGrammarHW: "100%",
    prevSpeakingHW: "100% - Video xuất sắc",
    teacherComment: "Gia Bảo hôm nay rất chủ động xung phong lên thuyết trình bài về bạn Kỳ lam ma thuật. Phát âm rành rọt, tự tin tương tác với cô Ms. Emily. Rất đáng khen!",
    nextLectureLabel: "🎬 Bài giảng Unit 16: Past Simple & Regular Verbs",
    nextLectureLink: "https://elearning.cse.edu.vn/lecture/unit-16-past-simple",
    nextHomework: "Quay video ngắn 1 phút nói về màu sắc yêu thích (Unit 3 trang 12)",
    notes: null
  },
  {
    id: "2",
    studentCode: "HS-20260710",
    sessionName: "Buổi 14: Kể chuyện Tấm Cám bằng tiếng Anh (Storytelling)",
    date: "2026-07-25",
    timeSlot: "17:30:00 - 19:00:00",
    attitude: "Khá",
    prevGrammarHW: "90%",
    prevSpeakingHW: "85% - Ghi âm ngữ điệu",
    teacherComment: "Con nhớ từ vựng nhân vật tốt. Giọng đọc truyền cảm. Cần rèn luyện thêm cách nối âm giữa các ngữ điệu trong câu dài.",
    nextLectureLabel: "🎬 Bài giảng Unit 15: Storytelling & Intonation",
    nextLectureLink: "https://elearning.cse.edu.vn/lecture/unit-15-storytelling",
    nextHomework: "Hoàn thành bài tập nối từ vựng trang 10 sách Workbook Phonics",
    notes: "Khen thưởng"
  },
  {
    id: "3",
    studentCode: "HS-20260710",
    sessionName: "Buổi 13: Luyện phát âm phụ âm kép /s/ & /z/ Sounds",
    date: "2026-07-17",
    timeSlot: "16:35:00 - 16:50:00",
    attitude: "Tốt",
    prevGrammarHW: "100%",
    prevSpeakingHW: "100% - Đã ghi âm phát",
    teacherComment: "Tập trung lắng nghe hướng dẫn khẩu hình, phát âm 2 âm /s/ và /z/ đã rõ và chuẩn hơn hẳn tuần trước.",
    nextLectureLabel: "🎬 Bài giảng Unit 14: Phonics /s/ & /z/ Sounds",
    nextLectureLink: "https://elearning.cse.edu.vn/lecture/unit-14-phonics-sz",
    nextHomework: "Luyện đọc 5 câu ghi âm trên ứng dụng E-Learning",
    notes: null
  },
  {
    id: "4",
    studentCode: "HS-20260710",
    sessionName: "Buổi 12: Roleplay tại siêu thị (Roleplay at Supermarket)",
    date: "2026-07-10",
    timeSlot: "17:30:00 - 19:00:00",
    attitude: "TB Khá",
    prevGrammarHW: "70%",
    prevSpeakingHW: "75% - TB Khá",
    teacherComment: "Con còn rụt rè khi nhập vai, cần luyện tập thêm mẫu câu hỏi giá và số đếm ở nhà.",
    nextLectureLabel: "🎬 Bài giảng Unit 13: Roleplay at Supermarket",
    nextLectureLink: "https://elearning.cse.edu.vn/lecture/unit-13-roleplay-market",
    nextHomework: "Xem lại video bài giảng \"Supermarket Vocab\" & viết 3 câu",
    notes: null
  },
  {
    id: "5",
    studentCode: "HS-20260710",
    sessionName: "Buổi 11: Ôn tập giữa kỳ - Grammar & Speaking Review",
    date: "2026-07-03",
    timeSlot: "17:30:00 - 19:00:00",
    attitude: "Tốt",
    prevGrammarHW: "95%",
    prevSpeakingHW: "95% - Tốt",
    teacherComment: "Con nắm chắc kiến thức ngữ pháp giữa kỳ, sẵn sàng cho bài thi nói tuần sau.",
    nextLectureLabel: "🎬 Bài giảng Midterm Grammar & Speaking Review",
    nextLectureLink: "https://elearning.cse.edu.vn/lecture/midterm-review",
    nextHomework: "Chuẩn bị cho bài thi nói giữa kỳ tuần sau",
    notes: null
  }
];

export default function DailyLearningProgressTab({ studentName, studentCode }: DailyLearningProgressTabProps) {
  const [selectedSessionId, setSelectedSessionId] = useState<string>("ALL");
  const logs = MOCK_LOGS;
  const filteredLogs = logs.filter((log) => selectedSessionId === "ALL" || log.id === selectedSessionId);
  const displayCode = studentCode || logs[0]?.studentCode || "";

  const getAttitudeStyle = (attitude: AttitudeType) => {
    switch (attitude) {
      case "Tốt":
        return "bg-emerald-50 text-emerald-800 border-emerald-300";
      case "Khá":
        return "bg-teal-50 text-teal-800 border-teal-300";
      case "TB Khá":
        return "bg-sky-50 text-sky-800 border-sky-300";
      case "TB":
        return "bg-amber-50 text-amber-800 border-amber-300";
      case "Yếu":
        return "bg-orange-50 text-orange-800 border-orange-300";
      case "Kém":
        return "bg-rose-50 text-rose-800 border-rose-300";
      default:
        return "bg-slate-50 text-slate-800 border-slate-300";
    }
  };

  return (
    <div className="space-y-6">
      {/* Header Bar */}
      <div className="bg-slate-50/80 p-5 rounded-2xl border border-line/80 flex flex-col md:flex-row md:items-center justify-between gap-4 shadow-sm">
        <div>
          <div className="flex items-center gap-2">
            <h2 className="text-lg md:text-xl font-black text-ink font-display">Nhận xét hàng ngày theo buổi học</h2>
            <span className="px-2.5 py-0.5 rounded-full bg-teal/10 text-teal text-xs font-bold border border-teal/20">UC-21</span>
          </div>
          <p className="text-xs text-muted font-bold mt-1">
            Quá trình rèn luyện &amp; Đánh giá phản xạ ngôn ngữ của học viên{" "}
            <span className="text-teal font-extrabold">
              {studentName} ({displayCode})
            </span>
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          <div className="relative">
            <select
              value={selectedSessionId}
              onChange={(e) => setSelectedSessionId(e.target.value)}
              className="appearance-none bg-white border border-line rounded-xl px-3.5 py-2 pr-8 text-xs font-bold text-ink focus:outline-none focus:ring-2 focus:ring-teal/50 shadow-sm cursor-pointer"
            >
              <option value="ALL">🗓️ Tất cả các buổi học ({logs.length})</option>
              {logs.map((log) => (
                <option key={log.id} value={log.id}>
                  {log.date} ({log.timeSlot}) - {log.sessionName.split(":")[0]}
                </option>
              ))}
            </select>
            <Calendar size={14} className="absolute right-2.5 top-2.5 text-muted pointer-events-none" />
          </div>

          <button
            onClick={() => alert("Đã xuất báo cáo quá trình học tập ra file Excel (.xlsx) thành công!")}
            className="flex items-center gap-1.5 px-3 py-2 bg-white hover:bg-slate-100 border border-line rounded-xl text-xs font-bold text-ink transition-colors shadow-sm cursor-pointer"
            title="Tải báo cáo nhật ký học tập"
          >
            <Download size={14} className="text-teal" />
            <span className="hidden sm:inline">Tải Excel (.xlsx)</span>
          </button>
        </div>
      </div>

      {/* Summary KPI Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3">
        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-teal/10 text-teal flex items-center justify-center shrink-0">
            <TrendingUp size={20} />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Thái độ chung</p>
            <p className="text-sm font-black text-ink">Đạt Loại Tốt (95%)</p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center shrink-0">
            <ShieldCheck size={20} />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Tỷ lệ hoàn thành BTVN</p>
            <p className="text-sm font-black text-emerald-600">92% Trung Bình</p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center shrink-0">
            <Award size={20} />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Buổi học ghi nhận</p>
            <p className="text-sm font-black text-ink">{logs.length} Buổi gần nhất</p>
          </div>
        </div>

        <div className="p-4 bg-sky-2 rounded-2xl border border-line/60 flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-purple-50 text-purple-600 flex items-center justify-center shrink-0">
            <Sparkles size={20} />
          </div>
          <div>
            <p className="text-[10px] text-muted font-extrabold uppercase">Tình trạng chuyên cần</p>
            <p className="text-sm font-black text-purple-600">Đạt Chuẩn (100%)</p>
          </div>
        </div>
      </div>

      {/* Main Table */}
      <div className="bg-white border border-line rounded-2xl shadow-sm overflow-hidden">
        <div className="p-4 border-b border-line bg-slate-50/50 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-3">
          <div className="flex items-center gap-2">
            <MessageSquareText size={18} className="text-teal" />
            <h3 className="text-sm font-black text-ink font-display uppercase tracking-wider">
              Bảng theo dõi nhật ký học tập học viên {studentName}
            </h3>
          </div>
          <div className="text-xs font-bold text-muted">
            Hiển thị <span className="text-teal font-black">{filteredLogs.length}</span> nhật ký buổi học
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-slate-100/80 border-b border-line text-[11px] font-black uppercase text-[#6e7c93] tracking-wider whitespace-nowrap">
                <th className="p-3.5 pl-4">Mã ID</th>
                <th className="p-3.5">Buổi Học &amp; Thời Gian</th>
                <th className="p-3.5 min-w-[120px]">Thái Độ Học Tập</th>
                <th className="p-3.5 min-w-[180px]">BTVN Ngữ Pháp Buổi Trước</th>
                <th className="p-3.5 min-w-[180px]">BTVN Nghe-Nói Buổi Trước</th>
                <th className="p-3.5 min-w-[320px]">Nhận Xét Học Sinh</th>
                <th className="p-3.5 min-w-[280px]">BTVN Buổi Sau (Link &amp; Bài Tập)</th>
                <th className="p-3.5 pr-4">Ghi Chú</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/60 text-xs font-semibold text-ink">
              {filteredLogs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-50/80 transition-colors align-top">
                  {/* Mã ID */}
                  <td className="p-3.5 pl-4 font-mono font-bold text-slate-700 whitespace-nowrap pt-4">{log.studentCode}</td>

                  {/* Buổi Học & Thời Gian */}
                  <td className="p-3.5 pt-4">
                    <div className="font-bold text-teal-deep text-xs leading-tight min-w-[140px]">{log.sessionName}</div>
                    <div className="text-[10px] text-muted font-mono flex items-center gap-1 mt-1">
                      <Clock size={10} /> {log.date} ({log.timeSlot})
                    </div>
                  </td>

                  {/* Thái Độ Học Tập — kiểu dáng dropdown nhưng KHÔNG chỉnh sửa được (học sinh chỉ xem) */}
                  <td className="p-3.5 pt-3">
                    <div
                      className={`w-full flex items-center justify-between gap-1.5 px-2.5 py-1.5 rounded-xl border text-xs font-black shadow-sm select-none ${getAttitudeStyle(log.attitude)}`}
                    >
                      <span>{log.attitude}</span>
                      <ChevronDown size={13} className="opacity-50" />
                    </div>
                  </td>

                  {/* BTVN Ngữ Pháp Buổi Trước */}
                  <td className="p-3.5 pt-3">
                    <div className="w-full bg-slate-50 border border-line rounded-xl px-2.5 py-1.5 text-xs font-bold text-ink">{log.prevGrammarHW || "—"}</div>
                  </td>

                  {/* BTVN Nghe-Nói Buổi Trước */}
                  <td className="p-3.5 pt-3">
                    <div className="w-full bg-purple-50/50 border border-purple-200 rounded-xl px-2.5 py-1.5 text-xs font-bold text-purple-900">
                      {log.prevSpeakingHW || "—"}
                    </div>
                  </td>

                  {/* Nhận Xét Học Sinh */}
                  <td className="p-3.5 pt-3">
                    <div className="p-2.5 bg-slate-50 rounded-xl border border-line/60 text-xs text-ink/90 leading-relaxed font-sans italic">"{log.teacherComment}"</div>
                  </td>

                  {/* BTVN Buổi Sau (Link & Bài Tập gộp 1 cột) */}
                  <td className="p-3.5 pt-3">
                    <div className="p-3 bg-slate-50/80 rounded-2xl border border-line/80 space-y-2.5 shadow-2xs">
                      <div className="space-y-1">
                        <div className="flex items-center gap-1 text-[10px] font-black text-blue-700 uppercase tracking-wider">
                          <Video size={11} className="text-blue-600" /> Link bài giảng buổi sau
                        </div>
                        <div className="w-full bg-blue-50/70 border border-blue-200 rounded-xl px-2 py-1 text-xs font-bold text-blue-900">{log.nextLectureLabel}</div>
                        <a
                          href={log.nextLectureLink}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="inline-flex items-center gap-1 text-[11px] font-extrabold text-blue-600 hover:text-blue-800 hover:underline pt-0.5"
                        >
                          <ExternalLink size={11} /> Mở link video bài giảng
                        </a>
                      </div>

                      <hr className="border-line/60 my-1" />

                      <div className="space-y-1">
                        <div className="flex items-center gap-1 text-[10px] font-black text-amber-800 uppercase tracking-wider">
                          <FileCheck size={11} className="text-amber-600" /> Bài tập buổi sau
                        </div>
                        <div className="w-full bg-amber-50/80 border border-amber-200 rounded-xl px-2 py-1 text-xs font-bold text-amber-950">{log.nextHomework}</div>
                      </div>
                    </div>
                  </td>

                  {/* Ghi Chú */}
                  <td className="p-3.5 pr-4 whitespace-nowrap pt-4">
                    {log.notes ? (
                      <span className="text-[11px] font-bold text-teal bg-teal/10 px-2 py-1 rounded-md border border-teal/20 inline-block">{log.notes}</span>
                    ) : (
                      <span className="text-muted text-[11px] italic">--</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
