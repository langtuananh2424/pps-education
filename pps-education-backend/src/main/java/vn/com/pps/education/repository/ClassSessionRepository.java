package vn.com.pps.education.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.com.pps.education.domain.ClassSession;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ClassSessionRepository extends JpaRepository<ClassSession, Long> {

    List<ClassSession> findBySchoolClassIdOrderBySessionDateAsc(Long classId);

    /**
     * UC-21 mở rộng (BTVN theo buổi, V55): buổi học liền TRƯỚC 1 buổi,
     * cùng lớp — tra lại FK "sẽ giao gì" đã ghi ở dòng student_comments
     * của buổi trước. Loại CANCELLED/RESCHEDULED (bổ sung ngoài SDD gốc,
     * đã xác nhận với người dùng 2026-08-19, fix lỗ hổng thật: bản gốc
     * không lọc status, chỉ đúng ngẫu nhiên nhờ tiebreak idDesc khi 1 buổi
     * CANCELLED trùng ngày với 1 buổi SCHEDULED có id lớn hơn — nếu buổi
     * CANCELLED có id LỚN hơn thì bị chọn nhầm làm "buổi trước", trong khi
     * buổi đó chưa từng dạy nên chưa từng có student_comments, dẫn tới bỏ
     * sót buổi THẬT SỰ dạy gần nhất trước đó). Mirror
     * findUpcomingSessions (dùng cùng excludedStatuses ở call site).
     *
     * V167 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — fix bug thật: derived
     * query cũ (`SessionDateLessThan`) chỉ so `session_date`, KHÔNG phân biệt được 2 buổi CÙNG NGÀY
     * (VD lớp có buổi sáng + buổi chiều cùng 1 ngày, rất phổ biến khi có cả GV Việt Nam lẫn GV nước
     * ngoài). Buổi chiều tra "buổi trước" sẽ NHẢY QUA buổi sáng cùng ngày (vì session_date bằng nhau,
     * không "<") thẳng tới tận NGÀY LỊCH trước đó — sai hoàn toàn "buổi liền trước". Đổi sang @Query so
     * theo cặp (session_date, id) — cùng ngày thì so id, mirror đúng cách countEarlierSessions đã dùng
     * để đánh số "Buổi N" (buổi sinh trước có id nhỏ hơn, đúng thứ tự sinh lịch hàng loạt). Đổi từ
     * `findFirst...Optional` sang trả `List` (giữ ORDER BY, Service tự lấy phần tử đầu) vì derived-name
     * `findFirst` không áp dụng được với @Query có điều kiện OR phức tạp.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id = :classId
            AND (cs.sessionDate < :sessionDate OR (cs.sessionDate = :sessionDate AND cs.id < :sessionId))
            AND cs.status NOT IN (:excludedStatuses)
            ORDER BY cs.sessionDate DESC, cs.id DESC
            """)
    List<ClassSession> findSessionsBeforeOrderedDesc(@Param("classId") Long classId, @Param("sessionDate") LocalDate sessionDate,
                                                      @Param("sessionId") Long sessionId,
                                                      @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * UC-21 mở rộng (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-12): buổi học liền TRƯỚC 1 buổi, cùng lớp VÀ cùng
     * teacher_type — dùng khi buổi đang xét CÓ xác định loại giáo viên
     * (VIETNAMESE/FOREIGN), để "BTVN buổi trước" đối chiếu đúng mạch bài
     * của cùng loại GV (VD GVNN buổi 6 đối chiếu GVNN buổi 3, bỏ qua buổi
     * GVVN xen giữa) thay vì buổi liền kề tuyệt đối bất kể ai dạy. Loại
     * CANCELLED/RESCHEDULED — cùng lý do đã ghi ở method phía trên.
     *
     * V167 (2026-09-05) — cùng fix bug "cùng ngày" đã ghi ở
     * {@link #findSessionsBeforeOrderedDesc}, cộng thêm lọc teacherType.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id = :classId
            AND (cs.sessionDate < :sessionDate OR (cs.sessionDate = :sessionDate AND cs.id < :sessionId))
            AND cs.teacherType = :teacherType
            AND cs.status NOT IN (:excludedStatuses)
            ORDER BY cs.sessionDate DESC, cs.id DESC
            """)
    List<ClassSession> findSessionsBeforeWithTeacherTypeOrderedDesc(@Param("classId") Long classId, @Param("sessionDate") LocalDate sessionDate,
                                                                     @Param("sessionId") Long sessionId,
                                                                     @Param("teacherType") ClassSession.TeacherType teacherType,
                                                                     @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * V65 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng): buổi học
     * liền SAU 1 buổi, cùng lớp — dùng tính hạn nộp (dueAt) khi Giáo viên
     * chọn 1 đề/video làm "BTVN buổi sau" ở Nhận xét. Loại trừ
     * CANCELLED/RESCHEDULED (buổi đã dời không tính là "buổi kế tiếp" —
     * buổi thay thế nó mới là buổi thật, tự nhiên nằm trong kết quả vì có
     * sessionDate riêng), mirror cách loại trừ trạng thái ở
     * findOverlappingForClass.
     *
     * Sửa lại 2026-08-14 (bổ sung ngoài SDD gốc, đã xác nhận với người
     * dùng — phát hiện lệch logic khi lớp có buổi VIETNAMESE/FOREIGN xen
     * kẽ nhau): teacherType khác null thì CHỈ tính buổi kế tiếp CÙNG loại
     * giáo viên — mirror đúng
     * findFirstBySchoolClassIdAndSessionDateLessThanAndTeacherTypeOrderBySessionDateDescIdDesc
     * (dùng để tra "buổi trước" khi hiển thị điểm/%). Trước đây 2 chiều
     * (hạn nộp mặc định vs. nơi hiển thị điểm buổi trước) dùng 2 tiêu chí
     * khác nhau (buổi kế tiếp tuyệt đối vs. buổi kế tiếp cùng loại GV) —
     * VD lớp Buổi 1=VN/2=Nước ngoài/3=VN xen kẽ: giao BTVN ở Buổi 1 (VN)
     * mặc định hạn nộp = Buổi 2 (Nước ngoài) nhưng điểm chỉ hiện ở Buổi 3
     * (VN) — học sinh phải nộp trước Buổi 2 dù giáo viên VN không thấy
     * điểm cho tới tận Buổi 3. teacherType=null giữ nguyên hành vi cũ
     * (buổi kế tiếp tuyệt đối, không lọc).
     *
     * V167 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-09-05) — fix bug thật, cùng gốc với
     * findSessionsBeforeOrderedDesc: `cs.sessionDate > :sessionDate` KHÔNG phân biệt được 2 buổi CÙNG
     * NGÀY — buổi sáng tra "buổi kế tiếp" sẽ NHẢY QUA buổi chiều cùng ngày (session_date bằng nhau,
     * không ">") thẳng tới tận ngày lịch SAU đó, khiến hạn nộp mặc định lệch xa hơn nhiều so với thực
     * tế (VD lớp có buổi sáng+chiều cùng ngày, "buổi kế tiếp" của buổi sáng phải là buổi chiều CÙNG
     * NGÀY, không phải buổi của ngày lịch kế tiếp). Thêm điều kiện cùng ngày thì so id (mirror
     * findSessionsBeforeOrderedDesc) — cần thêm tham số sessionId để loại trừ/so sánh đúng chính nó.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id = :classId
            AND (cs.sessionDate > :sessionDate OR (cs.sessionDate = :sessionDate AND cs.id > :sessionId))
            AND cs.status NOT IN (:excludedStatuses)
            AND (:teacherType IS NULL OR cs.teacherType = :teacherType)
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findUpcomingSessions(@Param("classId") Long classId, @Param("sessionDate") LocalDate sessionDate,
                                             @Param("sessionId") Long sessionId,
                                             @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses,
                                             @Param("teacherType") ClassSession.TeacherType teacherType);

    /** UC-09 A12/A13 (Chấm công GV — cửa sổ theo lịch dạy): tiết dạy trong ngày của 1 GV. */
    List<ClassSession> findByPrimaryTeacherIdAndSessionDateAndStatusNotIn(
            Long primaryTeacherId, LocalDate sessionDate, List<ClassSession.Status> excludedStatuses);

    /**
     * UC-10 bước 3 (bổ sung ngoài SDD gốc, đã xác nhận với người dùng
     * 2026-08-05): buổi dạy của 1 Giáo viên trong khoảng nghỉ đã chọn, để
     * hiển thị cho GV chọn giáo viên dạy thay.
     */
    List<ClassSession> findByPrimaryTeacherIdAndSessionDateBetweenAndStatusNotIn(
            Long primaryTeacherId, LocalDate startDate, LocalDate endDate, List<ClassSession.Status> excludedStatuses);

    /**
     * FR-FAC-03 — kiểm tra trùng phòng: cùng room_id, cùng ngày, khoảng
     * thời gian giao nhau, status không phải CANCELLED/RESCHEDULED, loại
     * trừ chính session đang sửa (editingSessionId=null khi tạo mới).
     * Chỉ gọi khi room.isFlexible()=false (Service tự lọc trước).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.room.id = :roomId
            AND cs.sessionDate = :date
            AND cs.status NOT IN (:excludedStatuses)
            AND :startTime < cs.endTime AND :endTime > cs.startTime
            AND (:editingSessionId IS NULL OR cs.id <> :editingSessionId)
            """)
    List<ClassSession> findOverlappingInRoom(@Param("roomId") Long roomId, @Param("date") LocalDate date,
                                              @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                              @Param("editingSessionId") Long editingSessionId,
                                              @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * Chặn trùng giờ Giáo viên (bổ sung ngoài SDD gốc, đã xác nhận với
     * người dùng 2026-07-30): cùng primary_teacher_id, cùng ngày, khung
     * giờ giao nhau, loại trừ CANCELLED/RESCHEDULED và chính session đang
     * sửa. Áp dụng mọi lớp, không giới hạn 1 lớp — khác findOverlappingInRoom
     * chỉ xét trong phạm vi 1 phòng.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.primaryTeacher.id = :teacherId
            AND cs.sessionDate = :date
            AND cs.status NOT IN (:excludedStatuses)
            AND :startTime < cs.endTime AND :endTime > cs.startTime
            AND (:editingSessionId IS NULL OR cs.id <> :editingSessionId)
            """)
    List<ClassSession> findOverlappingForTeacher(@Param("teacherId") Long teacherId, @Param("date") LocalDate date,
                                                  @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                                  @Param("editingSessionId") Long editingSessionId,
                                                  @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * Chặn trùng giờ trong cùng 1 Lớp (bổ sung ngoài SDD gốc, đã xác nhận
     * với người dùng 2026-07-30) — VD lỡ tạo 2 buổi chồng giờ cho cùng 1
     * lớp, kể cả khi không gán phòng hoặc phòng khác nhau (room-check
     * không bắt được trường hợp này).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id = :classId
            AND cs.sessionDate = :date
            AND cs.status NOT IN (:excludedStatuses)
            AND :startTime < cs.endTime AND :endTime > cs.startTime
            AND (:editingSessionId IS NULL OR cs.id <> :editingSessionId)
            """)
    List<ClassSession> findOverlappingForClass(@Param("classId") Long classId, @Param("date") LocalDate date,
                                                @Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime,
                                                @Param("editingSessionId") Long editingSessionId,
                                                @Param("excludedStatuses") List<ClassSession.Status> excludedStatuses);

    /**
     * UC-58: "Lịch của tôi" — mọi buổi học của 1 Giáo viên qua MỌI lớp,
     * lọc theo khoảng ngày tùy chọn (null = không giới hạn). Tận dụng
     * index có sẵn idx_class_sessions_teacher_date (primary_teacher_id,
     * session_date DESC).
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.primaryTeacher.id = :teacherId
            AND (cast(:fromDate as date) IS NULL OR cs.sessionDate >= :fromDate)
            AND (cast(:toDate as date) IS NULL OR cs.sessionDate <= :toDate)
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findByPrimaryTeacherAndDateRange(@Param("teacherId") Long teacherId,
                                                         @Param("fromDate") LocalDate fromDate,
                                                         @Param("toDate") LocalDate toDate);

    /**
     * UC-59: "Lịch học của tôi" (Học sinh) — mọi buổi học của các lớp
     * học sinh đang ghi danh (classIds đã lọc ACTIVE ở Service), lọc theo
     * khoảng ngày tùy chọn (null = không giới hạn). Đối xứng với
     * findByPrimaryTeacherAndDateRange (UC-58) — khác ở chỗ lọc theo
     * nhiều classId thay vì 1 teacherId.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.id IN (:classIds)
            AND (cast(:fromDate as date) IS NULL OR cs.sessionDate >= :fromDate)
            AND (cast(:toDate as date) IS NULL OR cs.sessionDate <= :toDate)
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findBySchoolClassIdInAndDateRange(@Param("classIds") List<Long> classIds,
                                                          @Param("fromDate") LocalDate fromDate,
                                                          @Param("toDate") LocalDate toDate);

    /**
     * Bổ sung ngoài SDD gốc, xác nhận 2026-08-17: nguồn lịch dạy cho trang
     * roster "Lịch làm việc" toàn công ty (EmployeeScheduleService) —
     * teacherIds LUÔN non-null/non-empty (Service tự chặn danh sách rỗng
     * trước khi gọi, xem ClassSessionService.listForScheduleOverview).
     * siteIds/classId optional (null = không lọc). siteIds đổi từ Long đơn
     * sang List (UC-71, bổ sung ngoài SDD gốc, đã xác nhận với người dùng)
     * để hỗ trợ Quản lý điểm trường phụ trách NHIỀU điểm trường cùng lúc
     * (site_managers cho phép N site/1 người) khi site-scoping trang "Lịch
     * làm việc" — xem EmployeeScheduleService.resolveAllowedSiteIds.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.primaryTeacher.id IN :teacherIds
            AND (:siteIds IS NULL OR cs.schoolClass.site.id IN :siteIds)
            AND (:classId IS NULL OR cs.schoolClass.id = :classId)
            AND cs.sessionDate BETWEEN :fromDate AND :toDate
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findByPrimaryTeacherIdInAndFiltersAndDateRange(@Param("teacherIds") List<Long> teacherIds,
                                                                       @Param("siteIds") List<Long> siteIds,
                                                                       @Param("classId") Long classId,
                                                                       @Param("fromDate") LocalDate fromDate,
                                                                       @Param("toDate") LocalDate toDate);

    /**
     * "Buổi N" hiển thị FE: đếm số buổi đứng TRƯỚC buổi này (theo
     * sessionDate rồi id), kể cả CANCELLED — bổ sung ngoài SDD gốc, đã
     * xác nhận với người dùng 2026-07-29.
     */
    @Query("""
            SELECT COUNT(s) FROM ClassSession s
            WHERE s.schoolClass.id = :classId
            AND (s.sessionDate < :sessionDate OR (s.sessionDate = :sessionDate AND s.id < :sessionId))
            """)
    long countEarlierSessions(@Param("classId") Long classId, @Param("sessionDate") LocalDate sessionDate,
                               @Param("sessionId") Long sessionId);

    /** Buổi học hôm nay của 1 lớp (tab Nhận xét, bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29). */
    List<ClassSession> findBySchoolClassIdAndSessionDate(Long classId, LocalDate sessionDate);

    /** Liên kết buổi hủy↔bù (bổ sung ngoài SDD gốc, đã xác nhận với người dùng 2026-07-29). */
    List<ClassSession> findBySchoolClassIdAndStatus(Long classId, ClassSession.Status status);

    /** "1 buổi hủy — đúng 1 buổi bù": true nếu đã có buổi MAKEUP nào liên kết tới buổi này. */
    boolean existsByMakeupForSessionId(Long sessionId);

    /**
     * UC-18 changeTeacher (bổ sung ngoài SDD gốc, xác nhận 2026-08-13):
     * các buổi học tương lai còn SCHEDULED, cùng loại giáo viên vừa đổi —
     * ứng viên để cascade cập nhật giáo viên phụ trách (Service tự loại
     * trừ buổi đang có dạy thay active qua LeaveSubstitutionRepository).
     */
    List<ClassSession> findBySchoolClassIdAndTeacherTypeAndStatusAndSessionDateGreaterThanEqual(
            Long classId, ClassSession.TeacherType teacherType, ClassSession.Status status, LocalDate fromDate);

    /**
     * Lưới thời khóa biểu toàn điểm trường theo tuần (bổ sung ngoài SDD
     * gốc, xác nhận 2026-08-19) — mọi buổi của MỌI lớp thuộc 1 site, lọc
     * theo khoảng ngày. Đối xứng findBySchoolClassIdInAndDateRange (UC-59)
     * nhưng lọc theo site thay vì danh sách classId.
     */
    @Query("""
            SELECT cs FROM ClassSession cs
            WHERE cs.schoolClass.site.id = :siteId
            AND cs.sessionDate BETWEEN :fromDate AND :toDate
            ORDER BY cs.sessionDate ASC, cs.startTime ASC
            """)
    List<ClassSession> findBySiteIdAndDateRange(@Param("siteId") Long siteId,
                                                 @Param("fromDate") LocalDate fromDate,
                                                 @Param("toDate") LocalDate toDate);
}
