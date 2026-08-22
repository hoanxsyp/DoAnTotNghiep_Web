# -*- coding: utf-8 -*-
"""
Làm giàu dữ liệu phongtro123: vào trang CHI TIẾT lấy MÔ TẢ ĐẦY ĐỦ (full tiện ích),
NGÀY ĐĂNG, TẦNG — bổ sung vào các file data/raw/quan-*.jsonl (đã crawl từ list).
Đa luồng vừa phải (mặc định 6 worker) + delay nhẹ để lịch sự.

Chạy:  python enrich_detail.py            # enrich tất cả file quan-*.jsonl
       python enrich_detail.py --workers 6
"""
import argparse, glob, json, re, sys, time, random, io, threading
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import requests
from bs4 import BeautifulSoup

sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(__file__).resolve().parents[1]   # thư mục ai_rental (data/ nằm ở đây)
H = {"User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
     "Accept-Language": "vi,en;q=0.9"}
SESSION = requests.Session(); SESSION.headers.update(H)
_lock = threading.Lock()
_done = [0]


def fetch_detail(url: str) -> dict:
    """Trả về {full_description, posted_date, floor} (giá trị None nếu không lấy được)."""
    out = {"full_description": None, "posted_date": None, "floor": None}
    try:
        time.sleep(random.uniform(0.15, 0.5))
        r = SESSION.get(url, timeout=25)
        if r.status_code != 200:
            return out
        html = r.text
        d = BeautifulSoup(html, "lxml")
        h = d.find(string=re.compile("Thông tin mô tả"))
        if h:
            h2 = h.find_parent()
            parts = []
            for sib in h2.find_next_siblings():       # gom MỌI <p> tới heading kế
                if sib.name in ("h2", "h3"):
                    break
                t = sib.get_text(" ", strip=True)
                if t:
                    parts.append(t)
            out["full_description"] = " ".join(parts)
        i = html.find("Ngày đăng")
        if i >= 0:
            m = re.search(r"(\d{1,2}/\d{1,2}/\d{4})", html[i:i + 200])
            if m:
                out["posted_date"] = m.group(1)
        text = out["full_description"] or ""
        m = re.search(r"tầng\s*(\d{1,2})\b", text, re.I)
        if m:
            out["floor"] = int(m.group(1))
    except requests.RequestException:
        pass
    return out


def enrich_file(fp: str, workers: int):
    recs = [json.loads(l) for l in open(fp, encoding="utf-8") if l.strip()]
    total = len(recs)

    def work(rec):
        extra = fetch_detail(rec["source_url"])
        rec.update(extra)
        with _lock:
            _done[0] += 1
            if _done[0] % 200 == 0:
                print(f"  ...{_done[0]} tin đã enrich", flush=True)
        return rec

    with ThreadPoolExecutor(max_workers=workers) as ex:
        recs = list(ex.map(work, recs))

    with io.open(fp, "w", encoding="utf-8") as f:
        for r in recs:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")
    got = sum(1 for r in recs if r.get("full_description"))
    print(f"[{Path(fp).name}] {total} tin | có mô tả đầy đủ: {got} "
          f"| có tầng: {sum(1 for r in recs if r.get('floor'))}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--workers", type=int, default=6)
    args = ap.parse_args()
    files = sorted(glob.glob(str(ROOT / "data" / "raw" / "quan-*.jsonl")))
    files = [f for f in files if Path(f).stat().st_size > 0]
    print(f"Enrich {len(files)} file, {args.workers} worker...")
    for fp in files:
        enrich_file(fp, args.workers)
    print(f"\nHOÀN TẤT enrich {_done[0]} tin.")


if __name__ == "__main__":
    main()
