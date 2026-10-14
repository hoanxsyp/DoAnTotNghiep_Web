# -*- coding: utf-8 -*-
"""
TRANG CHỦ — portal module AI dự đoán giá thuê phòng trọ (gọi API rental-api).

Chạy:  streamlit run home.py
"""
import streamlit as st

import ui_rental

st.set_page_config(page_title="Đồ án AI — Trang chủ", page_icon="🎓", layout="wide")


def page_home():
    st.title("🎓 Module AI — Đồ án tốt nghiệp")
    st.write("Module AI phục vụ website quảng cáo phòng trọ Hà Nội. "
             "Chọn module ở thanh bên trái, hoặc bấm nút bên dưới.")
    with st.container(border=True):
        st.subheader("🏠 Dự đoán giá thuê")
        try:
            meta = ui_rental.get_meta()
            m = meta["metrics"]
            st.caption(f"Model **{meta['model_type']}** · {meta['n_samples']:,} tin · "
                       f"MAE {m['MAE']:.2f} triệu · R² {m.get('R2', 0):.2f}")
        except Exception:
            st.caption("Dự đoán giá thuê theo quận / diện tích / tiện ích "
                       "(cần rental-api đang chạy).")
        st.write("Nhập thông tin phòng → ước lượng giá thuê + khoảng tham khảo.")
        st.page_link(rental_page, label="Mở trang dự đoán giá", icon="💰")
    st.divider()
    st.caption("Module giá thuê dùng LightGBM. Đồ án học thuật — dữ liệu từ tin đăng công khai.")


rental_page = st.Page(ui_rental.render, title="Dự đoán giá thuê", icon="💰", url_path="gia-thue")
home_page = st.Page(page_home, title="Trang chủ", icon="🏠", default=True)

st.navigation([home_page, rental_page]).run()
