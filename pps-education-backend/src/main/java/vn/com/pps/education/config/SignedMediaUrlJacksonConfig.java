package vn.com.pps.education.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdScalarSerializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import vn.com.pps.education.media.service.MediaUrlSigner;

import java.io.IOException;
import java.util.List;

/**
 * Rà soát bảo mật 2026-09-28 (đã xác nhận với người dùng) - xem {@link MediaUrlSigner}. Gắn việc ký URL
 * file cá nhân vào tầng JSON của HTTP (chỉ ObjectMapper của MappingJackson2HttpMessageConverter, KHÔNG
 * đụng ObjectMapper dùng chung - tránh lưu nhầm URL đã ký vào cột JSON hay gửi sang dịch vụ AI):
 * - Response: mọi chuỗi là URL gốc file cá nhân -> URL có chữ ký, có hạn. Không phải sửa từng DTO, DTO
 *   thêm sau này cũng tự được ký.
 * - Request: URL đã ký do client gửi ngược lên -> URL gốc, nên DB luôn lưu URL gốc như trước.
 * Tắt hẳn (không thay ObjectMapper) khi chưa cấu hình signed-url-endpoint.
 */
@Configuration
public class SignedMediaUrlJacksonConfig implements WebMvcConfigurer {

    private final MediaUrlSigner mediaUrlSigner;

    public SignedMediaUrlJacksonConfig(MediaUrlSigner mediaUrlSigner) {
        this.mediaUrlSigner = mediaUrlSigner;
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        if (!mediaUrlSigner.enabled()) {
            return;
        }
        SimpleModule module = new SimpleModule("SignedMediaUrl");
        module.addSerializer(String.class, new SigningStringSerializer(mediaUrlSigner));
        module.addDeserializer(String.class, new CanonicalizingStringDeserializer(mediaUrlSigner));
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                ObjectMapper mapper = jackson.getObjectMapper().copy();
                mapper.registerModule(module);
                jackson.setObjectMapper(mapper);
            }
        }
    }

    static final class SigningStringSerializer extends StdScalarSerializer<String> {

        private final transient MediaUrlSigner signer;

        SigningStringSerializer(MediaUrlSigner signer) {
            super(String.class, false);
            this.signer = signer;
        }

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(signer.sign(value));
        }
    }

    static final class CanonicalizingStringDeserializer extends StdScalarDeserializer<String> {

        private final transient MediaUrlSigner signer;

        CanonicalizingStringDeserializer(MediaUrlSigner signer) {
            super(String.class);
            this.signer = signer;
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            return signer.toCanonical(StringDeserializer.instance.deserialize(p, ctxt));
        }
    }
}
