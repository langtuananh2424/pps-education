package vn.com.pps.education.lms.service;

import vn.com.pps.education.media.service.MediaStorageService;

import org.junit.jupiter.api.Test;
import vn.com.pps.education.common.AiTokenUsage;
import vn.com.pps.education.lms.dto.ReflexPictureBriefResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** V200 — UC-23b dạng tả tranh: AI viết NHÁP mô tả tranh. Test thuần Service logic, mock storage + AI. */
class ReflexPictureServiceTest {

    private static final String IMAGE_URL = "https://media.example/lms/review-videos/generated/frame.jpg";
    private static final AiTokenUsage USAGE = new AiTokenUsage("gemini-3.6-flash", false, 900, 0, 60, 0, 1200);

    private final MediaStorageService storage = mock(MediaStorageService.class);
    private final NineRouterAiClient ai = mock(NineRouterAiClient.class);
    private final ClaudeAiClient claude = mock(ClaudeAiClient.class);
    private final ReflexPictureService service = new ReflexPictureService(storage, ai, claude);

    private void imageStored(String contentType) {
        when(storage.downloadWithContentType(IMAGE_URL)).thenReturn(new MediaStorageService.DownloadedFile(new byte[]{1, 2, 3}, contentType));
    }

    @Test
    void draftBrief_UC23b_MainFlow_cleansNumberingAndKeepsAtMostThreeLines() {
        imageStored("image/jpeg");
        when(ai.chatWithImage(eq(ReflexPictureService.BRIEF_SYSTEM_PROMPT), any(), any(), eq("image/jpeg"), any()))
                .thenReturn(new NineRouterAiClient.AiTextResponse("1. Công viên có bãi cỏ rộng.\n\n- Sáu trẻ em đang chơi bóng.\n**Trời nắng.**\nDòng thừa.", USAGE));

        ReflexPictureBriefResponse r = service.draftBrief(IMAGE_URL);

        assertThat(r.brief()).isEqualTo("Công viên có bãi cỏ rộng.\nSáu trẻ em đang chơi bóng.\nTrời nắng.");
        assertThat(r.pictureFound()).isTrue();
        assertThat(r.aiAvailable()).isTrue();
    }

    @Test
    void draftBrief_UC23b_A_noPictureInFrame_returnsEmptyDraft() {
        imageStored("image/jpeg");
        when(ai.chatWithImage(any(), any(), any(), any(), any())).thenReturn(new NineRouterAiClient.AiTextResponse("KHÔNG THẤY TRANH", USAGE));

        ReflexPictureBriefResponse r = service.draftBrief(IMAGE_URL);

        assertThat(r.brief()).isEmpty();
        assertThat(r.pictureFound()).isFalse();
        assertThat(r.aiAvailable()).isTrue();
    }

    @Test
    void draftBrief_UC23b_A_aiFails_tellsTeacherToTypeTheBrief() {
        imageStored("image/png");
        when(ai.chatWithImage(any(), any(), any(), any(), any())).thenReturn(null);

        ReflexPictureBriefResponse r = service.draftBrief(IMAGE_URL);

        assertThat(r.aiAvailable()).isFalse();
        assertThat(r.brief()).isEmpty();
    }

    @Test
    void draftBrief_UC23b_A_notAnImage_isRejected() {
        imageStored("video/mp4");

        assertThatThrownBy(() -> service.draftBrief(IMAGE_URL)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void draftBrief_claudeDirectEnabled_usesHaikuAndSkipsNineRouter() {
        imageStored("image/jpeg");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeDirectEnabled", true);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeModel", "claude-haiku-5-5");
        when(claude.isConfigured()).thenReturn(true);
        when(claude.supportsImage(eq("image/jpeg"), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(claude.chatWithImage(any(), any(), any(), eq("image/jpeg"), eq("claude-haiku-5-5")))
                .thenReturn(new NineRouterAiClient.AiTextResponse("Công viên có bãi cỏ.", USAGE));

        ReflexPictureBriefResponse r = service.draftBrief(IMAGE_URL);

        assertThat(r.brief()).isEqualTo("Công viên có bãi cỏ.");
        org.mockito.Mockito.verifyNoInteractions(ai);
    }

    @Test
    void draftBrief_claudeDirectFails_fallsBackToNineRouter() {
        imageStored("image/jpeg");
        org.springframework.test.util.ReflectionTestUtils.setField(service, "claudeDirectEnabled", true);
        when(claude.isConfigured()).thenReturn(true);
        when(claude.supportsImage(any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(true);
        when(claude.chatWithImage(any(), any(), any(), any(), any())).thenReturn(null);
        when(ai.chatWithImage(any(), any(), any(), any(), any()))
                .thenReturn(new NineRouterAiClient.AiTextResponse("Một con chó.", USAGE));

        assertThat(service.draftBrief(IMAGE_URL).brief()).isEqualTo("Một con chó.");
    }
}
