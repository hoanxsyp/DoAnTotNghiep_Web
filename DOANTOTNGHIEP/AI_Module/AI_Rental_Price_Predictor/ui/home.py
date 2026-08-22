# -*- coding: utf-8 -*-
"""
TRANG CHỦ CHUNG — portal liên kết 2 module AI:
  • Dự đoán giá thuê phòng trọ (Keras MLP)
  • Phân tích cảm xúc bình luận (Keras, gọi API sentiment-api)

Chạy:  streamlit run home.py
"""
import streamlit as st

import ui_rental
import ui_sentiment

st.set_page_config(page_title="Đồ án AI — Trang chủ", page_icon="🎓", layout="wide")


def page_home():
    st.title("🎓 Cụm Module AI — Đồ án tốt nghiệp")
    st.write("Hệ thống gồm **2 module AI** phục vụ website quảng cáo phòng trọ Hà Nội. "
             "Chọn module ở thanh bên trái, hoặc bấm nút bên dưới.")
    c1, c2 = st.columns(2)
    with c1:
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
    with c2:
        with st.container(border=True):
            st.subheader("💬 Phân tích cảm xúc")
            st.caption("Mạng nơ-ron Keras · phân loại bình luận tích cực / tiêu cực")
            st.write("Nhập bình luận/đánh giá phòng trọ → xác định sắc thái cảm xúc.")
            st.page_link(sentiment_page, label="Mở trang phân tích cảm xúc", icon="💬")
    st.divider()
    st.caption("Module giá thuê dùng LightGBM; module cảm xúc dùng Keras. Đồ án học thuật — "
               "dữ liệu từ tin đăng công khai.")


rental_page = st.Page(ui_rental.render, title="Dự đoán giá thuê", icon="💰", url_path="gia-thue")
sentiment_page = st.Page(ui_sentiment.render, title="Phân tích cảm xúc", icon="💬", url_path="cam-xuc")
home_page = st.Page(page_home, title="Trang chủ", icon="🏠", default=True)

st.navigation([home_page, rental_page, sentiment_page]).run()
