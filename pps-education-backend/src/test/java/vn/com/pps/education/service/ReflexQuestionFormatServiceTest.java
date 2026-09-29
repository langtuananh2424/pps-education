package vn.com.pps.education.service;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.ReflexQuestionFormat;
import vn.com.pps.education.domain.Curriculum;
import vn.com.pps.education.domain.ReviewVideoQuestion;
import vn.com.pps.education.dto.ReflexQuestionFormatOptionResponse;
import vn.com.pps.education.repository.CurriculumRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** V200 — UC-23b: dạng đề của câu hỏi Video phản xạ. Test thuần Service logic, mock repository/storage. */
class ReflexQuestionFormatServiceTest {

    private final CurriculumRepository curriculumRepository = mock(CurriculumRepository.class);
    private final MediaStorageService mediaStorageService = mock(MediaStorageService.class);
    private final ReflexQuestionFormatService service = new ReflexQuestionFormatService(curriculumRepository, mediaStorageService);

    private static Curriculum curriculum(Curriculum.GradeLevel grade, Curriculum.Track track) {
        Curriculum c = new Curriculum();
        c.setGradeLevel(grade);
        c.setTrack(track);
        return c;
    }

    private static final Curriculum G8_CAM = curriculum(Curriculum.GradeLevel.GRADE_8, Curriculum.Track.CAMBRIDGE);

    @Test
    void optionsForCurriculum_UC23b_listsFormatsWithRecommendedSeconds() {
        when(curriculumRepository.findById(1L)).thenReturn(Optional.of(G8_CAM));

        List<ReflexQuestionFormatOptionResponse> options = service.optionsForCurriculum(1L);

        assertThat(options).extracting(ReflexQuestionFormatOptionResponse::format).containsExactly("PET4", "PICTURE");
        assertThat(options).extracting(ReflexQuestionFormatOptionResponse::recommendedSeconds).containsExactly(60, 60);
        assertThat(options).extracting(ReflexQuestionFormatOptionResponse::requiresPictureBrief).containsExactly(false, true);
    }

    @Test
    void applyTo_UC23b_picture_savesTrimmedBriefAndValidatedImage() {
        ReviewVideoQuestion q = new ReviewVideoQuestion();

        service.applyTo(q, G8_CAM, "PICTURE", " https://media.example/lms/review-videos/frame.jpg ", "  Công viên.\nTrẻ em chơi bóng.  ");

        assertThat(q.getQuestionFormat()).isEqualTo(ReflexQuestionFormat.PICTURE);
        assertThat(q.getPictureBrief()).isEqualTo("Công viên.\nTrẻ em chơi bóng.");
        assertThat(q.getPictureImageUrl()).isEqualTo("https://media.example/lms/review-videos/frame.jpg");
        verify(mediaStorageService).requireStoredUrl("https://media.example/lms/review-videos/frame.jpg");
    }

    @Test
    void applyTo_UC23b_A_pictureWithoutBrief_isRejected() {
        assertThatThrownBy(() -> service.applyTo(new ReviewVideoQuestion(), G8_CAM, "PICTURE", null, "  "))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mô tả tranh");
    }

    @Test
    void applyTo_UC23b_A_pictureImageNotStoredBySystem_isRejected() {
        doThrow(new IllegalArgumentException("URL không hợp lệ")).when(mediaStorageService).requireStoredUrl("https://evil.example/a.jpg");

        assertThatThrownBy(() -> service.applyTo(new ReviewVideoQuestion(), G8_CAM, "PICTURE", "https://evil.example/a.jpg", "Công viên."))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void applyTo_UC23b_A_formatNotAllowedForGradeTrack_isRejected() {
        assertThatThrownBy(() -> service.applyTo(new ReviewVideoQuestion(), G8_CAM, "PART2", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("PET4, PICTURE");
        assertThatThrownBy(() -> service.applyTo(new ReviewVideoQuestion(), G8_CAM, "ESSAY", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("không hợp lệ");
    }

    @Test
    void applyTo_UC23b_A_curriculumWithoutRubric_rejectsAnyFormat() {
        Curriculum g9Cam = curriculum(Curriculum.GradeLevel.GRADE_9, Curriculum.Track.CAMBRIDGE);
        assertThatThrownBy(() -> service.applyTo(new ReviewVideoQuestion(), g9Cam, "SHORT", null, null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("để trống dạng đề");
    }

    @Test
    void applyTo_UC23b_switchingAwayFromPicture_orClearingFormat_dropsOldPictureData() {
        ReviewVideoQuestion q = new ReviewVideoQuestion();
        service.applyTo(q, G8_CAM, "PICTURE", null, "Công viên.");

        service.applyTo(q, G8_CAM, "PET4", "https://media.example/x.jpg", "mô tả cũ");
        assertThat(q.getQuestionFormat()).isEqualTo(ReflexQuestionFormat.PET4);
        assertThat(q.getPictureBrief()).isNull();
        assertThat(q.getPictureImageUrl()).isNull();

        service.applyTo(q, G8_CAM, null, null, null);
        assertThat(q.getQuestionFormat()).isNull();
    }
}
