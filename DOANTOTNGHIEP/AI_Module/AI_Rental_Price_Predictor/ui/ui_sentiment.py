# -*- coding: utf-8 -*-
"""Trang 'Phân tích cảm xúc' — gọi API của module PhanTichCamXuc (Flask + Keras)."""
import os

import requests
import streamlit as st

# Trong Docker: http://sentiment-api:5000 ; chạy local: http://localhost:5000
API_URL = os.environ.get("SENTIMENT_API_URL", "http://localhost:5000")

SAMPLES = [
    "Phòng đẹp, chủ nhà thân thiện, giá hợp lý, sẽ giới thiệu bạn bè",
    "Phòng ẩm mốc, chủ khó tính, an ninh kém, không nên thuê",
    "Vị trí thuận tiện gần trường nhưng hơi ồn",
]


def render():
    st.title("💬 Phân tích cảm xúc bình luận")
    st.caption(f"Gọi module **Phân tích cảm xúc** (mạng nơ-ron Keras) qua API `{API_URL}`.")

    sample = st.selectbox("Chọn câu mẫu (hoặc tự nhập bên dưới)", ["— tự nhập —"] + SAMPLES)
    default = "" if sample == "— tự nhập —" else sample
    text = st.text_area("Bình luận / nhận xét về phòng trọ", value=default or SAMPLES[0], height=120)

    if st.button("🔍 Phân tích cảm xúc", type="primary"):
        if not text.strip():
            st.warning("Vui lòng nhập nội dung.")
            return
        try:
            resp = requests.post(f"{API_URL}/predict", json={"text": text}, timeout=25)
            data = resp.json()
        except requests.RequestException as e:
            st.error(f"Không gọi được API cảm xúc tại {API_URL}. "
                     f"Hãy chắc chắn service `sentiment-api` đang chạy.\n\n{e}")
            return

        if "error" in data:
            st.error(data["error"]); return

        sentiment = data.get("sentiment")
        conf = float(data.get("confidence", 0))
        if sentiment == "positive":
            st.success(f"😊 Tích cực — độ tin cậy {conf:.1%}")
            st.progress(conf)
        else:
            st.error(f"☹️ Tiêu cực — độ tin cậy {1 - conf:.1%}")
            st.progress(1 - conf)
        st.caption(data.get("message", ""))

    st.divider()
    st.caption("Module dùng để phân loại cảm xúc bình luận/đánh giá phòng trọ — bổ trợ cho hệ thống "
               "quảng cáo phòng trọ (lọc đánh giá tích cực/tiêu cực).")
