import React, { useState } from "react";
import { HelpCircle, Send } from "lucide-react";
import { FeedbackTicket } from "@/types";
import { mockFeedbackTickets } from "@/data/mockData";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import { cn } from "@/lib/cn";

export default function FeedbackPage() {
  const [tickets, setTickets] = useState<FeedbackTicket[]>(mockFeedbackTickets);
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [replyText, setReplyText] = useState("");
  const selectedTicket = tickets.find((t) => t.id === selectedTicketId) || null;

  const handleReply = (e: React.FormEvent) => {
    e.preventDefault();
    if (!replyText || !selectedTicket) return;

    const timeString = new Date().toISOString().replace("T", " ").substring(0, 16);
    const newEntry = { text: replyText, time: timeString, author: "Trần Đức Nam (CAMPUS_MANAGER)" };

    setTickets((prev) =>
      prev.map((t) => (t.id === selectedTicket.id ? { ...t, status: "RESOLVED", resolutionText: replyText, history: [...t.history, newEntry] } : t))
    );
    setReplyText("");
    alert("Đã cập nhật câu trả lời phản hồi giải quyết kiến nghị trường liên kết thành công (UC-39)!");
  };

  return (
    <div className="space-y-6">
      <div className="border-b border-slate-200 pb-4">
        <h1 className="text-xl font-bold font-display tracking-tight text-slate-900">Quản Lý Điểm Trường, Phòng Học & Đối Tác</h1>
        <p className="text-xs text-slate-500 mt-1">Giải quyết phản hồi kiến nghị từ trường liên kết (UC-38/39).</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card padded={false} className="lg:col-span-2 overflow-hidden">
          <div className="px-5 py-4 border-b border-slate-100 bg-slate-50">
            <span className="text-xs font-bold text-slate-700 font-display">Kênh giải quyết ý kiến phản hồi đối tác (UC-39)</span>
          </div>

          <div className="divide-y divide-slate-100 max-h-[500px] overflow-y-auto">
            {tickets.map((tkt) => (
              <div
                key={tkt.id}
                onClick={() => setSelectedTicketId(tkt.id)}
                className={cn("p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4 hover:bg-slate-50/40 transition-colors cursor-pointer", selectedTicket?.id === tkt.id && "bg-slate-50")}
              >
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h4 className="text-xs font-bold text-slate-900">{tkt.campusName}</h4>
                    <Badge variant={tkt.priority === "HIGH" ? "danger" : "neutral"}>{tkt.priority}</Badge>
                  </div>
                  <span className="text-[11px] text-slate-500 font-medium block">Người phản hồi: {tkt.senderName}</span>
                  <p className="text-[11px] text-slate-500 line-clamp-2 mt-1">Nội dung: "{tkt.content}"</p>
                </div>

                <Badge variant={tkt.status === "NEW" ? "danger" : tkt.status === "IN_PROGRESS" ? "warning" : "success"} className="shrink-0 self-start sm:self-center">
                  {tkt.status === "NEW" && "Mới gửi"}
                  {tkt.status === "IN_PROGRESS" && "Đang xử lý"}
                  {tkt.status === "RESOLVED" && "Đã khắc phục"}
                </Badge>
              </div>
            ))}
          </div>
        </Card>

        <Card>
          {selectedTicket ? (
            <div className="space-y-4">
              <div className="border-b pb-2.5 flex items-center justify-between">
                <div>
                  <span className="text-[10px] font-mono font-bold text-slate-400">TICKET: {selectedTicket.id}</span>
                  <h3 className="text-xs font-bold text-slate-800 truncate max-w-[150px]">{selectedTicket.campusName}</h3>
                </div>
                <button onClick={() => setSelectedTicketId(null)} className="text-xs text-slate-400 hover:text-slate-800">
                  Đóng
                </button>
              </div>

              <div className="space-y-2">
                <span className="text-[10px] uppercase font-bold tracking-wider text-slate-400 block font-display">Lịch sử tương tác</span>
                <div className="space-y-2 bg-slate-50 p-2.5 rounded-lg border max-h-48 overflow-y-auto">
                  {selectedTicket.history.map((hist, idx) => (
                    <div key={idx} className="text-[10px] text-slate-500 border-b border-dashed pb-1.5 last:border-b-0 leading-relaxed">
                      <div className="flex justify-between font-bold text-slate-700 mb-0.5">
                        <span>{hist.author}</span>
                        <span className="font-mono text-[9px] font-normal">{hist.time}</span>
                      </div>
                      <p>{hist.text}</p>
                    </div>
                  ))}
                </div>
              </div>

              <form onSubmit={handleReply} className="space-y-3.5 pt-2">
                <div className="space-y-1">
                  <label className="text-[10px] uppercase font-bold tracking-wider text-slate-500">Phương án khắc phục (Resolution)</label>
                  <textarea
                    required
                    placeholder="Gõ chi tiết phương án giải quyết gửi lại trường liên kết..."
                    value={replyText}
                    onChange={(e) => setReplyText(e.target.value)}
                    rows={3}
                    className="w-full bg-slate-50 border border-slate-200 text-xs px-3 py-2 rounded-lg focus:outline-none"
                  />
                </div>

                <button type="submit" className="w-full bg-slate-900 hover:bg-slate-800 text-white font-semibold text-xs py-2 rounded-lg flex items-center justify-center gap-1.5 shadow-soft">
                  <Send className="w-3.5 h-3.5 text-brand-yellow shrink-0" />
                  Xác nhận Giải Quyết
                </button>
              </form>
            </div>
          ) : (
            <div className="h-64 border border-dashed rounded-xl flex flex-col items-center justify-center text-slate-400 text-xs italic gap-1.5 text-center p-4">
              <HelpCircle className="w-6 h-6 text-slate-300 animate-bounce" />
              <span>Nhấp chọn một Ticket kiến nghị đối tác từ hàng chờ để rà soát lịch sử tương tác và ghi nội dung khắc phục (UC-39).</span>
            </div>
          )}
        </Card>
      </div>
    </div>
  );
}
