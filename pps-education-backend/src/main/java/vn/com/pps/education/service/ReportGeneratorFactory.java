package vn.com.pps.education.service;

import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.ReportTemplate;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** UC-68: Factory Method — chọn đúng {@link ReportDataResolver} theo loại báo cáo. */
@Component
public class ReportGeneratorFactory {

    private final Map<ReportTemplate.TemplateType, ReportDataResolver> resolversByType;

    public ReportGeneratorFactory(List<ReportDataResolver> resolvers) {
        this.resolversByType = resolvers.stream()
                .collect(Collectors.toMap(ReportDataResolver::supports, Function.identity()));
    }

    public ReportDataResolver getResolver(ReportTemplate.TemplateType type) {
        ReportDataResolver resolver = resolversByType.get(type);
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "Chưa hỗ trợ xuất báo cáo loại " + type + " ở giai đoạn này.");
        }
        return resolver;
    }
}
