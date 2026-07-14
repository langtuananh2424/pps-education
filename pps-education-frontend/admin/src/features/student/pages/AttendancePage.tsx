import React, { useEffect, useState } from "react";
import { Save, UserCheck } from "lucide-react";
import { mockClassrooms } from "@/data/mockData";
import { readStoredStudents, writeStoredStudents } from "../storage";
import NotificationBanner from "../components/NotificationBanner";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";

export default function AttendancePage() {
  const [students, setStudents] = useState(readStoredStudents);
  const classrooms = mockClassrooms;
  const [selectedClassId, setSelectedClassId] = useState(classrooms[0]?.id || "CLS-01");
  const [attendanceMode, setAttendanceMode] = useState<"SESSION_LEVEL" | "PERIOD_LEVEL">("SESSION_LEVEL");
  const [rollCallState, setRollCallState] = useState<Record<string, "PRESENT" | "ABSENT" | "LATE">>({});
  const [notification, setNotification] = useState<string | null>(null);

  useEffect(() => writeStoredStudents(students), [students]);

  useEffect(() => {
    const classStudents = students.filter((s) => s.classIds.includes(selectedClassId));
    const initial: Record<string, "PRESENT" | "ABSENT" | "LATE"> = {};
    classStudents.forEach((s) => {
      initial[s.id] = "PRESENT";
    });
    setRollCallState(initial);
  }, [selectedClassId, students]);

  const classStudents = students.filter((s) => s.classIds.includes(selectedClassId));

  const handleSaveAttendance = () => {
    const absentStudents = classStudents.filter((s) => rollCallState[s.id] === "ABSENT");
    const lateStudents = classStudents.filter((s) => rollCallState[s.id] === "LATE");

    let message = "🔔 BÁO CÁO CHUYÊN CẦN:\n- Hệ thống đã lưu điểm danh lớp học trực tuyến.\n";
    if (absentStudents.length > 0) {
      message += `- ĐÃ TỰ ĐỘNG GỬI thông báo khẩn qua SMS & Zalo tới phụ huynh học sinh vắng mặt: ${absentStudents.map((s) => s.fullName).join(", ")}.\n`;
    }
    if (lateStudents.length > 0) {
      message += `- Đã gửi tin nhắn đi muộn tới phụ huynh các em: ${lateStudents.map((s) => s.fullName).join(", ")}.\n`;
    }
    if (absentStudents.length === 0 && lateStudents.length === 0) {
      message = "✅ Điểm danh thành công! Toàn bộ học sinh trong lớp đã có mặt đầy đủ.";
    }
    setNotification(message);

    const timeString = new Date().toISOString().substring(0, 10);
    setStudents((prev) =>
      prev.map((s) => {
        if (!s.classIds.includes(selectedClassId)) return s;
        const stat = rollCallState[s.id] || "PRESENT";
        const statusText = stat === "ABSENT" ? "Vắng học (Tự động gửi tin báo PH)" : stat === "LATE" ? "Đi muộn" : "Có mặt";
        return { ...s, history: [...s.history, `${timeString}: Điểm danh chuyên cần lớp [${classrooms.find((c) => c.id === selectedClassId)?.name || "Lớp"}]: ${statusText}.`] };
      })
    );

    setTimeout(() => setNotification(null), 6000);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Điểm Danh Chuyên Cần (SMS)</h1>
        <p className="text-xs text-slate-500 mt-1">Giảng viên lưu chuyên cần, vắng học tự động cảnh báo phụ huynh (UC-15).</p>
      </div>

      <NotificationBanner message={notification} onClose={() => setNotification(null)} />

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-slate-50">
            <div>
              <span className="text-xs font-bold text-slate-700 font-display">Điểm danh Chuyên cần đầu giờ (UC-15)</span>
              <p className="text-[10px] text-slate-400 mt-0.5">Giảng viên lưu chuyên cần, vắng học tự động cảnh báo phụ huynh.</p>
            </div>

            <select
              value={attendanceMode}
              onChange={(e) => setAttendanceMode(e.target.value as "SESSION_LEVEL" | "PERIOD_LEVEL")}
              className="bg-white border text-[10px] font-bold text-slate-700 px-2 py-1 rounded focus:outline-none"
            >
              <option value="SESSION_LEVEL">Mức cả Buổi (SESSION)</option>
              <option value="PERIOD_LEVEL">Mức từng Tiết (PERIOD)</option>
            </select>
          </div>

          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Mã ID</Th>
                <Th>Họ và tên</Th>
                <Th className="text-center">Có mặt</Th>
                <Th className="text-center">Vắng mặt (ABSENT)</Th>
                <Th className="text-center">Đi trễ (LATE)</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {classStudents.length === 0 ? (
                <tr>
                  <td colSpan={5} className="px-6 py-12 text-center text-xs text-slate-400 italic">
                    Không tìm thấy học sinh nào thuộc lớp học này.
                  </td>
                </tr>
              ) : (
                classStudents.map((stud) => (
                  <tr key={stud.id} className="hover:bg-slate-50/40 transition-colors">
                    <Td className="font-mono font-bold text-slate-500">{stud.id}</Td>
                    <Td className="font-bold text-slate-900">{stud.fullName}</Td>
                    <Td className="text-center">
                      <input
                        type="radio"
                        name={`att-${stud.id}`}
                        checked={rollCallState[stud.id] === "PRESENT" || rollCallState[stud.id] === undefined}
                        onChange={() => setRollCallState((prev) => ({ ...prev, [stud.id]: "PRESENT" }))}
                        className="h-4 w-4 text-emerald-600 focus:ring-emerald-500 border-slate-300"
                      />
                    </Td>
                    <Td className="text-center">
                      <input
                        type="radio"
                        name={`att-${stud.id}`}
                        checked={rollCallState[stud.id] === "ABSENT"}
                        onChange={() => setRollCallState((prev) => ({ ...prev, [stud.id]: "ABSENT" }))}
                        className="h-4 w-4 text-rose-600 focus:ring-rose-500 border-slate-300"
                      />
                    </Td>
                    <Td className="text-center">
                      <input
                        type="radio"
                        name={`att-${stud.id}`}
                        checked={rollCallState[stud.id] === "LATE"}
                        onChange={() => setRollCallState((prev) => ({ ...prev, [stud.id]: "LATE" }))}
                        className="h-4 w-4 text-amber-500 focus:ring-amber-500 border-slate-300"
                      />
                    </Td>
                  </tr>
                ))
              )}
            </tbody>
          </TableContainer>

          <div className="px-6 py-4 bg-slate-50 border-t flex justify-end">
            <button
              onClick={handleSaveAttendance}
              disabled={classStudents.length === 0}
              className="bg-brand-orange hover:bg-brand-orange/90 text-white font-semibold text-xs px-4 py-2 rounded-lg flex items-center gap-1.5 shadow-soft transition-all disabled:opacity-50"
            >
              <Save className="w-4 h-4 text-white" />
              <span>Xác nhận & Lưu điểm danh (UC-15)</span>
            </button>
          </div>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft space-y-4 self-start">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2">Chọn lớp điểm danh</h3>
          <div className="space-y-2">
            {classrooms.map((cls) => (
              <button
                key={cls.id}
                onClick={() => setSelectedClassId(cls.id)}
                className={`w-full p-3 rounded-lg text-left text-xs font-semibold flex items-center justify-between transition-all border ${
                  selectedClassId === cls.id ? "bg-brand-orange border-brand-orange text-white shadow-sm" : "bg-slate-50 hover:bg-slate-100/60 border-slate-100 text-slate-600"
                }`}
              >
                <div>
                  <span className={`font-bold block ${selectedClassId === cls.id ? "text-white" : "text-slate-800"}`}>{cls.name}</span>
                  <span className="text-[10px] text-slate-400 block font-normal mt-0.5">{cls.schedule}</span>
                </div>
                <UserCheck className={`w-4 h-4 shrink-0 ${selectedClassId === cls.id ? "text-white" : "text-brand-orange"}`} />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
