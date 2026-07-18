package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumSubject;
import vn.com.pps.education.domain.Lesson;
import vn.com.pps.education.domain.LessonHistory;
import vn.com.pps.education.domain.LessonMaterial;
import vn.com.pps.education.domain.SchoolClass;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.AddLessonMaterialRequest;
import vn.com.pps.education.dto.CreateLessonRequest;
import vn.com.pps.education.dto.LessonMaterialResponse;
import vn.com.pps.education.dto.LessonResponse;
import vn.com.pps.education.dto.UpdateLessonRequest;
import vn.com.pps.education.exception.InvalidLessonScopeException;
import vn.com.pps.education.exception.NotAssignedTeacherForClassException;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassTeacherRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.CurriculumSubjectRepository;
import vn.com.pps.education.repository.LessonHistoryRepository;
import vn.com.pps.education.repository.LessonMaterialRepository;
import vn.com.pps.education.repository.LessonRepository;
import vn.com.pps.education.repository.SchoolClassRepository;
import vn.com.pps.education.repository.UserRepository;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UC-23: Quản lý bài giảng (FR-LMS-01). Xem docs/uc/phan-he-07-lms-portal.md.
 *
 * Tệp (video/PDF) được xem là đã upload lên CDN/Object Storage từ trước
 * (NFR-TECH-07) — Service chỉ nhận URL phân phối đã có sẵn, không tự làm
 * multipart upload (không có hạ tầng CDN trong phạm vi backend hiện tại,
 * giống pattern students.portrait_url).
 */
@Service
public class LessonService {

    private final LessonRepository lessonRepository;
    private final LessonMaterialRepository lessonMaterialRepository;
    private final LessonHistoryRepository lessonHistoryRepository;
    private final CurriculumRepository curriculumRepository;
    private final SchoolClassRepository schoolClassRepository;
    private final CurriculumSubjectRepository curriculumSubjectRepository;
    private final ClassTeacherRepository classTeacherRepository;
    private final UserRepository userRepository;

    public LessonService(LessonRepository lessonRepository,
                          LessonMaterialRepository lessonMaterialRepository,
                          LessonHistoryRepository lessonHistoryRepository,
                          CurriculumRepository curriculumRepository,
                          SchoolClassRepository schoolClassRepository,
                          CurriculumSubjectRepository curriculumSubjectRepository,
                          ClassTeacherRepository classTeacherRepository,
                          UserRepository userRepository) {
        this.lessonRepository = lessonRepository;
        this.lessonMaterialRepository = lessonMaterialRepository;
        this.lessonHistoryRepository = lessonHistoryRepository;
        this.curriculumRepository = curriculumRepository;
        this.schoolClassRepository = schoolClassRepository;
        this.curriculumSubjectRepository = curriculumSubjectRepository;
        this.classTeacherRepository = classTeacherRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow bước 3-4: tạo bài giảng mới (metadata), gán vào khung chương trình hoặc lớp cụ thể. */
    @Transactional
    public LessonResponse createLesson(CreateLessonRequest request, Long actorUserId) {
        if ((request.curriculumId() == null) == (request.classId() == null)) {
            throw new InvalidLessonScopeException(
                    "Bài giảng phải gán đúng 1 trong 2: curriculumId (bài chung) hoặc classId (bài riêng lớp), không cả hai hoặc không cái nào.");
        }
        User actor = getUserOrThrow(actorUserId);

        Lesson lesson = new Lesson();
        lesson.setCode(request.code());
        lesson.setTitle(request.title());
        if (request.curriculumId() != null) {
            Curriculum curriculum = curriculumRepository.findByIdAndDeletedAtIsNull(request.curriculumId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + request.curriculumId()));
            requireAssignedTeacherForCurriculum(request.curriculumId(), actorUserId);
            lesson.setCurriculum(curriculum);
        } else {
            SchoolClass schoolClass = getClassOrThrow(request.classId());
            requireAssignedTeacher(request.classId(), actorUserId);
            lesson.setSchoolClass(schoolClass);
        }
        if (request.subjectId() != null) {
            lesson.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        lesson.setLessonOrder(request.lessonOrder());
        lesson.setLessonType(Lesson.LessonType.valueOf(request.lessonType()));
        lesson.setDurationMinutes(request.durationMinutes());
        lesson.setCreatedBy(actor);
        lesson = lessonRepository.save(lesson);

        writeHistory(lesson, actor, LessonHistory.Action.CREATED);
        return toResponse(lesson);
    }

    /** Main Flow bước 5: sửa metadata hoặc gỡ bài giảng khỏi kho (status=ARCHIVED). */
    @Transactional
    public LessonResponse updateLesson(Long id, UpdateLessonRequest request, Long actorUserId) {
        Lesson lesson = getLessonOrThrow(id);
        requireOwnerScope(lesson, actorUserId);
        User actor = getUserOrThrow(actorUserId);

        lesson.setTitle(request.title());
        if (request.subjectId() != null) {
            lesson.setSubject(curriculumSubjectOrThrow(request.subjectId()));
        }
        lesson.setLessonOrder(request.lessonOrder());
        lesson.setDurationMinutes(request.durationMinutes());
        Lesson.Status newStatus = Lesson.Status.valueOf(request.status());
        if (newStatus == Lesson.Status.PUBLISHED && lesson.getStatus() != Lesson.Status.PUBLISHED) {
            lesson.setPublishedAt(OffsetDateTime.now());
        }
        lesson.setStatus(newStatus);
        lesson = lessonRepository.save(lesson);

        writeHistory(lesson, actor, LessonHistory.Action.UPDATED);
        return toResponse(lesson);
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listByClass(Long classId) {
        return lessonRepository.findBySchoolClassIdOrderByLessonOrder(classId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<LessonResponse> listByCurriculum(Long curriculumId) {
        return lessonRepository.findByCurriculumIdOrderByLessonOrder(curriculumId).stream().map(this::toResponse).toList();
    }

    /** Main Flow bước 1-2: đính kèm tệp đã upload CDN vào bài giảng. */
    @Transactional
    public LessonMaterialResponse addMaterial(Long lessonId, AddLessonMaterialRequest request, Long actorUserId) {
        Lesson lesson = getLessonOrThrow(lessonId);
        requireOwnerScope(lesson, actorUserId);

        LessonMaterial material = new LessonMaterial();
        material.setLesson(lesson);
        material.setMaterialType(LessonMaterial.MaterialType.valueOf(request.materialType()));
        material.setTitle(request.title());
        material.setFileUrl(request.fileUrl());
        material.setFileSizeBytes(request.fileSizeBytes());
        material.setDurationSeconds(request.durationSeconds());
        material.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        material.setDownloadable(request.isDownloadable());
        material = lessonMaterialRepository.save(material);
        return toResponse(material);
    }

    @Transactional(readOnly = true)
    public List<LessonMaterialResponse> listMaterials(Long lessonId) {
        return lessonMaterialRepository.findByLessonIdOrderByDisplayOrder(lessonId).stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private void requireOwnerScope(Lesson lesson, Long actorUserId) {
        if (lesson.getCurriculum() != null) {
            requireAssignedTeacherForCurriculum(lesson.getCurriculum().getId(), actorUserId);
        } else {
            requireAssignedTeacher(lesson.getSchoolClass().getId(), actorUserId);
        }
    }

    private void requireAssignedTeacher(Long classId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClassIdAndTeacherIdAndAssignedToIsNull(classId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không được phân công giảng dạy lớp id=" + classId + ".");
        }
    }

    private void requireAssignedTeacherForCurriculum(Long curriculumId, Long actorUserId) {
        if (!classTeacherRepository.existsBySchoolClass_CurriculumIdAndTeacherIdAndAssignedToIsNull(curriculumId, actorUserId)) {
            throw new NotAssignedTeacherForClassException(
                    "Tài khoản id=" + actorUserId + " không dạy lớp nào thuộc khung chương trình id=" + curriculumId + ".");
        }
    }

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private SchoolClass getClassOrThrow(Long id) {
        return schoolClassRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy lớp học id=" + id));
    }

    private CurriculumSubject curriculumSubjectOrThrow(Long id) {
        return curriculumSubjectRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy học phần id=" + id));
    }

    private Lesson getLessonOrThrow(Long id) {
        return lessonRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bài giảng id=" + id));
    }

    private void writeHistory(Lesson lesson, User actor, LessonHistory.Action action) {
        LessonHistory history = new LessonHistory();
        history.setLesson(lesson);
        history.setChangedBy(actor);
        history.setAction(action);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("title", lesson.getTitle());
        snapshot.put("lessonType", lesson.getLessonType().name());
        snapshot.put("status", lesson.getStatus().name());
        history.setDetails(snapshot);
        lessonHistoryRepository.save(history);
    }

    private LessonResponse toResponse(Lesson l) {
        return new LessonResponse(
                l.getId(), l.getCode(), l.getTitle(),
                l.getCurriculum() == null ? null : l.getCurriculum().getId(),
                l.getSchoolClass() == null ? null : l.getSchoolClass().getId(),
                l.getSubject() == null ? null : l.getSubject().getId(),
                l.getLessonOrder(), l.getLessonType().name(), l.getDurationMinutes(),
                l.getStatus().name(), l.getPublishedAt(), l.getCreatedBy().getId());
    }

    private LessonMaterialResponse toResponse(LessonMaterial m) {
        return new LessonMaterialResponse(
                m.getId(), m.getLesson().getId(), m.getMaterialType().name(), m.getTitle(), m.getFileUrl(),
                m.getFileSizeBytes(), m.getDurationSeconds(), m.getDisplayOrder(), m.isDownloadable());
    }
}
