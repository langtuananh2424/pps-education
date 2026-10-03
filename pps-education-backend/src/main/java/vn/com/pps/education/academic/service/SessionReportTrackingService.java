package vn.com.pps.education.academic.service;

import vn.com.pps.education.permission.service.DataScopeService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.common.ExcelExportHelper;
import vn.com.pps.education.permission.domain.ApprovalFlow;
import vn.com.pps.education.academic.domain.ClassSession;
import vn.com.pps.education.student.domain.StudentComment;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.academic.dto.SessionReportApproverSummary;
import vn.com.pps.education.academic.dto.SessionReportStatusRow;
import vn.com.pps.education.academic.dto.SessionReportTeacherSummary;
import vn.com.pps.education.academic.dto.SessionReportTimelineEvent;
import vn.com.pps.education.academic.dto.SessionReportTrackingResponse;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.permission.repository.ApprovalFlowRepository;
import vn.com.pps.education.academic.repository.ClassSessionRepository;
import vn.com.pps.education.facility.repository.SiteRepository;
import vn.com.pps.education.student.repository.StudentCommentRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Theo dõi nộp & duyệt báo cáo buổi học (V207 — bổ sung ngoài SDD gốc, xác nhận với người dùng 2026-10-01).
 *
 * "Báo cáo" của 1 buổi = giáo viên gửi duyệt nhận xét của buổi đó (UC-21) → Quản lý điểm trường duyệt hoặc
 * từ chối (UC-22) → giáo viên sửa và gửi lại nếu bị từ chối. Mỗi lần gửi duyệt tạo 1 approval_flows mới (bản
 * cũ giữ nguyên) nên dựng lại được đủ các vòng mà không cần bảng riêng. Hạn của từng khâu đọc từ
 * {@link SessionReportSettings}:
 * <ul>
 *   <li>Nộp: submit_deadline_hours tính từ lúc buổi học kết thúc;</li>
 *   <li>Duyệt: approval_deadline_hours tính từ mỗi lượt gửi duyệt;</li>
 *   <li>Gửi lại: resubmit_deadline_hours tính từ lúc bị từ chối.</li>
 * </ul>
 * Người chịu trách nhiệm nộp = giáo viên phụ trách buổi (primary_teacher_id). Dùng chung cho trang theo dõi,
 * cảnh báo ({@link SessionReportAlertSchedulerService}), thống kê giảng dạy và dashboard.
 */
@Service
public class SessionReportTrackingService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    /** Giới hạn khoảng ngày mỗi lần xem — tránh nạp quá nhiều nhận xét/lượt duyệt trong 1 request. */
    public static final int MAX_RANGE_DAYS = 92;
    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private static final Map<String, String> STATE_LABELS = Map.ofEntries(
            Map.entry("NOT_DUE", "Chưa tới hạn"),
            Map.entry("ON_TIME", "Đúng hạn"),
            Map.entry("LATE", "Muộn"),
            Map.entry("MISSING", "Chưa nộp"),
            Map.entry("NONE", "—"),
            Map.entry("WAITING", "Đang chờ"),
            Map.entry("OVERDUE", "Quá hạn"));

    private final ClassSessionRepository classSessionRepository;
    private final StudentCommentRepository studentCommentRepository;
    private final ApprovalFlowRepository approvalFlowRepository;
    private final SiteRepository siteRepository;
    private final DataScopeService dataScopeService;
    private final SessionReportSettings settings;
    private final Clock clock;

    public SessionReportTrackingService(ClassSessionRepository classSessionRepository,
                                        StudentCommentRepository studentCommentRepository,
                                        ApprovalFlowRepository approvalFlowRepository,
                                        SiteRepository siteRepository,
                                        DataScopeService dataScopeService,
                                        SessionReportSettings settings,
                                        Clock clock) {
        this.classSessionRepository = classSessionRepository;
        this.studentCommentRepository = studentCommentRepository;
        this.approvalFlowRepository = approvalFlowRepository;
        this.siteRepository = siteRepository;
        this.dataScopeService = dataScopeService;
        this.settings = settings;
        this.clock = clock;
    }

    /** Hạn của 3 khâu tại thời điểm tính — đọc 1 lần cho cả lô buổi học. */
    record Deadlines(int submitDeadlineHours, int approvalDeadlineHours, int resubmitDeadlineHours) {
    }

    /** 1 buổi học kèm nhận xét và các lượt gửi duyệt của chúng (đã sắp theo thời gian gửi). */
    record SessionReportData(ClassSession session, List<StudentComment> comments,
                             Map<Long, List<ApprovalFlow>> flowsByCommentId) {

        List<ApprovalFlow> allFlows() {
            return flowsByCommentId.values().stream().flatMap(List::stream)
                    .sorted(Comparator.comparing(ApprovalFlow::getSubmittedAt)).toList();
        }
    }

    // ===================== API cho trang theo dõi =====================

    @Transactional(readOnly = true)
    public SessionReportTrackingResponse getTracking(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        validateRange(fromDate, toDate);
        String siteName = siteId == null ? null : siteRepository.findById(siteId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy điểm trường id=" + siteId))
                .getName();
        Deadlines deadlines = currentDeadlines();
        OffsetDateTime now = now();
        List<SessionReportData> data = loadInScope(siteId, fromDate, toDate, actorUserId);
        List<SessionReportStatusRow> rows = data.stream().map(d -> computeRow(d, deadlines, now)).toList();
        return new SessionReportTrackingResponse(fromDate, toDate, siteId, siteName,
                deadlines.submitDeadlineHours(), deadlines.approvalDeadlineHours(), deadlines.resubmitDeadlineHours(),
                rows, summarizeByTeacher(rows), summarizeByApprover(data, deadlines));
    }

    /** Trạng thái từng buổi trong phạm vi dữ liệu của người xem — dùng cho thống kê giảng dạy và dashboard. */
    @Transactional(readOnly = true)
    public List<SessionReportStatusRow> computeRows(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        Deadlines deadlines = currentDeadlines();
        OffsetDateTime now = now();
        return loadInScope(siteId, fromDate, toDate, actorUserId).stream()
                .map(d -> computeRow(d, deadlines, now)).toList();
    }

    @Transactional(readOnly = true)
    public List<SessionReportTimelineEvent> getTimeline(Long sessionId, Long actorUserId) {
        ClassSession session = classSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("error.sessionReport.sessionNotFound", new Object[]{sessionId},
                        "Không tìm thấy buổi học id=" + sessionId));
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        if (allowedSiteIds != null && !allowedSiteIds.contains(session.getSchoolClass().getSite().getId())) {
            throw new ResourceNotFoundException("error.sessionReport.sessionNotFound", new Object[]{sessionId},
                    "Không tìm thấy buổi học id=" + sessionId);
        }
        return buildTimeline(load(List.of(session)).get(0), currentDeadlines());
    }

    /** Dòng thời gian 1 buổi, không kiểm tra phạm vi — chỉ dùng nội bộ khi phạm vi đã được lọc trước (lịch sử thay đổi). */
    @Transactional(readOnly = true)
    public List<SessionReportTimelineEvent> timelineForSession(Long sessionId) {
        return classSessionRepository.findById(sessionId)
                .map(s -> buildTimeline(load(List.of(s)).get(0), currentDeadlines()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public byte[] exportExcel(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        SessionReportTrackingResponse tracking = getTracking(siteId, fromDate, toDate, actorUserId);
        List<String> headers = List.of("Ngày học", "Giờ học", "Lớp", "Điểm trường", "Giáo viên", "Hạn nộp",
                "Nộp lần đầu", "Trạng thái nộp", "Nộp muộn (phút)", "Trạng thái duyệt", "Duyệt trễ (phút)",
                "Người duyệt", "Số lần bị từ chối", "Gửi lại", "Gửi lại trễ (phút)", "Duyệt xong lúc");
        List<List<Object>> rows = new ArrayList<>();
        for (SessionReportStatusRow r : tracking.sessions()) {
            List<Object> row = new ArrayList<>();
            row.add(r.sessionDate().toString());
            row.add(r.startTime() + " - " + r.endTime());
            row.add(r.className() + " (" + r.classCode() + ")");
            row.add(r.siteName());
            row.add(r.teacherName());
            row.add(formatTime(r.submitDeadline()));
            row.add(formatTime(r.firstSubmittedAt()));
            row.add(STATE_LABELS.getOrDefault(r.submitState(), r.submitState()));
            row.add(r.submitLateMinutes());
            row.add(STATE_LABELS.getOrDefault(r.approvalState(), r.approvalState()));
            row.add(r.approvalLateMinutes());
            row.add(String.join(", ", r.approverNames()));
            row.add(r.rejectionCount());
            row.add(STATE_LABELS.getOrDefault(r.resubmitState(), r.resubmitState()));
            row.add(r.resubmitLateMinutes());
            row.add(formatTime(r.fullyApprovedAt()));
            rows.add(row);
        }
        List<String> notes = List.of(
                "Khoảng thời gian: " + tracking.fromDate() + " - " + tracking.toDate(),
                "Điểm trường: " + (tracking.siteName() == null ? "Tất cả điểm trường trong phạm vi" : tracking.siteName()),
                "Báo cáo buổi học = giáo viên gửi duyệt nhận xét của buổi.",
                "Hạn nộp: " + tracking.submitDeadlineHours() + " giờ kể từ lúc buổi học kết thúc.",
                "Hạn duyệt: " + tracking.approvalDeadlineHours() + " giờ kể từ lúc giáo viên gửi duyệt.",
                "Hạn gửi lại: " + tracking.resubmitDeadlineHours() + " giờ kể từ lúc bị từ chối.");
        return ExcelExportHelper.buildWorkbook("Nộp & duyệt báo cáo", headers, rows, notes);
    }

    // ===================== API cho scheduler cảnh báo =====================

    /** Buổi học + trạng thái, không giới hạn phạm vi dữ liệu — chỉ dùng nội bộ cho cảnh báo/tổng hợp. */
    record EvaluatedSession(ClassSession session, SessionReportStatusRow row) {
    }

    @Transactional(readOnly = true)
    List<EvaluatedSession> evaluateAll(LocalDate fromDate, LocalDate toDate, OffsetDateTime now) {
        Deadlines deadlines = currentDeadlines();
        List<ClassSession> sessions = classSessionRepository.findForReportTracking(fromDate, toDate, null, false, List.of(-1L));
        return load(sessions).stream()
                .map(d -> new EvaluatedSession(d.session(), computeRow(d, deadlines, now)))
                .toList();
    }

    // ===================== Tính trạng thái =====================

    private List<SessionReportData> loadInScope(Long siteId, LocalDate fromDate, LocalDate toDate, Long actorUserId) {
        List<Long> allowedSiteIds = dataScopeService.resolveAllowedSiteIds(actorUserId);
        boolean restrictSites = allowedSiteIds != null;
        List<Long> siteIdsForQuery = allowedSiteIds == null || allowedSiteIds.isEmpty() ? List.of(-1L) : allowedSiteIds;
        return load(classSessionRepository.findForReportTracking(fromDate, toDate, siteId, restrictSites, siteIdsForQuery));
    }

    private List<SessionReportData> load(List<ClassSession> sessions) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        Map<Long, List<StudentComment>> commentsBySessionId = studentCommentRepository
                .findByClassSessionIdIn(sessions.stream().map(ClassSession::getId).toList()).stream()
                .collect(Collectors.groupingBy(c -> c.getClassSession().getId()));
        List<Long> commentIds = commentsBySessionId.values().stream().flatMap(List::stream).map(StudentComment::getId).toList();
        Map<Long, List<ApprovalFlow>> flowsByCommentId = commentIds.isEmpty() ? Map.of()
                : approvalFlowRepository.findWithActorsByEntityTypeAndEntityIdIn(ApprovalFlow.EntityType.STUDENT_COMMENT, commentIds)
                .stream()
                .sorted(Comparator.comparing(ApprovalFlow::getSubmittedAt))
                .collect(Collectors.groupingBy(ApprovalFlow::getEntityId, LinkedHashMap::new, Collectors.toList()));
        return sessions.stream().map(s -> {
            List<StudentComment> comments = commentsBySessionId.getOrDefault(s.getId(), List.of());
            Map<Long, List<ApprovalFlow>> flows = new LinkedHashMap<>();
            for (StudentComment c : comments) {
                flows.put(c.getId(), flowsByCommentId.getOrDefault(c.getId(), List.of()));
            }
            return new SessionReportData(s, comments, flows);
        }).toList();
    }

    SessionReportStatusRow computeRow(SessionReportData data, Deadlines deadlines, OffsetDateTime now) {
        ClassSession session = data.session();
        List<ApprovalFlow> flows = data.allFlows();
        OffsetDateTime submitDeadline = submitDeadline(session, deadlines.submitDeadlineHours());

        // Khâu 1 — giáo viên nộp (lượt gửi duyệt đầu tiên của buổi).
        OffsetDateTime firstSubmittedAt = flows.isEmpty() ? null : flows.get(0).getSubmittedAt();
        String submitState;
        long submitLateMinutes = 0;
        if (firstSubmittedAt != null) {
            submitLateMinutes = lateMinutes(submitDeadline, firstSubmittedAt);
            submitState = submitLateMinutes > 0 ? "LATE" : "ON_TIME";
        } else if (now.isAfter(submitDeadline)) {
            submitLateMinutes = lateMinutes(submitDeadline, now);
            submitState = "MISSING";
        } else {
            submitState = "NOT_DUE";
        }

        // Khâu 2 — duyệt: mỗi lượt gửi có hạn riêng = lúc gửi + approval_deadline_hours.
        Duration approvalWindow = Duration.ofHours(deadlines.approvalDeadlineHours());
        boolean anyOpen = false;
        boolean anyOpenOverdue = false;
        boolean anyDecidedLate = false;
        long approvalLateMinutes = 0;
        OffsetDateTime openApprovalSince = null;
        Set<String> approverNames = new LinkedHashSet<>();
        for (ApprovalFlow f : flows) {
            OffsetDateTime due = f.getSubmittedAt().plus(approvalWindow);
            if (f.getStatus() == ApprovalFlow.Status.PENDING) {
                anyOpen = true;
                openApprovalSince = openApprovalSince == null || f.getSubmittedAt().isBefore(openApprovalSince)
                        ? f.getSubmittedAt() : openApprovalSince;
                if (now.isAfter(due)) {
                    anyOpenOverdue = true;
                    approvalLateMinutes = Math.max(approvalLateMinutes, lateMinutes(due, now));
                }
            } else if (f.getDecidedAt() != null) {
                if (f.getApprover() != null) {
                    approverNames.add(f.getApprover().getFullName());
                }
                long late = lateMinutes(due, f.getDecidedAt());
                if (late > 0) {
                    anyDecidedLate = true;
                    approvalLateMinutes = Math.max(approvalLateMinutes, late);
                }
            }
        }
        String approvalState = flows.isEmpty() ? "NONE"
                : anyOpenOverdue ? "OVERDUE" : anyOpen ? "WAITING" : anyDecidedLate ? "LATE" : "ON_TIME";

        // Khâu 3 — gửi lại sau khi bị từ chối: hạn = lúc từ chối + resubmit_deadline_hours.
        Duration resubmitWindow = Duration.ofHours(deadlines.resubmitDeadlineHours());
        boolean anyRejection = false;
        boolean anyResubmitOpen = false;
        boolean anyResubmitOverdue = false;
        boolean anyResubmitLate = false;
        long resubmitLateMinutes = 0;
        OffsetDateTime openRejectionSince = null;
        Set<String> rejectionEvents = new HashSet<>();
        Map<Long, StudentComment> commentsById = data.comments().stream()
                .collect(Collectors.toMap(StudentComment::getId, c -> c));
        for (Map.Entry<Long, List<ApprovalFlow>> entry : data.flowsByCommentId().entrySet()) {
            List<ApprovalFlow> commentFlows = entry.getValue();
            for (int i = 0; i < commentFlows.size(); i++) {
                ApprovalFlow f = commentFlows.get(i);
                if (f.getStatus() != ApprovalFlow.Status.REJECTED || f.getDecidedAt() == null) {
                    continue;
                }
                anyRejection = true;
                rejectionEvents.add(decisionEventKey(f));
                OffsetDateTime due = f.getDecidedAt().plus(resubmitWindow);
                ApprovalFlow next = i + 1 < commentFlows.size() ? commentFlows.get(i + 1) : null;
                if (next != null) {
                    long late = lateMinutes(due, next.getSubmittedAt());
                    if (late > 0) {
                        anyResubmitLate = true;
                        resubmitLateMinutes = Math.max(resubmitLateMinutes, late);
                    }
                } else if (commentsById.get(entry.getKey()).getStatus() == StudentComment.Status.REJECTED) {
                    anyResubmitOpen = true;
                    openRejectionSince = openRejectionSince == null || f.getDecidedAt().isAfter(openRejectionSince)
                            ? f.getDecidedAt() : openRejectionSince;
                    if (now.isAfter(due)) {
                        anyResubmitOverdue = true;
                        resubmitLateMinutes = Math.max(resubmitLateMinutes, lateMinutes(due, now));
                    }
                }
            }
        }
        String resubmitState = !anyRejection ? "NONE"
                : anyResubmitOverdue ? "OVERDUE" : anyResubmitOpen ? "WAITING" : anyResubmitLate ? "LATE" : "ON_TIME";

        int approvedCount = countByStatus(data.comments(), StudentComment.Status.APPROVED);
        int pendingCount = countByStatus(data.comments(), StudentComment.Status.PENDING);
        int rejectedCount = countByStatus(data.comments(), StudentComment.Status.REJECTED);
        OffsetDateTime fullyApprovedAt = !data.comments().isEmpty() && approvedCount == data.comments().size()
                ? flows.stream().filter(f -> f.getStatus() == ApprovalFlow.Status.APPROVED && f.getDecidedAt() != null)
                .map(ApprovalFlow::getDecidedAt).max(Comparator.naturalOrder()).orElse(null)
                : null;

        return new SessionReportStatusRow(
                session.getId(),
                session.getSchoolClass().getId(),
                session.getSchoolClass().getName(),
                session.getSchoolClass().getClassCode(),
                session.getSchoolClass().getSite().getId(),
                session.getSchoolClass().getSite().getName(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                session.getPrimaryTeacher().getId(),
                session.getPrimaryTeacher().getFullName(),
                submitDeadline,
                firstSubmittedAt,
                submitState,
                submitLateMinutes,
                data.comments().size(),
                approvedCount,
                pendingCount,
                rejectedCount,
                approvalState,
                approvalLateMinutes,
                openApprovalSince,
                List.copyOf(approverNames),
                rejectionEvents.size(),
                resubmitState,
                resubmitLateMinutes,
                openRejectionSince,
                fullyApprovedAt);
    }

    /**
     * Dòng thời gian của 1 buổi: gom các lượt gửi/quyết định cùng người, cùng phút thành 1 mốc (giáo viên
     * gửi cả lớp 1 lần = 1 mốc). Mốc gửi đầu tiên so với hạn nộp; các lần gửi sau so với hạn gửi lại tính từ
     * lần từ chối gần nhất trước đó; mỗi quyết định so với hạn duyệt tính từ lượt gửi sớm nhất trong mốc.
     */
    List<SessionReportTimelineEvent> buildTimeline(SessionReportData data, Deadlines deadlines) {
        List<ApprovalFlow> flows = data.allFlows();
        OffsetDateTime submitDeadline = submitDeadline(data.session(), deadlines.submitDeadlineHours());
        Duration approvalWindow = Duration.ofHours(deadlines.approvalDeadlineHours());
        Duration resubmitWindow = Duration.ofHours(deadlines.resubmitDeadlineHours());

        Map<String, List<ApprovalFlow>> submitGroups = new LinkedHashMap<>();
        for (ApprovalFlow f : flows) {
            submitGroups.computeIfAbsent(f.getSubmittedBy().getId() + "|" + f.getSubmittedAt().truncatedTo(ChronoUnit.MINUTES), k -> new ArrayList<>()).add(f);
        }
        Map<String, List<ApprovalFlow>> decisionGroups = new LinkedHashMap<>();
        flows.stream()
                .filter(f -> f.getDecidedAt() != null && f.getDecision() != null)
                .sorted(Comparator.comparing(ApprovalFlow::getDecidedAt))
                .forEach(f -> decisionGroups.computeIfAbsent(decisionEventKey(f), k -> new ArrayList<>()).add(f));
        List<OffsetDateTime> rejectionTimes = flows.stream()
                .filter(f -> f.getStatus() == ApprovalFlow.Status.REJECTED && f.getDecidedAt() != null)
                .map(ApprovalFlow::getDecidedAt).sorted().toList();

        List<SessionReportTimelineEvent> events = new ArrayList<>();
        boolean first = true;
        for (List<ApprovalFlow> group : submitGroups.values()) {
            ApprovalFlow head = group.get(0);
            OffsetDateTime at = head.getSubmittedAt();
            OffsetDateTime deadline;
            String type;
            if (first) {
                type = "SUBMITTED";
                deadline = submitDeadline;
            } else {
                type = "RESUBMITTED";
                OffsetDateTime lastRejection = rejectionTimes.stream().filter(t -> !t.isAfter(at)).reduce((a, b) -> b).orElse(null);
                deadline = lastRejection == null ? null : lastRejection.plus(resubmitWindow);
            }
            first = false;
            events.add(timelineEvent(type, at, head.getSubmittedBy(), group.size(), null, deadline));
        }
        for (List<ApprovalFlow> group : decisionGroups.values()) {
            ApprovalFlow head = group.get(0);
            OffsetDateTime earliestSubmit = group.stream().map(ApprovalFlow::getSubmittedAt).min(Comparator.naturalOrder()).orElseThrow();
            String reason = group.stream().map(ApprovalFlow::getComment).filter(Objects::nonNull)
                    .filter(c -> !c.isBlank()).findFirst().orElse(null);
            events.add(timelineEvent(head.getDecision().name(), head.getDecidedAt(), head.getApprover(), group.size(),
                    reason, earliestSubmit.plus(approvalWindow)));
        }
        events.sort(Comparator.comparing(SessionReportTimelineEvent::at));
        return events;
    }

    private static SessionReportTimelineEvent timelineEvent(String type, OffsetDateTime at, User actor, int count,
                                                            String reason, OffsetDateTime deadline) {
        long late = deadline == null ? 0 : lateMinutes(deadline, at);
        return new SessionReportTimelineEvent(type, at, actor == null ? null : actor.getId(),
                actor == null ? null : actor.getFullName(), count, reason, deadline,
                deadline == null ? null : late > 0 ? "LATE" : "ON_TIME", late);
    }

    // ===================== Tổng hợp =====================

    private static List<SessionReportTeacherSummary> summarizeByTeacher(List<SessionReportStatusRow> rows) {
        Map<Long, List<SessionReportStatusRow>> byTeacher = rows.stream()
                .collect(Collectors.groupingBy(SessionReportStatusRow::teacherUserId, LinkedHashMap::new, Collectors.toList()));
        List<SessionReportTeacherSummary> result = new ArrayList<>();
        for (List<SessionReportStatusRow> teacherRows : byTeacher.values()) {
            int onTime = countState(teacherRows, "ON_TIME");
            int late = countState(teacherRows, "LATE");
            int missing = countState(teacherRows, "MISSING");
            int due = onTime + late + missing;
            result.add(new SessionReportTeacherSummary(
                    teacherRows.get(0).teacherUserId(),
                    teacherRows.get(0).teacherName(),
                    teacherRows.size(),
                    onTime, late, missing,
                    teacherRows.stream().mapToInt(SessionReportStatusRow::rejectionCount).sum(),
                    (int) teacherRows.stream().filter(r -> "LATE".equals(r.resubmitState()) || "OVERDUE".equals(r.resubmitState())).count(),
                    due == 0 ? null : Math.round(onTime * 1000.0 / due) / 10.0));
        }
        result.sort(Comparator.comparing(SessionReportTeacherSummary::teacherName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static List<SessionReportApproverSummary> summarizeByApprover(List<SessionReportData> data, Deadlines deadlines) {
        Duration approvalWindow = Duration.ofHours(deadlines.approvalDeadlineHours());
        record Acc(User approver, Set<Long> sessions, Set<Long> lateSessions, Set<Long> rejectedSessions, List<Long> waits) {
        }
        Map<Long, Acc> byApprover = new LinkedHashMap<>();
        for (SessionReportData d : data) {
            for (ApprovalFlow f : d.allFlows()) {
                if (f.getApprover() == null || f.getDecidedAt() == null) {
                    continue;
                }
                Acc acc = byApprover.computeIfAbsent(f.getApprover().getId(),
                        k -> new Acc(f.getApprover(), new HashSet<>(), new HashSet<>(), new HashSet<>(), new ArrayList<>()));
                Long sessionId = d.session().getId();
                acc.sessions().add(sessionId);
                if (f.getDecidedAt().isAfter(f.getSubmittedAt().plus(approvalWindow))) {
                    acc.lateSessions().add(sessionId);
                }
                if (f.getStatus() == ApprovalFlow.Status.REJECTED) {
                    acc.rejectedSessions().add(sessionId);
                }
                acc.waits().add(Duration.between(f.getSubmittedAt(), f.getDecidedAt()).toMinutes());
            }
        }
        return byApprover.values().stream()
                .map(a -> new SessionReportApproverSummary(a.approver().getId(), a.approver().getFullName(),
                        a.sessions().size(), a.lateSessions().size(), a.rejectedSessions().size(),
                        a.waits().isEmpty() ? null : Math.round(a.waits().stream().mapToLong(Long::longValue).average().orElse(0))))
                .sorted(Comparator.comparing(SessionReportApproverSummary::approverName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ===================== Helpers =====================

    Deadlines currentDeadlines() {
        return new Deadlines(settings.submitDeadlineHours(), settings.approvalDeadlineHours(), settings.resubmitDeadlineHours());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), APP_ZONE);
    }

    /** Hạn nộp = giờ kết thúc buổi học + deadlineHours (giờ Việt Nam). */
    static OffsetDateTime submitDeadline(ClassSession session, int deadlineHours) {
        LocalDateTime sessionEnd = session.getSessionDate().atTime(session.getEndTime());
        return sessionEnd.plusHours(deadlineHours).atZone(APP_ZONE).toOffsetDateTime();
    }

    private static long lateMinutes(OffsetDateTime deadline, OffsetDateTime at) {
        return at.isAfter(deadline) ? Math.max(1, Duration.between(deadline, at).toMinutes()) : 0;
    }

    private static String decisionEventKey(ApprovalFlow f) {
        return (f.getApprover() == null ? "-" : f.getApprover().getId()) + "|" + f.getDecision() + "|"
                + f.getDecidedAt().truncatedTo(ChronoUnit.MINUTES);
    }

    private static int countByStatus(List<StudentComment> comments, StudentComment.Status status) {
        return (int) comments.stream().filter(c -> c.getStatus() == status).count();
    }

    private static int countState(List<SessionReportStatusRow> rows, String submitState) {
        return (int) rows.stream().filter(r -> submitState.equals(r.submitState())).count();
    }

    private static String formatTime(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(APP_ZONE).format(DATE_TIME_FMT);
    }

    private static void validateRange(LocalDate fromDate, LocalDate toDate) {
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Từ ngày phải trước hoặc bằng Đến ngày.");
        }
        if (ChronoUnit.DAYS.between(fromDate, toDate) >= MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Khoảng thời gian tối đa " + MAX_RANGE_DAYS + " ngày.");
        }
    }
}
