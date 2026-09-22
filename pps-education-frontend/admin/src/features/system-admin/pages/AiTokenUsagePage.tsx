import React, { useCallback, useEffect, useMemo, useState } from "react";
import { Activity, Coins, Layers, Radio, RefreshCw, Sparkles } from "lucide-react";
import { ApiError } from "@/lib/apiClient";
import TableContainer, { Td, Th } from "@/components/ui/TableContainer";
import StatCard from "@/components/ui/StatCard";
import Button from "@/components/ui/Button";
import Badge from "@/components/ui/Badge";
import Card from "@/components/ui/Card";
import Tabs from "@/components/ui/Tabs";
import DatePicker from "@/components/ui/DatePicker";
import EmptyState from "@/components/ui/EmptyState";
import {
  AiTokenUsageEvent,
  AiTokenUsageSummary,
  getAiTokenUsageSummary,
  subscribeAiTokenUsage
} from "../aiTokenUsageApi";

/**
 * Quản trị hệ thống → Sử dụng token AI (V192, bổ sung ngoài SDD gốc, đã xác nhận với người dùng
 * 2026-09-22). Xem chi phí token của luồng chấm AI theo 3 chiều: học sinh, bài tập, bước chấm.
 *
 * Trang tự cập nhật qua SSE (đã xác nhận với người dùng: KHÔNG bắn webhook ra dịch vụ ngoài, dữ liệu học
 * sinh không rời hệ thống). Sự kiện SSE chỉ cộng vào ô "Lượt chấm gần đây" và con số chạy — bảng tổng
 * hợp vẫn lấy từ API để không tự cộng sai khi mất kết nối giữa chừng.
 */

const TODAY_ISO = new Date().toISOString().slice(0, 10);
const THIRTY_DAYS_AGO_ISO = new Date(Date.now() - 30 * 24 * 3600 * 1000).toISOString().slice(0, 10);

/** Nhãn tiếng Việt cho enum Step của backend — xem AiGradingTokenUsage.Step. */
const STEP_LABELS: Record<string, string> = {
  WRITING: "Chấm bài viết",
  TRANSCRIPTION: "Phiên âm (mù)",
  SPEAKING: "Chấm bài nói",
  CORRECTED_ANSWER: "Sinh câu sửa mẫu",
  ESSAY: "Chấm Writing (UC-40/41)",
  OTHER: "Không rõ ngữ cảnh"
};

function stepLabel(step: string): string {
  return STEP_LABELS[step] ?? step;
}

function formatNumber(value: number): string {
  return value.toLocaleString("vi-VN");
}

/** Token tính tiền = input + output + thinking. Token cache đã nằm trong input nên KHÔNG cộng lại. */
function billableTokens(row: { promptTokens: number; completionTokens: number; reasoningTokens: number }): number {
  return row.promptTokens + row.completionTokens + row.reasoningTokens;
}

type TabId = "step" | "student" | "assignment";

export default function AiTokenUsagePage() {
  const [from, setFrom] = useState(THIRTY_DAYS_AGO_ISO);
  const [to, setTo] = useState(TODAY_ISO);
  const [summary, setSummary] = useState<AiTokenUsageSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<TabId>("step");
  const [liveEvents, setLiveEvents] = useState<AiTokenUsageEvent[]>([]);
  const [liveConnected, setLiveConnected] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSummary(await getAiTokenUsageSummary(from, to));
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Không tải được số liệu sử dụng token.");
    } finally {
      setLoading(false);
    }
  }, [from, to]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    setLiveConnected(true);
    const unsubscribe = subscribeAiTokenUsage((event) => {
      // Giữ tối đa 20 lượt gần nhất — đây là ô "đang chạy", không phải nhật ký đầy đủ (nhật ký nằm ở DB).
      setLiveEvents((prev) => [event, ...prev].slice(0, 20));
    });
    return () => {
      setLiveConnected(false);
      unsubscribe();
    };
  }, []);

  const totals = summary?.totals;
  const cacheHitPercent = useMemo(() => {
    if (!totals || totals.promptTokens === 0) return 0;
    return Math.round((totals.cachedTokens / totals.promptTokens) * 100);
  }, [totals]);

  const tabItems = [
    { id: "step", label: "Theo bước chấm" },
    { id: "student", label: "Theo học sinh" },
    { id: "assignment", label: "Theo bài tập" }
  ];

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Sử dụng token AI</h1>
          <p className="text-sm text-slate-500">
            Chi phí token của luồng chấm AI (Video phản xạ, chấm Writing) theo học sinh, bài tập và từng bước chấm.
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <DatePicker value={from} onChange={setFrom} max={to} className="w-40" />
          <span className="text-slate-400">→</span>
          <DatePicker value={to} onChange={setTo} min={from} max={TODAY_ISO} className="w-40" />
          <Button variant="secondary" onClick={() => void load()} disabled={loading}>
            <RefreshCw className={loading ? "h-4 w-4 animate-spin" : "h-4 w-4"} />
            Tải lại
          </Button>
        </div>
      </div>

      {error && (
        <Card className="border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">{error}</Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          icon={Coins}
          label="Tổng token tính tiền"
          value={totals ? formatNumber(billableTokens(totals)) : "—"}
          hint="Input + output + thinking"
        />
        <StatCard
          icon={Sparkles}
          label="Token thinking"
          value={totals ? formatNumber(totals.reasoningTokens) : "—"}
          hint="Tính giá như output, không hiện trong kết quả"
          tone="warning"
        />
        <StatCard
          icon={Layers}
          label="Tỷ lệ token được cache"
          value={totals ? `${cacheHitPercent}%` : "—"}
          hint={cacheHitPercent === 0 ? "Chưa ghi nhận cache hit nào" : "Phần input được tính giá rẻ"}
          tone={cacheHitPercent === 0 ? "danger" : "brand"}
        />
        <StatCard
          icon={Activity}
          label="Lượt gọi AI"
          value={totals ? formatNumber(totals.callCount) : "—"}
          hint={totals && totals.rejectedCalls > 0 ? `${formatNumber(totals.rejectedCalls)} lượt bị loại vẫn tính tiền` : undefined}
          tone={totals && totals.rejectedCalls > 0 ? "warning" : "slate"}
        />
      </div>

      {totals && totals.unattributedCalls > 0 && (
        <Card className="border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
          Có {formatNumber(totals.unattributedCalls)} lượt gọi không gắn được học sinh (chấm Writing UC-40/41 và
          các lượt bị loại kết quả). Vì vậy tổng ở tab <strong>Theo học sinh</strong> nhỏ hơn tổng chung — đây là
          chênh lệch có lý do, không phải thiếu dữ liệu.
        </Card>
      )}

      <Card className="p-0">
        <div className="flex items-center justify-between border-b border-slate-100 px-4 py-3">
          <Tabs items={tabItems} activeId={tab} onChange={(id) => setTab(id as TabId)} />
          <Badge variant={liveConnected ? "success" : "neutral"}>
            <Radio className="mr-1 inline h-3 w-3" />
            {liveConnected ? "Đang nhận trực tiếp" : "Ngoại tuyến"}
          </Badge>
        </div>

        {loading && !summary ? (
          <div className="p-8 text-center text-sm text-slate-500">Đang tải…</div>
        ) : (
          <div className="p-4">
            {tab === "step" && <StepTable rows={summary?.byStep ?? []} />}
            {tab === "student" && <StudentTable rows={summary?.byStudent ?? []} />}
            {tab === "assignment" && <AssignmentTable rows={summary?.byAssignment ?? []} />}
          </div>
        )}
      </Card>

      <Card className="p-4">
        <h2 className="mb-3 text-sm font-semibold text-slate-900">Lượt chấm gần đây (trực tiếp)</h2>
        {liveEvents.length === 0 ? (
          <p className="text-sm text-slate-500">
            Chưa có lượt chấm nào kể từ khi mở trang. Số liệu tổng hợp phía trên vẫn là dữ liệu đầy đủ trong khoảng ngày đã chọn.
          </p>
        ) : (
          <TableContainer>
            <thead>
              <tr>
                <Th>Thời điểm</Th>
                <Th>Bước chấm</Th>
                <Th>Học sinh</Th>
                <Th className="text-right">Input</Th>
                <Th className="text-right">Output</Th>
                <Th className="text-right">Thinking</Th>
                <Th className="text-right">Thời gian chờ</Th>
              </tr>
            </thead>
            <tbody>
              {liveEvents.map((event, index) => (
                <tr key={`${event.at}-${index}`}>
                  <Td>{new Date(event.at).toLocaleTimeString("vi-VN")}</Td>
                  <Td>{stepLabel(event.step)}</Td>
                  <Td>{event.studentName ?? <span className="text-slate-400">—</span>}</Td>
                  <Td className="text-right tabular-nums">{formatNumber(event.promptTokens)}</Td>
                  <Td className="text-right tabular-nums">{formatNumber(event.completionTokens)}</Td>
                  <Td className="text-right tabular-nums">{formatNumber(event.reasoningTokens)}</Td>
                  <Td className="text-right tabular-nums">{(event.elapsedMs / 1000).toFixed(1)}s</Td>
                </tr>
              ))}
            </tbody>
          </TableContainer>
        )}
      </Card>
    </div>
  );
}

function StepTable({ rows }: { rows: AiTokenUsageSummary["byStep"] }) {
  if (rows.length === 0) return <EmptyState icon={Layers} title="Chưa có dữ liệu" description="Không có lượt chấm AI nào trong khoảng ngày đã chọn." />;
  return (
    <TableContainer>
      <thead>
        <tr>
          <Th>Bước chấm</Th>
          <Th className="text-right">Lượt gọi</Th>
          <Th className="text-right">Input</Th>
          <Th className="text-right">Trong đó cache</Th>
          <Th className="text-right">Output</Th>
          <Th className="text-right">Thinking</Th>
          <Th className="text-right">Tổng tính tiền</Th>
          <Th className="text-right">Chờ TB</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.step}>
            <Td>{stepLabel(row.step)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.callCount)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.promptTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.cachedTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.completionTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.reasoningTokens)}</Td>
            <Td className="text-right font-medium tabular-nums">{formatNumber(billableTokens(row))}</Td>
            <Td className="text-right tabular-nums">{(row.avgElapsedMs / 1000).toFixed(1)}s</Td>
          </tr>
        ))}
      </tbody>
    </TableContainer>
  );
}

function StudentTable({ rows }: { rows: AiTokenUsageSummary["byStudent"] }) {
  if (rows.length === 0) return <EmptyState icon={Layers} title="Chưa có dữ liệu" description="Không có lượt chấm AI nào gắn với học sinh trong khoảng ngày đã chọn." />;
  return (
    <TableContainer>
      <thead>
        <tr>
          <Th>Học sinh</Th>
          <Th className="text-right">Lượt gọi</Th>
          <Th className="text-right">Input</Th>
          <Th className="text-right">Output</Th>
          <Th className="text-right">Thinking</Th>
          <Th className="text-right">Tổng tính tiền</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.studentId}>
            <Td>{row.studentName}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.callCount)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.promptTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.completionTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.reasoningTokens)}</Td>
            <Td className="text-right font-medium tabular-nums">{formatNumber(billableTokens(row))}</Td>
          </tr>
        ))}
      </tbody>
    </TableContainer>
  );
}

function AssignmentTable({ rows }: { rows: AiTokenUsageSummary["byAssignment"] }) {
  if (rows.length === 0) return <EmptyState icon={Layers} title="Chưa có dữ liệu" description="Không có lượt chấm AI nào gắn với bài tập trong khoảng ngày đã chọn." />;
  return (
    <TableContainer>
      <thead>
        <tr>
          <Th>Bài tập (bộ video ôn tập)</Th>
          <Th className="text-right">Lượt gọi</Th>
          <Th className="text-right">Input</Th>
          <Th className="text-right">Output</Th>
          <Th className="text-right">Thinking</Th>
          <Th className="text-right">Tổng tính tiền</Th>
        </tr>
      </thead>
      <tbody>
        {rows.map((row) => (
          <tr key={row.assignmentId}>
            <Td>{row.assignmentName}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.callCount)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.promptTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.completionTokens)}</Td>
            <Td className="text-right tabular-nums">{formatNumber(row.reasoningTokens)}</Td>
            <Td className="text-right font-medium tabular-nums">{formatNumber(billableTokens(row))}</Td>
          </tr>
        ))}
      </tbody>
    </TableContainer>
  );
}
