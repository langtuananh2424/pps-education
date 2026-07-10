package vn.com.pps.education.security;

/** Danh tính đã xác thực từ Google id_token (UC-01 Main Flow bước 4). */
public record GoogleIdentity(String subject, String email) {}
