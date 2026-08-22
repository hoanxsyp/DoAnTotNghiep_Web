# -*- coding: utf-8 -*-
"""
Giao diện dự đoán giá thuê (chỉ 1 trang, standalone).
Chạy:  streamlit run app_demo.py
Muốn TRANG CHỦ CHUNG (cả 2 module): streamlit run home.py
"""
import streamlit as st

import ui_rental

st.set_page_config(page_title="Dự đoán giá thuê phòng trọ Hà Nội", page_icon="🏠", layout="wide")
ui_rental.render()
