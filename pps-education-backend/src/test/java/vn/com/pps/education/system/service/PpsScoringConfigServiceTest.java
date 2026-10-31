package vn.com.pps.education.system.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.com.pps.education.auth.domain.User;
import vn.com.pps.education.auth.repository.UserRepository;
import vn.com.pps.education.common.pronunciation.PpsConfig;
import vn.com.pps.education.lms.service.PpsConfigHolder;
import vn.com.pps.education.system.domain.PpsScoringConfig;
import vn.com.pps.education.system.domain.PpsScoringConfigHistory;
import vn.com.pps.education.system.dto.PpsScoringConfigResponse;
import vn.com.pps.education.system.repository.PpsScoringConfigHistoryRepository;
import vn.com.pps.education.system.repository.PpsScoringConfigRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Trang cấu hình PPS Hybrid v2: kiểm tra, lưu, lịch sử, khôi phục mặc định và nạp vào bộ chấm. */
class PpsScoringConfigServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PpsScoringConfigRepository repository = mock(PpsScoringConfigRepository.class);
    private final PpsScoringConfigHistoryRepository historyRepository = mock(PpsScoringConfigHistoryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final PpsConfigHolder holder = new PpsConfigHolder();
    private final PpsScoringConfigService service = new PpsScoringConfigService(repository, historyRepository, userRepository, holder, mapper);
    private PpsScoringConfig row;

    @BeforeEach
    void setUp() {
        row = new PpsScoringConfig();
        row.setOverrides(mapper.createObjectNode());
        User actor = mock(User.class);
        when(actor.getFullName()).thenReturn("Quản trị viên");
        when(userRepository.findById(5L)).thenReturn(Optional.of(actor));
        when(repository.findById(PpsScoringConfig.SINGLETON_ID)).thenAnswer(i -> Optional.of(row));
        when(repository.save(any(PpsScoringConfig.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void getListsEveryFieldWithItsDefaultAndFlagsNothingChangedInitially() {
        PpsScoringConfigResponse r = service.get();

        assertThat(r.fields()).hasSameSizeAs(PpsConfig.FIELDS);
        assertThat(r.overrides()).isEmpty();
        assertThat(r.fields()).allSatisfy(f -> {
            assertThat(f.changed()).isFalse();
            assertThat(f.value()).isEqualTo(f.defaultValue());
            assertThat(f.min()).isLessThan(f.max());
        });
        assertThat(r.weightGroups()).containsExactly("utterance.weights", "spontaneous.weights");
    }

    @Test
    void saveStoresOnlyChangedValuesWritesHistoryAndUpdatesTheScorer() {
        PpsScoringConfigResponse r = service.save(Map.of("word", Map.of("ending_penalty", 2.0)), "  siết phụ âm cuối  ", 5L);

        assertThat(r.overrides()).containsExactly(Map.entry("word.ending_penalty", 2.0));
        assertThat(r.fields()).filteredOn(f -> f.path().equals("word.ending_penalty")).singleElement()
                .satisfies(f -> {
                    assertThat(f.changed()).isTrue();
                    assertThat(f.value()).isEqualTo(2.0);
                });
        assertThat(row.getOverrides().get("word.ending_penalty").asDouble()).isEqualTo(2.0);
        assertThat(row.getOverrides().size()).isEqualTo(1);
        assertThat(holder.get().get("word.ending_penalty")).isEqualTo(2.0);
        assertThat(r.updatedByName()).isEqualTo("Quản trị viên");

        ArgumentCaptor<PpsScoringConfigHistory> h = ArgumentCaptor.forClass(PpsScoringConfigHistory.class);
        verify(historyRepository).save(h.capture());
        assertThat(h.getValue().getNote()).isEqualTo("siết phụ âm cuối");
        assertThat(h.getValue().getPrevious().size()).isZero();
        assertThat(h.getValue().getOverrides().get("word.ending_penalty").asDouble()).isEqualTo(2.0);
    }

    @Test
    void saveAcceptsFlatKeysAndTheExportedWrapper() {
        service.save(Map.of("config", Map.of("fluency.long_pause_s", 0.8)), null, 5L);

        assertThat(holder.get().get("fluency.long_pause_s")).isEqualTo(0.8);
    }

    @Test
    void parametersLeftOutGoBackToTheirDefaults() {
        service.save(Map.of("word.ending_penalty", 2.0), null, 5L);
        assertThat(holder.get().get("word.ending_penalty")).isEqualTo(2.0);

        PpsScoringConfigResponse r = service.save(Map.of("phone.floor", 0.9), null, 5L);

        assertThat(r.overrides()).containsExactly(Map.entry("phone.floor", 0.9));
        assertThat(holder.get().get("word.ending_penalty")).isEqualTo(1.5);
    }

    @Test
    void invalidValuesAreRejectedWithTheParameterNameAndNothingIsSaved() {
        assertThatThrownBy(() -> service.save(Map.of("word.ending_penalty", 99), null, 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("word.ending_penalty");
        assertThatThrownBy(() -> service.save(Map.of("khong.co.tham.so", 1), null, 5L))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("khong.co.tham.so");
        assertThatThrownBy(() -> service.save(Map.of("word.ending_penalty", "abc"), null, 5L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.save(Map.of("utterance.weights.accuracy", 0.9), null, 5L))
                .as("trọng số của Total không còn cộng bằng 1")
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("cộng bằng 1");

        verify(repository, never()).save(any());
        verify(historyRepository, never()).save(any());
        assertThat(holder.get()).isEqualTo(PpsConfig.defaults());
    }

    @Test
    void savingTheSameConfigAgainWritesNoHistory() {
        service.save(Map.of("word.ending_penalty", 2.0), null, 5L);
        org.mockito.Mockito.clearInvocations(historyRepository);

        service.save(Map.of("word.ending_penalty", 2.0), "không đổi", 5L);

        verify(historyRepository, never()).save(any());
    }

    @Test
    void resetRestoresTheSpecDefaultsAndIsRecordedInHistory() {
        service.save(Map.of("word.ending_penalty", 2.0), null, 5L);
        org.mockito.Mockito.clearInvocations(historyRepository);

        PpsScoringConfigResponse r = service.reset(null, 5L);

        assertThat(r.overrides()).isEmpty();
        assertThat(holder.get()).isEqualTo(PpsConfig.defaults());
        ArgumentCaptor<PpsScoringConfigHistory> h = ArgumentCaptor.forClass(PpsScoringConfigHistory.class);
        verify(historyRepository).save(h.capture());
        assertThat(h.getValue().getNote()).isEqualTo("Khôi phục mặc định");
        assertThat(h.getValue().getPrevious().get("word.ending_penalty").asDouble()).isEqualTo(2.0);
    }

    @Test
    void exportCanBeImportedBackUnchanged() {
        service.save(Map.of("whisper.cap.diff", 6.0, "fluency.target_wpm.7", 100), null, 5L);

        Map<String, Object> exported = service.export();
        service.reset(null, 5L);
        service.save(exported, null, 5L);

        assertThat(exported).containsKey("config");
        assertThat(holder.get().get("whisper.cap.diff")).isEqualTo(6.0);
        assertThat(holder.get().get("fluency.target_wpm.7")).isEqualTo(100.0);
    }

    @Test
    void reloadAppliesTheStoredConfigOnceAndSkipsWhenNothingChanged() {
        row.setOverrides(mapper.valueToTree(Map.of("word.ending_penalty", 3.0)));
        row.setUpdatedAt(OffsetDateTime.now());

        service.loadAtStartup();
        assertThat(holder.get().get("word.ending_penalty")).isEqualTo(3.0);

        holder.set(PpsConfig.defaults());   // nếu lần nạp sau bỏ qua thì giá trị tay này còn nguyên
        service.refresh();
        assertThat(holder.get()).as("DB không đổi thì không nạp lại").isEqualTo(PpsConfig.defaults());

        row.setUpdatedAt(OffsetDateTime.now().plusSeconds(1));
        service.refresh();
        assertThat(holder.get().get("word.ending_penalty")).isEqualTo(3.0);
    }

    @Test
    void corruptStoredConfigFallsBackToDefaultsInsteadOfBreakingStartup() {
        row.setOverrides(mapper.valueToTree(Map.of("word.ending_penalty", 999.0, "khong.co", 1.0)));
        row.setUpdatedAt(OffsetDateTime.now());

        service.loadAtStartup();

        assertThat(holder.get()).isEqualTo(PpsConfig.defaults());
        assertThat(service.get().overrides()).isEmpty();
    }

    @Test
    void historyIsMappedWithTheAuthorName() {
        PpsScoringConfigHistory h = new PpsScoringConfigHistory();
        h.setPrevious(mapper.createObjectNode());
        h.setOverrides(mapper.valueToTree(Map.of("phone.floor", 0.9)));
        h.setNote("thử");
        User u = mock(User.class);
        when(u.getFullName()).thenReturn("Quản trị viên");
        h.setChangedBy(u);
        when(historyRepository.findTop50ByOrderByCreatedAtDesc()).thenReturn(List.of(h));

        var out = service.history();

        assertThat(out).singleElement().satisfies(x -> {
            assertThat(x.changedByName()).isEqualTo("Quản trị viên");
            assertThat(x.overrides()).containsEntry("phone.floor", 0.9);
            assertThat(x.previous()).isEmpty();
            assertThat(x.note()).isEqualTo("thử");
        });
    }
}
