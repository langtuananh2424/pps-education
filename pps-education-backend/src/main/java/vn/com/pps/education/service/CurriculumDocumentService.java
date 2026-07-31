package vn.com.pps.education.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.com.pps.education.domain.ClassEnrollment;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.CurriculumDocument;
import vn.com.pps.education.domain.Student;
import vn.com.pps.education.domain.User;
import vn.com.pps.education.dto.CreateCurriculumDocumentRequest;
import vn.com.pps.education.dto.CurriculumDocumentResponse;
import vn.com.pps.education.dto.UpdateCurriculumDocumentRequest;
import vn.com.pps.education.exception.ResourceNotFoundException;
import vn.com.pps.education.repository.ClassEnrollmentRepository;
import vn.com.pps.education.repository.CurriculumDocumentRepository;
import vn.com.pps.education.repository.CurriculumRepository;
import vn.com.pps.education.repository.StudentRepository;
import vn.com.pps.education.repository.UserRepository;

import java.util.List;

/**
 * UC-60: Kho tài liệu tham khảo (FR-LMS-13 — bổ sung ngoài SDD gốc, đã
 * xác nhận với người dùng). Xem docs/uc/phan-he-07-lms-portal.md.
 *
 * Độc lập với LessonService (UC-23/23a) — tài liệu gắn theo curriculum,
 * không gắn 1 bài giảng cụ thể nào. Authorization qua
 * @PreAuthorize("hasPermission(null,'lms.document.create/update/view')") ở
 * CurriculumDocumentController cho thao tác ghi (Hybrid PBAC — V41/V62);
 * đọc tự-xem của Học sinh check quyền ngay trong Service (giống pattern
 * LessonService.listByClass).
 */
@Service
public class CurriculumDocumentService {

    private final CurriculumDocumentRepository curriculumDocumentRepository;
    private final CurriculumRepository curriculumRepository;
    private final ClassEnrollmentRepository classEnrollmentRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;

    public CurriculumDocumentService(CurriculumDocumentRepository curriculumDocumentRepository,
                                      CurriculumRepository curriculumRepository,
                                      ClassEnrollmentRepository classEnrollmentRepository,
                                      StudentRepository studentRepository,
                                      UserRepository userRepository) {
        this.curriculumDocumentRepository = curriculumDocumentRepository;
        this.curriculumRepository = curriculumRepository;
        this.classEnrollmentRepository = classEnrollmentRepository;
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
    }

    /** Main Flow: GV/Admin upload tài liệu (đã có URL CDN), gắn vào 1 curriculum. */
    @Transactional
    public CurriculumDocumentResponse createDocument(CreateCurriculumDocumentRequest request, Long actorUserId) {
        Curriculum curriculum = curriculumOrThrow(request.curriculumId());
        User actor = userOrThrow(actorUserId);

        CurriculumDocument document = new CurriculumDocument();
        document.setCurriculum(curriculum);
        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setDocumentType(CurriculumDocument.DocumentType.valueOf(request.documentType()));
        document.setFileUrl(request.fileUrl());
        document.setCoverImageUrl(request.coverImageUrl());
        document.setDisplayOrder(request.displayOrder() == null ? 0 : request.displayOrder());
        document.setCreatedBy(actor);
        document = curriculumDocumentRepository.save(document);
        return toResponse(document);
    }

    /** Sửa metadata hoặc publish/archive (status). */
    @Transactional
    public CurriculumDocumentResponse updateDocument(Long id, UpdateCurriculumDocumentRequest request, Long actorUserId) {
        CurriculumDocument document = documentOrThrow(id);
        document.setTitle(request.title());
        document.setDescription(request.description());
        document.setCoverImageUrl(request.coverImageUrl());
        if (request.displayOrder() != null) {
            document.setDisplayOrder(request.displayOrder());
        }
        document.setStatus(CurriculumDocument.Status.valueOf(request.status()));
        document = curriculumDocumentRepository.save(document);
        return toResponse(document);
    }

    /** Staff (lms.document.view) — xem mọi status của 1 curriculum. */
    @Transactional(readOnly = true)
    public List<CurriculumDocumentResponse> listByCurriculum(Long curriculumId) {
        return curriculumDocumentRepository.findByCurriculumIdOrderByDisplayOrder(curriculumId).stream()
                .map(this::toResponse).toList();
    }

    /**
     * Bổ sung: Học sinh tự xem kho tài liệu theo curriculum của (các) lớp
     * đang ghi danh ACTIVE — chỉ PUBLISHED. curriculumIdFilter tùy chọn
     * (ngữ cảnh "lớp đang xem" — UC-42).
     */
    @Transactional(readOnly = true)
    public List<CurriculumDocumentResponse> listMyDocuments(Long actorUserId, Long curriculumIdFilter) {
        Student student = studentRepository.findByUserId(actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản id=" + actorUserId + " không có hồ sơ học sinh."));
        List<Long> curriculumIds = classEnrollmentRepository.findByStudentId(student.getId()).stream()
                .filter(e -> e.getStatus() == ClassEnrollment.Status.ACTIVE)
                .map(e -> e.getSchoolClass().getCurriculum().getId())
                .distinct()
                .filter(id -> curriculumIdFilter == null || id.equals(curriculumIdFilter))
                .toList();
        if (curriculumIds.isEmpty()) {
            return List.of();
        }
        return curriculumDocumentRepository
                .findByCurriculumIdInAndStatusOrderByDisplayOrder(curriculumIds, CurriculumDocument.Status.PUBLISHED)
                .stream().map(this::toResponse).toList();
    }

    // ===================== Helpers =====================

    private Curriculum curriculumOrThrow(Long id) {
        return curriculumRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khung chương trình id=" + id));
    }

    private CurriculumDocument documentOrThrow(Long id) {
        return curriculumDocumentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu id=" + id));
    }

    private User userOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản id=" + id));
    }

    private CurriculumDocumentResponse toResponse(CurriculumDocument d) {
        return new CurriculumDocumentResponse(
                d.getId(), d.getCurriculum().getId(), d.getTitle(), d.getDescription(),
                d.getDocumentType().name(), d.getFileUrl(), d.getDisplayOrder(), d.getStatus().name(),
                d.getCreatedBy().getId(), d.getCoverImageUrl());
    }
}
