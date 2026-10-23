package com.roomfinder.chat.listing;

/**
 * Không lấy được dữ liệu phòng từ backend (sập / timeout / lỗi 5xx). KHÔNG nuốt thành "không có
 * phòng": trả lỗi để phía web báo "chatbot đang gặp sự cố" thay vì nói sai là hết phòng.
 */
public class ListingSourceException extends RuntimeException {

    public ListingSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}
