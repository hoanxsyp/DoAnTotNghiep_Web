# -*- coding: utf-8 -*-
"""
Crawler mogi.vn (phòng trọ Hà Nội) — requests + BeautifulSoup.
List (?cp=N) -> gom link chi tiết (-idXXXX) -> vào từng trang chi tiết lấy:
giá (JSON-LD, VND), diện tích, địa chỉ (có Quận/Phường), TOẠ ĐỘ GPS, mô tả đầy đủ, ngày đăng.
District suy từ slug trong URL chi tiết (vd .../quan-cau-giay/...).

Chạy:  python crawl_mogi.py --max-details 400
Ra:    data/raw/mogi_ha-noi.jsonl
"""
import argparse, json, re, sys, time, random, io
from datetime import datetime, timezone
from pathlib import Path
import requests
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding="utf-8")

LIST = "https://mogi.vn/ha-noi/thue-phong-tro-nha-tro"
HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
    "Accept-Language": "vi,en;q=0.9",
}
SESSION = requests.Session(); SESSION.headers.update(HEADERS)
SLUG2NAME = {  # slug quận (mogi dùng nhãn cũ) -> tên chuẩn
    "quan-ba-dinh": "Ba Đình", "quan-bac-tu-liem": "Bắc Từ Liêm",
    "quan-cau-giay": "Cầu Giấy", "quan-dong-da": "Đống Đa",
    "quan-ha-dong": "Hà Đông", "quan-hai-ba-trung": "Hai Bà Trưng",
    "quan-hoan-kiem": "Hoàn Kiếm", "quan-hoang-mai": "Hoàng Mai",
    "quan-long-bien": "Long Biên", "quan-nam-tu-liem": "Nam Từ Liêm",
    "quan-tay-ho": "Tây Hồ", "quan-thanh-xuan": "Thanh Xuân",
}


def get(url, retries=3):
    for i in range(retries):
        try:
            r = SESSION.get(url, timeout=25)
            if r.status_code == 200:
                return r.text
            if r.status_code in (429, 503):
                time.sleep(5 * (i + 1)); continue
        except requests.RequestException:
            time.sleep(3 * (i + 1))
    return ""


def collect_detail_links(max_details, delay=(0.8, 1.8)):
    links, cp = [], 1
    while len(links) < max_details and cp <= 200:
        html = get(LIST + ("" if cp == 1 else f"?cp={cp}"))
        if not html:
            break
        found = re.findall(r'href="(https://mogi\.vn/[^"]*-id\d+)"', html)
        found = list(dict.fromkeys(found))                 # unique, giữ thứ tự
        new = [u for u in found if u not in links]
        if not new:
            break
        links.extend(new)
        print(f"  list cp={cp}: +{len(new)} link (tổng {len(links)})")
        cp += 1
        time.sleep(random.uniform(*delay))
    return links[:max_details]


def parse_detail(html, url):
    d = BeautifulSoup(html, "lxml")
    price = title = desc = None
    for sc in d.find_all("script", type="application/ld+json"):
        try:
            o = json.loads(sc.string)
        except (json.JSONDecodeError, TypeError):
            continue
        if o.get("@type") == "Person":
            off = o.get("makesOffer", {}) or {}
            price = (off.get("priceSpecification", {}) or {}).get("price")
            item = off.get("itemOffered", {}) or {}
            title, desc = item.get("name"), item.get("description")
    co = re.search(r"[?&]q=(-?\d+\.\d+),\s*(-?\d+\.\d+)", html)
    ar = re.search(r"Diện tích[^0-9]*([\d.,]+)\s*m", html)
    ad = re.search(r"((?:Phường|P\.)[^<>]*?Hà Nội)", html)
    dt = re.search(r"Ngày đăng</span>\s*<span>\s*([\d/]+)", html)
    dm = re.search(r"mogi\.vn/(quan-[a-z-]+)/", url)
    return {
        "title": title,
        "price_raw": price,
        "area_raw": ar.group(1) if ar else None,
        "address": ad.group(1) if ad else None,
        "district": SLUG2NAME.get(dm.group(1)) if dm else None,
        "latitude": float(co.group(1)) if co else None,
        "longitude": float(co.group(2)) if co else None,
        "description": desc,
        "posted_date": dt.group(1) if dt else None,
        "source_url": url,
        "source_name": "mogi.vn",
        "crawled_at": datetime.now(timezone.utc).isoformat(),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-details", type=int, default=400)
    args = ap.parse_args()

    print(f"Crawl mogi.vn phòng trọ Hà Nội | tối đa {args.max_details} tin chi tiết")
    links = collect_detail_links(args.max_details)
    print(f"Thu {len(links)} link chi tiết. Bắt đầu lấy chi tiết...")

    rows, seen = [], set()
    for i, url in enumerate(links, 1):
        if url in seen:
            continue
        seen.add(url)
        html = get(url)
        if not html:
            continue
        rec = parse_detail(html, url)
        if rec["price_raw"] and rec["area_raw"]:
            rows.append(rec)
        if i % 25 == 0:
            print(f"  {i}/{len(links)} (giữ {len(rows)})")
        time.sleep(random.uniform(0.7, 1.6))

    out = Path(__file__).resolve().parents[1] / "data" / "raw" / "mogi_ha-noi.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    with io.open(out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"\nĐÃ LƯU {len(rows)} tin (có toạ độ) -> {out}")


if __name__ == "__main__":
    main()
