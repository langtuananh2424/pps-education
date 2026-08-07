package vn.com.pps.education.service.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.com.pps.education.domain.Notification;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mẫu email cố định theo NotificationType (Phụ huynh/Giáo viên/Quản lý điểm
 * trường) — nội dung do người dùng cung cấp trực tiếp, đã xác nhận
 * 2026-08-07. Phần thân mẫu (lời chào, đoạn nội dung, footer) giữ NGUYÊN
 * VĂN không tự sáng tác thêm; riêng dòng "Học viên/Lớp" là bổ sung ngoài
 * mẫu gốc, đã xác nhận với người dùng cùng ngày để định danh đúng học viên
 * trên dữ liệu thật — chèn ngay sau câu "Đây là email tự động...", trước
 * đoạn nội dung. Type nào không truyền được studentName/className (VD
 * thông báo theo lớp/theo lô, không phải 1 học viên cụ thể) thì bỏ qua
 * dòng đó, không hiện chỗ trống. Type không có trong {@link #templates}
 * thì {@link EmailNotificationSender} tự dùng lại title/content của
 * Notification (hành vi cũ) — không tự bịa mẫu cho type chưa được xác
 * nhận nội dung.
 */
@Component
public class NotificationEmailTemplateService {

    public record EmailTemplate(String subject, String htmlBody) {}

    private record TemplateSpec(String subject, String greeting, List<String> bodyParagraphs) {}

    private static final String BRAND = "Hệ thống LMS Tiếng Anh PPS Việt Nam";
    private static final String GREETING_PARENT = "Kính gửi Quý Phụ huynh,";
    private static final String GREETING_TEACHER = "Kính gửi Quý Thầy/Cô,";
    private static final String GREETING_SITE_MANAGER = "Kính gửi Quý Anh/Chị Quản lý Điểm trường,";

    private final String hotline;
    private final String supportEmail;
    private final String website;
    private final Map<Notification.NotificationType, TemplateSpec> templates =
            new EnumMap<>(Notification.NotificationType.class);

    public NotificationEmailTemplateService(@Value("${app.mail.hotline:}") String hotline,
                                             @Value("${app.mail.support-email:}") String supportEmail,
                                             @Value("${app.mail.website:}") String website) {
        this.hotline = blankToPlaceholder(hotline, "[Hotline PPS]");
        this.supportEmail = blankToPlaceholder(supportEmail, "[Email hỗ trợ]");
        this.website = blankToPlaceholder(website, "[Website PPS]");
        registerTemplates();
    }

    /**
     * @param studentName tên học viên liên quan (null nếu thông báo theo lớp/theo lô, không phải 1 học viên cụ thể)
     * @param className   tên lớp liên quan (null nếu không xác định được, VD gộp nhiều lớp trong 1 thông báo)
     */
    public Optional<EmailTemplate> renderFor(Notification.NotificationType type, String studentName, String className) {
        TemplateSpec spec = templates.get(type);
        if (spec == null) {
            return Optional.empty();
        }
        StringBuilder html = new StringBuilder();
        html.append("<p>").append(escape(spec.greeting())).append("</p>");
        html.append("<p>Đây là email tự động từ ").append(BRAND).append(".</p>");
        html.append(studentClassBlock(studentName, className));
        for (String paragraph : spec.bodyParagraphs()) {
            html.append("<p>").append(escape(paragraph)).append("</p>");
        }
        html.append(footerHtml());
        return Optional.of(new EmailTemplate(spec.subject(), html.toString()));
    }

    private void registerTemplates() {
        templates.put(Notification.NotificationType.HOMEWORK_MISS_REMINDER, spec(
                GREETING_PARENT, "Nhắc nhở về việc hoàn thành bài tập của học viên",
                "Hệ thống ghi nhận học viên chưa hoàn thành đầy đủ bài tập được giao trong 02 lần liên tiếp.",
                "Việc hoàn thành bài tập đúng hạn giúp học viên củng cố kiến thức, duy trì thói quen học tập và đạt hiệu quả tốt trong quá trình học.",
                "Kính mong Quý Phụ huynh dành thời gian nhắc nhở và đồng hành cùng học viên hoàn thành đầy đủ các bài tập được giao.",
                "Nếu cần hỗ trợ hoặc có bất kỳ thắc mắc nào, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.HOMEWORK_MISS_WARNING, spec(
                GREETING_PARENT, "Cảnh báo việc học viên chưa hoàn thành bài tập",
                "Hệ thống ghi nhận học viên đã 03 lần liên tiếp chưa hoàn thành đầy đủ bài tập được giao.",
                "Tình trạng này có thể ảnh hưởng đến khả năng tiếp thu kiến thức, kết quả học tập và tiến độ của học viên.",
                "Kính mong Quý Phụ huynh quan tâm, nhắc nhở và phối hợp cùng giáo viên để hỗ trợ học viên hoàn thành bài tập đầy đủ trong các buổi học tiếp theo.",
                "Nếu cần được tư vấn hoặc hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.HOMEWORK_MISS_PARENT_MEETING_INVITE, spec(
                GREETING_PARENT, "Thư mời trao đổi về tình hình học tập của học viên",
                "Hệ thống ghi nhận học viên đã 04 lần liên tiếp chưa hoàn thành đầy đủ bài tập được giao.",
                "Để đảm bảo chất lượng học tập và tìm giải pháp hỗ trợ phù hợp, Hệ thống kính mời Quý Phụ huynh sắp xếp thời gian trao đổi cùng giáo viên phụ trách hoặc bộ phận học vụ trong thời gian sớm nhất.",
                "Sự phối hợp giữa gia đình và nhà trường sẽ giúp học viên cải thiện tinh thần học tập và đạt kết quả tốt hơn.",
                "Mọi thông tin vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.HOMEWORK_MISS_REMINDER_NON_CONSECUTIVE, spec(
                GREETING_PARENT, "Nhắc nhở về việc hoàn thành bài tập",
                "Hệ thống ghi nhận học viên đã có 03 lần chưa hoàn thành đầy đủ bài tập trong thời gian gần đây.",
                "Mặc dù các lần chưa hoàn thành không diễn ra liên tiếp, việc bỏ sót bài tập nhiều lần vẫn có thể ảnh hưởng đến quá trình học và kết quả của học viên.",
                "Kính mong Quý Phụ huynh tiếp tục đồng hành và nhắc nhở học viên duy trì việc hoàn thành bài tập đầy đủ và đúng thời hạn.",
                "Nếu cần hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.HOMEWORK_DUE_SOON_REMINDER, spec(
                GREETING_PARENT, "Nhắc nhở hoàn thành bài tập trước hạn",
                "Hệ thống xin thông báo học viên còn bài tập sắp đến hạn nộp nhưng hiện vẫn chưa hoàn thành.",
                "Để tránh quá hạn và đảm bảo tiến độ học tập, kính mong Quý Phụ huynh nhắc nhở học viên hoàn thành bài tập trước thời hạn quy định.",
                "Xin cảm ơn sự đồng hành của Quý Phụ huynh.",
                "Mọi hỗ trợ vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.ATTENDANCE_ABSENT, spec(
                GREETING_PARENT, "Thông báo học viên nghỉ học không phép",
                "Hệ thống ghi nhận học viên đã vắng mặt trong buổi học gần nhất và chưa có thông báo xin phép nghỉ học.",
                "Việc nghỉ học không phép có thể ảnh hưởng đến quá trình tiếp thu kiến thức, tiến độ học tập và kết quả rèn luyện của học viên.",
                "Kính mong Quý Phụ huynh phối hợp cùng Hệ thống, nhắc nhở học viên tham gia đầy đủ các buổi học theo lịch và hoàn thành các bài học, bài tập còn thiếu để đảm bảo không bị gián đoạn quá trình học tập.",
                "Trong trường hợp học viên có lý do đặc biệt hoặc cần hỗ trợ sắp xếp lịch học bù, Quý Phụ huynh vui lòng liên hệ với giáo viên phụ trách hoặc Hotline: " + hotline + " để được hỗ trợ kịp thời."));

        templates.put(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION, spec(
                GREETING_TEACHER, "Thông báo học viên vi phạm quy định khi làm bài",
                "Hệ thống ghi nhận trong quá trình thực hiện bài tập/bài kiểm tra, một hoặc nhiều học viên đã chuyển sang ứng dụng hoặc cửa sổ khác (chuyển tab/thu nhỏ màn hình) từ 03 lần trở lên.",
                "Để đảm bảo tính trung thực và khách quan trong quá trình đánh giá, hệ thống đã ghi nhận các sự kiện này vào lịch sử làm bài của từng học viên.",
                "Kính mời Quý Thầy/Cô đăng nhập vào hệ thống để xem chi tiết số lần vi phạm, thời điểm xảy ra và kết quả làm bài của học viên, từ đó đưa ra đánh giá hoặc quyết định xử lý phù hợp theo quy định của Hệ thống.",
                "Nếu cần hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.HOMEWORK_DEADLINE_SUMMARY, spec(
                GREETING_TEACHER, "Thông báo kết quả bài tập đã giao",
                "Hệ thống xin thông báo bài tập Quý Thầy/Cô đã giao cho học viên đã hết thời hạn nộp bài.",
                "Kết quả thực hiện của học viên đã được hệ thống tổng hợp và sẵn sàng để Quý Thầy/Cô theo dõi, đánh giá và đưa ra nhận xét.",
                "Để đảm bảo việc theo dõi tiến độ học tập của học viên được kịp thời, kính mời Quý Thầy/Cô đăng nhập vào hệ thống để xem chi tiết kết quả của từng học viên và thực hiện các nhận xét cần thiết.",
                "Nếu cần hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.COMMENT_PENDING_APPROVAL, spec(
                GREETING_SITE_MANAGER, "Thông báo có nhận xét của giáo viên cần phê duyệt",
                "Hệ thống ghi nhận có nhận xét của giáo viên đang chờ Quý Anh/Chị phê duyệt.",
                "Để đảm bảo việc phản hồi kết quả học tập tới phụ huynh và học viên được thực hiện kịp thời, kính đề nghị Quý Anh/Chị đăng nhập vào hệ thống để xem xét và phê duyệt các nhận xét đang chờ xử lý.",
                "Sau khi được phê duyệt, nhận xét sẽ được gửi tới phụ huynh và học viên theo quy trình của Hệ thống.",
                "Nếu cần hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.EXAM_INTEGRITY_VIOLATION_PARENT, spec(
                GREETING_PARENT, "Thông báo về việc học viên vi phạm quy định khi làm bài",
                "Hệ thống ghi nhận trong quá trình thực hiện bài tập/bài kiểm tra, học viên đã chuyển sang ứng dụng hoặc cửa sổ khác (chuyển tab/thu nhỏ màn hình) từ 03 lần trở lên.",
                "Để đảm bảo tính trung thực và khách quan trong quá trình học tập, hệ thống đã ghi nhận sự kiện này vào lịch sử làm bài của học viên để giáo viên theo dõi và đánh giá.",
                "Kính mong Quý Phụ huynh phối hợp nhắc nhở học viên tập trung làm bài trong suốt thời gian thực hiện bài tập, hạn chế chuyển sang các ứng dụng hoặc cửa sổ khác nếu không thực sự cần thiết.",
                "Nếu cần hỗ trợ hoặc có thắc mắc liên quan đến kết quả ghi nhận của hệ thống, vui lòng liên hệ Hotline: " + hotline + "."));

        templates.put(Notification.NotificationType.COMMENT_REJECTED, spec(
                GREETING_TEACHER, "Thông báo nhận xét chưa được phê duyệt",
                "Hệ thống xin thông báo nhận xét của Quý Thầy/Cô đối với học viên chưa được Quản lý Điểm trường phê duyệt.",
                "Đề nghị Quý Thầy/Cô đăng nhập vào hệ thống để xem lý do từ chối (nếu có), chỉnh sửa hoặc bổ sung nội dung nhận xét theo yêu cầu và gửi lại để được xem xét phê duyệt.",
                "Việc cập nhật kịp thời sẽ giúp đảm bảo thông tin phản hồi đến phụ huynh và học viên được đầy đủ, chính xác và đúng thời gian.",
                "Nếu cần hỗ trợ, vui lòng liên hệ Hotline: " + hotline + "."));
    }

    private TemplateSpec spec(String greeting, String subjectSuffix, String... bodyParagraphs) {
        return new TemplateSpec("[" + BRAND + "] " + subjectSuffix, greeting, List.of(bodyParagraphs));
    }

    private String studentClassBlock(String studentName, String className) {
        boolean hasStudent = studentName != null && !studentName.isBlank();
        boolean hasClass = className != null && !className.isBlank();
        if (!hasStudent && !hasClass) {
            return "";
        }
        StringBuilder block = new StringBuilder("<p>");
        if (hasStudent) {
            block.append("<strong>Học viên:</strong> ").append(escape(studentName));
        }
        if (hasStudent && hasClass) {
            block.append(" &mdash; ");
        }
        if (hasClass) {
            block.append("<strong>Lớp:</strong> ").append(escape(className));
        }
        block.append("</p>");
        return block.toString();
    }

    private String footerHtml() {
        return "<hr/>"
                + "<p>Trân trọng,<br/>" + BRAND + "</p>"
                + "<p>Đây là email tự động được gửi từ " + BRAND + ". Vui lòng không trả lời trực tiếp email này.</p>"
                + "<p>Nếu Quý Phụ huynh, Quý Thầy/Cô hoặc Quý Anh/Chị cần hỗ trợ, vui lòng liên hệ:</p>"
                + "<p>Hotline: " + escape(hotline) + "<br/>"
                + "Email: " + escape(supportEmail) + "<br/>"
                + "Website: " + escape(website) + "</p>"
                + "<p>Xin chân thành cảm ơn Quý Phụ huynh, Quý Thầy/Cô và Quý Anh/Chị đã luôn đồng hành cùng " + BRAND + ".</p>";
    }

    private String blankToPlaceholder(String value, String placeholder) {
        return (value == null || value.isBlank()) ? placeholder : value;
    }

    private String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
