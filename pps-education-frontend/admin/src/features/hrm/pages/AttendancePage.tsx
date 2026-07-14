import React, { useState } from "react";
import { Fingerprint, Play } from "lucide-react";
import { AttendanceLog } from "@/types";
import { mockAttendanceLogs } from "@/data/mockData";
import { readStoredEmployees } from "../storage";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import Badge from "@/components/ui/Badge";

export default function AttendancePage() {
  const [employees] = useState(readStoredEmployees);
  const [attendanceLogs, setAttendanceLogs] = useState<AttendanceLog[]>(mockAttendanceLogs);

  const handleSimulatePunch = (employeeId: string, method: AttendanceLog["method"]) => {
    const employee = employees.find((e) => e.id === employeeId);
    if (!employee) return;

    const newLog: AttendanceLog = {
      id: `ATT-${Date.now().toString().substring(8)}`,
      employeeId,
      employeeName: employee.fullName,
      time: new Date().toISOString().replace("T", " ").substring(0, 16),
      method,
      campusId: employee.campusIds[0] || "CAMP-01",
      status: "SUCCESS"
    };

    setAttendanceLogs((prev) => [newLog, ...prev]);
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Phân hệ Quản lý Nhân sự & Tiền lương (HRM)</h1>
        <p className="text-xs text-slate-500 mt-1">Dữ liệu chấm công thực tế đa phương thức (vân tay, khuôn mặt, GPS) (UC-09).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white rounded-xl border border-slate-200 shadow-soft overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 flex items-center justify-between bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Nhật ký Chấm công Thực tế</span>
            <Badge variant="success">Kết nối Máy quét trực tuyến</Badge>
          </div>
          <TableContainer className="rounded-none border-0">
            <thead>
              <tr>
                <Th>Nhân viên</Th>
                <Th>Thời gian</Th>
                <Th>Phương thức</Th>
                <Th>Điểm quét</Th>
                <Th className="text-center">Trạng thái</Th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {attendanceLogs.map((log) => (
                <tr key={log.id} className="hover:bg-slate-50/40 transition-colors">
                  <Td className="font-semibold text-slate-800">{log.employeeName}</Td>
                  <Td className="font-mono">{log.time}</Td>
                  <Td>
                    <span className="bg-slate-100 text-slate-600 font-bold text-[9px] px-2 py-0.5 rounded">{log.method}</span>
                  </Td>
                  <Td>{log.campusId === "CAMP-01" ? "Hai Bà Trưng" : "Cầu Giấy"}</Td>
                  <Td className="text-center">
                    <Badge variant="success">Hợp lệ</Badge>
                  </Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        </div>

        <div className="bg-white p-5 rounded-xl border border-slate-200 shadow-soft">
          <h3 className="text-xs font-bold text-slate-400 block uppercase tracking-wider font-display border-b border-slate-100 pb-2 mb-4">
            Giả lập chấm công đa phương thức (UC-09)
          </h3>
          <p className="text-xs text-slate-500 mb-4">
            Chọn nhân viên và hình thức quét vân tay, nhận dạng khuôn mặt hoặc định vị GPS để lưu log chấm công tức thì.
          </p>

          <div className="space-y-3">
            {employees.slice(3, 7).map((emp) => (
              <div key={emp.id} className="p-3.5 bg-slate-50 rounded-lg border border-slate-100 flex items-center justify-between gap-2">
                <div>
                  <span className="text-xs font-bold text-slate-800 block">{emp.fullName}</span>
                  <span className="text-[10px] text-slate-400 block">{emp.role}</span>
                </div>

                <div className="flex gap-1.5">
                  <button onClick={() => handleSimulatePunch(emp.id, "FINGERPRINT")} className="bg-slate-900 hover:bg-slate-800 text-white p-1.5 rounded text-xs" title="Quét vân tay cơ sở">
                    <Fingerprint className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={() => handleSimulatePunch(emp.id, "GPS")} className="bg-brand-gradient text-white p-1.5 rounded text-xs" title="Chấm công định vị GPS">
                    <Play className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
