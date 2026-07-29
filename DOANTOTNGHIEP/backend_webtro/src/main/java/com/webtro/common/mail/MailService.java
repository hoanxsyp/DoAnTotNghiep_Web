package com.webtro.common.mail;

import java.util.Map;

/**
 * Gửi email (canonical mục 11, §5.6). Gửi BẤT ĐỒNG BỘ để không chặn request người dùng — mail
 * chậm/lỗi không được ảnh hưởng luồng chính.
 */
public interface MailService {

    /**
     * Gửi email HTML render từ template Thymeleaf.
     *
     * @param to           địa chỉ nhận
     * @param subject      tiêu đề
     * @param templateName tên template trong {@code resources/templates/mail/} (không đuôi .html)
     * @param variables    biến truyền vào template
     */
    void sendHtml(String to, String subject, String templateName, Map<String, Object> variables);
}
