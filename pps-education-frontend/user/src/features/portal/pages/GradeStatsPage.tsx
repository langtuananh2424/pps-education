import React, { useEffect, useMemo, useState } from "react";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer, LineChart, Line } from "recharts";
import { Award } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import {
  GradeComponentSetupResponse,
  GradeEvaluationResultResponse,
  getMyEvaluationResult,
  getEvaluationResult,
  listGradeComponentSetups
} from "../api";

interface GradeStatsPageProps {
  studentId?: number;
  classId: number;
}

function parseTermInfo(academicTermName: string): { termNumber: string; year: string } {
  const match = academicTermName.match(/Học kỳ (\d+) \((\d{4}-\d{4})\)/);
  if (match) {
    return { termNumber: match[1], year: match[2] };
  }
  return { termNumber: "", year: "" };
}

export default function GradeStatsPage({ studentId, classId }: GradeStatsPageProps) {
  const [setupResults, setSetupResults] = useState<{ setup: GradeComponentSetupResponse; result: GradeEvaluationResultResponse }[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [chartType, setChartType] = useState<"bar" | "line">("bar");

  useEffect(() => {
    setLoading(true);
    setError(null);
    const fetchEvaluationResult = (s: GradeComponentSetupResponse) =>
      studentId != null
        ? getEvaluationResult(studentId, classId, s.academicTermId, s.evaluationType)
        : getMyEvaluationResult(classId, s.academicTermId, s.evaluationType);

    listGradeComponentSetups(classId)
      .then((setupList) =>
        Promise.all(
          setupList.map((s) =>
            fetchEvaluationResult(s)
              .then((result) => ({ setup: s, result }))
              .catch((err) => {
                if (err instanceof ApiError && err.status === 404) return null;
                throw err;
              })
          )
        )
      )
      .then((rows) => {
        const filtered = rows.filter((r): r is { setup: GradeComponentSetupResponse; result: GradeEvaluationResultResponse } => r !== null);
        setSetupResults(filtered);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không tải được dữ liệu thống kê."))
      .finally(() => setLoading(false));
  }, [studentId, classId]);

  const chartData = useMemo(() => {
    return setupResults
      .map((sr) => {
        const { termNumber, year } = parseTermInfo(sr.setup.academicTermName);
        const evaluationLabel = sr.setup.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ";
        return {
          name: `HK${termNumber} (${year}) — ${evaluationLabel}`,
          overall: sr.result.overallScore ? parseFloat(sr.result.overallScore.toString()) : null,
          level: sr.result.level || "N/A"
        };
      })
      .filter((d) => d.overall !== null);
  }, [setupResults]);

  if (loading) return <p className="text-sm text-muted font-bold">Đang tải...</p>;

  if (error) {
    return (
      <div className="text-xs font-bold text-rose-600 bg-rose-50 border border-rose-100 p-3 rounded-xl">
        {error}
      </div>
    );
  }

  if (setupResults.length === 0) {
    return (
      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)]">
        <p className="text-xs text-muted font-bold italic">Chưa có điểm nào để thống kê.</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)] space-y-5">
        <div className="flex items-center justify-between">
          <h2 className="text-xl font-extrabold text-ink flex items-center gap-2">
            <Award className="text-teal" /> Thống kê điểm qua từng kỳ
          </h2>
          <div className="flex gap-2">
            <button
              onClick={() => setChartType("bar")}
              className={`text-sm font-bold px-3 py-1.5 rounded-lg transition ${
                chartType === "bar"
                  ? "bg-teal text-white"
                  : "text-teal-deep hover:bg-teal/5"
              }`}
            >
              Biểu đồ cột
            </button>
            <button
              onClick={() => setChartType("line")}
              className={`text-sm font-bold px-3 py-1.5 rounded-lg transition ${
                chartType === "line"
                  ? "bg-teal text-white"
                  : "text-teal-deep hover:bg-teal/5"
              }`}
            >
              Biểu đồ đoạn
            </button>
          </div>
        </div>

        {chartData.length > 0 ? (
          <div className="w-full h-[400px]">
            <ResponsiveContainer width="100%" height="100%">
              {chartType === "bar" ? (
                <BarChart data={chartData} margin={{ top: 20, right: 30, left: 0, bottom: 60 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(204,212,220,0.5)" />
                  <XAxis
                    dataKey="name"
                    angle={-45}
                    textAnchor="end"
                    height={80}
                    tick={{ fontSize: 12 }}
                  />
                  <YAxis
                    domain={[0, 100]}
                    tick={{ fontSize: 12 }}
                    label={{ value: "Điểm Overall (0-100)", angle: -90, position: "insideLeft" }}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "rgba(255,255,255,0.95)",
                      border: "1px solid rgba(204,212,220,1)",
                      borderRadius: "8px",
                      padding: "8px"
                    }}
                    formatter={(value, name) => {
                      if (name === "overall") return [`${typeof value === "number" ? value.toFixed(2) : value}`, "Overall"];
                      return [value, name];
                    }}
                    labelStyle={{ color: "#1e2a45", fontWeight: "600" }}
                  />
                  <Legend wrapperStyle={{ fontSize: "12px" }} />
                  <Bar dataKey="overall" fill="#0ea5a5" radius={[8, 8, 0, 0]} />
                </BarChart>
              ) : (
                <LineChart data={chartData} margin={{ top: 20, right: 30, left: 0, bottom: 60 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(204,212,220,0.5)" />
                  <XAxis
                    dataKey="name"
                    angle={-45}
                    textAnchor="end"
                    height={80}
                    tick={{ fontSize: 12 }}
                  />
                  <YAxis
                    domain={[0, 100]}
                    tick={{ fontSize: 12 }}
                    label={{ value: "Điểm Overall (0-100)", angle: -90, position: "insideLeft" }}
                  />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: "rgba(255,255,255,0.95)",
                      border: "1px solid rgba(204,212,220,1)",
                      borderRadius: "8px",
                      padding: "8px"
                    }}
                    formatter={(value, name) => {
                      if (name === "overall") return [`${typeof value === "number" ? value.toFixed(2) : value}`, "Overall"];
                      return [value, name];
                    }}
                    labelStyle={{ color: "#1e2a45", fontWeight: "600" }}
                  />
                  <Legend wrapperStyle={{ fontSize: "12px" }} />
                  <Line
                    type="monotone"
                    dataKey="overall"
                    stroke="#0ea5a5"
                    dot={{ fill: "#0ea5a5", r: 4 }}
                    activeDot={{ r: 6 }}
                    strokeWidth={2}
                  />
                </LineChart>
              )}
            </ResponsiveContainer>
          </div>
        ) : (
          <p className="text-xs text-muted font-bold italic text-center py-8">
            Không có dữ liệu để vẽ biểu đồ.
          </p>
        )}
      </div>

      {/* Bảng thống kê chi tiết */}
      <div className="bg-white border border-line/80 p-6 rounded-[20px] shadow-[0_8px_30px_rgba(30,42,69,0.03)]">
        <h3 className="text-sm font-extrabold text-ink mb-4">Chi tiết từng kỳ</h3>
        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse">
            <thead>
              <tr className="bg-slate-100/80 border-b border-line text-sm font-black uppercase text-[#6e7c93] tracking-wider">
                <th className="p-3 pl-4">Kỳ học</th>
                <th className="p-3 text-center">Overall</th>
                <th className="p-3 text-center">Level</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/60 text-sm font-bold text-ink">
              {setupResults.map((sr) => {
                const { termNumber, year } = parseTermInfo(sr.setup.academicTermName);
                const evaluationLabel = sr.setup.evaluationType === "MID_TERM" ? "Giữa kỳ" : "Cuối kỳ";
                return (
                  <tr key={sr.setup.id} className="hover:bg-slate-50/80">
                    <td className="p-3 pl-4 font-extrabold">
                      HK{termNumber} ({year}) — {evaluationLabel}
                    </td>
                    <td className="p-3 text-center text-[17px] font-extrabold text-teal-deep">
                      {sr.result.overallScore ?? "—"}
                    </td>
                    <td className="p-3 text-center">
                      {sr.result.level ? (
                        <span className="text-[22px] font-black text-teal-deep">{sr.result.level}</span>
                      ) : (
                        <span className="text-slate-300">—</span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
