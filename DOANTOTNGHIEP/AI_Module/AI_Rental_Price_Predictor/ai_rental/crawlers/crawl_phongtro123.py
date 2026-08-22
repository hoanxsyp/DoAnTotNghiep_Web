# -*- coding: utf-8 -*-
"""
Crawler phongtro123.com theo QUẬN (chỉ cần requests + BeautifulSoup).
Bóc dữ liệu ngay từ trang DANH SÁCH qua JSON-LD nhúng trong mỗi card
(title, price, address) + area trong text card -> không cần vào trang chi tiết.

Chạy:  python crawl_phongtro123.py --district quan-thanh-xuan --max-pages 40
Ra:    data/raw/<district>.jsonl
"""
import argparse, json, re, sys, time, random, io
from datetime import datetime, timezone
from pathlib import Path
import requests
from bs4 import BeautifulSoup

# normalizers.py nằm ở ../preprocessing — thêm vào path để import cross-folder
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preprocessing"))
from normalizers import SLUG2NAME, is_location_outside_hanoi

sys.stdout.reconfigure(encoding="utf-8")

BASE = "https://phongtro123.com/tinh-thanh/ha-noi"
HEADERS = {  # UA trình duyệt thật (KHÔNG dùng UA bot)
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
    "Accept-Language": "vi,en;q=0.9",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
}
SESSION = requests.Session()
SESSION.headers.update(HEADERS)


def get(url: str, retries: int = 3) -> str:
    for i in range(retries):
        try:
            r = SESSION.get(url, timeout=25)
            if r.status_code == 200:
                return r.text
            if r.status_code in (429, 503):        # bị rate-limit -> backoff
                time.sleep(5 * (i + 1)); continue
            r.raise_for_status()
        except requests.RequestException as e:
            if i == retries - 1:
                print(f"  [ERR] {url} -> {e}")
            time.sleep(3 * (i + 1))
    return ""


def area_from_card(li) -> str | None:
    t = li.get_text(" ", strip=True).replace("m²", "m2")
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*m\s*2", t)
    return m.group(1) if m else None


def parse_card(li) -> dict | None:
    sc = li.find("script", type="application/ld+json")
    if not sc:
        return None
    try:
        o = json.loads(sc.string)
    except (json.JSONDecodeError, TypeError):
        return None
    addr = o.get("address") or {}
    a = li.select_one("h3 a[href]") or li.find("a", href=True)
    href = a["href"] if a else o.get("url", "")
    url = href if href.startswith("http") else "https://phongtro123.com" + href
    return {
        "title": o.get("name"),
        "price_raw": o.get("priceRange"),                 # giá VND dạng số (chuỗi)
        "area_raw": area_from_card(li),                   # m² (chuỗi)
        "address": addr.get("streetAddress"),             # "... Phường X, Hà Nội"
        "description": o.get("description"),              # snippet mô tả
        "phone": o.get("telephone"),
        "image": o.get("image"),
        "source_url": url,
        "source_name": "phongtro123.com",
        "crawled_at": datetime.now(timezone.utc).isoformat(),
    }


def crawl_district(district_slug: str, max_pages: int, delay=(1.0, 2.5)) -> list[dict]:
    district_name = SLUG2NAME.get(district_slug, district_slug)
    seen, rows = set(), []
    for pg in range(1, max_pages + 1):
        url = f"{BASE}/{district_slug}" + ("" if pg == 1 else f"?page={pg}")
        html = get(url)
        if not html:
            break
        cards = BeautifulSoup(html, "lxml").select("ul.post__listing > li")
        if not cards:
            print(f"  page {pg}: 0 card -> dừng."); break
        new = 0
        for li in cards:
            rec = parse_card(li)
            if rec and is_location_outside_hanoi(rec["address"]):
                continue
            if rec and rec["source_url"] not in seen:
                rec["district"] = district_name          # gắn quận từ slug crawl
                seen.add(rec["source_url"]); rows.append(rec); new += 1
        print(f"  page {pg}: {len(cards)} card, +{new} mới (tổng {len(rows)})")
        if new == 0:                                       # trùng hết -> hết tin
            break
        time.sleep(random.uniform(*delay))
    return rows


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--district", default="quan-thanh-xuan")
    ap.add_argument("--max-pages", type=int, default=40)
    args = ap.parse_args()

    print(f"Crawl phongtro123.com | {args.district} | tối đa {args.max_pages} trang")
    rows = crawl_district(args.district, args.max_pages)

    out = Path(__file__).resolve().parents[1] / "data" / "raw" / f"{args.district}.jsonl"
    out.parent.mkdir(parents=True, exist_ok=True)
    with io.open(out, "w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"\nĐÃ LƯU {len(rows)} tin -> {out}")


if __name__ == "__main__":
    main()
