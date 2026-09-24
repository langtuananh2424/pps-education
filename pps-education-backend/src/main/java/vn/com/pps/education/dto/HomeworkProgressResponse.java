package vn.com.pps.education.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Cổng phụ huynh — tiến độ BTVN của con theo từng buổi có giao BTVN (chỉ
 * xem, không phải giao diện làm bài). *OfflineText khác null CHỈ khi giáo
 * viên giao offline (không có *AssignmentId tương ứng).
 *
 * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-30):
 * videoAssignmentId là id bản giao (ReviewVideoAssignment) tự động tạo
 * cho cả lớp — đổi tên từ videoSetId cũ (trước V65 trỏ thẳng ReviewVideoSet).
 *
 * grammarPassed/videoPassed/readingPassed/writingPassed (bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-08-06) — null khi không có bản giao
 * tương ứng (kênh đó null), tái dùng thẳng
 * {@link vn.com.pps.education.service.HomeworkProgressService#grammarPassed}/
 * {@code videoPassed} để Cổng phụ huynh phân biệt được "đạt" hay "chưa đạt"
 * thay vì chỉ có % (VD 45% trước đây hiện y hệt màu xanh như 100%, dễ hiểu
 * nhầm là đã ổn).
 *
 * readingAssignmentId/readingTitle/.../writingAssignmentId/... + *Items
 * (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22, mirror
 * kênh Ngữ pháp) — 2 kênh BTVN Reading/Writing (V135 offline, V137 online,
 * chỉ áp dụng buổi teacherType=VIETNAMESE) đã có dữ liệu ở StudentComment
 * từ trước nhưng CHƯA lộ ra Cổng phụ huynh (chỉ thấy ở Portal Giáo viên/
 * DailyCommentPanel) — nay bổ sung đủ. *Items cho phép xem % TỪNG bài lẻ
 * trong 1 Lô (grammar/reading/writing đều có thể gồm nhiều Bài cùng kỹ
 * năng — xem HomeworkSkillBatch), không chỉ % gộp cả lô như trước.
 *
 * grammarSkillCategory/*DueAt/videoType/videoTeacherType (bổ sung ngoài SDD
 * gốc, đã xác nhận với người dùng 2026-09-22) — để FE (ParentHomeworkProgressTab)
 * hiện đủ badge như thẻ BTVN bên Portal Học sinh (loại bài/GV phụ trách/hạn
 * nộp/quá hạn), tránh cảm giác trùng lặp với tab "Quá trình học tập" (vốn
 * chỉ có %). grammarSkillCategory phân biệt "Ngữ pháp" (VOCAB_GRAMMAR, buổi
 * VIETNAMESE) hay "Nghe" (LISTENING, buổi FOREIGN) — 2 kênh dùng CHUNG field
 * grammarXxx, xem StudentCommentService#grammarChannelSkillCategory. Reading/
 * Writing luôn là GV Việt Nam (V137), không cần field riêng.
 *
 * *UnitTitle (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-22)
 * — Unit chứa Lesson (Exam)/Bộ Video này (Exam.subTopic.unit /
 * ReviewVideoSet.subTopic.unit), NULL nếu Đề/Bộ cũ chưa phân loại vào cấu
 * trúc Sách → Unit → Sub Topic. Dùng cho FE điều hướng phân cấp Unit → Kỹ
 * năng giống Portal Học sinh (mirror AssignmentsTab.tsx), chỉ khác ở chỗ
 * đây là bản CHỈ XEM (không có nút làm bài).
 */
public record HomeworkProgressResponse(
        Long commentId,
        Long classSessionId,
        LocalDate commentDate,
        Long grammarAssignmentId,
        String grammarTitle,
        String grammarOfflineText,
        String grammarProgress,
        Boolean grammarPassed,
        List<HomeworkSkillItemResponse> grammarItems,
        String grammarSkillCategory,
        OffsetDateTime grammarDueAt,
        String grammarUnitTitle,
        Long readingAssignmentId,
        String readingTitle,
        String readingOfflineText,
        String readingProgress,
        Boolean readingPassed,
        List<HomeworkSkillItemResponse> readingItems,
        OffsetDateTime readingDueAt,
        String readingUnitTitle,
        Long writingAssignmentId,
        String writingTitle,
        String writingOfflineText,
        String writingProgress,
        Boolean writingPassed,
        List<HomeworkSkillItemResponse> writingItems,
        OffsetDateTime writingDueAt,
        String writingUnitTitle,
        Long videoAssignmentId,
        String videoTitle,
        String videoProgress,
        Boolean videoPassed,
        String videoType,
        String videoTeacherType,
        OffsetDateTime videoDueAt,
        String videoUnitTitle
) {}
