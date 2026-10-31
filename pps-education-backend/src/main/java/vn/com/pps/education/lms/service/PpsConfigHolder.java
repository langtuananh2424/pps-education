package vn.com.pps.education.lms.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.common.pronunciation.PpsConfig;

/**
 * Giữ cấu hình đang dùng của bộ chấm PPS Hybrid v2. Hiện luôn là giá trị mặc định của đặc tả; chỗ này là nơi cắm bảng cấu hình
 * trong DB (trang Cài đặt AI) mà không phải đổi các service chấm. Gán nguyên tử (volatile) nên bài đang chấm dở vẫn giữ bản cũ.
 */
@Component
public class PpsConfigHolder {

    private volatile PpsConfig current = PpsConfig.defaults();

    public PpsConfig get() {
        return current;
    }

    public void set(PpsConfig config) {
        this.current = config == null ? PpsConfig.defaults() : config;
    }
}
