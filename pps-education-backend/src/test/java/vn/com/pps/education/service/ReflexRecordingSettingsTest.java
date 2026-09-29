package vn.com.pps.education.service;

import com.fasterxml.jackson.databind.node.BooleanNode;
import org.junit.jupiter.api.Test;
import vn.com.pps.education.domain.SystemSetting;
import vn.com.pps.education.repository.SystemSettingRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** V199 — công tắc bộ lọc thu âm ở Cài đặt hệ thống. Test thuần Service logic, mock repository. */
class ReflexRecordingSettingsTest {

    private final SystemSettingRepository repository = mock(SystemSettingRepository.class);
    private final ReflexRecordingSettings settings = new ReflexRecordingSettings(repository);

    @Test
    void config_UC23b_readsTheSwitchFromSystemSettings() {
        SystemSetting on = new SystemSetting();
        on.setSettingValue(BooleanNode.TRUE);
        when(repository.findBySettingKey(ReflexRecordingSettings.FILTER_ENABLED)).thenReturn(Optional.of(on));

        assertThat(settings.config().filterEnabled()).isTrue();
    }

    @Test
    void config_UC23b_missingKey_meansFilterOff_insteadOfBreakingRecording() {
        when(repository.findBySettingKey(ReflexRecordingSettings.FILTER_ENABLED)).thenReturn(Optional.empty());

        assertThat(settings.config().filterEnabled()).isFalse();
    }
}
