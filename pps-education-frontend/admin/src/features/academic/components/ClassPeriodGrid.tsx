import { useEffect, useMemo, useRef, useState, type MouseEvent as ReactMouseEvent } from "react";
import { CalendarPlus, Pencil, Save, Trash2, Undo2, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";
import { ApiError } from "@/lib/apiClient";
import { useApp } from "@/context/AppContext";
import { useDialog } from "@/components/ui/DialogProvider";
import Button from "@/components/ui/Button";
import ContextMenu from "@/components/ui/ContextMenu";
import { assignLanes, DEFAULT_LANES, laneCountUsed } from "@/lib/timetableLanes";
import { toISODate } from "@/lib/calendarDates";
import {
  DayPart,
  dayPartLabels,
  dayPartOrder,
  listRoomsBySite,
  listSitePeriodTemplates,
  RoomResponse,
  SitePeriodTemplateResponse
} from "@/features/facility/api";
import {
  BulkCreateClassSessionRequest,
  bulkCreateClassSessions,
  cancelClassSession,
  ClassSessionResponse,
  listSessionsForSiteTimetable,
  updateSessionAssignment,
  UpdateSessionAssignmentRequest
} from "../api";
import TimetableSessionCard, { SessionPendingKind } from "./TimetableSessionCard";
import SessionInfoModal from "./SessionInfoModal";
import SessionEditModal, { SessionAssignmentPreview } from "./SessionEditModal";
import CreateSessionModal, { CreateSessionModalPrefill, QueuedCreatePayload, weekdayOf } from "./CreateSessionModal";

const HEADER_ROW_HEIGHT = 44;
const SECTION_ROW_HEIGHT = 24;
const PERIOD_ROW_HEIGHT = 96;
const PERIOD_LABEL_COLUMN_WIDTH = 80;
/** Độ rộng 1 lane (1 buổi học) trong cột ngày — cột tự giãn theo bội số này (bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21). */
const LANE_WIDTH = 170;

interface ClassPeriodGridProps {
  siteId: number;
  /** Danh sách ngày hiển thị theo cột (tăng dần) — cho phép tái dùng cả ở "Thời khóa biểu" (1 tuần cố định) lẫn "Lịch làm việc" (Ngày/Tuần tùy chọn). */
  dates: Date[];
  /** Tùy chọn — chỉ hiển thị buổi của 1 lớp cụ thể (khớp filter "Lớp" ở trang Lịch làm việc). Không truyền = hiện mọi lớp của site. */
  classId?: number;
}

interface PendingCreate {
  id: string;
  classId: number;
  className: string;
  classColor: string;
  request: BulkCreateClassSessionRequest;
  primaryTeacherName: string;
  assistantTeacherName: string | null;
  cmTeacherName: string | null;
}

interface PendingUpdate {
  sessionId: number;
  classId: number;
  request: UpdateSessionAssignmentRequest;
  preview: SessionAssignmentPreview;
}

interface PendingCancel {
  sessionId: number;
  classId: number;
  reason?: string;
}

type OpEntry =
  | { kind: "create"; id: string }
  | { kind: "removeCreate"; item: PendingCreate }
  | { kind: "editCreate"; id: string; prev: PendingCreate }
  | { kind: "update"; sessionId: number; prev: PendingUpdate | undefined }
  | { kind: "cancel"; sessionId: number; prevUpdate: PendingUpdate | undefined };

type DisplaySession = ClassSessionResponse & { pendingKind?: SessionPendingKind; localCreateId?: string };

/**
 * Lưới thời khóa biểu (hàng đầu = thứ, cột đầu = tiết) — tách khỏi
 * TimetablePage.tsx để dùng chung được ở trang "Lịch làm việc" (HRM), bổ
 * sung ngoài SDD gốc, xác nhận với người dùng 2026-08-20 (thử gộp 2 trang
 * lịch để so sánh UX, chưa xoá trang Thời khóa biểu độc lập).
 *
 * Cơ chế nháp/Lưu (bổ sung ngoài SDD gốc, xác nhận với người dùng
 * 2026-08-21) — Tạo/Sửa/Hủy trên lưới chỉ ghi vào hàng chờ cục bộ
 * (pendingCreates/Updates/Cancels), KHÔNG gọi API ngay. Chỉ khi bấm "Lưu"
 * mới lần lượt gọi API thật (create/update/cancel) rồi tải lại lưới. Có
 * "Hoàn tác" (LIFO, dùng opStack) và cảnh báo rời trang chưa lưu (tái dùng
 * AppContext.setUnsavedChanges — cùng cơ chế Sidebar chặn điều hướng đã có
 * cho DailyCommentPanel).
 */
export default function ClassPeriodGrid({ siteId, dates, classId }: ClassPeriodGridProps) {
  const { setUnsavedChanges } = useApp();
  const { promptDialog } = useDialog();

  const [periods, setPeriods] = useState<SitePeriodTemplateResponse[]>([]);
  const [sessions, setSessions] = useState<ClassSessionResponse[]>([]);
  const [rooms, setRooms] = useState<RoomResponse[]>([]);
  const [loading, setLoading] = useState(false);

  const [viewSession, setViewSession] = useState<DisplaySession | null>(null);
  const [editSession, setEditSession] = useState<DisplaySession | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [createPrefill, setCreatePrefill] = useState<CreateSessionModalPrefill | undefined>(undefined);

  const [cellSelection, setCellSelection] = useState<{ dateStr: string; dayPart: DayPart; periods: Set<number> } | null>(null);
  const [cellMenu, setCellMenu] = useState<{ x: number; y: number; dateStr: string; dayPart: DayPart; periods: number[] } | null>(null);
  const [cardMenu, setCardMenu] = useState<{ x: number; y: number; session: DisplaySession } | null>(null);
  const draggingRef = useRef(false);

  const [pendingCreates, setPendingCreates] = useState<PendingCreate[]>([]);
  const [pendingUpdates, setPendingUpdates] = useState<Map<number, PendingUpdate>>(new Map());
  const [pendingCancels, setPendingCancels] = useState<Map<number, PendingCancel>>(new Map());
  const [opStack, setOpStack] = useState<OpEntry[]>([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const localIdRef = useRef(0);

  const pendingCount = pendingCreates.length + pendingUpdates.size + pendingCancels.size;
  const hasPending = pendingCount > 0;

  const fromDate = toISODate(dates[0]);
  const toDate = toISODate(dates[dates.length - 1]);

  // Bôi đen ô tiết trên lưới rồi chuột phải → "Xếp lịch" (bổ sung ngoài SDD gốc,
  // xác nhận với người dùng 2026-08-20). Click/kéo ngoài ô chọn để bỏ chọn.
  useEffect(() => {
    function onWindowMouseUp() {
      draggingRef.current = false;
    }
    function onWindowMouseDown(e: MouseEvent) {
      const target = e.target as HTMLElement;
      if (target.closest("[data-tt-cell]") || target.closest("[data-tt-menu]")) return;
      setCellSelection(null);
      setCellMenu(null);
      setCardMenu(null);
    }
    window.addEventListener("mouseup", onWindowMouseUp);
    window.addEventListener("mousedown", onWindowMouseDown);
    return () => {
      window.removeEventListener("mouseup", onWindowMouseUp);
      window.removeEventListener("mousedown", onWindowMouseDown);
    };
  }, []);

  useEffect(() => {
    listSitePeriodTemplates(siteId).then(setPeriods).catch(() => undefined);
    listRoomsBySite(siteId).then(setRooms).catch(() => undefined);
  }, [siteId]);

  const load = () => {
    setLoading(true);
    listSessionsForSiteTimetable(siteId, fromDate, toDate)
      .then(setSessions)
      .finally(() => setLoading(false));
  };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(load, [siteId, fromDate, toDate]);

  // Đổi trường/khoảng ngày đang xem thì hàng chờ cũ (tọa độ theo lưới cũ) không còn ý nghĩa — dọn sạch
  // để tránh áp nhầm thay đổi lên site/khoảng ngày khác.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => {
    setPendingCreates([]);
    setPendingUpdates(new Map());
    setPendingCancels(new Map());
    setOpStack([]);
    setSaveError(null);
  }, [siteId]);

  const queueCreate = (payload: QueuedCreatePayload) => {
    const id = `local-${localIdRef.current++}`;
    const item: PendingCreate = { id, ...payload };
    setPendingCreates((prev) => [...prev, item]);
    setOpStack((prev) => [...prev, { kind: "create", id }]);
  };

  const handleDeletePendingCreate = (localId: string) => {
    const item = pendingCreates.find((c) => c.id === localId);
    if (!item) return;
    setPendingCreates((prev) => prev.filter((c) => c.id !== localId));
    setOpStack((prev) => [...prev, { kind: "removeCreate", item }]);
  };

  /** "Sửa" 1 buổi mới thêm, chưa lưu — cập nhật thẳng vào mục hàng chờ (không có sessionId thật để PATCH), bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-08-21. */
  const updatePendingCreate = (localId: string, request: UpdateSessionAssignmentRequest, preview: SessionAssignmentPreview) => {
    const prevItem = pendingCreates.find((c) => c.id === localId);
    if (!prevItem) return;
    setOpStack((stack) => [...stack, { kind: "editCreate", id: localId, prev: prevItem }]);
    setPendingCreates((prev) =>
      prev.map((c) =>
        c.id === localId
          ? {
              ...c,
              request: {
                ...c.request,
                roomId: request.roomId,
                teacherType: request.teacherType,
                primaryTeacherId: request.primaryTeacherId,
                assistantTeacherId: request.assistantTeacherId,
                cmTeacherId: request.cmTeacherId,
                dayPart: request.dayPart,
                periodNumbers: request.periodNumbers
              },
              primaryTeacherName: preview.primaryTeacherName,
              assistantTeacherName: preview.assistantTeacherName,
              cmTeacherName: preview.cmTeacherName
            }
          : c
      )
    );
  };

  const queueUpdate = (session: ClassSessionResponse, request: UpdateSessionAssignmentRequest, preview: SessionAssignmentPreview) => {
    const prev = pendingUpdates.get(session.id);
    setOpStack((stack) => [...stack, { kind: "update", sessionId: session.id, prev }]);
    setPendingUpdates((map) => {
      const next = new Map(map);
      next.set(session.id, { sessionId: session.id, classId: session.classId, request, preview });
      return next;
    });
  };

  const queueCancelExisting = (session: ClassSessionResponse, reason?: string) => {
    const prevUpdate = pendingUpdates.get(session.id);
    setOpStack((stack) => [...stack, { kind: "cancel", sessionId: session.id, prevUpdate }]);
    setPendingCancels((map) => {
      const next = new Map(map);
      next.set(session.id, { sessionId: session.id, classId: session.classId, reason });
      return next;
    });
    if (prevUpdate) {
      setPendingUpdates((map) => {
        const next = new Map(map);
        next.delete(session.id);
        return next;
      });
    }
  };

  const handleUndo = () => {
    setOpStack((stack) => {
      if (stack.length === 0) return stack;
      const last = stack[stack.length - 1];
      if (last.kind === "create") {
        setPendingCreates((prev) => prev.filter((c) => c.id !== last.id));
      } else if (last.kind === "removeCreate") {
        setPendingCreates((prev) => [...prev, last.item]);
      } else if (last.kind === "editCreate") {
        setPendingCreates((prev) => prev.map((c) => (c.id === last.id ? last.prev : c)));
      } else if (last.kind === "update") {
        setPendingUpdates((map) => {
          const next = new Map(map);
          if (last.prev) next.set(last.sessionId, last.prev);
          else next.delete(last.sessionId);
          return next;
        });
      } else {
        setPendingCancels((map) => {
          const next = new Map(map);
          next.delete(last.sessionId);
          return next;
        });
        if (last.prevUpdate) {
          const prevUpdate = last.prevUpdate;
          setPendingUpdates((map) => {
            const next = new Map(map);
            next.set(last.sessionId, prevUpdate);
            return next;
          });
        }
      }
      return stack.slice(0, -1);
    });
  };

  const handleSaveAll = async (): Promise<{ ok: boolean; message?: string }> => {
    setSaving(true);
    setSaveError(null);
    const errors: string[] = [];
    try {
      for (const pc of pendingCreates) {
        try {
          const res = await bulkCreateClassSessions(pc.classId, pc.request);
          if (res.skippedCount > 0) {
            errors.push(`${pc.className}: bỏ qua ${res.skippedCount}/${res.totalDates} ngày trùng lịch.`);
          }
        } catch (err) {
          errors.push(`${pc.className}: ${err instanceof ApiError ? err.message : "Tạo buổi thất bại."}`);
        }
      }
      for (const pu of pendingUpdates.values()) {
        try {
          await updateSessionAssignment(pu.classId, pu.sessionId, pu.request);
        } catch (err) {
          errors.push(`Buổi #${pu.sessionId}: ${err instanceof ApiError ? err.message : "Sửa thất bại."}`);
        }
      }
      for (const pcx of pendingCancels.values()) {
        try {
          await cancelClassSession(pcx.classId, pcx.sessionId, pcx.reason);
        } catch (err) {
          errors.push(`Buổi #${pcx.sessionId}: ${err instanceof ApiError ? err.message : "Hủy thất bại."}`);
        }
      }
      setPendingCreates([]);
      setPendingUpdates(new Map());
      setPendingCancels(new Map());
      setOpStack([]);
      load();
      if (errors.length > 0) {
        const message = errors.join("\n");
        setSaveError(message);
        return { ok: false, message };
      }
      return { ok: true };
    } finally {
      setSaving(false);
    }
  };

  // Đăng ký với AppContext để Sidebar chặn điều hướng + hỏi "Lưu tạm trước khi rời đi?" khi còn thay đổi
  // chưa lưu (tái dùng đúng cơ chế đã có cho DailyCommentPanel — xem AppContext.setUnsavedChanges).
  const handleSaveAllRef = useRef(handleSaveAll);
  handleSaveAllRef.current = handleSaveAll;
  useEffect(() => {
    setUnsavedChanges(hasPending, hasPending ? () => handleSaveAllRef.current() : null);
    return () => setUnsavedChanges(false, null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasPending]);

  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      if (!hasPending) return;
      e.preventDefault();
      e.returnValue = "";
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [hasPending]);

  const sections = useMemo(() => {
    let rowCursor = 2; // hàng 1 = header ngày
    return dayPartOrder
      .map((dp) => {
        const dpPeriods = periods.filter((p) => p.dayPart === dp);
        if (dpPeriods.length === 0) return null;
        const headerRow = rowCursor;
        rowCursor += 1;
        const startRow = rowCursor;
        const rowIndex = new Map<number, number>();
        dpPeriods.forEach((p) => {
          rowIndex.set(p.periodNumber, rowCursor);
          rowCursor += 1;
        });
        return { dayPart: dp, headerRow, startRow, periods: dpPeriods, rowIndex };
      })
      .filter((s): s is NonNullable<typeof s> => s !== null);
  }, [periods]);

  const rangeBetween = (dayPart: DayPart, fromPeriod: number, toPeriod: number): Set<number> => {
    const section = sections.find((s) => s.dayPart === dayPart);
    if (!section) return new Set([fromPeriod]);
    const numbers = section.periods.map((p) => p.periodNumber);
    const fromIdx = numbers.indexOf(fromPeriod);
    const toIdx = numbers.indexOf(toPeriod);
    if (fromIdx === -1 || toIdx === -1) return new Set([fromPeriod]);
    const [lo, hi] = fromIdx <= toIdx ? [fromIdx, toIdx] : [toIdx, fromIdx];
    return new Set(numbers.slice(lo, hi + 1));
  };

  const handleCellMouseDown = (e: ReactMouseEvent, dateStr: string, dayPart: DayPart, periodNumber: number) => {
    if (e.button !== 0) return;
    draggingRef.current = true;
    setCellMenu(null);
    setCardMenu(null);
    setCellSelection({ dateStr, dayPart, periods: new Set([periodNumber]) });
  };

  const handleCellMouseEnter = (dateStr: string, dayPart: DayPart, periodNumber: number) => {
    if (!draggingRef.current) return;
    setCellSelection((prev) => {
      if (!prev || prev.dateStr !== dateStr || prev.dayPart !== dayPart) return prev;
      const anchor = [...prev.periods][0] ?? periodNumber;
      return { dateStr, dayPart, periods: rangeBetween(dayPart, anchor, periodNumber) };
    });
  };

  const handleCellContextMenu = (e: ReactMouseEvent, dateStr: string, dayPart: DayPart, periodNumber: number) => {
    e.preventDefault();
    e.stopPropagation();
    draggingRef.current = false;
    const inSelection = cellSelection?.dateStr === dateStr && cellSelection.dayPart === dayPart && cellSelection.periods.has(periodNumber);
    const selPeriods = inSelection ? [...cellSelection!.periods].sort((a, b) => a - b) : [periodNumber];
    if (!inSelection) setCellSelection({ dateStr, dayPart, periods: new Set([periodNumber]) });
    setCardMenu(null);
    setCellMenu({ x: e.clientX, y: e.clientY, dateStr, dayPart, periods: selPeriods });
  };

  const openScheduleFromCell = (menu: NonNullable<typeof cellMenu>) => {
    setCreatePrefill({ date: menu.dateStr, dayPart: menu.dayPart, periodNumbers: menu.periods, classId });
    setCreateOpen(true);
  };

  const handleCardLeftClick = (session: DisplaySession) => setViewSession(session);

  const handleCardContextMenu = (e: ReactMouseEvent, session: DisplaySession) => {
    e.preventDefault();
    e.stopPropagation();
    draggingRef.current = false;
    if (session.pendingKind !== "create" && session.status !== "SCHEDULED") return;
    setCellMenu(null);
    setCardMenu({ x: e.clientX, y: e.clientY, session });
  };

  const handleCancelExisting = async (session: DisplaySession) => {
    const reason = await promptDialog("Lý do hủy buổi (không bắt buộc):", { title: "Hủy buổi học" });
    if (reason === null) return;
    queueCancelExisting(session, reason || undefined);
  };

  const rowHeights = useMemo(
    () => [HEADER_ROW_HEIGHT, ...sections.flatMap((s) => [SECTION_ROW_HEIGHT, ...s.periods.map(() => PERIOD_ROW_HEIGHT)])],
    [sections]
  );

  const displaySessions = useMemo<DisplaySession[]>(() => {
    const fromReal: DisplaySession[] = sessions.map((s) => {
      const cancel = pendingCancels.get(s.id);
      if (cancel) {
        return { ...s, status: "CANCELLED", cancellationReason: cancel.reason ?? s.cancellationReason, pendingKind: "cancel" };
      }
      const upd = pendingUpdates.get(s.id);
      if (upd) {
        return {
          ...s,
          dayPart: upd.request.dayPart,
          periodNumbers: upd.request.periodNumbers,
          roomId: upd.request.roomId ?? null,
          roomName: upd.preview.roomName,
          teacherType: upd.preview.teacherType,
          primaryTeacherId: upd.request.primaryTeacherId,
          primaryTeacherName: upd.preview.primaryTeacherName,
          assistantTeacherId: upd.request.assistantTeacherId ?? null,
          assistantTeacherName: upd.preview.assistantTeacherName,
          cmTeacherId: upd.request.cmTeacherId ?? null,
          cmTeacherName: upd.preview.cmTeacherName,
          pendingKind: "update"
        };
      }
      return s;
    });

    const ghosts: DisplaySession[] = [];
    let ghostId = -1;
    for (const pc of pendingCreates) {
      for (const d of dates) {
        const dateStr = toISODate(d);
        if (dateStr < pc.request.startDate || dateStr > pc.request.endDate) continue;
        if (!pc.request.daysOfWeek.includes(weekdayOf(dateStr))) continue;
        ghosts.push({
          id: ghostId--,
          classId: pc.classId,
          className: pc.className,
          sessionDate: dateStr,
          startTime: "",
          endTime: "",
          dayPart: pc.request.dayPart,
          periodNumbers: pc.request.periodNumbers,
          roomId: pc.request.roomId ?? null,
          roomName: null,
          primaryTeacherId: pc.request.primaryTeacherId,
          primaryTeacherName: pc.primaryTeacherName,
          assistantTeacherId: pc.request.assistantTeacherId ?? null,
          assistantTeacherName: pc.assistantTeacherName,
          cmTeacherId: pc.request.cmTeacherId ?? null,
          cmTeacherName: pc.cmTeacherName,
          sessionType: pc.request.sessionType,
          status: "SCHEDULED",
          cancellationReason: null,
          rescheduledToSessionId: null,
          teacherType: pc.request.teacherType,
          actualTeacherName: null,
          sessionNumber: 0,
          lessonContent: null,
          makeupForSessionId: null,
          classColor: pc.classColor,
          pendingKind: "create",
          localCreateId: pc.id
        });
      }
    }
    return [...fromReal, ...ghosts];
  }, [sessions, pendingUpdates, pendingCancels, pendingCreates, dates]);

  const sessionsByDateAndDayPart = useMemo(() => {
    const filtered = classId == null ? displaySessions : displaySessions.filter((s) => s.classId === classId);
    const map = new Map<string, DisplaySession[]>();
    filtered.forEach((s) => {
      if (!s.dayPart) return;
      const key = `${s.sessionDate}:${s.dayPart}`;
      const list = map.get(key) ?? [];
      list.push(s);
      map.set(key, list);
    });
    return map;
  }, [displaySessions, classId]);

  // Xếp lane cho từng ô (ngày + buổi) — KHÔNG giới hạn số buổi trùng tiết (bổ sung ngoài SDD gốc, xác
  // nhận với người dùng 2026-08-21, bỏ cap 3 lane cũ): tính trước 1 lần ở đây để dùng lại cả lúc suy ra
  // độ rộng cột (laneCountByDate) lẫn lúc render thẻ, tránh gọi assignLanes 2 lần cho cùng 1 ô.
  const laneAssignmentsByCell = useMemo(() => {
    const map = new Map<string, ReturnType<typeof assignLanes>>();
    dates.forEach((d) => {
      const dateStr = toISODate(d);
      sections.forEach((section) => {
        const daySessions = sessionsByDateAndDayPart.get(`${dateStr}:${section.dayPart}`) ?? [];
        const laneItems = daySessions
          .filter((s) => s.periodNumbers.length > 0)
          .map((s) => ({ id: s.id, startPeriod: Math.min(...s.periodNumbers), endPeriod: Math.max(...s.periodNumbers) }));
        map.set(`${dateStr}:${section.dayPart}`, assignLanes(laneItems));
      });
    });
    return map;
  }, [dates, sections, sessionsByDateAndDayPart]);

  /** Độ rộng cột theo NGÀY = số lane nhiều nhất trong bất kỳ buổi nào của ngày đó (tối thiểu DEFAULT_LANES), vì 1 cột ngày dùng chung 1 độ rộng cho mọi buổi Sáng/Chiều/Tối xếp chồng bên trong. */
  const laneCountByDate = useMemo(
    () =>
      dates.map((d) => {
        const dateStr = toISODate(d);
        let max = DEFAULT_LANES;
        sections.forEach((section) => {
          const assignments = laneAssignmentsByCell.get(`${dateStr}:${section.dayPart}`);
          if (assignments) max = Math.max(max, laneCountUsed(assignments));
        });
        return max;
      }),
    [dates, sections, laneAssignmentsByCell]
  );

  if (loading && periods.length === 0) {
    return <p className="text-xs text-slate-500 text-center py-8">Đang tải...</p>;
  }
  if (periods.length === 0) {
    return (
      <p className="text-xs text-slate-400 italic text-center py-8">
        Điểm trường này chưa cấu hình tiết học — vào Cơ sở vật chất &amp; Đối tác &gt; Điểm trường &gt; tab "Tiết học" để thêm.
      </p>
    );
  }

  return (
    <div>
      <div className="flex items-center justify-between gap-2 px-4 pt-4 pb-2 flex-wrap">
        <div className="flex items-center gap-2 flex-wrap">
          {hasPending && (
            <span className="text-[10px] font-bold text-purple-700 bg-purple-50 border border-purple-200 rounded-full px-2.5 py-1">
              {pendingCount} thay đổi chưa lưu
            </span>
          )}
          {saveError && (
            <span className="text-[10px] text-rose-600 bg-rose-50 border border-rose-100 rounded-lg px-2 py-1 whitespace-pre-line max-w-md">
              {saveError}
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="secondary" size="sm" onClick={handleUndo} disabled={opStack.length === 0 || saving}>
            <Undo2 className="w-3.5 h-3.5" />
            Hoàn tác
          </Button>
          <Button
            type="button"
            variant="secondary"
            size="sm"
            onClick={() => {
              setCreatePrefill(classId != null ? { classId } : undefined);
              setCreateOpen(true);
            }}
          >
            <CalendarPlus className="w-3.5 h-3.5" />
            Xếp lịch
          </Button>
          <Button type="button" variant="primary" size="sm" onClick={handleSaveAll} disabled={!hasPending || saving}>
            <Save className="w-3.5 h-3.5" />
            {saving ? "Đang lưu..." : "Lưu"}
          </Button>
        </div>
      </div>

      {/* Tự cuộn cả 2 chiều bên trong khung riêng (thay vì cuộn theo trang) — nút chức năng ở trên
          nằm ngoài khung này nên luôn cố định; header Thứ dùng "sticky top-0" NGAY TRONG khung này
          (không phải theo trang) để tránh vỡ khi card cha có overflow-hidden (bổ sung ngoài SDD gốc,
          xác nhận với người dùng 2026-08-21). */}
      <div className="p-4 pt-0 overflow-auto max-h-[70vh]">
        <div
          className="grid"
          style={{
            gridTemplateColumns: `${PERIOD_LABEL_COLUMN_WIDTH}px ${laneCountByDate.map((n) => `${n * LANE_WIDTH}px`).join(" ")}`,
            gridTemplateRows: rowHeights.map((h) => `${h}px`).join(" ")
          }}
        >
          <div className="sticky top-0 z-20 border-b border-r border-slate-200 bg-slate-50" style={{ gridColumn: 1, gridRow: 1 }} />
          {dates.map((d, i) => (
            <div
              key={i}
              className="sticky top-0 z-20 border-b border-slate-200 bg-slate-50 flex flex-col items-center justify-center"
              style={{ gridColumn: i + 2, gridRow: 1 }}
            >
              <span className="text-[13px] font-bold text-slate-700">{d.toLocaleDateString("vi-VN", { weekday: "short" })}</span>
              <span className="text-[11px] text-slate-900 font-mono">{d.getDate()}/{d.getMonth() + 1}</span>
            </div>
          ))}

          {sections.map((section) => (
            <div
              key={section.dayPart}
              className="border-b border-slate-200 bg-brand-red/5 flex items-center px-2"
              style={{ gridColumn: "1 / -1", gridRow: section.headerRow }}
            >
              <span className="text-[12px] font-bold uppercase text-brand-red">Buổi {dayPartLabels[section.dayPart]}</span>
            </div>
          ))}

          {sections.flatMap((section) =>
            section.periods.map((p) => (
              <div
                key={p.id}
                className="border-b border-r border-slate-200 bg-slate-50 flex flex-col items-center justify-center px-1"
                style={{ gridColumn: 1, gridRow: section.rowIndex.get(p.periodNumber) }}
              >
                <span className="text-[12px] font-bold text-slate-700">{p.label ?? `Tiết ${p.periodNumber}`}</span>
                <span className="text-[10px] text-slate-900 font-mono">
                  {p.startTime.slice(0, 5)}–{p.endTime.slice(0, 5)}
                </span>
              </div>
            ))
          )}

          {dates.flatMap((d, dayIdx) =>
            sections.map((section) => {
              const dateStr = toISODate(d);
              const daySessions = sessionsByDateAndDayPart.get(`${dateStr}:${section.dayPart}`) ?? [];
              const lanes = laneAssignmentsByCell.get(`${dateStr}:${section.dayPart}`) ?? new Map();
              const laneCount = laneCountByDate[dayIdx];

              return (
                <div
                  key={`${dayIdx}:${section.dayPart}`}
                  className="border-r border-slate-200 relative select-none"
                  style={{ gridColumn: dayIdx + 2, gridRow: `${section.startRow} / span ${section.periods.length}` }}
                >
                  <div
                    className="grid absolute inset-0"
                    style={{ gridTemplateColumns: "1fr", gridTemplateRows: `repeat(${section.periods.length}, ${PERIOD_ROW_HEIGHT}px)` }}
                  >
                    {section.periods.map((p, i) => {
                      const isSelected =
                        cellSelection?.dateStr === dateStr && cellSelection.dayPart === section.dayPart && cellSelection.periods.has(p.periodNumber);
                      return (
                        <div
                          key={p.id}
                          data-tt-cell
                          className={cn(
                            "border-b border-slate-100/70 cursor-pointer",
                            isSelected ? "bg-purple-100/70 ring-1 ring-inset ring-purple-300" : "hover:bg-slate-50"
                          )}
                          style={{ gridColumn: 1, gridRow: i + 1 }}
                          onMouseDown={(e) => handleCellMouseDown(e, dateStr, section.dayPart, p.periodNumber)}
                          onMouseEnter={() => handleCellMouseEnter(dateStr, section.dayPart, p.periodNumber)}
                          onContextMenu={(e) => handleCellContextMenu(e, dateStr, section.dayPart, p.periodNumber)}
                        />
                      );
                    })}
                  </div>
                  <div
                    className="grid absolute inset-0 gap-0.5 p-0.5 pointer-events-none"
                    style={{
                      gridTemplateColumns: `repeat(${laneCount}, 1fr)`,
                      gridTemplateRows: `repeat(${section.periods.length}, ${PERIOD_ROW_HEIGHT}px)`
                    }}
                  >
                    {daySessions.map((s) => {
                      if (s.periodNumbers.length === 0) return null;
                      const startPeriod = Math.min(...s.periodNumbers);
                      const endPeriod = Math.max(...s.periodNumbers);
                      const startRow = section.rowIndex.get(startPeriod);
                      const endRow = section.rowIndex.get(endPeriod);
                      if (startRow === undefined || endRow === undefined) return null;
                      const localStartRow = startRow - section.startRow;
                      const localEndRow = endRow - section.startRow;
                      const laneInfo = lanes.get(s.id);
                      return (
                        <TimetableSessionCard
                          key={s.id}
                          session={s}
                          pendingKind={s.pendingKind}
                          onClick={() => handleCardLeftClick(s)}
                          onContextMenu={(e) => handleCardContextMenu(e, s)}
                          style={{
                            gridColumn: (laneInfo?.lane ?? 0) + 1,
                            gridRow: `${localStartRow + 1} / span ${localEndRow - localStartRow + 1}`
                          }}
                        />
                      );
                    })}
                  </div>
                </div>
              );
            })
          )}
        </div>
      </div>

      {viewSession && (
        <SessionInfoModal session={viewSession} isPendingCreate={viewSession.pendingKind === "create"} onClose={() => setViewSession(null)} />
      )}

      {editSession && (
        <SessionEditModal
          session={editSession}
          siteId={siteId}
          rooms={rooms}
          hideReschedule={editSession.pendingKind === "create"}
          onClose={() => setEditSession(null)}
          onQueueUpdate={(request, preview) => {
            if (editSession.pendingKind === "create") {
              updatePendingCreate(editSession.localCreateId!, request, preview);
            } else {
              queueUpdate(editSession, request, preview);
            }
            setEditSession(null);
          }}
          onRescheduled={() => {
            setEditSession(null);
            load();
          }}
        />
      )}

      {cellMenu && (
        <ContextMenu
          x={cellMenu.x}
          y={cellMenu.y}
          onClose={() => setCellMenu(null)}
          items={[
            {
              label: "Xếp lịch",
              icon: <CalendarPlus className="w-3.5 h-3.5" />,
              onClick: () => openScheduleFromCell(cellMenu)
            }
          ]}
        />
      )}

      {cardMenu && (
        <ContextMenu
          x={cardMenu.x}
          y={cardMenu.y}
          onClose={() => setCardMenu(null)}
          items={
            cardMenu.session.pendingKind === "create"
              ? [
                  { label: "Sửa", icon: <Pencil className="w-3.5 h-3.5" />, onClick: () => setEditSession(cardMenu.session) },
                  {
                    label: "Xóa",
                    icon: <Trash2 className="w-3.5 h-3.5" />,
                    danger: true,
                    onClick: () => handleDeletePendingCreate(cardMenu.session.localCreateId!)
                  }
                ]
              : [
                  { label: "Sửa", icon: <Pencil className="w-3.5 h-3.5" />, onClick: () => setEditSession(cardMenu.session) },
                  {
                    label: "Hủy buổi",
                    icon: <XCircle className="w-3.5 h-3.5" />,
                    danger: true,
                    onClick: () => handleCancelExisting(cardMenu.session)
                  }
                ]
          }
        />
      )}

      {createOpen && (
        <CreateSessionModal
          siteId={siteId}
          prefill={createPrefill}
          onClose={() => {
            setCreateOpen(false);
            setCreatePrefill(undefined);
          }}
          onQueued={(payload) => {
            queueCreate(payload);
            setCreateOpen(false);
            setCreatePrefill(undefined);
            setCellSelection(null);
          }}
        />
      )}
    </div>
  );
}
