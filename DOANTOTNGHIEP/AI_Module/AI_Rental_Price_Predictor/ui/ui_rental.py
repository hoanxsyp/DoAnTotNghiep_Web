# -*- coding: utf-8 -*-
"""
Trang 'Dự đoán giá thuê' — GỌI API rental (không import model trực tiếp).
Lấy danh sách quận/phường/tiện ích + thống kê từ GET /meta, dự đoán qua POST /predict.
"""
import os

import requests
import streamlit as st

# Trong Docker: http://rental-api:8000 ; chạy local: http://localhost:8000
API_URL = os.environ.get("RENTAL_API_URL", "http://localhost:8000")
ROOM_LABELS = {
    "phong_tro": "Phòng trọ", "can_ho_mini": "Căn hộ mini / CCMN",
    "nha_nguyen_can": "Nhà nguyên căn", "o_ghep": "Ở ghép", "can_ho": "Căn hộ / chung cư",
}


@st.cache_data(ttl=300, show_spinner=False)
def get_meta():
    r = requests.get(f"{API_URL}/meta", timeout=30)
    r.raise_for_status()        # 404/5xx -> RequestException để render() bắt & báo lỗi gọn
    return r.json()


def render():
    try:
        meta = get_meta()
    except requests.RequestException as e:
        st.error(f"Không kết nối được API giá thuê tại `{API_URL}`. "
                 f"Hãy chắc chắn service `rental-api` đang chạy.\n\n{e}")
        return

    mt = meta["metrics"]
    st.title("🏠 Dự đoán giá thuê phòng trọ Hà Nội")
    c1, c2, c3, c4 = st.columns(4)
    c1.metric("Mô hình", meta["model_type"])
    c2.metric("Số mẫu train", f"{meta['n_samples']:,}")
    c3.metric("Sai số TB (MAE)", f"{mt['MAE']:.2f} triệu")
    c4.metric("MAPE", f"{mt['MAPE']:.1f}%")
    st.caption(f"Model huấn luyện trên {meta['n_samples']:,} tin ({len(meta['districts'])} quận). "
               "Nhập thông tin phòng bên trái → **Dự đoán giá**.")
    st.divider()

    left, right = st.columns([1, 1.3])
    with left:
        st.subheader("Thông tin phòng")
        districts = meta["districts"]
        district = st.selectbox("Quận", districts,
                                index=districts.index("Cầu Giấy") if "Cầu Giấy" in districts else 0)
        ward_opts = ["(không rõ)"] + meta["wards"].get(district, [])
        ward = st.selectbox("Phường", ward_opts)
        ward = "unknown" if ward == "(không rõ)" else ward

        ca, cb = st.columns(2)
        area = ca.number_input("Diện tích (m²)", 8.0, 80.0, 25.0, step=1.0)
        floor = cb.number_input("Tầng (0 = không rõ)", 0, 30, 0, step=1)
        room_type = st.selectbox("Loại phòng", meta["room_types"],
                                 format_func=lambda k: ROOM_LABELS.get(k, k))

        with st.expander("Vị trí chính xác (tùy chọn)"):
            st.caption("Nhập tọa độ từ Google Maps để ước lượng theo vị trí chính xác hơn.")
            latitude_text = st.text_input("Vĩ độ", placeholder="21.0287")
            longitude_text = st.text_input("Kinh độ", placeholder="105.8524")

        st.write("**Tiện ích**")
        chosen = []
        cols = st.columns(2)
        for i, (key, label) in enumerate(meta["amenities"]):
            if cols[i % 2].checkbox(label, value=key in ("khep_kin", "dieu_hoa")):
                chosen.append(key)

        go = st.button("🔮 Dự đoán giá", type="primary", use_container_width=True)

    with right:
        st.subheader("Kết quả")
        if not go:
            st.info("Nhập thông tin và nhấn **Dự đoán giá** để xem kết quả.")
            return
        try:
            if bool(latitude_text.strip()) != bool(longitude_text.strip()):
                st.error("Hãy nhập cả vĩ độ và kinh độ hoặc để trống cả hai.")
                return
            coordinates = {}
            if latitude_text.strip():
                coordinates = {"latitude": float(latitude_text), "longitude": float(longitude_text)}
            response = requests.post(f"{API_URL}/predict", json={
                "district": district, "ward": ward, "area_m2": area,
                "room_type": room_type, "amenities": chosen,
                "floor": (floor or None), **coordinates}, timeout=30)
            response.raise_for_status()
            r = response.json()
        except (ValueError, requests.RequestException) as e:
            st.error(f"Lỗi gọi API dự đoán: {e}")
            return

        price = r["predicted_price_million"]
        lo, hi = r["price_range"]
        st.markdown(f"### 💰 {price:.2f} triệu/tháng")
        st.progress(min(price / 12, 1.0))
        st.caption(f"Khoảng tham khảo: **{lo:.2f} – {hi:.2f} triệu** "
                   f"(≈ MAPE {r.get('mape_pct', 0):.0f}%)")

        k1, k2, k3 = st.columns(3)
        k1.metric("Cách trung tâm", f"{r['distance_to_center_km']} km")
        k2.metric("Số tiện ích", len(chosen))
        k3.metric("Đơn giá", f"{price/area:.2f} tr/m²")

        stt = meta["district_stats"].get(district)
        if stt:
            avg = stt["mean"]
            delta = (price - avg) / avg * 100
            st.info(f"Giá TB **{district}**: {avg:.2f} triệu → dự đoán "
                    f"{'cao' if delta >= 0 else 'thấp'} hơn mặt bằng **{abs(delta):.0f}%**.")
            st.bar_chart({"Giá (triệu)": stt["hist_labels"], "Số tin": stt["hist_counts"]},
                         x="Giá (triệu)", y="Số tin", height=220)
