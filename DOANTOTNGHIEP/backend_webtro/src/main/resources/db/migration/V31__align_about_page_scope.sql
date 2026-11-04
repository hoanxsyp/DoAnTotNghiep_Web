UPDATE system_configs
SET config_value = CASE
        WHEN config_value = '"<div class=about-page><h1>Giới thiệu WebTro</h1><p>WebTro là nền tảng kết nối người thuê trọ và chủ nhà trọ trên toàn quốc, giúp tìm phòng nhanh chóng, minh bạch và an toàn.</p><p>Chúng tôi cung cấp công cụ tìm kiếm thông minh, gợi ý tin phù hợp và hệ thống kiểm duyệt nhằm hạn chế tin giả.</p></div>"'
            THEN '"<div class=about-page><h1>Giới thiệu WebTro</h1><p>WebTro là nền tảng kết nối người thuê trọ và chủ nhà trọ tại Hà Nội, giúp tìm phòng nhanh chóng, minh bạch và an toàn.</p><p>Chúng tôi cung cấp công cụ tìm kiếm thông minh, gợi ý tin phù hợp và hệ thống kiểm duyệt nhằm hạn chế tin giả.</p></div>"'
        ELSE config_value
    END,
    default_value = '"<div class=about-page><h1>Giới thiệu WebTro</h1><p>WebTro là nền tảng kết nối người thuê trọ và chủ nhà trọ tại Hà Nội, giúp tìm phòng nhanh chóng, minh bạch và an toàn.</p><p>Chúng tôi cung cấp công cụ tìm kiếm thông minh, gợi ý tin phù hợp và hệ thống kiểm duyệt nhằm hạn chế tin giả.</p></div>"'
WHERE config_key = 'page.about'
  AND deleted_at IS NULL;
