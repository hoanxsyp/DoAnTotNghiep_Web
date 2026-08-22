# Lộ trình xây dựng Module AI dự đoán giá thuê phòng trọ tại Hà Nội

> **Phạm vi:** phòng trọ / nhà trọ / căn hộ mini (CCMN) / phòng cho thuê tại Hà Nội — **KHÔNG** phải mua bán bất động sản.
> **Trạng thái:** chưa có dataset → xây từ đầu (khảo sát nguồn → crawl → làm sạch → train → đánh giá → API → tích hợp website).
> **Nguồn dữ liệu đã kiểm chứng trực tiếp ngày 05/07/2026** (HTTP probe + đọc robots.txt + thử trang danh sách/chi tiết).

## Tóm tắt nhanh (TL;DR)

| Chủ đề | Kết luận |
|---|---|
| **Nguồn crawl chính** (chỉ cần `requests + BeautifulSoup`) | **phongtro123.com** (~5.649 tin HN, volume lớn nhất), **mogi.vn** (có sẵn toạ độ GPS + JSON-LD), **phongtot.com** (có GPS, CCMN) |
| **Nguồn thứ tư** | **tromoi.com** (có ngày đăng; crawl qua `sitemap.xml`, dùng UA trình duyệt thật vì robots chặn UA bot AI) |
| **Nguồn phụ** (cần Playwright + proxy do Cloudflare Turnstile) | **batdongsan.com.vn**, **muaban.net** |
| **Loại bỏ** | **Ohana (ohanaliving.vn)** — app-first, web không có trang danh sách phòng |
| **Lưu ý tên miền trong đề bài** | `ohanha.com` và `roomily.vn` **KHÔNG tồn tại** (NXDOMAIN). Nhầm với **Ohana** và **Trọ Mới (tromoi.com)**. |
| **Model khuyến nghị** | So sánh Ridge / RandomForest / LightGBM / CatBoost → thường **CatBoost hoặc LightGBM** thắng trên tabular nhỏ nhiều categorical |
| **Metric chính** | **MAE + MAPE** để giao tiếp với người dùng; R²/RMSE cho báo cáo kỹ thuật |
| **Serving** | **FastAPI** `/predict`, model theo từng quận + fallback `hanoi_all`, load 1 lần bằng `@lru_cache` |
| **Ngưỡng dữ liệu tối thiểu** | ≥ 300–500 bản ghi/quận để train mô hình phi tuyến; dưới 200 → dùng `district` làm feature trong 1 model tổng |

> **Ghi chú kiểm chứng:** Trong lần khảo sát này, agent kiểm tra **nha.chotot.com (Nhà Tốt / Chợ Tốt)** bị lỗi nên chưa xác minh lại. Chợ Tốt là classifieds lớn, thường crawl qua **API JSON nội bộ** (`gateway.chotot.com/v1/public/ad-listing`) — là ứng viên nguồn mạnh, **nên khảo sát lại thủ công** ở Giai đoạn 1 (mục Bước 7). Các phần code tham chiếu "chotot/nhatot" bên dưới mang tính minh hoạ chung.

> **Bối cảnh hành chính 2025–2026:** Hà Nội đã sắp xếp lại đơn vị hành chính (bỏ cấp quận ở một số nơi, gộp phường). Dataset trộn tin cũ + mới → **giữ nhãn quận truyền thống** (Đống Đa, Cầu Giấy, Thanh Xuân...) làm feature ổn định cho ML, đồng thời lưu `ward` thô. Cần một bảng ánh xạ phường↔quận để không vỡ one-hot khi train.

---


# BƯỚC 1: Phân tích các website đăng tin cho thuê phòng trọ

Mục tiêu của bước này là khảo sát và chấm điểm các nguồn dữ liệu web đăng tin **cho thuê phòng trọ / nhà trọ / căn hộ mini (CCMN) / phòng ở ghép tại Hà Nội**, từ đó chọn ra nguồn ưu tiên để xây dựng pipeline thu thập dữ liệu phục vụ bài toán **dự đoán giá thuê**. Bảy nguồn dưới đây được kiểm chứng trực tiếp ngày **05/07/2026** (HTTP probe qua `curl -I`, đọc `robots.txt`, thử một trang danh sách và một trang chi tiết mỗi site). Tiêu chí đánh giá gồm: khả năng lấy được các trường phục vụ mô hình (giá, diện tích, vị trí, tiện ích, thời gian), độ khó kỹ thuật (SSR hay cần trình duyệt thật), rào cản chống bot, và ràng buộc `robots.txt`/điều khoản.

> Lưu ý phạm vi: đây là bài toán **giá thuê phòng trọ**, KHÔNG phải mua bán bất động sản. Vì vậy ưu tiên các site chuyên phòng trọ (phongtro123, phongtot, tromoi) và các chuyên mục "cho thuê nhà trọ/phòng trọ" trong portal tổng hợp (mogi, batdongsan, muaban), loại các tin bán nhà/bán đất.

## 1.1 Bảng so sánh tổng hợp các nguồn

| Website | Phù hợp crawl | Trường dữ liệu chính | Login? | Phân trang | Render | Chống bot | Công nghệ | Khuyến nghị |
|---|---|---|---|---|---|---|---|---|
| **phongtro123.com** | **Cao** | Giá (`x.x triệu/tháng`), diện tích, địa chỉ đầy đủ (số nhà→đường→phường→quận), tiện ích, ngày đăng, mã tin, SĐT, ảnh | Không | `?page=N` (~283 trang HN, ~20 tin/trang) | SSR (Laravel/PHP 8.3) | Thấp–TB: Cloudflare nhưng **không** JS-challenge/captcha | **requests + BeautifulSoup** | **Ưu tiên chính** |
| **mogi.vn** | **Cao** | Giá (int trong JSON-LD), diện tích (`floorSize`), địa chỉ, **toạ độ GPS (lat/lng)**, ngày đăng, mã BDS, SĐT, ảnh | Không | `?cp=N` (15 tin/trang) | SSR (ASP.NET) + **JSON-LD** | Thấp–TB (Cloudflare, không challenge) | **requests + BeautifulSoup** | **Ưu tiên chính** |
| **phongtot.com** | **Cao** | Giá (từ/theo phòng), diện tích, địa chỉ, **toạ độ GPS**, tiện ích, số phòng trống, tên toà nhà | Không | `?st=N` (~89 trang, ~12 toà/trang) | SSR (ASP.NET) | Thấp–TB (Cloudflare, không challenge) | **requests + BeautifulSoup** | **Ưu tiên** (thiếu ngày đăng, gom theo toà nhà) |
| **tromoi.com** | **Cao** | Giá, diện tích, địa chỉ (số nhà/ngõ/ngách), tiện ích, ngày đăng, SĐT (`tel:`), loại hình | Không | `?page=N` (~27 trang) → **nên dùng sitemap** | SSR (Laravel) | TB–Thấp: Cloudflare + **chặn UA bot AI** | **requests + BeautifulSoup** | **Ưu tiên** (dùng sitemap + UA trình duyệt thật) |
| **batdongsan.com.vn** | Thấp (nếu chỉ `requests`) | Giá, diện tích, địa chỉ chi tiết đường/phường/quận, số PN/WC, hướng, ngày đăng, mã tin | Không (SĐT che, "bấm để hiện") | `/pN` (path) + lọc theo path | SSR (ASP.NET) | **CAO**: Cloudflare **Turnstile** (403, `Cf-Mitigated: challenge`) | Playwright / undetected-chromedriver + **proxy dân cư** | **Phụ** (bổ sung / đối chiếu) |
| **Muaban.net** | Trung bình | Giá, diện tích, địa chỉ đến đường, tiện ích, ngày đăng, SĐT + Zalo, ảnh | Không | `?page=N` | SSR | **Cao**: CF **managed challenge** (403 mọi URL) | Playwright stealth + proxy | **Phụ** |
| **Ohana (ohanaliving.vn)** | **Thấp** | (Chỉ có trong app; web **không** có listing phòng) | Có (qua app) | Không có web listing | App-first; web chỉ là landing WordPress | Web thấp; API app chưa xác minh | Không khả thi qua web thường | **Loại bỏ** |

> Ghi chú về "ohanha.com" và "roomily.vn" trong đề bài: cả hai **không tồn tại** (DNS trả `NXDOMAIN`/`ENOTFOUND`). "ohanha" là nhầm thương hiệu **Ohana** (ohanaliving.vn — app-first, không crawl web được); "roomily" là nhầm **Trọ Mới** (tromoi.com — đã đưa vào bảng). Đề nghị chốt lại 2 tên này với người yêu cầu, nhưng về mặt dữ liệu đã xử lý xong.

## 1.2 Phân tích chi tiết các nguồn ưu tiên

### phongtro123.com — nguồn chính, khối lượng lớn nhất
- **Dữ liệu lấy được:** đầy đủ trường phục vụ mô hình — giá dạng `x.x triệu/tháng`, diện tích `m²`, tiện ích dưới dạng danh sách (đầy đủ nội thất, có gác, điều hoà, thang máy, giờ giấc tự do...), ngày đăng, mã tin `#XXXXXX`. Khối lượng lớn: trang hiển thị **"Có 5.649 tin đăng cho thuê phòng trọ Hà Nội"** (~283 trang, T7/2026), tin cập nhật liên tục.
- **Độ chi tiết địa chỉ:** cao nhất trong nhóm text — tới **số nhà + đường + phường/xã + quận/huyện** (vd `2/54/16 Đường Tôn Thất Tùng, Phường Kim Liên` + breadcrumb `Quận Đống Đa`). **Không có** toạ độ lat/lng (bản đồ chỉ là iframe Google Maps Embed geocode từ chuỗi địa chỉ) → cần **geocode ngoài** nếu muốn toạ độ.
- **robots.txt & điều khoản:** `User-agent: *`, `Allow: /`. Chỉ disallow `/admincp`, `/api` và các biến thể query `?paged=`, `?s=`, `?cat=`... Quan trọng: `?paged=` bị chặn nhưng **`?page=` được phép** → đường crawl phân trang hợp lệ. Có `sitemap.xml`.
- **Rủi ro bị chặn:** thấp–trung bình. Có Cloudflare (`Server: cloudflare`) nhưng `Cf-Cache-Status: DYNAMIC` và trả 200 ngay với UA thường, không captcha. Rủi ro chính là **rate-limit** nếu bắn quá nhanh → dùng delay 1–3s + UA thật.

```python
import re, time, random, requests
from bs4 import BeautifulSoup

HEADERS = {  # UA trình duyệt thật, KHÔNG dùng UA bot
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                   "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"),
    "Accept-Language": "vi,en;q=0.9",
}

def crawl_phongtro123_list(page: int) -> list[str]:
    url = f"https://phongtro123.com/tinh-thanh/ha-noi?page={page}"
    r = requests.get(url, headers=HEADERS, timeout=20)
    r.raise_for_status()
    soup = BeautifulSoup(r.text, "lxml")
    # Link chi tiet co dang '/<slug>-prXXXX.html'
    links = {a["href"] for a in soup.select("a[href]")
             if re.search(r"-pr\d+\.html$", a.get("href", ""))}
    return sorted(links)

# Duyet 1..283, ton trong robots (chi ?page=), throttle ngau nhien
all_links = []
for p in range(1, 284):
    all_links += crawl_phongtro123_list(p)
    time.sleep(random.uniform(1.0, 3.0))
```

### mogi.vn — nguồn chính, chuẩn hoá tốt nhất (có toạ độ + JSON-LD)
- **Dữ liệu lấy được:** mỗi trang chi tiết có **2 khối `application/ld+json`** (`House`, `Offer`, `UnitPriceSpecification`, `GeoCoordinates`, `PostalAddress`) → lấy giá dạng số nguyên (`price=5500000`), `floorSize`, địa chỉ, **lat/lng** rất sạch để nạp thẳng vào mô hình. Khối lượng mục phòng trọ HN ~**1.760 tin** (+ 93 tin ở ghép).
- **Độ chi tiết địa chỉ:** đường/ngõ + phường + quận + thành phố (vd `Ngõ 05 Từ Mơ, Trung Kính, Phường Trung Hoà, Quận Cầu Giấy`). Có breakdown theo từng quận qua URL con `/ha-noi/quan-cau-giay/thue-phong-tro-nha-tro`.
- **robots.txt & điều khoản:** `Allow: /`; disallow `/api/`, `/Property/`, `/MarketPrice/`... nhưng **listing và detail được phép**. Có `sitemap-detail.xml` liệt kê thẳng URL tin → nên ưu tiên đọc sitemap thay vì duyệt phân trang.
- **Rủi ro & bẫy dữ liệu:** thấp–TB (Cloudflare không challenge). **Cảnh báo parse:** trong JSON-LD, `RealEstateAgent` chứa `streetAddress`/lat-lng là **địa chỉ văn phòng môi giới** (có thể ở tỉnh khác) — **toạ độ THẬT của phòng** nằm ở **iframe Google Maps embed** (`q=lat,lng`), phải tách đúng.

```python
import json, re, requests
from bs4 import BeautifulSoup

def parse_mogi_detail(html: str) -> dict:
    soup = BeautifulSoup(html, "lxml")
    out = {}
    for tag in soup.find_all("script", type="application/ld+json"):
        try:
            data = json.loads(tag.string or "{}")
        except json.JSONDecodeError:
            continue
        for node in (data if isinstance(data, list) else [data]):
            t = node.get("@type")
            if t in ("House", "Product", "Offer"):
                out.setdefault("price", node.get("offers", {}).get("price") or node.get("price"))
                out.setdefault("floorSize", str(node.get("floorSize", "")))
            if t == "PostalAddress" or "address" in node:
                addr = node.get("address", node)
                out["street"] = addr.get("streetAddress")
                out["locality"] = addr.get("addressLocality")  # phuong
    # Toa do THAT o iframe embed q=lat,lng (khong lay tu RealEstateAgent!)
    m = re.search(r"[?&]q=([\d.]+),\s*([\d.]+)", html)
    if m:
        out["lat"], out["lng"] = float(m.group(1)), float(m.group(2))
    return out
```

### phongtot.com — nguồn phòng trọ/CCMN sạch, có GPS
- **Dữ liệu lấy được:** giá (giá từ + giá từng phòng), diện tích, tiện ích (đếm `9+ tiện ích`), **toạ độ GPS** nhúng sẵn, số phòng trống (`Chỉ còn 1 phòng trống!`). Đặc thù: dữ liệu tổ chức **theo TOÀ NHÀ** — trang liệt kê `1061 toà nhà / 3183 phòng trống` tại HN → phù hợp theo dõi tồn kho hơn theo dòng thời gian. **Thiếu ngày đăng** (chấp nhận đánh đổi).
- **Độ chi tiết địa chỉ:** đường + phường + quận (vd `Đường Cầu Diễn, Phường Minh Khai, Quận Bắc Từ Liêm`) **kèm toạ độ GPS chính xác**, thường không có số nhà.
- **robots.txt & điều khoản:** chỉ là bản mặc định "content-signals" của Cloudflare, **không** có `Disallow` nào → không cấm đường dẫn. Cần lưu tuyên bố hạn chế **ai-train** (Điều 4 EU Directive 2019/790) nếu dùng dữ liệu để train model.
- **Rủi ro:** thấp–TB; UA thường trả 200. SĐT hiển thị thường là **hotline chung của nền tảng** (không phải chủ trọ) → không dùng SĐT để dedup.

```python
import re
# Toa do dang: maps.google.com/maps?q=21.0493044,105.7418102&output=embed
def extract_geo(html: str):
    m = re.search(r"maps\.google\.com/maps\?q=([\d.]+),([\d.]+)", html)
    return (float(m.group(1)), float(m.group(2))) if m else (None, None)
# Duyet /cho-thue-phong-tro-hn?st=1..89 -> link detail '...-tn{ID}'
```

### tromoi.com — nguồn chuyên phòng trọ, dùng sitemap để tuân thủ robots
- **Dữ liệu lấy được:** giá (nhiều mức theo diện tích), diện tích, tiện ích, **ngày đăng + cập nhật**, loại hình. SĐT bị che trên UI (`097688****`) nhưng **số đầy đủ nằm trong HTML** dưới link `tel:` → lấy được không cần login. Khối lượng vừa: **627 kết quả** phòng trọ HN (~27 trang), có phân bổ theo quận trong sidebar.
- **Độ chi tiết địa chỉ:** tới số nhà/ngõ/ngách + phường + quận (vd `số nhà 15 ngõ 43/66/9A, Trung Hoà, Cầu Giấy`). **Không có** toạ độ → geocode ngoài.
- **robots.txt & điều khoản (điểm cần chú ý nhất):** khối "Cloudflare Managed" **chặn hẳn UA bot AI** (`ClaudeBot`, `GPTBot`, `CCBot`, `Bytespider`, `Google-Extended`, `meta-externalagent`...) với `Disallow: /`, và `Disallow /*?*page=` → **URL phân trang bị robots chặn**. Cách tuân thủ: **duyệt qua `sitemap.xml`** lấy URL `/phong-tro/{slug}`, và dùng **UA trình duyệt thật** (không để lộ UA bot).
- **Rủi ro:** TB–thấp; nếu để lộ UA bot AI sẽ bị chặn. `Crawl-delay: 10` khai báo cho GPTBot/CCBot → nên bò chậm, theo dõi mã 429/403.

```python
import requests, xml.etree.ElementTree as ET

def tromoi_urls_from_sitemap() -> list[str]:
    r = requests.get("https://tromoi.com/sitemap.xml", headers=HEADERS, timeout=20)
    root = ET.fromstring(r.content)
    ns = {"sm": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    return [loc.text for loc in root.iterfind(".//sm:loc", ns)
            if loc.text and "/phong-tro/" in loc.text]  # tranh URL ?page= bi robots chan
```

### Nguồn phụ: batdongsan.com.vn & Muaban.net (cần trình duyệt thật + proxy)
- **Giá trị:** chất lượng tin và độ chi tiết địa chỉ rất tốt (đến tận đường/phường/quận), số PN/WC, hướng nhà — dùng để **bổ sung & đối chiếu** (cross-check) với nguồn chính. batdongsan còn có Apify actor `minhlucvan/batdongsan-scraper` cho thấy crawl được bằng browser tự động.
- **Rào cản:** cả hai đứng sau **Cloudflare managed challenge / Turnstile** — `requests` bị **403 ngay** kể cả giả UA. Bắt buộc **Playwright / undetected-chromedriver (stealth)** để vượt Turnstile lấy cookie `cf_clearance`, kèm **proxy dân cư xoay IP**; hoặc dùng scraping API (ScrapFly/ScrapingBee/ZenRows). Sau khi có `cf_clearance` có thể tái dùng tạm với `requests` để parse HTML (vì nội dung là SSR).
- **robots.txt:** cả hai `Allow: /` cho các path phòng trọ (batdongsan chỉ cấm vài path `.ashx`) — **rào cản là ở tầng mạng, không phải robots**. Nên khai thác `sitemap.xml` để thu URL, throttle chậm, cache HTML tránh gọi lại.

```python
# Vuot Cloudflare Turnstile bang undetected-chromedriver + proxy
import undetected_chromedriver as uc
opts = uc.ChromeOptions()
opts.add_argument("--proxy-server=http://user:pass@residential-proxy:port")
driver = uc.Chrome(options=opts)
driver.get("https://batdongsan.com.vn/cho-thue-nha-tro-phong-tro-ha-noi/p2")
# doi Turnstile pass -> lay driver.page_source (SSR) roi parse bang BeautifulSoup
```

### Loại bỏ: Ohana (ohanaliving.vn)
Mô hình **app-first**: website chỉ là landing WordPress + blog, **không có bất kỳ trang danh sách phòng nào** để crawl (`sitemap_index` chỉ có post/page/category). Toàn bộ tin phòng nằm trong app iOS/Android qua **API private** — muốn lấy phải reverse-engineer API (mitmproxy/Frida + token), rủi ro pháp lý/kỹ thuật cao và có thể vi phạm điều khoản. → **Không đưa vào pipeline**, chuyển sang các portal có web listing.

## 1.3 Kết luận: chọn nguồn ưu tiên & chiến lược phủ sóng các quận Hà Nội

### Chọn 3 nguồn ƯU TIÊN để bắt đầu
1. **phongtro123.com — nguồn xương sống.** Khối lượng lớn nhất (**~5.649 tin HN**), SSR thuần, `requests + BeautifulSoup` là đủ, không challenge, địa chỉ chi tiết đến số nhà, có đủ tiện ích + ngày đăng. Đây là nguồn cho **độ phủ và số mẫu (volume)** cao nhất với chi phí kỹ thuật thấp nhất.
2. **mogi.vn — nguồn "vàng" về chất lượng cấu trúc.** Có **JSON-LD + toạ độ GPS lat/lng** sẵn → cung cấp feature không gian (khoảng cách tới trung tâm/tiện ích) mà phongtro123 và tromoi thiếu, đồng thời giá đã ở dạng số nguyên nên **giảm công chuẩn hoá**. `requests + BeautifulSoup` đủ.
3. **phongtot.com — bổ sung căn hộ mini/CCMN + GPS thứ hai.** Cũng có **toạ độ GPS**, chuyên phòng trọ/CCMN tổ chức theo toà nhà (`1061 toà / 3183 phòng`), dễ parse. Bù cho việc phongtro123 không có toạ độ và làm giàu phân khúc CCMN.

> **tromoi.com** đứng ngay sau, dùng làm nguồn thứ tư (dễ crawl, có ngày đăng, nhưng nhớ crawl qua **sitemap** và UA trình duyệt thật vì robots chặn UA bot AI + `?page=`).

**Lý do chọn 3 nguồn này:** đều **SSR + không Cloudflare challenge**, chỉ cần `requests + BeautifulSoup` (không tốn chi phí Playwright/proxy), `robots.txt` cho phép crawl listing/detail, và **cùng nhau bù đủ các trường thiếu**: phongtro123 (volume + địa chỉ số nhà) ⊕ mogi/phongtot (toạ độ GPS) ⊕ tromoi (ngày đăng). Hai nguồn nặng (batdongsan, muaban) chỉ nên thêm ở giai đoạn sau khi đã có pipeline browser + proxy, dùng để **đối chiếu và bù tin cao cấp**.

### Cách kết hợp nguồn chính + phụ để phủ đủ các quận
Tất cả nguồn ưu tiên đều cho **crawl theo từng quận/đường** — dùng để đảm bảo phủ đều Thanh Xuân, Cầu Giấy, Đống Đa, Hà Đông, Hai Bà Trưng, Hoàng Mai, Bắc/Nam Từ Liêm... thay vì chỉ lấy tin VIP nổi lên đầu:

| Nguồn | Cách chia theo quận | Ví dụ URL |
|---|---|---|
| phongtro123.com | Bộ lọc quận/huyện + phân trang `?page=` | `/tinh-thanh/ha-noi?page=N` (+ lọc quận) |
| mogi.vn | URL con theo quận | `/ha-noi/quan-cau-giay/thue-phong-tro-nha-tro?cp=N` |
| phongtot.com | Quận nằm trong slug URL detail | `/cho-thue-phong-tro-hn/quan-bac-tu-liem/...-tn1571` |
| batdongsan.com.vn (phụ) | Path theo quận + khung giá | `/cho-thue-nha-tro-phong-tro-cau-giay/p2` |
| Muaban.net (phụ) | URL riêng từng quận/đường | `/cho-thue-nha-tro-phong-tro-duong-...-quan-dong-da-ha-noi?page=N` |

**Quy trình gộp dữ liệu đề xuất:**
1. **Chạy song song** phongtro123 + mogi + phongtot (+ tromoi) bằng `requests + BeautifulSoup`, mỗi nguồn lặp theo danh sách 30 phường/quận HN để phủ đều; delay 1–3s + UA thật.
2. **Chuẩn hoá schema chung**: `title, price_vnd, area_m2, district, ward, street, lat, lng, amenities[], posted_date, phone, source_url, source_site`. Đưa giá về VND/tháng (parse `x.x triệu`), diện tích về `float m²`.
3. **Chuẩn hoá địa chỉ theo địa giới mới 2025–2026**: Hà Nội đã sắp xếp lại đơn vị hành chính (bỏ cấp quận, gộp phường) — tin mới ghi theo **phường trực thuộc TP**, tin cũ vẫn theo quận (Cầu Giấy, Ba Đình, Hà Đông...). Cần một bảng ánh xạ phường↔quận cũ để thống nhất feature `khu vực`, tránh vỡ one-hot encoding khi train mô hình.
4. **Khử trùng lặp (dedup)** vì cùng một phòng có thể xuất hiện ở nhiều nguồn: khoá dedup theo tổ hợp `(SĐT chủ trọ + diện tích + giá)` hoặc `(địa chỉ chuẩn hoá + giá)`. **Không** dùng SĐT của phongtot để dedup (là hotline chung nền tảng).
5. **Bù toạ độ** cho phongtro123 và tromoi (thiếu lat/lng) bằng geocode địa chỉ text — dùng `geopy` với Nominatim (miễn phí, rate-limit 1 req/s) hoặc Goong Maps API (chính xác hơn cho địa chỉ VN):

```python
from geopy.geocoders import Nominatim
from geopy.extra.rate_limiter import RateLimiter
geocoder = Nominatim(user_agent="rental-price-hanoi")
geocode = RateLimiter(geocoder.geocode, min_delay_seconds=1.1)
loc = geocode("2/54/16 Đường Tôn Thất Tùng, Phường Kim Liên, Quận Đống Đa, Hà Nội")
lat, lng = (loc.latitude, loc.longitude) if loc else (None, None)
```

6. **batdongsan + muaban (phụ)** chỉ kích hoạt sau, qua Playwright/undetected-chromedriver + proxy, để (a) bù các quận/phân khúc còn thưa mẫu và (b) đối chiếu giá nhằm phát hiện outlier — không dùng làm nguồn số lượng chính vì chi phí vượt Cloudflare cao.

Kết quả của Bước 1: chốt **phongtro123.com + mogi.vn + phongtot.com** (mở rộng tromoi.com) làm nguồn chính cho pipeline `requests + BeautifulSoup`, giữ **batdongsan.com.vn + Muaban.net** làm nguồn phụ khi cần bổ sung/kiểm chứng, và loại **Ohana** khỏi phạm vi crawl web.


---


## BƯỚC 2: Crawl dữ liệu và tạo dataset

Bước này biến các trang web tin đăng thành một **dataset có cấu trúc, đã làm sạch, phân theo quận** sẵn sàng cho huấn luyện mô hình dự đoán giá thuê. Toàn bộ pipeline được thiết kế cho bài toán **phòng trọ / nhà trọ / căn hộ mini / phòng ở ghép tại Hà Nội**, không phải mua bán bất động sản.

### 2.1. Phân loại nguồn theo độ khó crawl

Trước khi viết code, cần chốt chiến lược theo mức độ chống bot của từng nguồn (đã kiểm chứng tháng 7/2026):

| Nguồn | Loại hình | Kỹ thuật | Phân trang | Toạ độ GPS | Độ ưu tiên |
|---|---|---|---|---|---|
| `phongtro123.com` | Chuyên phòng trọ | `requests` + `BeautifulSoup` | `?page=N` | Không (chỉ địa chỉ text) | **Chính** |
| `mogi.vn` | Portal có mục phòng trọ | `requests` + BS4 + JSON-LD | `?cp=N` | **Có** (JSON-LD + Maps embed) | **Chính** |
| `phongtot.com` | Chuyên phòng trọ (theo toà) | `requests` + BS4 | `?st=N` | **Có** (Maps embed) | **Chính** |
| `tromoi.com` | Chuyên phòng trọ | `requests` + BS4 (qua sitemap) | `?page=N` (robots cấm → dùng sitemap) | Không | **Chính** |
| `batdongsan.com.vn` | Portal BĐS tổng hợp | Playwright + proxy | path `/pN` | Một phần | Phụ (bổ sung) |
| `muaban.net` | Rao vặt | Playwright + proxy | `?page=N` | Không rõ | Phụ (bổ sung) |

Nguyên tắc: **crawl 4 nguồn dễ trước** (chiếm phần lớn khối lượng: phongtro123 ~5.600 tin, mogi ~1.760 tin, phongtot ~3.180 phòng, tromoi ~627 tin), sau đó bổ sung 2 nguồn Cloudflare bằng browser automation nếu cần tăng mẫu.

### 2.2. Kiến trúc crawler

Kiến trúc chia module rõ ràng để mỗi nguồn chỉ cần viết lại tầng `parser`, tái dùng phần còn lại.

```
                        ┌──────────────────────┐
                        │   scheduler.py       │  (APScheduler/cron: chạy định kỳ)
                        │  - đọc config nguồn  │
                        │  - lặp qua source    │
                        └──────────┬───────────┘
                                   │ danh sách URL seed (listing / sitemap)
                                   ▼
   ┌────────────────────────────────────────────────────────────────┐
   │  robots_guard.py   (urllib.robotparser: kiểm tra can_fetch)     │
   └──────────┬─────────────────────────────────────────────────────┘
              │ URL được phép
              ▼
   ┌──────────────────────┐   HTML thô    ┌──────────────────────────┐
   │   fetcher.py         │──────────────▶│  raw_store (JSONL/Mongo) │
   │ - httpx/requests     │               │  giữ nguyên html_raw     │
   │ - Playwright (CF)    │◀──────────────┤  (audit / re-parse)      │
   │ - tenacity retry     │   cache HTML  └──────────────────────────┘
   │ - random delay + UA  │
   └──────────┬───────────┘
              │ HTML
              ▼
   ┌──────────────────────┐    link tin + giá sơ bộ
   │  list_parser.py      │──────────────┐
   │ (parse trang DANH    │              │  đẩy link chi tiết vào hàng đợi
   │  SÁCH → nhiều URL)   │              │
   └──────────────────────┘              ▼
   ┌──────────────────────┐        (quay lại fetcher lấy trang chi tiết)
   │  detail_parser.py    │
   │ (parse trang CHI TIẾT│───── record thô (dict) ──┐
   │  → đủ trường)        │                          │
   └──────────────────────┘                          ▼
                                    ┌────────────────────────────────┐
                                    │        pipeline.py             │
                                    │  1. normalize_price()          │
                                    │  2. normalize_area()           │
                                    │  3. detect_district_ward()     │
                                    │  4. validate schema            │
                                    │  5. dedup (hash + set)         │
                                    └───────────────┬────────────────┘
                                                    │ record sạch
                                                    ▼
                            ┌───────────────────────────────────────┐
                            │  storage.py                           │
                            │  - PostgreSQL (bảng listings sạch)    │
                            │  - export CSV/Parquet theo district   │
                            └───────────────────────────────────────┘
```

Sơ đồ thư mục dự án:

```
rental_crawler/
├── config/
│   └── sources.yaml          # cấu hình từng nguồn (base_url, selector, pagination)
├── crawler/
│   ├── fetcher.py            # tầng tải HTML (requests/httpx + Playwright)
│   ├── robots_guard.py       # kiểm tra robots.txt
│   ├── list_parser.py        # parse trang danh sách
│   ├── detail_parser.py      # parse trang chi tiết (per-source)
│   ├── pipeline.py           # chuẩn hoá + dedup + validate
│   ├── normalizers.py        # normalize_price / normalize_area / geo
│   ├── storage.py            # ghi Mongo/Postgres + export
│   └── scheduler.py          # điều phối, đa nguồn, đa trang
├── data/
│   ├── raw/                  # JSONL thô theo ngày crawl
│   └── datasets/             # CSV/Parquet sạch theo quận
└── requirements.txt
```

### 2.3. Công nghệ Python cụ thể

```txt
# requirements.txt
httpx[http2]==0.27.*        # HTTP client chính (nhanh, hỗ trợ HTTP/2, async)
requests==2.32.*            # dự phòng / script đơn giản
beautifulsoup4==4.12.*      # parse HTML
lxml==5.*                   # backend parser nhanh cho BS4 + xpath cho sitemap
playwright==1.45.*          # cho batdongsan.com.vn / muaban.net (Cloudflare)
tenacity==8.*               # retry có backoff
fake-useragent==1.5.*       # xoay User-Agent
rapidfuzz==3.*              # fuzzy match tên quận/phường
unidecode==1.3.*            # bỏ dấu tiếng Việt để so khớp
pymongo==4.*                # lưu raw
psycopg2-binary==2.9.*      # PostgreSQL
SQLAlchemy==2.*             # ORM cho bảng sạch
pandas==2.*                 # export CSV/Parquet
pyarrow==16.*               # ghi Parquet
APScheduler==3.10.*         # lên lịch định kỳ
python-dateutil==2.9.*      # parse ngày đăng tiếng Việt
```

Lựa chọn:
- **`httpx` (hoặc `requests`) + `BeautifulSoup(lxml)`**: đủ cho 4 nguồn chính. `lxml` làm parser cho tốc độ, đồng thời dùng để đọc sitemap XML.
- **`Playwright`** (khuyến nghị hơn Selenium): chỉ dùng cho 2 nguồn Cloudflare. Có thể nâng cấp lên `playwright-stealth` / `undetected-chromedriver` + residential proxy khi bị Turnstile.
- **`Scrapy`**: lựa chọn **nâng cấp** khi quy mô lớn (nhiều nguồn, cần queue, auto-throttle, middleware retry/proxy sẵn có). Ở quy mô đồ án, kiến trúc module thủ công ở trên đủ và dễ kiểm soát; nếu mở rộng sang >10 nguồn thì port sang Scrapy `Spider` + `Item Pipeline` (map thẳng: `list_parser`→`parse`, `detail_parser`→`parse_detail`, `pipeline`→`ItemPipeline`).

### 2.4. Tầng fetcher: tải HTML lịch sự, có retry

```python
# crawler/fetcher.py
import time, random, logging
import httpx
from fake_useragent import UserAgent
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type

log = logging.getLogger("fetcher")
_ua = UserAgent(browsers=["Chrome", "Firefox", "Edge"])

# Header trông giống trình duyệt thật — bắt buộc với các site đứng sau Cloudflare.
# LƯU Ý: tromoi.com chặn UA bot AI (ClaudeBot/GPTBot/CCBot...) trong robots —
# phải dùng UA trình duyệt thật, KHÔNG để lộ UA bot.
def _browser_headers() -> dict:
    return {
        "User-Agent": _ua.random,
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
        "Upgrade-Insecure-Requests": "1",
    }

class Fetcher:
    def __init__(self, min_delay=1.0, max_delay=3.0, timeout=20.0):
        self.min_delay, self.max_delay = min_delay, max_delay
        # client giữ cookie (XSRF-TOKEN / session Laravel của phongtro123, tromoi...)
        self.client = httpx.Client(
            headers=_browser_headers(), timeout=timeout,
            follow_redirects=True, http2=True,
        )

    @retry(
        retry=retry_if_exception_type((httpx.TransportError, httpx.HTTPStatusError)),
        wait=wait_exponential(multiplier=2, min=2, max=30),
        stop=stop_after_attempt(4),
        reraise=True,
    )
    def get(self, url: str) -> str:
        # delay ngẫu nhiên TRƯỚC mỗi request để tránh rate-limit Cloudflare
        time.sleep(random.uniform(self.min_delay, self.max_delay))
        # đổi UA mỗi lần để giảm fingerprint
        r = self.client.get(url, headers={"User-Agent": _ua.random})
        if r.status_code == 429:          # bị rate-limit → tenacity sẽ backoff
            log.warning("429 Too Many Requests: %s", url)
            r.raise_for_status()
        if r.status_code == 403:          # Cloudflare challenge → chuyển Playwright
            raise CloudflareChallenge(url)
        r.raise_for_status()
        return r.text

    def close(self):
        self.client.close()

class CloudflareChallenge(Exception):
    pass
```

Fetcher cho nguồn Cloudflare (batdongsan, muaban) — chỉ kích hoạt khi `CloudflareChallenge`:

```python
# crawler/fetcher_browser.py
from playwright.sync_api import sync_playwright

class BrowserFetcher:
    def __init__(self, proxy: str | None = None, headless=True):
        self._pw = sync_playwright().start()
        launch = {"headless": headless}
        if proxy:                      # residential/rotating proxy khuyến nghị
            launch["proxy"] = {"server": proxy}
        self.browser = self._pw.chromium.launch(**launch)
        self.ctx = self.browser.new_context(
            locale="vi-VN",
            user_agent=("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                        "AppleWebKit/537.36 (KHTML, like Gecko) "
                        "Chrome/126.0.0.0 Safari/537.36"),
            viewport={"width": 1366, "height": 768},
        )

    def get(self, url: str, wait_selector: str | None = None) -> str:
        page = self.ctx.new_page()
        page.goto(url, wait_until="domcontentloaded", timeout=45000)
        # chờ Cloudflare Turnstile giải xong (cf_clearance được set)
        page.wait_for_timeout(4000)
        if wait_selector:
            page.wait_for_selector(wait_selector, timeout=15000)
        html = page.content()
        page.close()
        return html
```

### 2.5. Tôn trọng robots.txt

```python
# crawler/robots_guard.py
import urllib.robotparser as robotparser
from urllib.parse import urlparse, urljoin
from functools import lru_cache

@lru_cache(maxsize=32)
def _parser_for(base: str) -> robotparser.RobotFileParser:
    rp = robotparser.RobotFileParser()
    rp.set_url(urljoin(base, "/robots.txt"))
    try:
        rp.read()
    except Exception:
        pass   # site chặn cả robots.txt (muaban) → tự áp quy tắc thủ công
    return rp

def can_fetch(url: str, user_agent="Mozilla/5.0") -> bool:
    base = "{0.scheme}://{0.netloc}".format(urlparse(url))
    return _parser_for(base).can_fetch(user_agent, url)
```

Ràng buộc thực tế theo robots đã kiểm chứng:
- **phongtro123.com**: `?page=` được phép; tránh `?paged=`, `?s=`, `?cat=`, `/api`, `/admincp`.
- **mogi.vn**: listing + detail được phép; tránh `/api/`, `/Property/`.
- **tromoi.com**: robots **cấm** `/*?*page=` → **không đi theo phân trang**, mà lấy URL chi tiết qua `sitemap.xml`; đồng thời phải dùng UA trình duyệt thật (UA bot AI bị `Disallow: /`, `Crawl-delay: 10`).
- **phongtot.com**: robots chỉ có content-signals, không cấm path nào.

### 2.6. Xử lý phân trang (3 kiểu)

**Kiểu A — query param `?page=N` / `?cp=N` / `?st=N`** (phongtro123, mogi, phongtot, muaban):

```python
# crawler/list_parser.py
from bs4 import BeautifulSoup

def iter_listing_pages(fetcher, base_url: str, page_param: str,
                       max_pages: int, start=1):
    """Duyệt tuần tự các trang danh sách, dừng khi trang rỗng."""
    for n in range(start, max_pages + 1):
        sep = "&" if "?" in base_url else "?"
        url = base_url if n == 1 and page_param != "st" else f"{base_url}{sep}{page_param}={n}"
        html = fetcher.get(url)
        links = extract_detail_links(html)
        if not links:               # hết tin → chốt phân trang
            break
        yield from links

def extract_detail_links(html: str) -> list[str]:
    soup = BeautifulSoup(html, "lxml")
    links = set()
    # phongtro123: <a href=".../<slug>-pr709133.html">
    for a in soup.select("a[href]"):
        href = a["href"]
        if any(tag in href for tag in ("-pr", "-id", "-tn")) and href.endswith((".html", "")):
            links.add(href)
    return list(links)
```

Cụ thể `page_param` từng nguồn: phongtro123 → `page` (base `/tinh-thanh/ha-noi`, tối đa ~283 trang); mogi → `cp` (`/ha-noi/thue-phong-tro-nha-tro`); phongtot → `st` (`/cho-thue-phong-tro-hn`, ~89 trang); muaban → `page`.

**Kiểu B — path `/pN`** (batdongsan.com.vn):

```python
def batdongsan_page_url(base: str, n: int) -> str:
    # /cho-thue-nha-tro-phong-tro-ha-noi  ->  .../p2, /p3, ...
    return base if n == 1 else f"{base.rstrip('/')}/p{n}"
```

**Kiểu C — sitemap XML** (tromoi.com, để tuân thủ robots; cũng dùng như nguồn URL cho mọi site có sitemap):

```python
import httpx
from lxml import etree

def iter_sitemap_urls(sitemap_url: str, keyword: str = "/phong-tro/"):
    xml = httpx.get(sitemap_url, timeout=20).content
    root = etree.fromstring(xml)
    ns = {"s": "http://www.sitemaps.org/schemas/sitemap/0.9"}
    for loc in root.iterfind(".//s:url/s:loc", ns):
        url = loc.text
        if keyword in url:                 # chỉ lấy URL chi tiết phòng trọ
            yield url
    # nếu là sitemap-index → đệ quy vào từng <sitemap><loc>
    for loc in root.iterfind(".//s:sitemap/s:loc", ns):
        yield from iter_sitemap_urls(loc.text, keyword)
```

**Kiểu D — infinite scroll → gọi API JSON** (mẫu chung khi gặp SPA/scroll vô hạn; muaban có thể đi qua API nội bộ). Mở DevTools → tab Network → tìm request XHR trả JSON, rồi gọi thẳng endpoint đó thay vì render browser:

```python
def fetch_json_api(fetcher, api_url: str, params: dict):
    # nhiều site trả JSON có phân trang dạng page/offset trong query
    page = 1
    while True:
        params["page"] = page
        data = fetcher.client.get(api_url, params=params).json()
        items = data.get("data") or data.get("ads") or []
        if not items:
            break
        yield from items
        page += 1
```

### 2.7. Lấy dữ liệu từ trang DANH SÁCH và trang CHI TIẾT

Trang **danh sách** chỉ để lấy `source_url` + giá/diện tích **sơ bộ** (phục vụ lọc nhanh, đối chiếu). Toàn bộ trường đầy đủ lấy ở trang **chi tiết**.

Ví dụ `detail_parser` cho **phongtro123.com** (HTML thuần):

```python
# crawler/detail_parser.py
from bs4 import BeautifulSoup
from datetime import datetime, timezone

def parse_phongtro123_detail(html: str, url: str) -> dict:
    s = BeautifulSoup(html, "lxml")
    def txt(sel):
        el = s.select_one(sel)
        return el.get_text(" ", strip=True) if el else None

    # bảng thông số: các <li> có <strong> nhãn + giá trị
    info = {}
    for li in s.select(".section-product-detail .info li, ul.section-product-info li"):
        label = li.select_one("strong")
        if label:
            key = label.get_text(strip=True).lower()
            val = li.get_text(" ", strip=True).replace(label.get_text(strip=True), "").strip()
            info[key] = val

    amenities = [a.get_text(strip=True)
                 for a in s.select(".section-product-amenities li, .amenities li")]

    return {
        "title": txt("h1"),
        "price_raw": info.get("mức giá") or info.get("giá"),
        "area_raw": info.get("diện tích"),
        "address_raw": txt(".address") or info.get("địa chỉ"),
        # breadcrumb: Trang chủ > Hà Nội > Quận Đống Đa > ...
        "breadcrumb": [b.get_text(strip=True) for b in s.select(".breadcrumb a, nav.breadcrumb li")],
        "amenities_raw": amenities,
        "description": txt(".section-product-description, .description"),
        "posted_date_raw": info.get("ngày đăng"),
        "room_type": "phong_tro",
        "latitude": None, "longitude": None,   # phongtro123 không có toạ độ
        "source_url": url,
        "source_name": "phongtro123.com",
        "crawled_at": datetime.now(timezone.utc).isoformat(),
        "html_raw": html,                       # giữ HTML thô để re-parse
    }
```

Ví dụ `detail_parser` cho **mogi.vn** — ưu tiên đọc **JSON-LD** (có sẵn toạ độ, giá số nguyên):

```python
import json

def parse_mogi_detail(html: str, url: str) -> dict:
    s = BeautifulSoup(html, "lxml")
    house, offer, geo = {}, {}, {}
    for tag in s.find_all("script", type="application/ld+json"):
        try:
            obj = json.loads(tag.string)
        except (json.JSONDecodeError, TypeError):
            continue
        for node in (obj if isinstance(obj, list) else [obj]):
            t = node.get("@type")
            if t in ("House", "Product", "Residence", "Apartment"):
                house = node
            if t == "Offer" or "offers" in node:
                offer = node.get("offers", node)
            if "geo" in node:
                geo = node["geo"]

    # CẢNH BÁO: trong JSON-LD RealEstateAgent, lat/lng và streetAddress là của
    # VĂN PHÒNG MÔI GIỚI (có thể ở tỉnh khác). Toạ độ THẬT của phòng nằm ở
    # iframe Google Maps embed q=lat,lng — ưu tiên lấy từ đó.
    lat = lng = None
    import re
    m = re.search(r'maps[^"]*[?&]q=(-?\d+\.\d+),(-?\d+\.\d+)', html)
    if m:
        lat, lng = float(m.group(1)), float(m.group(2))
    elif geo:
        lat, lng = geo.get("latitude"), geo.get("longitude")

    addr = house.get("address", {}) if isinstance(house.get("address"), dict) else {}
    return {
        "title": house.get("name") or (s.select_one("h1").get_text(strip=True) if s.select_one("h1") else None),
        "price_raw": str(offer.get("price")) if offer.get("price") else None,
        "area_raw": (house.get("floorSize", {}) or {}).get("value"),
        "address_raw": addr.get("streetAddress"),
        "ward_raw": addr.get("addressLocality"),
        "latitude": lat, "longitude": lng,
        "description": house.get("description"),
        "room_type": "phong_tro",
        "source_url": url, "source_name": "mogi.vn",
        "crawled_at": datetime.now(timezone.utc).isoformat(),
        "html_raw": html,
    }
```

Với **phongtot.com** dùng regex toạ độ tương tự mogi (`maps.google.com/maps?q=21.049,105.741&output=embed`); với **batdongsan/muaban** dùng `BrowserFetcher.get()` rồi parse HTML sau khi qua challenge.

### 2.8. Schema bản ghi đầy đủ

Chuẩn hoá mọi nguồn về một schema thống nhất (dùng `dataclass` để validate kiểu):

```python
# crawler/schema.py
from dataclasses import dataclass, field, asdict
from datetime import date

@dataclass
class Listing:
    title: str
    price_million: float | None        # giá thuê (triệu VND/tháng) — đã chuẩn hoá
    area_m2: float | None              # diện tích (m²) — đã chuẩn hoá
    address: str | None                # địa chỉ đầy đủ (text)
    district: str | None               # quận/huyện (chuẩn hoá: "Đống Đa")
    ward: str | None                   # phường/xã
    street: str | None                 # đường/ngõ
    latitude: float | None
    longitude: float | None
    room_type: str | None              # phong_tro | nha_tro | can_ho_mini | o_ghep
    amenities: list[str] = field(default_factory=list)
    description: str | None = None
    posted_date: date | None = None
    source_url: str = ""               # dùng làm khoá dedup
    source_name: str = ""
    crawled_at: str = ""               # ISO 8601 UTC

    def to_dict(self):
        d = asdict(self)
        d["posted_date"] = self.posted_date.isoformat() if self.posted_date else None
        return d
```

Bảng PostgreSQL tương ứng (DDL):

```sql
CREATE TABLE listings (
    id            BIGSERIAL PRIMARY KEY,
    fingerprint   CHAR(64) UNIQUE NOT NULL,     -- hash dedup
    title         TEXT,
    price_million NUMERIC(6,2),
    area_m2       NUMERIC(7,2),
    address       TEXT,
    district      TEXT,
    ward          TEXT,
    street        TEXT,
    latitude      DOUBLE PRECISION,
    longitude     DOUBLE PRECISION,
    room_type     TEXT,
    amenities     JSONB,
    description   TEXT,
    posted_date   DATE,
    source_url    TEXT,
    source_name   TEXT,
    crawled_at    TIMESTAMPTZ,
    UNIQUE (source_url)
);
CREATE INDEX idx_listings_district ON listings(district);
CREATE INDEX idx_listings_price    ON listings(price_million);
```

### 2.9. Hàm chuẩn hoá GIÁ THUÊ

Xử lý các dạng thực tế: `"4,5 triệu/tháng"`, `"4.500.000đ"`, `"4500000"`, `"3 tr"`, `"800 nghìn"`, `"Thỏa thuận"`, và phân biệt **giá/người** (ở ghép) với **giá/phòng**. Output: `price_million` (triệu VND/tháng).

```python
# crawler/normalizers.py
import re

_NEGOTIABLE = re.compile(r"th[oỏ]a\s*thu[aậ]n|li[eê]n\s*h[eệ]|gi[aá]\s*g[oọ]i", re.I)

def normalize_price(raw: str | float | None,
                    occupants: int | None = None) -> tuple[float | None, str]:
    """
    Trả về (price_million, price_basis).
    price_basis: 'room' (mặc định), 'person' (giá/người), 'negotiable', 'unknown'.
    """
    if raw is None:
        return None, "unknown"
    if isinstance(raw, (int, float)):
        return round(float(raw) / 1_000_000, 2), "room"

    text = str(raw).lower().strip()
    if _NEGOTIABLE.search(text):
        return None, "negotiable"

    # phát hiện đơn giá theo người (ở ghép): "1.2 triệu/người", "/ng"
    per_person = bool(re.search(r"/\s*(ng[uư]?[oờ]i|ng\b|hd)", text))

    # 1) dạng "4,5 triệu" / "4.5 tr" / "3 triệu"
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*(tri[eệ]u|tr\b|củ|m\b)", text)
    if m:
        val = float(m.group(1).replace(",", "."))   # 4,5 -> 4.5
        million = val
    else:
        # 2) dạng "800 nghìn" / "800k" -> 0.8 triệu
        m = re.search(r"(\d+(?:[.,]\d+)?)\s*(ngh[iì]n|ngàn|k\b|nghin)", text)
        if m:
            million = float(m.group(1).replace(",", ".")) / 1000
        else:
            # 3) dạng số nguyên VND: "4.500.000" / "4500000" / "4,500,000"
            digits = re.sub(r"[.,\s]", "", re.sub(r"[^\d.,]", "", text))
            if not digits.isdigit():
                return None, "unknown"
            vnd = int(digits)
            # loại nhiễu: giá trọ hợp lý 300k–50 triệu/tháng
            if vnd < 100_000 or vnd > 100_000_000:
                return None, "unknown"
            million = vnd / 1_000_000

    basis = "person" if (per_person or (occupants and occupants > 1)) else "room"
    return round(million, 3), basis


# --- kiểm thử nhanh ---
assert normalize_price("4,5 triệu/tháng")   == (4.5, "room")
assert normalize_price("4.500.000đ")        == (4.5, "room")
assert normalize_price("4500000")           == (4.5, "room")
assert normalize_price("800 nghìn/tháng")   == (0.8, "room")
assert normalize_price("1.2 triệu/người")[1] == "person"
assert normalize_price("Thỏa thuận")        == (None, "negotiable")
```

Với `price_basis == "person"`, ở khâu feature engineering nên tách cột `is_per_person` hoặc quy đổi về giá/phòng nếu biết số người — **không trộn lẫn** giá/người vào giá/phòng khi train.

### 2.10. Hàm chuẩn hoá DIỆN TÍCH

Xử lý `"25m2"`, `"25 m²"`, `"25,5 m2"`, `"20-25m2"` (dải → lấy trung bình), `"khoảng 30m²"`. Output: `area_m2` (float).

```python
def normalize_area(raw: str | float | None) -> float | None:
    if raw is None:
        return None
    if isinstance(raw, (int, float)):
        return float(raw) if 5 <= float(raw) <= 500 else None

    text = str(raw).lower().replace("²", "2")
    # dạng dải "20-25 m2" / "20 – 25m2"  -> trung bình
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*[-–~]\s*(\d+(?:[.,]\d+)?)\s*m2", text)
    if m:
        a = float(m.group(1).replace(",", "."))
        b = float(m.group(2).replace(",", "."))
        val = (a + b) / 2
    else:
        # dạng đơn "25 m2" / "25,5m2" / "30 mét vuông"
        m = re.search(r"(\d+(?:[.,]\d+)?)\s*(?:m2|mét\s*vuông|met\s*vuong)", text)
        if not m:
            # số trần không đơn vị nhưng hợp lý
            m = re.search(r"^\s*(\d+(?:[.,]\d+)?)\s*$", text)
        if not m:
            return None
        val = float(m.group(1).replace(",", "."))

    return round(val, 1) if 5 <= val <= 500 else None    # loại nhiễu

assert normalize_area("25m2")     == 25.0
assert normalize_area("25 m²")    == 25.0
assert normalize_area("20-25m2")  == 22.5
assert normalize_area("khoảng 30 mét vuông") == 30.0
```

### 2.11. Nhận diện QUẬN / PHƯỜNG từ địa chỉ

Kết hợp: (1) **dictionary** quận Hà Nội + danh sách phường; (2) **regex** bắt cụm "Quận X / Phường Y"; (3) **fuzzy match** (`unidecode` bỏ dấu + `rapidfuzz`) cho trường hợp sai chính tả/thiếu dấu; (4) ưu tiên **breadcrumb** khi có (chính xác nhất).

Lưu ý bối cảnh 2025–2026: Hà Nội đã sắp xếp lại đơn vị hành chính (bỏ cấp quận ở một số nơi, gộp phường). Vì dataset trộn tin cũ + mới, ta **giữ nhãn quận truyền thống** (Đống Đa, Cầu Giấy, Thanh Xuân...) làm feature ổn định để phục vụ ML, đồng thời lưu `ward` thô để tra cứu sau.

```python
# crawler/geo.py
from unidecode import unidecode
from rapidfuzz import process, fuzz
import re

# 12 quận + các huyện/thị xã có nhiều tin trọ
HANOI_DISTRICTS = [
    "Ba Đình", "Hoàn Kiếm", "Tây Hồ", "Long Biên", "Cầu Giấy", "Đống Đa",
    "Hai Bà Trưng", "Hoàng Mai", "Thanh Xuân", "Nam Từ Liêm", "Bắc Từ Liêm",
    "Hà Đông", "Sơn Tây", "Thanh Trì", "Gia Lâm", "Đông Anh", "Hoài Đức",
    "Đan Phượng", "Thanh Oai", "Thường Tín", "Chương Mỹ", "Mê Linh", "Sóc Sơn",
]
# key không dấu -> tên chuẩn
_DIST_LOOKUP = {unidecode(d).lower(): d for d in HANOI_DISTRICTS}
_DIST_KEYS = list(_DIST_LOOKUP.keys())

# (rút gọn) phường theo quận — nạp đầy đủ từ file JSON ngoài
WARDS_BY_DISTRICT = {
    "Thanh Xuân": ["Thanh Xuân Bắc", "Thanh Xuân Nam", "Khương Trung",
                   "Khương Mai", "Nhân Chính", "Hạ Đình", "Kim Giang"],
    "Cầu Giấy":   ["Dịch Vọng", "Dịch Vọng Hậu", "Quan Hoa", "Nghĩa Đô",
                   "Nghĩa Tân", "Mai Dịch", "Trung Hòa", "Yên Hòa"],
    # ... nạp full 500+ phường
}

def detect_district(address: str, breadcrumb: list[str] | None = None) -> str | None:
    # 1) breadcrumb có "Quận Đống Đa" -> chính xác nhất
    if breadcrumb:
        for crumb in breadcrumb:
            d = _match_district_exact(crumb)
            if d:
                return d
    if not address:
        return None
    # 2) regex "quận X" / "q. X" / "huyện X"
    m = re.search(r"(?:qu[aậ]n|huy[eệ]n|q\.?|h\.?)\s+([A-Za-zÀ-ỹ\s]+?)(?:,|$)", address, re.I)
    cand = m.group(1).strip() if m else address
    # 3) exact trên key không dấu
    d = _match_district_exact(cand)
    if d:
        return d
    # 4) fuzzy toàn bộ address (sai/thiếu dấu)
    key = unidecode(address).lower()
    best = process.extractOne(key, _DIST_KEYS, scorer=fuzz.partial_ratio,
                              score_cutoff=88)
    return _DIST_LOOKUP[best[0]] if best else None

def _match_district_exact(text: str) -> str | None:
    key = unidecode(text).lower().strip()
    for dk, name in _DIST_LOOKUP.items():
        if dk in key:
            return name
    return None

def detect_ward(address: str, district: str | None) -> str | None:
    if not address or not district:
        return None
    wards = WARDS_BY_DISTRICT.get(district, [])
    if not wards:
        return None
    key = unidecode(address).lower()
    ward_keys = {unidecode(w).lower(): w for w in wards}
    # exact trước
    for wk, name in ward_keys.items():
        if wk in key:
            return name
    # fuzzy
    best = process.extractOne(key, list(ward_keys), scorer=fuzz.partial_ratio,
                              score_cutoff=90)
    return ward_keys[best[0]] if best else None

# ví dụ
addr = "Ngõ 05 Tứ Mỡ, Trung Kính, Phường Trung Hòa, Quận Cầu Giấy, Hà Nội"
d = detect_district(addr)                 # -> "Cầu Giấy"
w = detect_ward(addr, d)                  # -> "Trung Hòa"
```

Xử lý dấu tiếng Việt: luôn `unidecode(...).lower()` cả hai vế trước khi so khớp để `"cau giay"`, `"Cầu Giấy"`, `"CẦU GIẤY"` cùng khớp; regex dùng lớp ký tự `À-ỹ` để không cắt mất chữ có dấu.

### 2.12. Tránh crawl trùng (dedup + upsert)

Hai tầng chống trùng: (1) `source_url` là khoá tự nhiên trong 1 nguồn; (2) **fingerprint nội dung** `hash(title+price+area+address)` để bắt tin đăng lại/đăng chéo nhiều nguồn.

```python
# crawler/dedup.py
import hashlib, json, os

def content_fingerprint(rec: dict) -> str:
    key = "|".join([
        (rec.get("title") or "").strip().lower(),
        str(rec.get("price_million") or ""),
        str(rec.get("area_m2") or ""),
        (rec.get("address") or "").strip().lower(),
    ])
    return hashlib.sha256(key.encode("utf-8")).hexdigest()

def url_fingerprint(url: str) -> str:
    return hashlib.sha256(url.strip().encode("utf-8")).hexdigest()

class SeenStore:
    """Set các id đã crawl, persist ra đĩa để chạy tiếp phiên sau."""
    def __init__(self, path="data/seen.json"):
        self.path = path
        self.seen: set[str] = set()
        if os.path.exists(path):
            self.seen = set(json.load(open(path, encoding="utf-8")))

    def is_new(self, fp: str) -> bool:
        return fp not in self.seen

    def add(self, fp: str):
        self.seen.add(fp)

    def flush(self):
        json.dump(list(self.seen), open(self.path, "w", encoding="utf-8"))
```

Upsert vào PostgreSQL (không nhân bản, cập nhật tin cũ nếu giá đổi):

```python
def upsert_listing(conn, rec: dict):
    rec["fingerprint"] = content_fingerprint(rec)
    sql = """
    INSERT INTO listings (fingerprint, title, price_million, area_m2, address,
        district, ward, street, latitude, longitude, room_type, amenities,
        description, posted_date, source_url, source_name, crawled_at)
    VALUES (%(fingerprint)s, %(title)s, %(price_million)s, %(area_m2)s, %(address)s,
        %(district)s, %(ward)s, %(street)s, %(latitude)s, %(longitude)s,
        %(room_type)s, %(amenities)s, %(description)s, %(posted_date)s,
        %(source_url)s, %(source_name)s, %(crawled_at)s)
    ON CONFLICT (source_url) DO UPDATE SET
        price_million = EXCLUDED.price_million,
        crawled_at    = EXCLUDED.crawled_at;
    """
    with conn.cursor() as cur:
        cur.execute(sql, {**rec, "amenities": json.dumps(rec.get("amenities", []),
                                                         ensure_ascii=False)})
    conn.commit()
```

### 2.13. Lưu dữ liệu THÔ vs SẠCH

**Nguyên tắc: luôn ghi thô trước, không bao giờ ghi đè.** Trang chi tiết được lưu **nguyên HTML** + record thô ra JSONL theo từng phiên crawl (để re-parse khi sửa selector, phục vụ audit, không phải crawl lại — quan trọng vì tin trọ hết hạn nhanh).

```python
# crawler/raw_store.py
import json, gzip, os
from datetime import date

def append_raw(rec: dict, source: str):
    """Ghi 1 dòng JSONL (nén gzip) vào file theo ngày + nguồn."""
    day = date.today().isoformat()
    path = f"data/raw/{source}_{day}.jsonl.gz"
    os.makedirs("data/raw", exist_ok=True)
    with gzip.open(path, "at", encoding="utf-8") as f:
        f.write(json.dumps(rec, ensure_ascii=False) + "\n")
```

Luồng: `detail_parser` → record thô (có `html_raw`, `price_raw`, `area_raw`, `address_raw`) → `append_raw()` (lưu thô) **và** → `pipeline` chuẩn hoá → `upsert_listing()` (lưu sạch, **bỏ** `html_raw`). Dữ liệu sạch chỉ giữ trường đã chuẩn hoá kiểu số/enum để feed ML.

### 2.14. Khuyến nghị lưu trữ (kết hợp 4 tầng)

Chiến lược phân tầng, mỗi tầng một mục đích:

| Tầng | Công nghệ | Vai trò | Khi nào dùng |
|---|---|---|---|
| 1. Raw dump | **JSONL.gz** (theo ngày) | Bản gốc bất biến, giữ HTML | Luôn ghi — audit, re-parse, backup |
| 2. Raw store | **MongoDB** | Chứa record thô schema biến động (nguồn khác nhau, trường khác nhau) | Khi >2 nguồn, schema chưa ổn định, cần query linh hoạt trên field lồng nhau |
| 3. Clean store | **PostgreSQL** | Dữ liệu sạch, có ràng buộc kiểu, index, dedup | Nguồn chân lý cho phân tích + train; join/aggregate theo quận/giá |
| 4. ML export | **CSV / Parquet** | Snapshot phẳng cho pandas/scikit-learn | Mỗi lần train; Parquet cho dataset lớn (nén tốt, đọc nhanh, giữ kiểu) |

Lý do kết hợp:
- **JSONL.gz** rẻ, an toàn, không phụ thuộc DB — nếu parser sai chỉ cần chạy lại từ file, không crawl lại (tránh bị site chặn IP).
- **MongoDB** hợp với **raw** vì mỗi nguồn có tập trường khác nhau (mogi có `latitude`, tromoi không; phongtot gom theo toà nhà). Schema-less giúp nạp thẳng không cần migration.
- **PostgreSQL** hợp với **sạch** vì cần ràng buộc (`price_million NUMERIC`), `UNIQUE(source_url)` cho upsert, index theo `district`/`price`, và SQL để thống kê (giá trung bình theo quận, phát hiện outlier).
- **Parquet** cho **ML**: giữ kiểu dữ liệu (float/int/category), nén cột, đọc bằng `pandas.read_parquet` nhanh hơn CSV nhiều lần; CSV chỉ dùng khi cần xem bằng mắt/Excel.

Với quy mô đồ án (vài chục nghìn bản ghi), có thể **rút gọn** bỏ MongoDB: JSONL.gz (raw) → PostgreSQL (sạch) → Parquet (ML). MongoDB chỉ thêm khi số nguồn/khối lượng tăng mạnh.

```python
# crawler/export.py  — xuất Parquet + CSV cho ML
import pandas as pd
from sqlalchemy import create_engine

def export_datasets(pg_url: str, out_dir="data/datasets"):
    eng = create_engine(pg_url)
    df = pd.read_sql("SELECT * FROM listings "
                     "WHERE price_million IS NOT NULL AND area_m2 IS NOT NULL", eng)
    # bảng tổng có cột district làm feature
    df.to_parquet(f"{out_dir}/hanoi_rooms_all.parquet", index=False)
    df.to_csv(f"{out_dir}/hanoi_rooms_all.csv", index=False, encoding="utf-8-sig")
    return df
```

### 2.15. Thiết kế dataset theo khu vực (partition theo quận)

Vì giá thuê phụ thuộc mạnh vào **quận** (Cầu Giấy/Đống Đa đắt hơn Hà Đông/Thanh Trì), thiết kế dataset để phục vụ cả mô hình toàn cục lẫn phân tích theo vùng.

**Phương án khuyến nghị (hybrid):** giữ **một bảng tổng** `hanoi_rooms_all.parquet` có cột `district` làm feature (để train mô hình chung), **đồng thời** xuất bản tách theo quận cho phân tích/mô hình theo vùng:

```python
def partition_by_district(df: pd.DataFrame, out_dir="data/datasets", min_rows=150):
    from unidecode import unidecode
    small = []
    for district, grp in df.groupby("district"):
        if district is None:
            continue
        slug = unidecode(district).lower().replace(" ", "_")   # "Thanh Xuân"->"thanh_xuan"
        if len(grp) >= min_rows:
            # đủ mẫu -> file riêng: data/datasets/thanh_xuan.csv, cau_giay.csv...
            grp.to_csv(f"{out_dir}/{slug}.csv", index=False, encoding="utf-8-sig")
            grp.to_parquet(f"{out_dir}/{slug}.parquet", index=False)
        else:
            small.append(grp)     # quận ít mẫu -> gộp lại
    # gộp các quận ít mẫu, VẪN giữ cột district làm feature phân biệt
    if small:
        pd.concat(small).to_csv(f"{out_dir}/_other_districts.csv",
                                index=False, encoding="utf-8-sig")
```

Quy tắc quyết định:
- **Quận nhiều mẫu** (≥150 bản ghi, ví dụ Cầu Giấy, Đống Đa, Thanh Xuân, Nam Từ Liêm, Hà Đông): tách file riêng `data/datasets/cau_giay.csv`... để có thể train mô hình chuyên biệt hoặc phân tích riêng.
- **Quận/huyện ít mẫu** (Sơn Tây, Mê Linh, Sóc Sơn...): **gộp lại** vào `_other_districts.csv` và **dùng `district` làm feature** (one-hot / target encoding) thay vì tách bảng — tránh mô hình theo vùng bị thiếu dữ liệu, overfit.
- Mô hình chính vẫn train trên **bảng tổng** với `district` (và `ward`, `latitude`, `longitude` khi có) làm feature; partition theo quận chủ yếu phục vụ EDA, so sánh giá vùng, và fallback khi cần mô hình riêng.

Kết quả Bước 2: bộ file trong `data/datasets/` gồm `hanoi_rooms_all.parquet` (train chính) + các `*.csv/*.parquet` theo quận (phân tích vùng), với mọi bản ghi đã chuẩn hoá `price_million`, `area_m2`, `district`, `ward` — sẵn sàng cho Bước tiền xử lý và huấn luyện mô hình dự đoán giá.


---


# BƯỚC 3: Tiền xử lý dữ liệu & Feature Engineering

Sau khi thu thập dữ liệu thô từ các nguồn tin đăng (Chợ Tốt, Phongtro123, Mogi, Batdongsan, các nhóm Facebook cho thuê phòng trọ Hà Nội), dữ liệu ở dạng bảng `raw_df` với các cột điển hình:

| Cột thô | Kiểu | Ví dụ giá trị |
|---|---|---|
| `id`, `url` | str | mã tin / đường link |
| `title` | str | "Cho thuê phòng trọ Cầu Giấy khép kín, có điều hòa" |
| `description` | str | đoạn text dài mô tả phòng |
| `price_raw` | str | "3,5 triệu/tháng", "3500000", "2tr8" |
| `area_raw` | str | "25 m2", "25m²", "0.25" (lỗi) |
| `district`, `ward`, `street` | str | "Cầu Giấy", "Dịch Vọng", ... |
| `room_type` | str | "Phòng trọ", "CCMN", "Nhà nguyên căn" |
| `lat`, `long` | float | 21.0369, 105.7905 (có thể null) |
| `floor`, `posted_date` | str | "Tầng 3", "2026-05-12" |

Mục tiêu của bước này là biến `raw_df` thành ma trận đặc trưng `X` sạch, chuẩn hoá, không rò rỉ, sẵn sàng huấn luyện; với **biến mục tiêu `target = price_million`** (giá thuê quy về đơn vị triệu VND/tháng).

---

## 3.1. Pipeline làm sạch dữ liệu cơ bản

Nguyên tắc: mỗi bước là một hàm thuần (pure function) nhận DataFrame, trả về DataFrame, để dễ ghép nối và test.

```python
import re
import numpy as np
import pandas as pd

# ----------------------------------------------------------------------
# 3.1.1 Bỏ trùng lặp theo id và url (một tin có thể được crawl nhiều lần)
# ----------------------------------------------------------------------
def drop_duplicates(df: pd.DataFrame) -> pd.DataFrame:
    before = len(df)
    # Ưu tiên id; nếu id trùng nhưng url khác vẫn coi là 1 tin
    df = df.drop_duplicates(subset=["id"], keep="first")
    df = df.drop_duplicates(subset=["url"], keep="first")
    # Trùng "mềm": cùng title + price_raw + area_raw + district (repost)
    df = df.drop_duplicates(
        subset=["title", "price_raw", "area_raw", "district"], keep="first"
    )
    print(f"[dedup] {before} -> {len(df)} dòng (loại {before - len(df)})")
    return df.reset_index(drop=True)


# ----------------------------------------------------------------------
# 3.1.2 Bỏ bản ghi thiếu price hoặc area (không thể huấn luyện/đánh giá)
# ----------------------------------------------------------------------
def drop_missing_core(df: pd.DataFrame) -> pd.DataFrame:
    df = df.dropna(subset=["price_raw", "area_raw"])
    df = df[df["price_raw"].astype(str).str.strip() != ""]
    df = df[df["area_raw"].astype(str).str.strip() != ""]
    return df.reset_index(drop=True)
```

### Chuẩn hoá đơn vị giá → `price_million`

Text giá ở Hà Nội xuất hiện dưới nhiều dạng: `"3,5 triệu"`, `"3.5tr"`, `"2tr8"`, `"3500000"`, `"3500 nghìn"`, `"350"` (nghìn). Cần một parser regex chịu lỗi:

```python
def parse_price_to_million(text) -> float:
    """Trả về giá theo TRIỆU VND/tháng, NaN nếu không parse được."""
    if pd.isna(text):
        return np.nan
    s = str(text).lower().strip()
    s = s.replace(".", "").replace(",", ".")  # 3.500.000 -> 3500000 ; 3,5 -> 3.5
    # (cẩn thận: cách replace trên phù hợp khi dấu . là phân tách nghìn)

    # Dạng "2tr8" -> 2.8 triệu
    m = re.search(r"(\d+)\s*tr(?:iệu)?\s*(\d)", s)
    if m:
        return float(m.group(1)) + float(m.group(2)) / 10

    # Lấy số đầu tiên
    num = re.search(r"(\d+(?:\.\d+)?)", s)
    if not num:
        return np.nan
    val = float(num.group(1))

    if "tr" in s or "triệu" in s:          # đơn vị triệu
        return val
    if "ngh" in s or "nghìn" in s or "k" in s:  # đơn vị nghìn
        return val / 1000
    # Không có đơn vị -> suy luận theo độ lớn
    if val >= 100_000:      # 3500000 -> 3.5 triệu
        return val / 1_000_000
    if val >= 100:          # 3500 (nghìn) -> 3.5 triệu
        return val / 1000
    return val              # 3.5 -> đã là triệu
```

### Chuẩn hoá diện tích → `area_m2`

```python
def parse_area_to_m2(text) -> float:
    if pd.isna(text):
        return np.nan
    s = str(text).lower().replace(",", ".")
    m = re.search(r"(\d+(?:\.\d+)?)", s)
    if not m:
        return np.nan
    val = float(m.group(1))
    # Lỗi thường gặp: nhập 0.25 thay vì 25, hoặc 250 (nhầm feet/nhập thừa)
    if val < 1:
        val *= 100          # 0.25 -> 25
    if val > 500:
        return np.nan        # phi lý cho phòng trọ -> loại
    return val


def normalize_units(df: pd.DataFrame) -> pd.DataFrame:
    df["price_million"] = df["price_raw"].apply(parse_price_to_million)
    df["area_m2"]       = df["area_raw"].apply(parse_area_to_m2)
    df = df.dropna(subset=["price_million", "area_m2"]).reset_index(drop=True)
    return df
```

---

## 3.2. Xử lý ngoại lệ (Outliers)

Với phòng trọ Hà Nội, chỉ dùng IQR thuần sẽ cắt nhầm (ví dụ CCMN cao cấp giá 12 triệu là hợp lệ). Chiến lược **3 tầng lọc**:

**Tầng 1 — Quy tắc nghiệp vụ (hard rules):** dựa trên hiểu biết thị trường Hà Nội 2024–2026.

| Loại hình | Khoảng giá hợp lệ (triệu) | Diện tích hợp lệ (m²) |
|---|---|---|
| Phòng trọ / phòng cho thuê | 1.0 – 15.0 | 8 – 60 |
| Nhà trọ khép kín | 1.5 – 12.0 | 10 – 45 |
| Căn hộ mini / CCMN | 2.5 – 18.0 | 15 – 70 |

**Tầng 2 — Đơn giá (`don_gia = price_million / area_m2`):** bắt các tin lệch bất thường (giá cao, diện tích nhỏ hoặc ngược lại). Đơn giá phòng trọ Hà Nội thực tế ~ 0.08–0.55 triệu/m²/tháng.

**Tầng 3 — IQR/percentile clipping trong từng nhóm quận** để không cắt oan khu trung tâm đắt đỏ (Hoàn Kiếm, Ba Đình) so với vùng ven (Hà Đông, Long Biên).

```python
# ----------------------------------------------------------------------
# 3.2.1 Quy tắc nghiệp vụ
# ----------------------------------------------------------------------
BUSINESS_RULES = {
    "default":   {"price": (1.0, 15.0), "area": (8, 60)},
    "can_ho_mini": {"price": (2.5, 18.0), "area": (15, 70)},
}

def filter_business_rules(df: pd.DataFrame) -> pd.DataFrame:
    p_min, p_max = 1.0, 18.0     # biên rộng nhất cho toàn tập
    a_min, a_max = 8, 70
    mask = (
        df["price_million"].between(p_min, p_max)
        & df["area_m2"].between(a_min, a_max)
    )
    return df[mask].reset_index(drop=True)


# ----------------------------------------------------------------------
# 3.2.2 Lọc theo đơn giá
# ----------------------------------------------------------------------
def filter_unit_price(df: pd.DataFrame,
                      lo: float = 0.06, hi: float = 0.60) -> pd.DataFrame:
    df["don_gia"] = df["price_million"] / df["area_m2"]
    mask = df["don_gia"].between(lo, hi)
    print(f"[unit_price] loại {(~mask).sum()} tin lệch đơn giá")
    return df[mask].reset_index(drop=True)


# ----------------------------------------------------------------------
# 3.2.3 IQR clipping theo từng quận (winsorize, KHÔNG xoá dòng)
# ----------------------------------------------------------------------
def clip_iqr_by_district(df: pd.DataFrame, col: str = "price_million",
                         k: float = 1.5) -> pd.DataFrame:
    def _clip(g):
        q1, q3 = g[col].quantile(0.25), g[col].quantile(0.75)
        iqr = q3 - q1
        lo, hi = q1 - k * iqr, q3 + k * iqr
        g[col] = g[col].clip(lower=lo, upper=hi)
        return g
    return df.groupby("district", group_keys=False).apply(_clip)
```

Lưu ý: với **giá** ta *winsorize* (kẹp về biên) thay vì xoá, để giữ số lượng mẫu; với **đơn giá phi lý** thì *xoá* vì đó thường là tin sai/spam.

---

## 3.3. Xử lý văn bản tiêu đề & mô tả

Text tiếng Việt cần: chuyển thường, bỏ dấu/emoji, **xoá số điện thoại và link** (nhiễu, không mang thông tin giá), tách từ tiếng Việt bằng `underthesea` hoặc `pyvi`.

```python
# pip install underthesea   (hoặc:  pip install pyvi)
import re, unicodedata
from underthesea import word_tokenize   # tách từ tiếng Việt chính xác hơn split()

PHONE_RE = re.compile(r"(0|\+84)(\s|\.|-)?(\d(\s|\.|-)?){8,10}")
LINK_RE  = re.compile(r"(https?://\S+|www\.\S+|\S+\.(com|vn|net)\S*)")
EMOJI_RE = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U0001F000-\U0001F0FF]+",
    flags=re.UNICODE,
)

def clean_text(text) -> str:
    if pd.isna(text):
        return ""
    s = str(text).lower()
    s = LINK_RE.sub(" ", s)                 # bỏ link
    s = PHONE_RE.sub(" ", s)                # bỏ SĐT
    s = EMOJI_RE.sub(" ", s)                # bỏ emoji
    s = re.sub(r"[^\w\sàáảãạăâ...đ]", " ", s, flags=re.UNICODE)  # bỏ ký tự đặc biệt
    s = re.sub(r"\s+", " ", s).strip()
    return s

def tokenize_vi(text: str) -> str:
    # underthesea nối cụm từ bằng dấu "_": "điều_hòa", "khép_kín"
    return word_tokenize(text, format="text")

df["desc_clean"]  = df["description"].apply(clean_text).apply(tokenize_vi)
df["title_clean"] = df["title"].apply(clean_text).apply(tokenize_vi)
```

**TF-IDF cho mô tả** (giới hạn số features để tránh sparse quá rộng và overfit). Vector này chỉ nên fit trên tập train (mục 3.8):

```python
from sklearn.feature_extraction.text import TfidfVectorizer

tfidf = TfidfVectorizer(
    max_features=150,        # giới hạn 150 từ/cụm quan trọng nhất
    ngram_range=(1, 2),      # unigram + bigram: "gần trường", "khép_kín"
    min_df=5,                # bỏ từ hiếm (< 5 tin)
    max_df=0.85,             # bỏ từ quá phổ biến (stopword ngầm)
)
# X_desc_tfidf = tfidf.fit_transform(train_df["desc_clean"])  # fit ở train
```

> Khuyến nghị cho đồ án: thay vì đưa toàn bộ 150 cột TF-IDF (khó giải thích, dễ nhiễu), **ưu tiên trích xuất keyword tiện ích thành cột boolean** (mục 3.4) — vừa dễ diễn giải, vừa mạnh cho mô hình cây. TF-IDF chỉ dùng như tập feature bổ sung cho mô hình tuyến tính khi cần.

---

## 3.4. Trích xuất tiện ích từ mô tả (từ điển từ khoá → cột `has_*`)

Đây là nhóm feature giàu thông tin nhất từ text. Dùng từ điển regex bao phủ các biến thể viết tay/không dấu:

```python
AMENITY_PATTERNS = {
    "has_air_conditioner": r"điều\s*hòa|điều\s*hoà|dieu\s*hoa|máy\s*lạnh|có\s*đh|\bđh\b",
    "has_water_heater":    r"nóng\s*lạnh|bình\s*nóng|nong\s*lanh|bình\s*nl|water\s*heater",
    "has_washing_machine": r"máy\s*giặt|may\s*giat|giặt\s*chung|máy\s*gịăt",
    "has_elevator":        r"thang\s*máy|thang\s*may|elevator",
    "has_balcony":         r"ban\s*công|ban\s*cong|logia|lô\s*gia|có\s*bancol",
    "has_parking":         r"chỗ\s*để\s*xe|để\s*xe|hầm\s*xe|gửi\s*xe|bãi\s*xe|de\s*xe",
    "has_private_wc":      r"khép\s*kín|khep\s*kin|vệ\s*sinh\s*riêng|wc\s*riêng|vs\s*khép|toilet\s*riêng",
    "has_kitchen":         r"\bbếp\b|kệ\s*bếp|nấu\s*ăn|khu\s*bếp|có\s*bep",
    "has_wifi":            r"wifi|internet|mạng\s*miễn\s*phí|free\s*wifi",
    "has_free_time":       r"giờ\s*giấc\s*tự\s*do|tự\s*do\s*giờ|khóa\s*vân\s*tay|ko\s*chung\s*chủ|không\s*chung\s*chủ|khóa\s*từ",
    "has_owner_separate":  r"không\s*chung\s*chủ|ko\s*chung\s*chủ|khép\s*kín\s*riêng",
    "has_fridge":          r"tủ\s*lạnh|tu\s*lanh",
    "has_furniture":       r"full\s*nội\s*thất|đầy\s*đủ\s*nội\s*thất|nội\s*thất\s*cao\s*cấp|có\s*giường|có\s*tủ",
}

def extract_amenities(df: pd.DataFrame,
                      text_col: str = "desc_clean") -> pd.DataFrame:
    # ghép title + description để không bỏ sót
    corpus = (df["title_clean"].fillna("") + " " + df[text_col].fillna(""))
    for col, pattern in AMENITY_PATTERNS.items():
        rx = re.compile(pattern, flags=re.IGNORECASE)
        df[col] = corpus.str.contains(rx).astype("int8")

    # Đếm tổng số tiện ích -> feature số rất mạnh
    amenity_cols = list(AMENITY_PATTERNS.keys())
    df["number_of_amenities"] = df[amenity_cols].sum(axis=1)
    return df
```

Kết quả: mỗi tin có ~13 cột boolean `has_*` và một cột đếm `number_of_amenities`.

---

## 3.5. Mã hoá biến phân loại (Encoding)

`district`, `ward`, `room_type` là categorical. Cách encode phụ thuộc họ mô hình — đây là điểm phải giải thích rõ trong đồ án:

| Chiến lược | Dùng cho mô hình | Lý do |
|---|---|---|
| **One-Hot Encoding** | Tuyến tính (Linear/Ridge/Lasso), SVR | Mô hình tuyến tính không hiểu quan hệ thứ tự; One-Hot tránh áp đặt thứ tự giả. Nhược điểm: `ward` Hà Nội có hàng trăm giá trị → bùng nổ chiều. |
| **Target Encoding** | Cây (LightGBM/XGBoost/RandomForest) | Thay mỗi phường bằng giá trung bình (đã smoothing) → 1 cột, giữ tín hiệu địa lý mà không tăng chiều. Phải fit trên train + smoothing để tránh rò rỉ. |
| **Ordinal / label** | Cây (nếu ít hạng) | Cây tự cắt ngưỡng nên không bị ảnh hưởng bởi thứ tự vô nghĩa. |
| **Để raw (string)** | CatBoost | CatBoost xử lý categorical nội bộ bằng ordered target statistics → chỉ cần truyền `cat_features`, không cần encode tay, chống rò rỉ tốt nhất. |

```python
# --- One-Hot cho mô hình tuyến tính ---
from sklearn.preprocessing import OneHotEncoder
ohe = OneHotEncoder(handle_unknown="ignore", sparse_output=False,
                    min_frequency=20)   # gộp phường hiếm vào 'infrequent'

# --- Target Encoding cho mô hình cây ---
# pip install category_encoders
from category_encoders import TargetEncoder
te = TargetEncoder(cols=["district", "ward", "room_type"],
                   smoothing=10)        # smoothing chống overfit phường ít mẫu

# --- CatBoost: KHÔNG encode, chỉ khai báo ---
cat_features = ["district", "ward", "room_type"]
# model = CatBoostRegressor(cat_features=cat_features, ...)
```

---

## 3.6. Chuẩn hoá đặc trưng số (Scaling) & `distance_to_center_km`

### Tính khoảng cách tới trung tâm (Hồ Gươm)

Hồ Gươm ≈ `(21.0287, 105.8524)`. Nếu có `lat/long` → dùng haversine; nếu thiếu → gán giá trị trung bình khoảng cách theo quận (fallback).

```python
def haversine_km(lat1, lon1, lat2=21.0287, lon2=105.8524) -> float:
    R = 6371.0
    lat1, lon1, lat2r, lon2r = map(np.radians, [lat1, lon1, lat2, lon2])
    dlat, dlon = lat2r - lat1, lon2r - lon1
    a = np.sin(dlat/2)**2 + np.cos(lat1)*np.cos(lat2r)*np.sin(dlon/2)**2
    return R * 2 * np.arcsin(np.sqrt(a))

def add_distance_to_center(df: pd.DataFrame) -> pd.DataFrame:
    has_geo = df["lat"].notna() & df["long"].notna()
    df.loc[has_geo, "distance_to_center_km"] = haversine_km(
        df.loc[has_geo, "lat"], df.loc[has_geo, "long"]
    )
    # Fallback: gán trung bình theo quận (tính CHỈ trên train để tránh rò rỉ)
    district_mean = df.loc[has_geo].groupby("district")["distance_to_center_km"].mean()
    df["distance_to_center_km"] = df.apply(
        lambda r: r["distance_to_center_km"] if pd.notna(r["distance_to_center_km"])
        else district_mean.get(r["district"], district_mean.mean()),
        axis=1,
    )
    return df
```

### Scaling

- **Mô hình cây (LightGBM/XGBoost/RandomForest/CatBoost): KHÔNG cần scale** — chúng cắt ngưỡng theo phân vị, bất biến với phép biến đổi đơn điệu.
- **Mô hình tuyến tính / SVR / KNN: BẮT BUỘC scale**. Do dữ liệu còn ngoại lệ nhẹ, ưu tiên `RobustScaler` (dựa trên median/IQR) hơn `StandardScaler`.

```python
from sklearn.preprocessing import StandardScaler, RobustScaler
num_cols = ["area_m2", "number_of_amenities", "distance_to_center_km", "floor"]
scaler = RobustScaler()   # bền với outlier hơn StandardScaler cho dữ liệu giá thuê
# scaler.fit(train_df[num_cols])  # CHỈ fit trên train
```

---

## 3.7. Xử lý giá trị thiếu (Missing Values)

Chiến lược riêng theo từng cột, đảm bảo mọi thống kê điền khuyết đều **fit trên train**:

| Cột | Chiến lược | Ghi chú |
|---|---|---|
| `area_m2`, `distance_to_center_km`, `floor` | Điền **median** (theo `district` nếu đủ mẫu) | Median bền với outlier hơn mean |
| `district` | `"unknown"` + suy luận từ `ward`/`street` nếu map được | |
| `ward` | Suy luận từ `street` qua bảng tra cứu phố→phường; nếu không → `"unknown"` | |
| `room_type` | Suy luận từ text (có "ccmn"/"căn hộ mini" → `can_ho_mini`); mặc định `"phong_tro"` | |
| `has_*` | Điền `0` (không nhắc trong mô tả coi như không có) | |
| `floor` | Median theo `room_type`; phòng trọ mặc định tầng 1–2 | |

```python
from sklearn.impute import SimpleImputer

def impute_missing(df, num_medians=None):
    # số: median theo quận
    for col in ["area_m2", "distance_to_center_km", "floor"]:
        med = df.groupby("district")[col].transform("median")
        df[col] = df[col].fillna(med).fillna(df[col].median())
    # categorical
    for col in ["district", "ward", "room_type"]:
        df[col] = df[col].fillna("unknown").replace("", "unknown")
    # boolean tiện ích
    for col in AMENITY_PATTERNS.keys():
        df[col] = df[col].fillna(0).astype("int8")
    return df

# Suy luận ward từ street (ví dụ bảng tra cứu tay/crawl từ danh mục hành chính)
def infer_ward_from_street(df, street2ward: dict):
    mask = df["ward"].isin(["unknown", "", np.nan])
    df.loc[mask, "ward"] = df.loc[mask, "street"].map(street2ward).fillna("unknown")
    return df
```

---

## 3.8. Chia dữ liệu & chống rò rỉ (Train / Validation / Test)

Tỉ lệ **70/15/15**, **stratify theo `district`** để mỗi tập giữ nguyên phân bố địa lý (khu trung tâm ít tin không bị dồn hết vào một tập).

```python
from sklearn.model_selection import train_test_split

# Tách test trước (15%)
train_val, test = train_test_split(
    df, test_size=0.15, random_state=42, stratify=df["district"]
)
# Tách val từ phần còn lại (15/85 ≈ 0.1765)
train, val = train_test_split(
    train_val, test_size=0.1765, random_state=42, stratify=train_val["district"]
)
print(len(train), len(val), len(test))   # ~70 / 15 / 15 %
```

**Chống rò rỉ (data leakage) — quy tắc bắt buộc:** mọi bộ biến đổi có "học" tham số (scaler, encoder, TF-IDF, target encoding, median điền khuyết) chỉ được `fit` trên `train`, sau đó `transform` cho `val`/`test`. Gói bằng `Pipeline` + `ColumnTransformer` để đảm bảo tự động đúng khi cross-validation:

```python
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.impute import SimpleImputer

numeric_features = ["area_m2", "number_of_amenities",
                    "distance_to_center_km", "floor"]
categorical_features = ["district", "ward", "room_type"]
boolean_features = list(AMENITY_PATTERNS.keys())   # đã 0/1, giữ nguyên

# --- Pipeline cho mô hình TUYẾN TÍNH ---
preprocess_linear = ColumnTransformer(transformers=[
    ("num", Pipeline([
        ("impute", SimpleImputer(strategy="median")),
        ("scale",  RobustScaler()),
    ]), numeric_features),
    ("cat", Pipeline([
        ("impute", SimpleImputer(strategy="constant", fill_value="unknown")),
        ("ohe",    OneHotEncoder(handle_unknown="ignore", min_frequency=20)),
    ]), categorical_features),
    ("bool", "passthrough", boolean_features),
])

# --- Pipeline cho mô hình CÂY (không scale, target-encode) ---
preprocess_tree = ColumnTransformer(transformers=[
    ("num",  SimpleImputer(strategy="median"), numeric_features),
    ("cat",  TargetEncoder(smoothing=10), categorical_features),
    ("bool", "passthrough", boolean_features),
])

X_train = preprocess_linear.fit_transform(train, train["price_million"])  # fit ở train
X_val   = preprocess_linear.transform(val)
X_test  = preprocess_linear.transform(test)
y_train = train["price_million"]
```

> Gợi ý bổ sung: vì phân bố giá lệch phải, có thể huấn luyện trên `y = log1p(price_million)` rồi `expm1` khi dự đoán để giảm ảnh hưởng đuôi giá cao; đánh giá vẫn quy về triệu VND (MAE/RMSE) để dễ diễn giải.

---

## 3.9. Bảng đặc trưng cuối cùng đưa vào mô hình

**Biến mục tiêu: `target = price_million`** (triệu VND/tháng).

| Feature | Kiểu | Nguồn / cách lấy |
|---|---|---|
| `area_m2` | float | Parse từ `area_raw`, chuẩn hoá m² (3.1) |
| `district` | category | Chuẩn hoá tên quận; encode theo mô hình (3.5) |
| `ward` | category | Chuẩn hoá / suy luận từ `street` (3.7) |
| `room_type` | category | `phong_tro` / `can_ho_mini` / `nha_tro`... suy từ text |
| `number_of_amenities` | int | Tổng các cột `has_*` (3.4) |
| `has_air_conditioner` | bool 0/1 | Regex điều hoà (3.4) |
| `has_private_wc` | bool 0/1 | Regex "khép kín / WC riêng" |
| `has_water_heater` | bool 0/1 | Regex "nóng lạnh" |
| `has_washing_machine` | bool 0/1 | Regex "máy giặt" |
| `has_parking` | bool 0/1 | Regex "chỗ để xe / gửi xe" |
| `has_balcony` | bool 0/1 | Regex "ban công / logia" |
| `has_elevator` | bool 0/1 | Regex "thang máy" |
| `has_kitchen` | bool 0/1 | Regex "bếp / khu nấu ăn" |
| `has_wifi` | bool 0/1 | Regex "wifi / internet" |
| `has_free_time` | bool 0/1 | Regex "giờ giấc tự do / không chung chủ" |
| `distance_to_center_km` | float | Haversine tới Hồ Gươm, fallback trung bình theo quận (3.6) |
| `floor` | int (tuỳ chọn) | Parse "Tầng X"; median nếu thiếu |
| `posted_month` | int (1–12) | `pd.to_datetime(posted_date).dt.month` — bắt yếu tố mùa vụ (mùa nhập học tháng 8–9 giá tăng) |
| `title_desc_tfidf_*` | float sparse (tuỳ chọn) | 150 cột TF-IDF chỉ cho mô hình tuyến tính (3.3) |

```python
df["posted_month"] = pd.to_datetime(df["posted_date"], errors="coerce").dt.month
```

---

## 3.10. Gợi ý mức độ quan trọng của đặc trưng (theo kinh nghiệm thị trường)

Xếp hạng kỳ vọng (sẽ kiểm chứng lại bằng `feature_importance` / SHAP ở bước mô hình hoá):

1. **Rất cao — `area_m2`**: quan hệ gần tuyến tính với giá; yếu tố đơn lẻ mạnh nhất.
2. **Rất cao — vị trí (`district`, `ward`, `distance_to_center_km`)**: cùng diện tích, Cầu Giấy/Đống Đa/Thanh Xuân đắt hơn hẳn Hà Đông/Gia Lâm. `ward` bổ sung độ phân giải trong quận.
3. **Cao — `room_type`**: căn hộ mini/CCMN có mặt bằng giá cao hơn phòng trọ thường ở cùng diện tích.
4. **Cao — tiện ích chính**: `has_private_wc` (khép kín), `has_air_conditioner`, `has_elevator` (báo hiệu CCMN), và `number_of_amenities` như chỉ số tổng hợp chất lượng.
5. **Trung bình — `floor`, `has_water_heater`, `has_balcony`, `has_parking`**: ảnh hưởng biên, phụ thuộc phân khúc.
6. **Thấp — `posted_month`, TF-IDF text**: chủ yếu là tín hiệu bổ trợ/mùa vụ, dễ nhiễu; giữ với trọng số thấp.

Kết luận nghiệp vụ: **bộ ba Diện tích × Vị trí × Loại phòng, cộng nhóm tiện ích khép kín/điều hoà**, giải thích phần lớn biến thiên giá thuê phòng trọ tại Hà Nội — đây là các đặc trưng cần được làm sạch và mã hoá cẩn thận nhất.


---


# BƯỚC 4: Huấn luyện model (ví dụ khu vực Thanh Xuân) & Serving

Sau khi đã có tập dữ liệu sạch với các đặc trưng đã mã hóa ở Bước 3, bước này tập trung vào việc chọn thuật toán, huấn luyện, so sánh, tinh chỉnh, lưu và phục vụ (serving) model dự đoán giá thuê. Ta minh họa chi tiết trên tập con **quận Thanh Xuân** (khoảng 1.500–3.000 bản ghi phòng trọ/nhà trọ/căn hộ mini sau khi lọc), sau đó tổng quát hóa sang kiến trúc **một model riêng cho mỗi quận** và fallback về model Hà Nội tổng.

---

## 4.1. Vì sao chọn 4 nhóm model này?

Bài toán của ta là **hồi quy** (regression) trên dữ liệu **dạng bảng (tabular)** với khoảng 10–20 đặc trưng, phần lớn là **categorical** (`ward`, `room_type`, các tiện ích dạng one-hot) trộn với vài đặc trưng numeric (`area_m2`, `floor`, `distance_to_center_km`). Kích thước dataset theo từng quận là **nhỏ đến trung bình** (vài nghìn dòng). Đặc điểm này quyết định lựa chọn model. Ta chọn 4 nhóm để có đủ phổ từ *đơn giản – dễ giải thích* đến *mạnh – chính xác*.

| Nhóm | Model đại diện | Lý do chọn cho bài toán giá thuê |
|------|----------------|-----------------------------------|
| (1) Tuyến tính | **Ridge Regression** | **Baseline bắt buộc**. Dễ giải thích cho chủ trọ/người thuê: mỗi hệ số cho biết "thêm 1 m² → +X đồng". Ridge (L2) hợp dataset nhỏ, chống overfit tốt hơn Linear thuần khi có nhiều cột one-hot tương quan. Serve cực nhẹ (chỉ là vector hệ số). |
| (2) Bagging | **RandomForestRegressor** | Bắt được quan hệ **phi tuyến** (giá không tăng tuyến tính theo diện tích) và **tương tác** (ví dụ "gần trung tâm + có thang máy"). Ít overfit trên dataset nhỏ nhờ trung bình hóa nhiều cây. Không cần scale dữ liệu, robust với outlier giá. |
| (3) Gradient Boosting nhanh | **XGBoost / LightGBM** | **Mạnh nhất với tabular** hiện nay. LightGBM có **native categorical** (không cần one-hot), train nhanh, model nhẹ (~vài MB), load nhanh → dễ deploy vào backend. Đây thường là model cho độ chính xác cao nhất. |
| (4) Boosting robust | **CatBoost / GradientBoosting** | **CatBoost xử lý categorical tốt nhất** (ordered target encoding native, chống target leakage), **ít overfit trên dataset nhỏ** nhờ ordered boosting, ít cần tuning. GradientBoosting của sklearn là phương án dự phòng thuần Python. |

**Tóm tắt mapping đặc điểm → model:**

- **Dataset nhỏ, sợ overfit** → Ridge, RandomForest, CatBoost (ordered boosting) an toàn hơn XGBoost thô.
- **Mạnh nhất với tabular** → nhóm gradient boosting (XGBoost/LightGBM/CatBoost).
- **Xử lý categorical native tốt nhất** → CatBoost (mã hóa native + ordered), kế đến LightGBM (`categorical_feature`). Ridge/RandomForest/XGBoost cần one-hot qua `ColumnTransformer`.
- **Dễ deploy vào backend** → Ridge (vector hệ số, <100 KB, load tức thì) và LightGBM (model nhẹ, `Booster` load nhanh). CatBoost file lớn hơn một chút nhưng vẫn ổn.

> Chiến lược: dùng **Ridge làm mốc so sánh**, kỳ vọng một trong nhóm boosting sẽ thắng. Nếu boosting **không vượt Ridge đủ nhiều** (ví dụ chỉ giảm MAE <5%) thì chọn Ridge để dễ serve và dễ giải thích.

---

## 4.2. Chuẩn bị dữ liệu và Pipeline

Dùng `sklearn.compose.ColumnTransformer` gói toàn bộ tiền xử lý vào Pipeline để **không rò rỉ dữ liệu** (mọi phép fit chỉ học trên fold train khi CV) và để **serving nhất quán** (input thô → output giá, không cần lặp lại code tiền xử lý ở backend).

```python
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer

# --- Load dữ liệu quận Thanh Xuân đã làm sạch ở Bước 3 ---
df = pd.read_parquet("data/processed/thanh_xuan.parquet")

# Đặc trưng đầu vào
NUMERIC_FEATURES = ["area_m2", "floor", "distance_to_center_km", "num_rooms"]
CATEGORICAL_FEATURES = ["ward", "room_type"]
BINARY_FEATURES = [  # tiện ích đã one-hot 0/1 ở Bước 3
    "has_wifi", "has_air_conditioner", "has_elevator",
    "has_parking", "has_private_wc", "has_kitchen", "has_security",
]
FEATURES = NUMERIC_FEATURES + CATEGORICAL_FEATURES + BINARY_FEATURES
TARGET = "price_million"  # giá thuê, đơn vị triệu đồng/tháng

X = df[FEATURES].copy()
y = df[TARGET].copy()

# ColumnTransformer: scale numeric, one-hot cho ward/room_type, giữ nguyên binary
preprocessor = ColumnTransformer(
    transformers=[
        ("num", Pipeline([
            ("imputer", SimpleImputer(strategy="median")),
            ("scaler", StandardScaler()),
        ]), NUMERIC_FEATURES),
        ("cat", Pipeline([
            ("imputer", SimpleImputer(strategy="most_frequent")),
            ("onehot", OneHotEncoder(handle_unknown="ignore", sparse_output=False)),
        ]), CATEGORICAL_FEATURES),
        ("bin", "passthrough", BINARY_FEATURES),
    ],
    remainder="drop",
)
```

> Lưu ý: `handle_unknown="ignore"` rất quan trọng cho serving — nếu người dùng nhập một `ward` chưa từng xuất hiện khi train, encoder sẽ trả vector 0 thay vì crash.

---

## 4.3. Huấn luyện và so sánh 4 model bằng cross-validation

Ta đánh giá bằng **K-Fold cross-validation (K=5)** thay vì một lần train/test split, vì dataset nhỏ → một split đơn dễ cho kết quả may rủi. Với mỗi model, tính MAE, RMSE, R², MAPE trung bình qua 5 fold.

```python
from sklearn.model_selection import KFold, cross_validate
from sklearn.linear_model import Ridge
from sklearn.ensemble import RandomForestRegressor
from lightgbm import LGBMRegressor
from catboost import CatBoostRegressor

# Scorer cho MAPE (sklearn có 'neg_mean_absolute_percentage_error')
SCORING = {
    "MAE":  "neg_mean_absolute_error",
    "RMSE": "neg_root_mean_squared_error",
    "R2":   "r2",
    "MAPE": "neg_mean_absolute_percentage_error",
}

cv = KFold(n_splits=5, shuffle=True, random_state=42)

def make_pipeline(model):
    return Pipeline([("prep", preprocessor), ("model", model)])

models = {
    "Ridge": make_pipeline(Ridge(alpha=1.0, random_state=42)),
    "RandomForest": make_pipeline(RandomForestRegressor(
        n_estimators=400, max_depth=12, min_samples_leaf=3,
        n_jobs=-1, random_state=42)),
    "LightGBM": make_pipeline(LGBMRegressor(
        n_estimators=600, learning_rate=0.05, max_depth=6,
        num_leaves=31, subsample=0.8, colsample_bytree=0.8,
        random_state=42, verbose=-1)),
    "CatBoost": make_pipeline(CatBoostRegressor(
        iterations=600, learning_rate=0.05, depth=6,
        random_state=42, verbose=0)),
}

results = []
for name, pipe in models.items():
    scores = cross_validate(
        pipe, X, y, cv=cv, scoring=SCORING,
        return_train_score=True, n_jobs=-1,
    )
    results.append({
        "Model": name,
        "MAE (val)":  -scores["test_MAE"].mean(),
        "RMSE (val)": -scores["test_RMSE"].mean(),
        "R2 (val)":    scores["test_R2"].mean(),
        "MAPE (val)": -scores["test_MAPE"].mean() * 100,   # đổi ra %
        "MAE (train)": -scores["train_MAE"].mean(),
        "R2_std":      scores["test_R2"].std(),            # độ ổn định qua CV
    })

result_df = pd.DataFrame(results).sort_values("MAE (val)").reset_index(drop=True)
print(result_df.round(3))
```

**Bảng kết quả mẫu (Thanh Xuân, minh họa — số thực tế tùy dataset):**

| Model | MAE (val) | RMSE (val) | R² (val) | MAPE (val) | MAE (train) | R²_std |
|-------|-----------|------------|----------|------------|-------------|--------|
| **LightGBM** | **0.41** | 0.63 | **0.86** | **8.2%** | 0.22 | 0.021 |
| CatBoost | 0.42 | 0.64 | 0.85 | 8.5% | 0.29 | 0.018 |
| RandomForest | 0.46 | 0.71 | 0.82 | 9.4% | 0.19 | 0.027 |
| Ridge (baseline) | 0.58 | 0.84 | 0.71 | 12.1% | 0.56 | 0.019 |

Đọc bảng:
- **LightGBM và CatBoost** dẫn đầu, giảm MAE ~28% so với Ridge → boosting thực sự thắng đáng kể → chấp nhận đánh đổi độ phức tạp.
- **Ridge** có `MAE (train) ≈ MAE (val)` (0.56 vs 0.58) → **không overfit** nhưng thiếu năng lực (underfit, R² chỉ 0.71).
- **RandomForest** train MAE 0.19 << val MAE 0.46 → hơi overfit; cần tăng `min_samples_leaf`.
- **CatBoost** khoảng cách train/val nhỏ nhất trong nhóm boosting (0.29 vs 0.42) → **ổn định, ít overfit** — đúng như kỳ vọng với ordered boosting trên dataset nhỏ.

---

## 4.4. Metrics và ý nghĩa cho bài toán giá thuê

Với giá thuê tính bằng **triệu đồng/tháng**, ý nghĩa từng metric:

| Metric | Công thức | Ý nghĩa & vai trò |
|--------|-----------|-------------------|
| **MAE** | trung bình \|dự đoán − thực tế\| | **Sai số tuyệt đối trung bình, cùng đơn vị (triệu)**. Dễ giao tiếp: "MAE = 0.41 → dự đoán lệch trung bình ~0.41 triệu ≈ 410k/tháng". Không bị outlier khuếch đại. Đây là metric chính để chọn model và hiển thị cho người dùng. |
| **RMSE** | căn của trung bình bình phương sai số | Phạt nặng sai số lớn. RMSE >> MAE báo hiệu có một số dự đoán lệch rất xa (thường là căn hộ giá cao bất thường). Dùng để phát hiện đuôi phân phối. |
| **R²** | 1 − SS_res/SS_tot | Tỷ lệ phương sai giá được model giải thích (0–1). R²=0.86 → model giải thích 86% biến động giá. Metric kỹ thuật, **không dùng nói với người dùng cuối**. |
| **MAPE** | trung bình \|sai số\|/thực tế | **Sai số phần trăm** — dễ hiểu nhất với người dùng: "MAPE = 8% → dự đoán sai khoảng 8%". Trực giác hơn MAE khi giá trải rộng (phòng 2 triệu vs căn hộ 8 triệu). |

> **Chọn cặp MAE + MAPE để giao tiếp với người dùng**: hiển thị *"Giá dự đoán 4.5 triệu/tháng, sai số khoảng ±0.4 triệu (~8%)"*. R² và RMSE giữ lại cho báo cáo kỹ thuật.

Code tính thủ công đủ 4 metric (dùng khi đánh giá cuối trên tập test riêng):

```python
from sklearn.metrics import (
    mean_absolute_error, mean_squared_error, r2_score,
    mean_absolute_percentage_error,
)

def evaluate(y_true, y_pred):
    return {
        "MAE":  mean_absolute_error(y_true, y_pred),
        "RMSE": np.sqrt(mean_squared_error(y_true, y_pred)),
        "R2":   r2_score(y_true, y_pred),
        "MAPE": mean_absolute_percentage_error(y_true, y_pred) * 100,  # %
    }

# Ví dụ dùng:
# m = evaluate(y_test, best_pipe.predict(X_test))
# print(f"Sai số ~{m['MAE']:.2f} triệu / ~{m['MAPE']:.1f}%")
```

---

## 4.5. Tiêu chí chọn model tốt nhất

Không chọn máy móc theo MAE thấp nhất. Thứ tự ưu tiên:

1. **MAE / MAPE thấp trên validation** — độ chính xác là điều kiện cần đầu tiên.
2. **Ổn định qua CV** — `R2_std` và độ lệch chuẩn của MAE giữa các fold phải nhỏ (model không "hên xui" theo split).
3. **Không overfit** — khoảng cách `MAE(train)` vs `MAE(val)` nhỏ. Nếu train MAE ~0.1 mà val MAE ~0.5 thì loại dù val có vẻ ổn, vì sẽ kém trên dữ liệu mới.
4. **Đủ đơn giản để serve** — khi hai model chênh nhau không đáng kể (ví dụ MAE 0.41 vs 0.42), ưu tiên model **nhẹ hơn, load nhanh hơn, dễ giải thích hơn**.

So sánh mẫu theo tiêu chí trên với bảng ở 4.3:
- LightGBM (MAE 0.41) và CatBoost (MAE 0.42) gần như ngang nhau về độ chính xác.
- CatBoost **ổn định hơn** (`R2_std` 0.018 < 0.021) và **ít overfit hơn** (train/val 0.29/0.42 so với 0.22/0.41).
- → Với dataset nhỏ như từng quận, **chọn CatBoost** để ưu tiên độ ổn định và khả năng chống overfit, chấp nhận file model lớn hơn LightGBM một chút. Nếu ưu tiên tối đa tốc độ load ở backend và dung lượng, chọn LightGBM. Cả hai đều là lựa chọn hợp lý; đây là quyết định kỹ thuật có cơ sở, không tùy tiện.

```python
def pick_best(result_df, tol=0.03):
    """Chọn model: MAE thấp nhất; nếu chênh trong khoảng tol (triệu)
    thì ưu tiên model ổn định hơn (R2_std nhỏ)."""
    best_mae = result_df["MAE (val)"].min()
    candidates = result_df[result_df["MAE (val)"] <= best_mae + tol]
    return candidates.sort_values("R2_std").iloc[0]["Model"]

best_name = pick_best(result_df)   # -> "CatBoost"
```

---

## 4.6. Tinh chỉnh siêu tham số (tuning)

Sau khi chốt nhóm boosting, tinh chỉnh nhanh 3 tham số quan trọng nhất: `n_estimators` (số cây), `max_depth`/`depth` (độ sâu), `learning_rate` (tốc độ học). Hai cách:

**Cách A — GridSearchCV (đơn giản, khi không gian nhỏ):**

```python
from sklearn.model_selection import GridSearchCV

param_grid = {
    "model__n_estimators": [400, 600, 800],
    "model__max_depth":    [4, 6, 8],
    "model__learning_rate":[0.03, 0.05, 0.1],
}
grid = GridSearchCV(
    make_pipeline(LGBMRegressor(random_state=42, verbose=-1)),
    param_grid, scoring="neg_mean_absolute_error",
    cv=cv, n_jobs=-1,
)
grid.fit(X, y)
print("Best params:", grid.best_params_)
print("Best CV MAE:", -grid.best_score_)
best_pipe = grid.best_estimator_
```

**Cách B — Optuna (hiệu quả hơn khi không gian lớn, tìm thông minh):**

```python
import optuna
from sklearn.model_selection import cross_val_score

def objective(trial):
    params = {
        "n_estimators":  trial.suggest_int("n_estimators", 300, 1000, step=100),
        "max_depth":     trial.suggest_int("max_depth", 3, 10),
        "learning_rate": trial.suggest_float("learning_rate", 0.01, 0.15, log=True),
        "num_leaves":    trial.suggest_int("num_leaves", 15, 63),
        "subsample":     trial.suggest_float("subsample", 0.6, 1.0),
    }
    pipe = make_pipeline(LGBMRegressor(random_state=42, verbose=-1, **params))
    score = cross_val_score(pipe, X, y, cv=cv,
                            scoring="neg_mean_absolute_error", n_jobs=-1)
    return -score.mean()   # Optuna minimize MAE

study = optuna.create_study(direction="minimize")
study.optimize(objective, n_trials=50, show_progress_bar=True)
print("Best params:", study.best_params, "| MAE:", study.best_value)

# Train lại pipeline cuối cùng trên toàn bộ dữ liệu với best params
best_pipe = make_pipeline(LGBMRegressor(random_state=42, verbose=-1,
                                        **study.best_params)).fit(X, y)
```

> Với dataset nhỏ, Grid 27 tổ hợp là đủ nhanh. Optuna đáng dùng khi thêm nhiều tham số (subsample, reg_alpha, min_child_samples...) vì nó bỏ qua các vùng kém.

---

## 4.7. Lưu model theo từng khu vực + metadata

Lưu **cả Pipeline** (gồm `ColumnTransformer` + model) để backend chỉ cần nạp 1 file. Kèm **metadata** để truy vết và kiểm soát phiên bản. Tổ chức thư mục **theo từng quận**:

```
models/
├── thanh_xuan/
│   ├── model.joblib          # Pipeline sklearn (prep + model)
│   ├── metadata.json         # version, feature list, metrics, ngày train
│   └── model.cbm             # (tùy chọn) CatBoost native, load nhanh hơn
├── cau_giay/
│   └── ...
└── hanoi_all/                # model fallback toàn Hà Nội
    ├── model.joblib
    └── metadata.json
```

```python
import joblib, json
from datetime import date
from pathlib import Path

def save_model(pipe, district_slug, metrics, feature_list, version="1.0.0"):
    out_dir = Path("models") / district_slug
    out_dir.mkdir(parents=True, exist_ok=True)

    # 1) Lưu toàn bộ Pipeline (sklearn) bằng joblib — chuẩn cho sklearn
    joblib.dump(pipe, out_dir / "model.joblib")

    # 2) (Tùy chọn) Lưu model native để load nhanh / dùng ngoài Python
    inner = pipe.named_steps["model"]
    if inner.__class__.__name__ == "CatBoostRegressor":
        inner.save_model(str(out_dir / "model.cbm"))          # CatBoost native
    elif inner.__class__.__name__ == "LGBMRegressor":
        inner.booster_.save_model(str(out_dir / "model.txt")) # LightGBM native

    # 3) Metadata — bắt buộc để truy vết
    metadata = {
        "version": version,
        "district": district_slug,
        "model_type": inner.__class__.__name__,
        "features": feature_list,
        "metrics": {k: round(float(v), 4) for k, v in metrics.items()},
        "trained_at": str(date.today()),
        "n_samples": int(len(X)),
    }
    with open(out_dir / "metadata.json", "w", encoding="utf-8") as f:
        json.dump(metadata, f, ensure_ascii=False, indent=2)

# Ví dụ:
# save_model(best_pipe, "thanh_xuan",
#            metrics=evaluate(y_test, best_pipe.predict(X_test)),
#            feature_list=FEATURES)
```

> Dùng `joblib` thay `pickle` cho sklearn vì joblib nén và serialize mảng numpy hiệu quả hơn. **Ghim phiên bản thư viện** (`scikit-learn`, `lightgbm`, `catboost`) trong `requirements.txt` vì file joblib **không tương thích ngược** giữa các phiên bản sklearn khác nhau — đây là lỗi deploy phổ biến nhất.

---

## 4.8. Load model để dự đoán trong website + hàm `predict()`

Hàm nhận **dict input thô** (như form người dùng gửi lên), trả về **giá triệu đồng** kèm **khoảng tin cậy**. Khoảng tin cậy đơn giản dựng từ **MAE lưu trong metadata** (`price ± MAE`); với model cây có thể ước lượng chặt hơn bằng phân tán dự đoán giữa các cây.

```python
import joblib, json
from functools import lru_cache
from pathlib import Path
import pandas as pd

@lru_cache(maxsize=32)  # cache model đã load, tránh đọc đĩa mỗi request
def load_model(district_slug):
    base = Path("models") / district_slug
    if not (base / "model.joblib").exists():
        base = Path("models") / "hanoi_all"       # fallback
    pipe = joblib.load(base / "model.joblib")
    with open(base / "metadata.json", encoding="utf-8") as f:
        meta = json.load(f)
    return pipe, meta

def predict(input_dict, district_slug="thanh_xuan"):
    pipe, meta = load_model(district_slug)

    # Chỉ giữ đúng các cột model cần; cột thiếu -> NaN (imputer xử lý)
    row = {feat: input_dict.get(feat, None) for feat in meta["features"]}
    X_one = pd.DataFrame([row])

    price = float(pipe.predict(X_one)[0])
    mae = meta["metrics"].get("MAE", 0.4)          # sai số dùng làm bán kính

    return {
        "predicted_price_million": round(price, 2),
        "price_range": [round(max(price - mae, 0), 2), round(price + mae, 2)],
        "mape_pct": meta["metrics"].get("MAPE"),
        "model_version": meta["version"],
    }

# Ví dụ gọi:
# predict({"ward": "Nhân Chính", "area_m2": 30, "room_type": "can_ho_mini",
#          "has_elevator": 1, "has_air_conditioner": 1, "distance_to_center_km": 6.5},
#         district_slug="thanh_xuan")
# -> {'predicted_price_million': 4.5, 'price_range': [4.1, 4.9], 'mape_pct': 8.5, ...}
```

---

## 4.9. Thiết kế API dự đoán bằng FastAPI

Endpoint `POST /predict` nhận JSON `{district, ward, area_m2, room_type, amenities[...]}`, chọn model theo `district`, fallback về Hà Nội tổng nếu quận chưa có model riêng, trả `{predicted_price_million, price_range, currency}`.

```python
# app.py
from fastapi import FastAPI
from pydantic import BaseModel, Field
from typing import List, Optional
import unicodedata, re

app = FastAPI(title="API Dự đoán giá thuê phòng trọ Hà Nội")

# ---- Pydantic: schema request/response ----
class PredictRequest(BaseModel):
    district: str = Field(..., example="Thanh Xuân")
    ward: str = Field(..., example="Nhân Chính")
    area_m2: float = Field(..., gt=0, le=200, example=30)
    room_type: str = Field(..., example="can_ho_mini")   # phong_tro | nha_tro | can_ho_mini
    amenities: List[str] = Field(default_factory=list,
                                 example=["wifi", "elevator", "air_conditioner"])
    floor: Optional[int] = Field(default=None, example=3)
    distance_to_center_km: Optional[float] = Field(default=None, example=6.5)

class PredictResponse(BaseModel):
    predicted_price_million: float
    price_range: List[float]
    currency: str = "VND_million_per_month"
    model_version: str
    used_district_model: str    # cho biết đã dùng model quận nào (hay fallback)

# ---- Tiện ích: chuẩn hóa tên quận -> slug thư mục ----
def to_slug(name: str) -> str:
    s = unicodedata.normalize("NFD", name).encode("ascii", "ignore").decode()
    s = re.sub(r"[^a-zA-Z0-9]+", "_", s).strip("_").lower()
    return s   # "Thanh Xuân" -> "thanh_xuan"

AMENITY_MAP = {   # tiện ích -> tên cột binary của model
    "wifi": "has_wifi", "air_conditioner": "has_air_conditioner",
    "elevator": "has_elevator", "parking": "has_parking",
    "private_wc": "has_private_wc", "kitchen": "has_kitchen",
    "security": "has_security",
}

def resolve_district(slug: str) -> str:
    """Trả slug quận nếu có model riêng, ngược lại fallback 'hanoi_all'."""
    return slug if (Path("models") / slug / "model.joblib").exists() else "hanoi_all"

@app.post("/predict", response_model=PredictResponse)
def predict_endpoint(req: PredictRequest):
    # 1) Chọn model theo district (fallback nếu chưa có model riêng)
    slug = resolve_district(to_slug(req.district))

    # 2) Dựng input_dict khớp feature của model
    input_dict = {
        "ward": req.ward,
        "room_type": req.room_type,
        "area_m2": req.area_m2,
        "floor": req.floor,
        "distance_to_center_km": req.distance_to_center_km,
    }
    for a in req.amenities:                       # bật cờ tiện ích
        col = AMENITY_MAP.get(a)
        if col:
            input_dict[col] = 1

    # 3) Dự đoán
    out = predict(input_dict, district_slug=slug)
    return PredictResponse(
        predicted_price_million=out["predicted_price_million"],
        price_range=out["price_range"],
        model_version=out["model_version"],
        used_district_model=slug,
    )

@app.get("/health")
def health():
    return {"status": "ok"}
```

Chạy: `uvicorn app:app --host 0.0.0.0 --port 8000`. Tài liệu Swagger tự sinh tại `/docs`.

Ví dụ request/response:

```jsonc
// POST /predict
{
  "district": "Thanh Xuân", "ward": "Nhân Chính",
  "area_m2": 30, "room_type": "can_ho_mini",
  "amenities": ["wifi", "elevator", "air_conditioner"]
}
// -> 200 OK
{
  "predicted_price_million": 4.5,
  "price_range": [4.1, 4.9],
  "currency": "VND_million_per_month",
  "model_version": "1.0.0",
  "used_district_model": "thanh_xuan"
}
```

**Chiến lược model theo quận + fallback:**
- Quận có đủ dữ liệu (Thanh Xuân, Cầu Giấy, Hà Đông, Đống Đa...) → train model riêng, đặt tại `models/<quan>/`. Model riêng bắt được đặc thù giá từng khu (giá/m² Cầu Giấy khác Hà Đông).
- Quận ít dữ liệu (Sóc Sơn, Mê Linh...) → `resolve_district()` không thấy file → tự động dùng `models/hanoi_all/` (train trên toàn bộ Hà Nội, có thêm cột `district` làm đặc trưng).
- Nhờ `@lru_cache`, mỗi model chỉ load từ đĩa 1 lần rồi giữ trong RAM, các request sau phản hồi tức thì — quan trọng khi Ridge/LightGBM/CatBoost nhẹ, nạp toàn bộ model các quận vào bộ nhớ vẫn khả thi.

---

**Tóm tắt Bước 4:** ta đã (1) chọn 4 nhóm model có lý do rõ ràng theo đặc điểm dataset nhỏ + tabular + nhiều categorical, (2) so sánh bằng Pipeline + 5-fold CV với bảng MAE/RMSE/R²/MAPE, (3) chọn model theo tiêu chí chính xác–ổn định–không overfit–dễ serve (CatBoost hoặc LightGBM), (4) tune nhanh bằng Grid/Optuna, (5) lưu Pipeline + metadata theo từng quận, (6) viết hàm `predict()` trả giá kèm khoảng tin cậy, và (7) đóng gói thành API FastAPI có chọn model theo quận và fallback Hà Nội tổng.


---


# BƯỚC 5: Kiến trúc pipeline tổng thể

Mục tiêu của bước này là ghép 4 bước trước (crawl → làm sạch → feature → model) thành một **pipeline end-to-end có thể chạy lại (reproducible)**, tách rõ ranh giới giữa các giai đoạn bằng **hợp đồng input/output (data contract)**. Nguyên tắc thiết kế:

- Mỗi giai đoạn là một **script/module độc lập**, đọc file từ giai đoạn trước và ghi file cho giai đoạn sau → dễ chạy lại một phần khi lỗi, dễ debug.
- **Không** để model training đọc thẳng dữ liệu crawl thô. Dữ liệu phải đi qua storage → clean → feature.
- Đặc thù bài toán **thuê phòng trọ/nhà trọ/căn hộ mini tại Hà Nội**: giá phụ thuộc mạnh vào **quận/huyện** và **loại hình phòng**, nên pipeline train được **chia (partition) theo khu vực** và loại hình, thay vì train một model chung cho cả Hà Nội.

## 5.1. Bảng tóm tắt các giai đoạn (data contract)

| # | Giai đoạn | Input | Output | Công cụ chính |
|---|-----------|-------|--------|---------------|
| 1 | Data crawling | Danh sách URL/khu vực (seed) | `raw/*.jsonl` (1 tin/dòng) | `httpx`, `playwright`, `scrapy`, `BeautifulSoup` |
| 2 | Raw storage | `raw/*.jsonl` | MongoDB collection có index (hoặc JSONL versioned) | `pymongo` |
| 3 | Data cleaning | Raw JSONL/Mongo | `cleaned.parquet` + bảng PostgreSQL `listings` | `pandas`, `pyarrow`, `SQLAlchemy` |
| 4 | Feature engineering | `cleaned.parquet` | `features.parquet` (X, y) + `transformers.joblib` | `scikit-learn`, `category_encoders` |
| 5 | Model training (theo khu vực) | features theo `district` | `models/<district>.pkl` + `metrics.json` | `lightgbm`, `scikit-learn`, `joblib` |
| 6 | Model evaluation | model + test set | `eval_report.json`, chọn `best_model` | `scikit-learn.metrics` |
| 7 | Serving API | request JSON (thông tin phòng) | `predicted_price` (VND/tháng) | `FastAPI`, `uvicorn`, `pydantic` |
| 8 | Tích hợp website | tin đăng của user | gợi ý giá + cảnh báo giá bất thường | REST call từ backend Node/PHP/Django |

---

## 5.2. Sơ đồ kiến trúc luồng dữ liệu (text/ASCII)

```
                          ┌─────────────────────────────────────────────┐
                          │              NGUỒN DỮ LIỆU                    │
                          │  batdongsan.com.vn / phongtro123.com /       │
                          │  nhatot.com / chotot ... (trang cho THUÊ)    │
                          └───────────────────────┬─────────────────────┘
                                                  │ URL seed theo quận
                                                  ▼
   (1) CRAWL                        ┌──────────────────────────┐
   in : seeds.txt (URL/khu vực)     │  crawl_listings.py       │
   out: raw/2026-07-05.jsonl        │  httpx + playwright + bs4│
                                    └────────────┬─────────────┘
                                                 │ raw JSONL (1 tin/dòng)
                                                 ▼
   (2) RAW STORAGE                   ┌──────────────────────────┐
   in : raw/*.jsonl                  │  MongoDB  db.raw_listings│
   out: collection + index          │  index: url(unique),     │
        (chống trùng theo url)       │         district, crawled│
                                    └────────────┬─────────────┘
                                                 │ query batch
                                                 ▼
   (3) CLEAN                         ┌──────────────────────────┐
   in : raw docs                     │  clean_pipeline.py       │
   out: cleaned.parquet              │  pandas + pyarrow        │──► PostgreSQL
        + PostgreSQL listings        │  chuẩn hoá giá, diện tích│    (nguồn cho
                                    └────────────┬─────────────┘     website + BI)
                                                 │ cleaned.parquet
                                                 ▼
   (4) FEATURE                       ┌──────────────────────────┐
   in : cleaned.parquet              │  build_features.py       │
   out: features.parquet             │  sklearn ColumnTransformer│
        + transformers.joblib        │  (fit trên train)        │
                                    └────────────┬─────────────┘
                                                 │ X, y + transformers
                                    ┌────────────┴─────────────┐
                                    ▼                          ▼
   (5) TRAIN (per district)   ┌──────────────┐         ┌──────────────┐
   in : features theo quận    │ train_model  │  ...    │ train_model  │
   out: models/<quan>.pkl     │ Cau_Giay.pkl │         │ Ha_Dong.pkl  │
        + metrics.json        └──────┬───────┘         └──────┬───────┘
                                     └────────┬───────────────┘
                                              ▼
   (6) EVALUATE                     ┌──────────────────────────┐
   in : models + test set           │  evaluate.py             │
   out: eval_report.json            │  MAE / RMSE / MAPE / R²   │
        chọn best model             │  chọn best → registry/   │
                                    └────────────┬─────────────┘
                                                 │ best model artifacts
                                                 ▼
   (7) SERVE                         ┌──────────────────────────┐
   in : request JSON (phòng)         │  FastAPI  POST /predict  │
   out: predicted_price (VND)        │  uvicorn + pydantic      │
                                    └────────────┬─────────────┘
                                                 │ HTTP JSON
                                                 ▼
   (8) WEBSITE                       ┌──────────────────────────┐
   Khi user ĐĂNG TIN cho thuê:       │ Backend (Node/PHP/Django)│
     - gọi /predict → "giá đề xuất"  │  gọi API, so sánh giá,   │
     - so với giá user nhập          │  hiển thị badge gợi ý +   │
     - cảnh báo nếu lệch bất thường  │  cảnh báo tin giá ảo      │
                                    └──────────────────────────┘

   ┌────────────────── VÒNG LẶP RETRAIN (cron/Airflow) ──────────────────┐
   │  Định kỳ (vd hàng tuần): (1)→(2)→(3)→(4)→(5)→(6). Nếu best model    │
   │  mới tốt hơn model đang serve → thay artifact → reload API.         │
   │  Monitoring data drift (Evidently) theo dõi phân phối input/giá.    │
   └────────────────────────────────────────────────────────────────────┘
```

---

## 5.3. Chi tiết từng giai đoạn

### (1) Data crawling pipeline
- **Input:** file seed `seeds.txt` chứa danh sách URL trang danh mục theo khu vực (ví dụ mỗi quận một URL phân trang), hoặc danh sách quận/huyện để sinh URL.
- **Output:** `raw/<ngày>.jsonl`, mỗi dòng là một tin thô (JSON) giữ **nguyên văn** các trường: `title`, `price_text`, `area_text`, `address`, `description`, `url`, `posted_date`, `crawled_at`. Không chuẩn hoá ở bước này.

```python
# crawl_listings.py  (in: seeds.txt -> out: raw/<date>.jsonl)
import json, datetime, httpx
from bs4 import BeautifulSoup

OUT = f"raw/{datetime.date.today()}.jsonl"

def parse_listing(html: str, url: str) -> dict:
    s = BeautifulSoup(html, "lxml")
    return {
        "url": url,
        "title":      s.select_one("h1.title").get_text(strip=True),
        "price_text": s.select_one(".price").get_text(strip=True),   # "3,5 triệu/tháng"
        "area_text":  s.select_one(".area").get_text(strip=True),    # "25 m²"
        "address":    s.select_one(".address").get_text(strip=True), # "Cầu Giấy, Hà Nội"
        "description":s.select_one(".description").get_text(" ", strip=True),
        "crawled_at": datetime.datetime.utcnow().isoformat(),
    }

with httpx.Client(timeout=20, headers={"User-Agent": "Mozilla/5.0"}) as cli, \
     open(OUT, "a", encoding="utf-8") as f:
    for url in open("seeds.txt", encoding="utf-8"):
        url = url.strip()
        try:
            r = cli.get(url); r.raise_for_status()
            f.write(json.dumps(parse_listing(r.text, url), ensure_ascii=False) + "\n")
        except Exception as e:
            print("SKIP", url, e)
```

Với trang render bằng JavaScript (nhiều sàn cho thuê load động), dùng `playwright` thay cho `httpx` để lấy HTML sau render. Với quy mô lớn hơn, đóng gói thành spider `scrapy` để có sẵn retry/throttle/pipeline.

### (2) Raw data storage
- **Input:** các file `raw/*.jsonl`.
- **Output:** collection MongoDB `raw_listings` có **index** phục vụ chống trùng và truy vấn theo khu vực/ngày. Nếu triển khai nhỏ, có thể giữ nguyên JSONL nhưng đặt tên theo phiên bản (`raw/2026-07-05.jsonl`) — đó cũng là một dạng "versioned storage".

```python
# load_raw.py  (in: raw/*.jsonl -> out: MongoDB có index)
import json, glob
from pymongo import MongoClient, ASCENDING

col = MongoClient("mongodb://localhost:27017")["rent_hn"]["raw_listings"]
col.create_index([("url", ASCENDING)], unique=True)   # chống trùng tin
col.create_index([("crawled_at", ASCENDING)])

for path in glob.glob("raw/*.jsonl"):
    for line in open(path, encoding="utf-8"):
        doc = json.loads(line)
        col.update_one({"url": doc["url"]}, {"$set": doc}, upsert=True)  # idempotent
```

Index `url` (unique) đảm bảo crawl lại nhiều lần **không nhân đôi** dữ liệu (`upsert`). Đây là nơi lưu "single source of truth" của dữ liệu thô.

### (3) Data cleaning pipeline
- **Input:** dữ liệu thô từ Mongo (hoặc raw JSONL).
- **Output:** `cleaned.parquet` (dùng cho ML, đọc nhanh, giữ kiểu dữ liệu) **và** bảng PostgreSQL `listings` (dùng cho website tra cứu, BI, join). Ở đây mới thực hiện: parse giá về **VND/tháng** (số), parse diện tích về **m²**, tách `district` từ địa chỉ, loại outlier giá/diện tích, chuẩn hoá `room_type`.

```python
# clean_pipeline.py  (in: raw -> out: cleaned.parquet + PostgreSQL)
import re, pandas as pd
from pymongo import MongoClient
from sqlalchemy import create_engine

df = pd.DataFrame(list(MongoClient()["rent_hn"]["raw_listings"].find()))

def price_to_vnd(t: str):                       # "3,5 triệu/tháng" -> 3_500_000
    t = t.lower().replace(",", ".")
    m = re.search(r"([\d.]+)\s*(triệu|tr|nghìn|k)?", t)
    if not m: return None
    v = float(m.group(1)); unit = m.group(2) or "triệu"
    return v * (1_000_000 if unit in ("triệu","tr") else 1_000)

df["price"]    = df["price_text"].map(price_to_vnd)
df["area"]     = df["area_text"].str.extract(r"([\d.]+)").astype(float)
df["district"] = df["address"].str.extract(r"(Cầu Giấy|Đống Đa|Hai Bà Trưng|Hà Đông|"
                                           r"Thanh Xuân|Nam Từ Liêm|Bắc Từ Liêm|Hoàng Mai|Long Biên)")
# lọc theo domain phòng trọ HN: bỏ tin giá/diện tích vô lý
df = df[(df.price.between(800_000, 30_000_000)) & (df.area.between(8, 120))]
df = df.dropna(subset=["price", "area", "district"])

df.to_parquet("cleaned.parquet", index=False)                       # cho ML
df.to_sql("listings", create_engine("postgresql+psycopg2://user:pw@localhost/rent_hn"),
          if_exists="replace", index=False)                         # cho website
```

Khoảng lọc `price 0.8–30 triệu` và `area 8–120 m²` là ràng buộc **đặc thù phòng trọ/căn hộ mini** — loại bỏ nhầm tin bán nhà, tin sai đơn vị.

### (4) Feature engineering pipeline
- **Input:** `cleaned.parquet`.
- **Output:** `features.parquet` (ma trận đặc trưng X + target y) **và** `transformers.joblib` (bộ biến đổi đã fit: encoder/scaler). Bắt buộc **fit trên tập train** rồi lưu lại để serving dùng lại y hệt — tránh train/serving skew.

```python
# build_features.py  (in: cleaned.parquet -> out: features.parquet + transformers.joblib)
import pandas as pd, joblib
from sklearn.compose import ColumnTransformer
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.model_selection import train_test_split

df = pd.read_parquet("cleaned.parquet")
# đặc trưng suy diễn đặc thù phòng trọ
df["price_per_m2"] = df["price"] / df["area"]
for kw in ["khép kín", "ban công", "thang máy", "gác xép", "điều hòa", "để xe"]:
    df[f"has_{kw.replace(' ','_')}"] = df["description"].str.contains(kw, case=False).astype(int)

num = ["area", "has_khép_kín", "has_ban_công", "has_thang_máy",
       "has_gác_xép", "has_điều_hòa", "has_để_xe"]
cat = ["district", "room_type"]
y   = df["price"]

pre = ColumnTransformer([
    ("num", StandardScaler(), num),
    ("cat", OneHotEncoder(handle_unknown="ignore"), cat),
])
X_tr, X_te, y_tr, y_te = train_test_split(df[num+cat], y, test_size=0.2, random_state=42)
X_tr_mat = pre.fit_transform(X_tr)              # FIT chỉ trên train
joblib.dump({"pre": pre, "num": num, "cat": cat}, "transformers.joblib")
pd.DataFrame(X_tr_mat.toarray()).assign(price=y_tr.values).to_parquet("features.parquet")
```

### (5) Model training pipeline (theo khu vực)
- **Input:** features **lọc theo `district`** (mỗi quận một tập train).
- **Output:** `models/<district>.pkl` cho từng khu vực + `metrics.json`. Chia theo khu vực vì mặt bằng giá thuê Cầu Giấy/Đống Đa khác hẳn Hà Đông/ngoại thành; model riêng theo quận thường cho MAE thấp hơn model gộp.

```python
# train_model.py  (in: features theo district -> out: models/<district>.pkl + metrics.json)
import json, joblib, pandas as pd
from pathlib import Path
from lightgbm import LGBMRegressor
from sklearn.metrics import mean_absolute_error

df = pd.read_parquet("cleaned.parquet")
Path("models").mkdir(exist_ok=True)
metrics = {}

for district, g in df.groupby("district"):
    if len(g) < 200:                     # quận quá ít dữ liệu -> gộp vào model chung
        continue
    X, y = build_X(g), g["price"]        # tái dùng transformer đã fit
    model = LGBMRegressor(n_estimators=600, learning_rate=0.03,
                          num_leaves=31, subsample=0.8)
    model.fit(X, y)
    joblib.dump(model, f"models/{district}.pkl")
    metrics[district] = {"n": len(g), "train_mae": mean_absolute_error(y, model.predict(X))}

json.dump(metrics, open("metrics.json", "w"), ensure_ascii=False, indent=2)
```

`LightGBM` phù hợp vì dữ liệu dạng bảng, nhiều biến hạng mục (quận, loại phòng), xử lý tương tác phi tuyến (diện tích × quận) tốt hơn hồi quy tuyến tính.

### (6) Model evaluation
- **Input:** các model + **test set** (tách sẵn ở bước 4, không dùng để train).
- **Output:** `eval_report.json` với các chỉ số **MAE, RMSE, MAPE, R²** theo từng quận; chọn **best model** (theo MAE/MAPE trên test) và đưa vào registry (thư mục `models/production/` hoặc MLflow).

```python
# evaluate.py  (in: model + test set -> out: eval_report.json, chọn best)
import json, joblib, numpy as np
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

def eval_one(model, X_te, y_te):
    p = model.predict(X_te)
    return {
        "MAE":  round(mean_absolute_error(y_te, p)),
        "RMSE": round(mean_squared_error(y_te, p, squared=False)),
        "MAPE": round(float(np.mean(np.abs((y_te - p) / y_te)) * 100), 2),  # % sai lệch
        "R2":   round(r2_score(y_te, p), 3),
    }

report = {d: eval_one(joblib.load(f"models/{d}.pkl"), *test_set[d]) for d in districts}
json.dump(report, open("eval_report.json", "w"), indent=2, ensure_ascii=False)
# tiêu chí chọn: MAPE thấp nhất và > model production hiện tại thì mới promote
```

**MAPE** (sai số phần trăm) là chỉ số dễ giải thích cho nghiệp vụ: "model dự đoán lệch trung bình ~12% so với giá thực".

### (7) Model serving API (FastAPI)
- **Input:** request JSON mô tả phòng (diện tích, quận, loại phòng, tiện ích).
- **Output:** `predicted_price` (VND/tháng), kèm khoảng tin cậy/gợi ý.

```python
# serve.py  (in: request JSON -> out: predicted_price)  chạy: uvicorn serve:app
import joblib
from fastapi import FastAPI
from pydantic import BaseModel, Field

app = FastAPI(title="Rent Price HN")
bundle = joblib.load("transformers.joblib")
models = {}                                   # nạp models/<district>.pkl 1 lần khi khởi động

class RentInput(BaseModel):
    district: str
    area: float = Field(gt=0, lt=200)
    room_type: str = "phòng trọ"
    has_khép_kín: int = 0
    has_thang_máy: int = 0

@app.post("/predict")
def predict(x: RentInput):
    model = models.get(x.district, models["_default"])
    X = transform_one(x, bundle)              # dùng lại transformer đã fit
    price = float(model.predict(X)[0])
    return {"predicted_price": round(price, -4),               # làm tròn 10k
            "range": [round(price*0.9, -4), round(price*1.1, -4)],
            "currency": "VND/tháng", "district": x.district}
```

Chạy production: `uvicorn serve:app --host 0.0.0.0 --port 8000 --workers 2`. Models và transformers được nạp **một lần lúc khởi động**, không load lại mỗi request.

### (8) Tích hợp vào website quảng cáo phòng trọ
Khi người dùng **đăng tin cho thuê**, backend (Node.js/PHP/Django) gọi `POST /predict`:

1. **Gợi ý giá:** hiển thị "Giá đề xuất cho phòng của bạn: ~3.4 triệu/tháng (khoảng 3.0–3.7 triệu)". Giúp chủ trọ định giá hợp lý.
2. **Hiển thị cho người thuê:** trên trang chi tiết, thêm badge "Giá này thấp/cao hơn ~X% so với mặt bằng khu vực".
3. **Cảnh báo tin giá bất thường:** nếu giá user nhập lệch quá ngưỡng so với dự đoán (ví dụ `|giá_nhập − giá_dự_đoán| / giá_dự_đoán > 40%`) → gắn cờ **nghi ngờ tin ảo/giá sai** để kiểm duyệt.

```javascript
// Node.js (Express) — gọi khi user submit tin đăng
const r = await fetch("http://ml-api:8000/predict", {
  method: "POST", headers: {"Content-Type": "application/json"},
  body: JSON.stringify({ district: post.district, area: post.area,
                         room_type: post.roomType, has_khép_kín: post.closed ? 1 : 0 })
});
const { predicted_price, range } = await r.json();
const deviation = Math.abs(post.price - predicted_price) / predicted_price;
post.suggestedPrice = predicted_price;
post.priceWarning   = deviation > 0.4;   // cờ cảnh báo tin giá bất thường
```

API ML nên nằm **nội bộ** (mạng riêng/`ml-api:8000`), không expose thẳng ra internet; backend website là lớp trung gian gọi vào.

---

## 5.4. Lịch chạy lại (retrain định kỳ)

Dữ liệu phòng trọ thay đổi theo mùa (mùa nhập học tháng 8–9 giá tăng), nên cần **retrain định kỳ**.

**Phương án đầy đủ — Airflow** (khi có nhiều nguồn, cần theo dõi phụ thuộc, retry):

```python
# dags/rent_pipeline.py  (Airflow) — chạy lại toàn bộ hàng tuần
from airflow import DAG
from airflow.operators.bash import BashOperator
import datetime

with DAG("rent_hn_retrain",
         schedule_interval="0 3 * * 1",          # 03:00 mỗi Thứ Hai
         start_date=datetime.datetime(2026, 7, 1),
         catchup=False) as dag:

    crawl   = BashOperator(task_id="crawl",   bash_command="python crawl_listings.py")
    load    = BashOperator(task_id="load",    bash_command="python load_raw.py")
    clean   = BashOperator(task_id="clean",   bash_command="python clean_pipeline.py")
    feature = BashOperator(task_id="feature", bash_command="python build_features.py")
    train   = BashOperator(task_id="train",   bash_command="python train_model.py")
    evaluate= BashOperator(task_id="evaluate",bash_command="python evaluate.py")
    deploy  = BashOperator(task_id="deploy",  bash_command="python promote_if_better.py")

    crawl >> load >> clean >> feature >> train >> evaluate >> deploy
```

Bước `promote_if_better.py` chỉ thay model production khi model mới có MAPE tốt hơn → tránh "retrain làm tệ đi". Sau khi thay artifact, gọi reload API (ví dụ `POST /reload` hoặc restart worker uvicorn).

## 5.5. Monitoring data drift

Theo dõi **phân phối input và giá** thay đổi theo thời gian (drift) để biết khi nào cần retrain gấp, dùng thư viện `evidently`:

```python
# monitor_drift.py — so phân phối dữ liệu tuần này với dữ liệu lúc train
import pandas as pd
from evidently.report import Report
from evidently.metric_preset import DataDriftPreset

ref = pd.read_parquet("cleaned.parquet")          # dữ liệu tham chiếu (lúc train)
cur = pd.read_parquet("cleaned_this_week.parquet")# dữ liệu mới crawl

report = Report(metrics=[DataDriftPreset()])
report.run(reference_data=ref[["area","price","district"]],
           current_data =cur[["area","price","district"]])
report.save_html("drift_report.html")
res = report.as_dict()
if res["metrics"][0]["result"]["dataset_drift"]:   # phát hiện drift
    print("DRIFT -> kích hoạt retrain sớm / gửi cảnh báo")
```

Ngoài drift dữ liệu đầu vào, nên log **prediction vs giá thực tế** (khi tin được thuê/cập nhật) để theo dõi **model performance decay** theo tuần; nếu MAPE thực tế vượt ngưỡng (ví dụ > 20%) thì cảnh báo.

## 5.6. Đề xuất stack tối giản (triển khai nhỏ — KHÔNG cần Airflow)

Với đồ án/triển khai quy mô nhỏ, **không cần Airflow** — chỉ cần **script Python + cron** (Linux) hoặc **Task Scheduler** (Windows). Storage có thể bỏ MongoDB, dùng thẳng **JSONL versioned + SQLite/PostgreSQL**.

Kiến trúc tối giản:

```
seeds.txt → run_all.py ─┬─ raw/<date>.jsonl   (thay MongoDB bằng file versioned)
                        ├─ cleaned.parquet    +  SQLite/PostgreSQL
                        ├─ models/*.pkl + transformers.joblib + metrics.json
                        └─ FastAPI (uvicorn, systemd) đọc models/  ← website gọi
   cron: 0 3 * * 1  python run_all.py   (retrain hàng tuần)
```

```python
# run_all.py — chạy tuần tự toàn bộ pipeline trong 1 tiến trình (đủ cho quy mô nhỏ)
import subprocess
for step in ["crawl_listings.py", "load_raw.py", "clean_pipeline.py",
             "build_features.py", "train_model.py", "evaluate.py",
             "promote_if_better.py"]:
    print("==>", step)
    subprocess.run(["python", step], check=True)   # dừng ngay nếu 1 bước lỗi
```

Đăng ký lịch với **cron** (Linux):

```cron
# crontab -e  → retrain 03:00 mỗi Thứ Hai, ghi log
0 3 * * 1  cd /opt/rent_hn && /opt/rent_hn/venv/bin/python run_all.py >> logs/retrain.log 2>&1
```

Hoặc **APScheduler** nếu muốn nhúng lịch ngay trong tiến trình Python (không cần cron hệ thống):

```python
from apscheduler.schedulers.blocking import BlockingScheduler
sched = BlockingScheduler()
sched.add_job(lambda: __import__("subprocess").run(["python","run_all.py"]),
              "cron", day_of_week="mon", hour=3)
sched.start()
```

**Tóm tắt lựa chọn stack theo quy mô:**

| Thành phần | Bản tối giản (đồ án) | Bản đầy đủ (production) |
|------------|----------------------|-------------------------|
| Điều phối | `cron` + `run_all.py` / APScheduler | Airflow / Prefect |
| Raw storage | JSONL versioned | MongoDB |
| Cleaned storage | SQLite / Parquet | PostgreSQL + Parquet |
| Model registry | thư mục `models/production/` | MLflow |
| Serving | FastAPI + uvicorn (systemd) | FastAPI + Docker + Nginx |
| Monitoring | script `evidently` chạy tay/tuần | Evidently + dashboard + alert |

Với đồ án tốt nghiệp, **bản tối giản là đủ**: `cron + script + FastAPI + Parquet/SQLite`, vẫn giữ đúng ranh giới input/output giữa các giai đoạn nên có thể nâng cấp lên bản đầy đủ mà không phải viết lại logic.


---


## BƯỚC 6: Cấu trúc thư mục project

Cấu trúc dưới đây tổ chức project theo pipeline dữ liệu: **crawl → lưu thô → làm sạch → sinh đặc trưng → huấn luyện → đánh giá → phục vụ API**. Mỗi tầng là một package Python độc lập, dễ test và tái sử dụng.

### Cây thư mục đầy đủ

```text
rental-price-hanoi/
│
├── crawlers/                     # Thu thập dữ liệu tin đăng cho thuê
│   ├── __init__.py
│   ├── base_crawler.py           # Lớp cơ sở: retry, rate-limit, User-Agent, parse HTML chung
│   ├── phongtro123.py            # Crawler cho phongtro123.com
│   ├── batdongsan.py             # Crawler cho batdongsan.com.vn (lọc mục "Cho thuê")
│   ├── chotot_nha.py             # Crawler cho nha.chotot.com (API JSON)
│   ├── mogi.py                   # Crawler cho mogi.vn
│   └── run_crawl.py              # Điều phối chạy nhiều crawler, ghi ra data/raw
│
├── data/
│   ├── raw/                      # Dữ liệu thô đúng nguyên trạng crawl (JSON/CSV theo ngày)
│   │   └── phongtro123_2026-07-05.jsonl
│   ├── processed/                # Dữ liệu đã làm sạch, chuẩn hoá cột
│   │   └── listings_clean.parquet
│   └── datasets/                 # Dữ liệu chia train/val/test đã sẵn sàng đưa vào model
│       ├── train.parquet
│       ├── val.parquet
│       └── test.parquet
│
├── preprocessing/                # Làm sạch và chuẩn hoá dữ liệu thô
│   ├── __init__.py
│   ├── clean.py                  # Loại HTML, dedup, xử lý null, ép kiểu số
│   ├── normalize_text.py         # Chuẩn hoá tiếng Việt (bỏ dấu, lower, tách token)
│   ├── parse_price.py            # Chuẩn hoá giá "3.5 triệu/tháng" -> 3_500_000 (VND/tháng)
│   ├── parse_area.py             # Chuẩn hoá diện tích "25m2" -> 25.0 (m²)
│   ├── geocode.py                # Map địa chỉ -> (quận, phường) chuẩn theo districts.yaml
│   └── outlier.py                # Lọc ngoại lai giá/diện tích (IQR, ngưỡng theo m²)
│
├── features/                     # Sinh đặc trưng cho mô hình
│   ├── __init__.py
│   ├── build_features.py         # Ghép toàn bộ transformer thành ma trận đặc trưng cuối
│   ├── amenities.py              # Trích tiện ích từ mô tả (điều hoà, gác xép, khép kín...)
│   ├── location_features.py      # Encode quận/phường, khoảng cách tới trung tâm/ĐH
│   └── text_features.py          # TF-IDF/embedding cho tiêu đề + mô tả
│
├── models/                       # Định nghĩa mô hình và artifact đã train
│   ├── __init__.py
│   ├── registry.py               # Factory: tên model -> estimator (LinearReg, RF, XGB, LGBM)
│   ├── baseline.py               # Mô hình nền (trung bình giá/m² theo quận)
│   └── artifacts/                # File .pkl/.joblib đã huấn luyện (không commit)
│       └── lgbm_v1.joblib
│
├── training/                     # Huấn luyện mô hình
│   ├── __init__.py
│   ├── train.py                  # Đọc datasets, fit pipeline, lưu artifact + metrics
│   ├── split.py                  # Chia train/val/test (theo thời gian đăng tin)
│   └── tune.py                   # Tối ưu siêu tham số (Optuna)
│
├── evaluation/                   # Đánh giá và diễn giải mô hình
│   ├── __init__.py
│   ├── evaluate.py               # Tính MAE, RMSE, MAPE, R² trên test
│   ├── metrics.py                # Hàm metric dùng chung
│   └── error_analysis.py         # Phân tích sai số theo quận/khoảng giá, SHAP
│
├── api/                          # Dịch vụ dự đoán trực tuyến
│   ├── __init__.py
│   ├── main.py                   # FastAPI app, endpoint POST /predict
│   ├── schemas.py                # Pydantic request/response (địa chỉ, diện tích, tiện ích...)
│   └── predictor.py              # Nạp artifact + pipeline, hàm predict()
│
├── configs/                      # Cấu hình tách khỏi code
│   ├── districts.yaml            # >>> Danh sách quận/phường Hà Nội <<<
│   ├── amenities.yaml            # >>> Từ điển tiện ích + từ khoá nhận diện <<<
│   ├── crawl.yaml                # URL nguồn, số trang, delay, header
│   └── model.yaml                # Loại model, siêu tham số, đường dẫn artifact
│
├── notebooks/                    # Khảo sát, EDA, thử nghiệm
│   ├── 01_eda.ipynb
│   ├── 02_feature_check.ipynb
│   └── 03_model_compare.ipynb
│
├── tests/                        # Unit test
│   ├── test_parse_price.py
│   ├── test_geocode.py
│   └── test_api.py
│
├── scripts/                      # Lệnh tiện ích chạy 1 lần / CLI
│   ├── build_dataset.sh          # crawl -> clean -> features -> datasets
│   └── serve.sh                  # uvicorn api.main:app
│
├── README.md                     # Mô tả project, cách cài đặt, chạy pipeline
├── requirements.txt              # Thư viện phụ thuộc (pin version)
├── .env                          # Biến bí mật (API key, DB URL) - KHÔNG commit
└── .gitignore                    # Bỏ qua data/, .env, models/artifacts/, __pycache__
```

### Bảng vai trò từng thư mục

| Thư mục | Vai trò | File tiêu biểu |
|---|---|---|
| `crawlers/` | Thu thập tin cho thuê từ các trang, chuẩn hoá về JSONL thô | `base_crawler.py`, `phongtro123.py`, `chotot_nha.py` |
| `data/raw` | Lưu dữ liệu crawl nguyên trạng, có version theo ngày | `phongtro123_2026-07-05.jsonl` |
| `data/processed` | Dữ liệu đã làm sạch, một dòng = một tin, cột đã chuẩn hoá | `listings_clean.parquet` |
| `data/datasets` | Bộ train/val/test sẵn sàng cho model | `train.parquet`, `test.parquet` |
| `preprocessing/` | Làm sạch, parse giá/diện tích, map địa chỉ, lọc outlier | `clean.py`, `parse_price.py`, `geocode.py` |
| `features/` | Biến dữ liệu sạch thành ma trận đặc trưng | `build_features.py`, `amenities.py`, `location_features.py` |
| `models/` | Định nghĩa estimator và lưu artifact đã train | `registry.py`, `artifacts/lgbm_v1.joblib` |
| `training/` | Huấn luyện, chia dữ liệu, tune siêu tham số | `train.py`, `tune.py` |
| `evaluation/` | Tính metric hồi quy, phân tích sai số, diễn giải | `evaluate.py`, `error_analysis.py` |
| `api/` | Phục vụ dự đoán realtime qua REST | `main.py`, `predictor.py`, `schemas.py` |
| `configs/` | Cấu hình khai báo (YAML), tách khỏi code | `districts.yaml`, `amenities.yaml` |
| `notebooks/` | EDA, thử nghiệm nhanh, so sánh model | `01_eda.ipynb` |
| `tests/` | Unit test cho hàm parse, geocode, API | `test_parse_price.py`, `test_api.py` |
| `scripts/` | Lệnh CLI chạy pipeline / khởi động server | `build_dataset.sh`, `serve.sh` |
| `README.md` | Hướng dẫn cài đặt và chạy | — |
| `requirements.txt` | Khai báo phụ thuộc pin version | — |
| `.env` | Biến bí mật (API key, DB), nạp bằng `python-dotenv` | — |
| `.gitignore` | Loại `data/`, artifact, `.env`, cache khỏi git | — |

### File config quan trọng cần biết

- **`configs/districts.yaml`** chứa **danh sách quận/phường Hà Nội** (dùng để chuẩn hoá địa chỉ trong `preprocessing/geocode.py` và encode vị trí trong `features/location_features.py`). Ví dụ nội dung:

```yaml
# configs/districts.yaml
Cầu Giấy:
  aliases: [cau giay, cầu giấy]
  wards: [Dịch Vọng, Dịch Vọng Hậu, Quan Hoa, Nghĩa Đô, Mai Dịch, Yên Hòa]
Đống Đa:
  aliases: [dong da, đống đa]
  wards: [Láng Hạ, Láng Thượng, Ô Chợ Dừa, Khương Thượng, Trung Liệt]
Hai Bà Trưng:
  aliases: [hai ba trung]
  wards: [Bách Khoa, Bạch Mai, Minh Khai, Vĩnh Tuy, Đồng Tâm]
Thanh Xuân:
  aliases: [thanh xuan]
  wards: [Nhân Chính, Khương Trung, Khương Đình, Thanh Xuân Bắc, Hạ Đình]
```

- **`configs/amenities.yaml`** chứa **từ điển tiện ích** (mỗi tiện ích gồm tập từ khoá regex để `features/amenities.py` quét trong mô tả tin, phù hợp đặc thù phòng trọ/nhà trọ/căn hộ mini). Ví dụ:

```yaml
# configs/amenities.yaml
khep_kin:        [khép kín, khep kin, wc riêng, vệ sinh riêng]
dieu_hoa:        [điều hòa, dieu hoa, máy lạnh, có đh]
nong_lanh:       [nóng lạnh, bình nóng lạnh, nong lanh]
gac_xep:         [gác xép, gac xep, gác lửng]
thang_may:       [thang máy, thang may]
để_xe:           [chỗ để xe, hầm để xe, gửi xe]
ban_công:        [ban công, ban cong, logia]
gio_giac_tu_do:  [giờ giấc tự do, tự do giờ giấc, không chung chủ]
```

Cách nạp config trong code (dùng `PyYAML`):

```python
# preprocessing/geocode.py
import yaml
from pathlib import Path

CONFIG_DIR = Path(__file__).resolve().parent.parent / "configs"

with open(CONFIG_DIR / "districts.yaml", encoding="utf-8") as f:
    DISTRICTS = yaml.safe_load(f)   # dict: {quận: {aliases, wards}}

with open(CONFIG_DIR / "amenities.yaml", encoding="utf-8") as f:
    AMENITIES = yaml.safe_load(f)   # dict: {tên_tiện_ích: [từ khoá]}
```

Nhờ tách `districts.yaml` và `amenities.yaml` ra khỏi code, khi Hà Nội thay đổi đơn vị hành chính hoặc cần bổ sung tiện ích mới, chỉ sửa file YAML mà không phải đụng tới logic xử lý.


---


# BƯỚC 7: Kế hoạch triển khai thực tế

Phần này chia toàn bộ quá trình xây dựng hệ thống dự đoán giá thuê phòng trọ/nhà trọ/căn hộ mini tại Hà Nội thành **8 giai đoạn tuần tự**. Mỗi giai đoạn được mô tả theo 6 mục: *Mục tiêu — Việc làm cụ thể — Công cụ — Đầu ra (deliverable) — Tiêu chí hoàn thành — Ước lượng thời gian*. Kế hoạch được thiết kế cho **một sinh viên làm đồ án tốt nghiệp** (làm một mình, không có team hỗ trợ), tổng thời lượng khoảng **7–9 tuần** nếu làm song song việc học.

> **Nguyên tắc xuyên suốt:** làm hẹp trước, rộng sau. Chọn **1 quận (Thanh Xuân)** làm pilot để chạy hết pipeline end-to-end, chứng minh hệ thống chạy được, rồi mới nhân bản sang các quận khác. Tránh crawl 12 quận ngay từ đầu rồi mắc kẹt ở khâu làm sạch.

---

## Bảng tổng quan 8 giai đoạn

| GĐ | Tên giai đoạn | Đầu ra chính | Thời gian |
|----|---------------|--------------|-----------|
| 1 | Khảo sát nguồn dữ liệu | Báo cáo nguồn + bảng chọn nguồn | 2–3 ngày |
| 2 | Crawl thử (proof-of-concept) | Script crawl chạy được, ~50–100 tin thô | 3–4 ngày |
| 3 | Làm sạch & tạo dataset Thanh Xuân | `thanhxuan_clean.csv` (≥ 300–500 bản ghi) | 5–7 ngày |
| 4 | Train 4 model | 4 model đã lưu (`.pkl`/`.joblib`) | 3–5 ngày |
| 5 | Đánh giá & chọn model | Bảng so sánh metrics + model vô địch | 2–3 ngày |
| 6 | Xây API | Service FastAPI có endpoint `/predict` | 3–4 ngày |
| 7 | Tích hợp website | Form nhập → hiển thị giá dự đoán | 4–6 ngày |
| 8 | Mở rộng các quận khác | Dataset đa quận + model tổng | 5–7 ngày |

---

## Giai đoạn 1 — Khảo sát nguồn dữ liệu

**Mục tiêu:** Xác định nguồn tin cho thuê phòng trọ/nhà trọ/căn hộ mini tại Hà Nội có **số lượng tin đủ lớn**, **cấu trúc HTML ổn định** và **không chặn crawler quá gắt**, làm cơ sở cho giai đoạn crawl.

**Việc làm cụ thể:**
- Lập danh sách nguồn tiềm năng chuyên **cho thuê** (không phải mua bán):
  - `phongtro123.com` — chuyên phòng trọ, tin nhiều, cấu trúc gọn (ưu tiên #1).
  - `nhatot.com` (mục Cho thuê phòng trọ) — dữ liệu lớn nhưng có API nội bộ + chống bot.
  - `batdongsan.com.vn` (lọc *Cho thuê → Nhà trọ, phòng trọ*) — trường dữ liệu chuẩn nhưng chặn mạnh.
  - `mogi.vn`, `alonhadat.com.vn`, các group Facebook (chỉ tham khảo, khó crawl có cấu trúc).
- Với từng nguồn, khảo sát thủ công (mở DevTools → tab Network/Elements):
  - Đếm số tin cho thuê ở khu vực Thanh Xuân (dùng bộ lọc quận trên chính website).
  - Kiểm tra tin nằm trong HTML server-render hay load bằng JavaScript/AJAX (quyết định dùng `requests` hay `Selenium/Playwright`).
  - Xác định các **trường cần lấy**: giá thuê (VND/tháng), diện tích (m²), địa chỉ/quận/phường, loại hình (phòng trọ / nhà trọ / căn hộ mini / chung cư mini), số phòng ngủ, nội thất, tiêu đề, mô tả, ngày đăng.
  - Đọc `robots.txt` (ví dụ `https://phongtro123.com/robots.txt`) để biết đường dẫn nào bị cấm.

**Công cụ:** Trình duyệt + DevTools (F12), tiện ích *Selectorgadget* để dò CSS selector, Google Sheets/Excel để ghi bảng khảo sát.

**Đầu ra (deliverable):**
- Bảng khảo sát so sánh các nguồn (số tin ước tính, render tĩnh/động, mức độ chặn, độ đầy đủ trường dữ liệu).
- Quyết định chọn nguồn chính (kỳ vọng: `phongtro123.com`) + 1 nguồn dự phòng.

**Tiêu chí hoàn thành:**
- Chọn được ít nhất **1 nguồn có ≥ 300–500 tin cho thuê khu vực Thanh Xuân** (đủ để đạt ngưỡng dữ liệu tối thiểu — xem mục cuối).
- Đã liệt kê đầy đủ CSS selector/đường dẫn của các trường cần crawl.

**Thời gian:** **2–3 ngày.**

---

## Giai đoạn 2 — Crawl thử (proof-of-concept)

**Mục tiêu:** Viết script crawl chạy được trên nguồn đã chọn, lấy về **50–100 tin thô** để kiểm chứng selector và định dạng dữ liệu, **chưa cần crawl toàn bộ**.

**Việc làm cụ thể:**
- Với trang **server-render** (HTML tĩnh): dùng `requests` + `BeautifulSoup`.
- Với trang **load bằng JS**: dùng `Selenium` hoặc `Playwright`.
- Crawl 2 tầng: (1) trang danh sách → lấy link chi tiết + phân trang; (2) trang chi tiết → lấy đầy đủ trường.
- Đặt `User-Agent` thật, thêm `time.sleep()` ngẫu nhiên 1–3s giữa các request để lịch sự và tránh bị chặn.

**Ví dụ script crawl tĩnh (`requests` + `BeautifulSoup`):**

```python
import requests
from bs4 import BeautifulSoup
import time, random, csv

HEADERS = {
    "User-Agent": ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                   "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Safari/537.36")
}

def get_soup(url):
    resp = requests.get(url, headers=HEADERS, timeout=15)
    resp.raise_for_status()
    return BeautifulSoup(resp.text, "html.parser")

def parse_detail(url):
    soup = get_soup(url)
    def txt(css):
        el = soup.select_one(css)
        return el.get_text(strip=True) if el else None
    return {
        "url": url,
        "tieu_de":   txt("h1.title"),
        "gia_raw":   txt(".price"),          # ví dụ "2,5 triệu/tháng"
        "dien_tich_raw": txt(".area"),       # ví dụ "20 m²"
        "dia_chi":   txt(".address"),
        "mo_ta":     txt(".description"),
    }

# Trang danh sách phòng trọ khu vực Thanh Xuân (thay bằng URL thật)
LIST_URL = "https://phongtro123.com/tinh-thanh/ha-noi/quan-thanh-xuan?page={}"

rows = []
for page in range(1, 4):          # crawl thử 3 trang
    soup = get_soup(LIST_URL.format(page))
    links = [a["href"] for a in soup.select(".post-item a.post-title")]
    for link in links[:10]:       # mỗi trang lấy thử 10 tin
        try:
            rows.append(parse_detail(link))
        except Exception as e:
            print("Loi:", link, e)
        time.sleep(random.uniform(1, 3))

with open("thanhxuan_raw.csv", "w", newline="", encoding="utf-8-sig") as f:
    writer = csv.DictWriter(f, fieldnames=rows[0].keys())
    writer.writeheader(); writer.writerows(rows)
print(f"Da crawl {len(rows)} tin.")
```

> Nếu trang dùng JavaScript, thay `get_soup` bằng `Playwright`:
> ```python
> from playwright.sync_api import sync_playwright
> with sync_playwright() as p:
>     browser = p.chromium.launch(headless=True)
>     page = browser.new_page()
>     page.goto(url, wait_until="networkidle")
>     html = page.content()
> ```

**Công cụ:** Python `requests`, `beautifulsoup4`, `lxml`; hoặc `selenium`/`playwright` cho trang động; `pandas` để xem nhanh kết quả.

**Đầu ra (deliverable):**
- Script crawl (`crawl_thanhxuan.py`) chạy không lỗi.
- File `thanhxuan_raw.csv` chứa 50–100 tin thô có đầy đủ các cột đã định nghĩa.

**Tiêu chí hoàn thành:**
- Chạy lại script cho ra dữ liệu ổn định (selector không vỡ giữa các lần chạy).
- ≥ 90% số tin lấy được **có giá và diện tích** (2 trường quan trọng nhất).

**Thời gian:** **3–4 ngày** (phần lớn là dò selector và xử lý phân trang/chống bot).

---

## Giai đoạn 3 — Làm sạch & tạo dataset Thanh Xuân

**Mục tiêu:** Từ dữ liệu thô, tạo **dataset sạch, chuẩn hóa, sẵn sàng train** cho riêng quận Thanh Xuân, đạt tối thiểu **300–500 bản ghi hợp lệ**.

**Việc làm cụ thể:**
1. **Crawl đầy đủ** quận Thanh Xuân (mở rộng script GĐ2 chạy hết các trang).
2. **Parse trường số từ text:**
   - Giá: `"2,5 triệu/tháng"` → `2_500_000`; `"800 nghìn"` → `800_000`.
   - Diện tích: `"20 m²"` → `20.0`.
3. **Chuẩn hóa địa chỉ** → tách `phuong`, gán cứng `quan = "Thanh Xuân"`.
4. **Trích đặc trưng từ mô tả** (regex): số phòng ngủ, có/không điều hòa, nóng lạnh, gác xép, khép kín, wifi, để xe.
5. **Loại outlier & tin rác:** giá < 500k hoặc > 15 triệu/tháng (tin sai hoặc là nhà nguyên căn cho thuê), diện tích < 8 m² hoặc > 100 m², tin trùng URL/trùng số điện thoại.
6. **Tạo đặc trưng dẫn xuất:** `gia_moi_m2 = gia / dien_tich` (để lọc outlier theo đơn giá), `loai_hinh` (phòng trọ / nhà trọ / căn hộ mini) suy từ tiêu đề + mô tả.

**Ví dụ code làm sạch (`pandas` + `regex`):**

```python
import pandas as pd, re

df = pd.read_csv("thanhxuan_raw.csv")

def parse_gia(s):
    if pd.isna(s): return None
    s = s.lower().replace(",", ".")
    num = re.search(r"[\d.]+", s)
    if not num: return None
    val = float(num.group())
    if "tri" in s:  return val * 1_000_000     # triệu
    if "ngh" in s or "k" in s: return val * 1_000
    return val

def parse_dientich(s):
    if pd.isna(s): return None
    m = re.search(r"[\d.]+", str(s).replace(",", "."))
    return float(m.group()) if m else None

df["gia"] = df["gia_raw"].apply(parse_gia)
df["dien_tich"] = df["dien_tich_raw"].apply(parse_dientich)

# Trích đặc trưng nhị phân từ mô tả
mo_ta = df["mo_ta"].fillna("").str.lower()
df["co_dieu_hoa"] = mo_ta.str.contains("điều hòa|điều hoà|máy lạnh").astype(int)
df["khep_kin"]    = mo_ta.str.contains("khép kín|khep kin|vệ sinh riêng").astype(int)
df["co_gac"]      = mo_ta.str.contains("gác xép|gác lửng").astype(int)

# Suy loại hình
def loai_hinh(row):
    t = (str(row["tieu_de"]) + " " + str(row["mo_ta"])).lower()
    if "căn hộ mini" in t or "chung cư mini" in t: return "can_ho_mini"
    if "nhà trọ" in t: return "nha_tro"
    return "phong_tro"
df["loai_hinh"] = df.apply(loai_hinh, axis=1)

df["quan"] = "Thanh Xuân"

# Loại outlier & tin rác
df = df.dropna(subset=["gia", "dien_tich"])
df = df[(df["gia"].between(500_000, 15_000_000)) &
        (df["dien_tich"].between(8, 100))]
df["gia_m2"] = df["gia"] / df["dien_tich"]
# Bỏ đơn giá bất thường (ngoài 1%-99%)
lo, hi = df["gia_m2"].quantile([0.01, 0.99])
df = df[df["gia_m2"].between(lo, hi)]
df = df.drop_duplicates(subset=["url"])

df.to_csv("thanhxuan_clean.csv", index=False, encoding="utf-8-sig")
print("So ban ghi sach:", len(df))
```

**Công cụ:** `pandas`, `numpy`, `re` (regex), `matplotlib`/`seaborn` để vẽ phân bố giá và phát hiện outlier.

**Đầu ra (deliverable):**
- `thanhxuan_clean.csv` với các cột: `gia` (target), `dien_tich`, `phuong`, `quan`, `loai_hinh`, `so_phong_ngu`, `co_dieu_hoa`, `khep_kin`, `co_gac`, ...
- Notebook EDA ngắn (biểu đồ phân bố giá, giá/m² theo phường, ma trận tương quan).

**Tiêu chí hoàn thành:**
- Dataset có **≥ 300–500 bản ghi hợp lệ** (không null ở `gia`, `dien_tich`).
- Không còn giá trị vô lý (đã lọc outlier), không trùng lặp.
- Biến mục tiêu `gia` có phân bố hợp lý (thường lệch phải → cân nhắc `log(gia)` khi train).

**Thời gian:** **5–7 ngày** (đây là giai đoạn tốn công nhất, chiếm ~60–70% công sức làm dữ liệu của cả đồ án).

---

## Giai đoạn 4 — Train 4 model

**Mục tiêu:** Huấn luyện **4 mô hình hồi quy** trên dataset Thanh Xuân với cùng một pipeline tiền xử lý để so sánh công bằng.

**Việc làm cụ thể:**
- Chọn 4 model đại diện đủ mức độ phức tạp:
  1. **Linear Regression** (baseline — để biết ngưỡng thấp nhất).
  2. **Random Forest Regressor**.
  3. **XGBoost Regressor**.
  4. **LightGBM Regressor** (hoặc CatBoost — mạnh với biến phân loại như `phuong`).
- Xây `Pipeline` + `ColumnTransformer`: `StandardScaler` cho biến số, `OneHotEncoder` cho biến phân loại (`phuong`, `loai_hinh`).
- Train trên `log(gia)` để giảm ảnh hưởng lệch phân bố, dự đoán xong `expm1` để về giá gốc.
- Chia train/test (80/20) + `KFold` cross-validation để đánh giá ổn định.

**Ví dụ code train 4 model:**

```python
import pandas as pd, numpy as np
from sklearn.model_selection import train_test_split, cross_val_score
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import OneHotEncoder, StandardScaler
from sklearn.linear_model import LinearRegression
from sklearn.ensemble import RandomForestRegressor
from xgboost import XGBRegressor
from lightgbm import LGBMRegressor
import joblib

df = pd.read_csv("thanhxuan_clean.csv")
num_cols = ["dien_tich", "so_phong_ngu", "co_dieu_hoa", "khep_kin", "co_gac"]
cat_cols = ["phuong", "loai_hinh"]
X = df[num_cols + cat_cols]
y = np.log1p(df["gia"])                 # train tren log(gia)

pre = ColumnTransformer([
    ("num", StandardScaler(), num_cols),
    ("cat", OneHotEncoder(handle_unknown="ignore"), cat_cols),
])

models = {
    "linear":  LinearRegression(),
    "rf":      RandomForestRegressor(n_estimators=300, random_state=42),
    "xgb":     XGBRegressor(n_estimators=400, learning_rate=0.05, max_depth=5,
                            subsample=0.8, random_state=42),
    "lgbm":    LGBMRegressor(n_estimators=400, learning_rate=0.05, random_state=42),
}

X_tr, X_te, y_tr, y_te = train_test_split(X, y, test_size=0.2, random_state=42)
for name, model in models.items():
    pipe = Pipeline([("pre", pre), ("model", model)])
    pipe.fit(X_tr, y_tr)
    joblib.dump(pipe, f"model_{name}.joblib")
    print(f"{name}: da luu model_{name}.joblib")
```

**Công cụ:** `scikit-learn`, `xgboost`, `lightgbm` (hoặc `catboost`), `joblib` để serialize model. Nếu cần tinh chỉnh siêu tham số: `GridSearchCV`/`RandomizedSearchCV` hoặc `optuna`.

**Đầu ra (deliverable):**
- 4 file model đã train: `model_linear.joblib`, `model_rf.joblib`, `model_xgb.joblib`, `model_lgbm.joblib`.
- Script train (`train.py`) tái lập được kết quả (đã set `random_state`).

**Tiêu chí hoàn thành:**
- Cả 4 model train xong không lỗi và **lưu được ra file**.
- Ít nhất 1 model phi tuyến (RF/XGB/LGBM) vượt rõ baseline Linear Regression (R² test cao hơn).

**Thời gian:** **3–5 ngày** (bao gồm thời gian tinh chỉnh siêu tham số).

---

## Giai đoạn 5 — Đánh giá & chọn model

**Mục tiêu:** So sánh 4 model bằng các **chỉ số hồi quy chuẩn** trên tập test và cross-validation, chọn ra **model vô địch** để đưa vào API.

**Việc làm cụ thể:**
- Tính các metric trên **giá gốc** (sau khi `expm1`): **MAE**, **RMSE**, **R²**, và **MAPE** (sai số phần trăm — dễ giải thích cho người dùng: "sai lệch trung bình ~12%").
- Chạy K-Fold CV để kiểm tra độ ổn định (tránh chọn model chỉ may mắn trên 1 lần chia).
- Vẽ biểu đồ *giá thực tế vs giá dự đoán* và phân bố phần dư (residuals).
- Xem **feature importance** (với RF/XGB/LGBM) để kiểm tra tính hợp lý (kỳ vọng `dien_tich` và `phuong` quan trọng nhất).

**Ví dụ code đánh giá:**

```python
import numpy as np, joblib, pandas as pd
from sklearn.metrics import mean_absolute_error, mean_squared_error, r2_score

def mape(y_true, y_pred):
    return np.mean(np.abs((y_true - y_pred) / y_true)) * 100

results = []
for name in ["linear", "rf", "xgb", "lgbm"]:
    pipe = joblib.load(f"model_{name}.joblib")
    y_pred = np.expm1(pipe.predict(X_te))      # ve gia goc
    y_true = np.expm1(y_te)
    results.append({
        "model": name,
        "MAE":  round(mean_absolute_error(y_true, y_pred)),
        "RMSE": round(np.sqrt(mean_squared_error(y_true, y_pred))),
        "R2":   round(r2_score(y_true, y_pred), 3),
        "MAPE(%)": round(mape(y_true, y_pred), 1),
    })
print(pd.DataFrame(results).sort_values("MAE"))
```

**Công cụ:** `scikit-learn.metrics`, `matplotlib`/`seaborn`, `pandas`.

**Đầu ra (deliverable):**
- Bảng so sánh 4 model theo MAE/RMSE/R²/MAPE.
- Biểu đồ predicted-vs-actual và biểu đồ feature importance.
- Kết luận: chọn 1 model + copy thành `model_final.joblib`.

**Tiêu chí hoàn thành:**
- Có bảng số liệu rõ ràng và **lý do chọn model** (không chỉ dựa MAE mà cân nhắc cả tốc độ dự đoán, khả năng giải thích).
- Model vô địch đạt ngưỡng chấp nhận được cho đồ án, ví dụ **R² ≥ 0.7** hoặc **MAPE ≤ 20%** (tùy chất lượng dữ liệu thực tế).

**Thời gian:** **2–3 ngày.**

---

## Giai đoạn 6 — Xây API

**Mục tiêu:** Đóng gói model vô địch thành **web service** nhận thông tin phòng và trả về giá thuê dự đoán, để website gọi được.

**Việc làm cụ thể:**
- Dùng **FastAPI** (nhẹ, tự sinh docs Swagger tại `/docs`).
- Định nghĩa schema đầu vào bằng `pydantic` (validate diện tích > 0, phường thuộc danh sách hợp lệ...).
- Load `model_final.joblib` **một lần** khi khởi động, không load lại mỗi request.
- Trả về giá dự đoán + khoảng tin cậy đơn giản (ví dụ ±MAE).

**Ví dụ code API (`FastAPI`):**

```python
from fastapi import FastAPI
from pydantic import BaseModel, Field
import joblib, numpy as np, pandas as pd

app = FastAPI(title="API Du doan gia thue phong tro Ha Noi")
model = joblib.load("model_final.joblib")
MAE = 450_000   # lay tu ket qua GD5

class PhongTro(BaseModel):
    dien_tich: float = Field(..., gt=0, le=100)
    so_phong_ngu: int = 1
    co_dieu_hoa: int = 0
    khep_kin: int = 1
    co_gac: int = 0
    phuong: str
    loai_hinh: str = "phong_tro"

@app.post("/predict")
def predict(p: PhongTro):
    X = pd.DataFrame([p.dict()])
    gia = float(np.expm1(model.predict(X)[0]))
    return {
        "gia_du_doan": round(gia, -3),                 # lam tron nghin
        "khoang_gia": [round(gia - MAE, -3), round(gia + MAE, -3)],
        "don_vi": "VND/thang",
    }
```

Chạy: `uvicorn main:app --reload` → test tại `http://127.0.0.1:8000/docs`.

**Công cụ:** `fastapi`, `uvicorn`, `pydantic`, `joblib`. Đóng gói bằng `Dockerfile` (tùy chọn) để dễ deploy.

**Đầu ra (deliverable):**
- Service FastAPI (`main.py`) có endpoint `POST /predict` chạy được.
- `requirements.txt` liệt kê đúng phiên bản thư viện.

**Tiêu chí hoàn thành:**
- Gọi `/predict` bằng Swagger UI hoặc `curl`/`requests` trả về JSON giá hợp lý.
- API xử lý được input sai (thiếu trường, diện tích âm) mà không crash (trả lỗi 422 rõ ràng).

**Thời gian:** **3–4 ngày.**

---

## Giai đoạn 7 — Tích hợp website

**Mục tiêu:** Xây giao diện web cho người dùng nhập thông tin phòng và xem **giá thuê dự đoán ngay trên trình duyệt**.

**Việc làm cụ thể:**
- Làm **form nhập**: diện tích, phường (dropdown), loại hình, các tiện nghi (checkbox điều hòa/khép kín/gác).
- Khi submit → gọi API `/predict` bằng `fetch` (JS) → hiển thị giá + khoảng giá.
- Hai hướng triển khai:
  - **Nhanh, gọn cho đồ án:** dùng **Streamlit** (một file Python vừa là giao diện vừa gọi model, không cần tách frontend/backend).
  - **Bài bản hơn:** frontend HTML/CSS/JS (hoặc React) gọi API FastAPI riêng.
- Xử lý **CORS** trong FastAPI nếu frontend chạy khác cổng (`fastapi.middleware.cors.CORSMiddleware`).

**Ví dụ nhanh với Streamlit:**

```python
import streamlit as st, requests

st.title("Dự đoán giá thuê phòng trọ Hà Nội")
dien_tich = st.number_input("Diện tích (m²)", 8.0, 100.0, 20.0)
phuong = st.selectbox("Phường", ["Nhân Chính", "Khương Trung", "Thanh Xuân Bắc", ...])
loai = st.selectbox("Loại hình", ["phong_tro", "nha_tro", "can_ho_mini"])
dh = st.checkbox("Có điều hòa")
kk = st.checkbox("Khép kín", value=True)

if st.button("Dự đoán"):
    payload = {"dien_tich": dien_tich, "phuong": phuong, "loai_hinh": loai,
               "co_dieu_hoa": int(dh), "khep_kin": int(kk), "co_gac": 0,
               "so_phong_ngu": 1}
    r = requests.post("http://127.0.0.1:8000/predict", json=payload).json()
    st.success(f"Giá dự đoán: {r['gia_du_doan']:,.0f} VND/tháng")
    st.caption(f"Khoảng giá tham khảo: {r['khoang_gia'][0]:,.0f} – {r['khoang_gia'][1]:,.0f}")
```

**Công cụ:** `streamlit` (đơn giản); hoặc HTML/CSS/JS + `fetch`, hoặc `React`; `CORSMiddleware` cho FastAPI.

**Đầu ra (deliverable):**
- Website chạy được: nhập form → nhận giá dự đoán.
- Ảnh chụp màn hình demo để đưa vào báo cáo đồ án.

**Tiêu chí hoàn thành:**
- Người dùng khác (không phải người code) tự nhập được và nhận kết quả.
- Kết quả hiển thị đúng định dạng tiền tệ VNĐ, có khoảng giá tham khảo.

**Thời gian:** **4–6 ngày** (Streamlit ~2–3 ngày; frontend riêng lâu hơn).

---

## Giai đoạn 8 — Mở rộng các quận khác

**Mục tiêu:** Nhân bản pipeline từ Thanh Xuân ra **các quận nội thành Hà Nội khác** (Cầu Giấy, Đống Đa, Hai Bà Trưng, Hoàng Mai, Nam Từ Liêm...) để hệ thống dùng được trên toàn thành phố.

**Việc làm cụ thể:**
- **Tái sử dụng script crawl** GĐ2/GĐ3, chỉ đổi tham số quận trong URL → crawl lần lượt từng quận.
- Gộp tất cả thành một dataset lớn `hanoi_clean.csv`, thêm cột `quan` làm **đặc trưng phân loại** (OneHotEncoder xử lý được thêm giá trị mới).
- **Train lại một model tổng** cho toàn Hà Nội (dùng `quan` + `phuong` làm feature), thay vì train model riêng cho mỗi quận.
- Cập nhật dropdown phường/quận trên website theo dữ liệu mới.
- Đánh giá lại theo từng quận để phát hiện quận nào dữ liệu yếu → xử lý (xem mục dưới).

**Công cụ:** như GĐ2–GĐ4 (`requests`/`playwright`, `pandas`, `scikit-learn`, `xgboost`/`lightgbm`).

**Đầu ra (deliverable):**
- `hanoi_clean.csv` gộp nhiều quận.
- `model_final_hanoi.joblib` train trên dữ liệu toàn thành phố.
- API + website cập nhật hỗ trợ chọn quận.

**Tiêu chí hoàn thành:**
- Hệ thống dự đoán được cho **≥ 4–5 quận** (không chỉ Thanh Xuân).
- Model tổng **không tệ đi rõ rệt** so với model riêng Thanh Xuân (MAPE tăng không quá vài %).

**Thời gian:** **5–7 ngày.**

---

## Lưu ý quan trọng về số lượng dữ liệu tối thiểu

Đây là rủi ro lớn nhất của đồ án — **model chỉ tốt khi đủ dữ liệu**. Một số ngưỡng và cách xử lý thực tế:

**1. Ngưỡng dữ liệu tối thiểu để train được:**
- **≥ 300–500 bản ghi hợp lệ/khu vực** là mức tối thiểu để mô hình phi tuyến (RF/XGBoost) học được quan hệ mà không overfit nặng.
- Dưới **200 bản ghi**: chỉ nên dùng **Linear Regression** hoặc **RandomForest cây nông** (`max_depth` nhỏ), tránh XGBoost/LightGBM vì rất dễ overfit trên dữ liệu ít.
- Lý tưởng cho model toàn Hà Nội: **≥ 2.000–3.000 bản ghi** tổng, phân bố tương đối đều giữa các quận.

**2. Cách xử lý khi ít dữ liệu (rất hay gặp với quận nhỏ):**

| Tình huống | Cách xử lý |
|-----------|-----------|
| Một quận chỉ có < 100 tin | **Không train model riêng.** Gộp vào dataset chung, dùng `quan`/`phuong` làm **feature phân loại** trong 1 model tổng. |
| Nhiều phường quá lẻ (mỗi phường vài tin) | Gộp phường thành **nhóm khu vực** (ví dụ theo cụm địa lý), hoặc bỏ `phuong`, chỉ giữ `quan`. |
| Biến phân loại có giá trị hiếm | Dùng `OneHotEncoder(handle_unknown="ignore")` để không vỡ khi gặp phường/quận mới lúc dự đoán. |
| Dữ liệu tổng vẫn ít (< 500) | Ưu tiên **ít feature, model đơn giản**; dùng **K-Fold CV** thay vì chỉ 1 lần chia train/test để đánh giá ổn định hơn. |
| Phân bố giá lệch mạnh | Train trên `log1p(gia)` (đã áp dụng ở GĐ4) để giảm ảnh hưởng của vài tin giá cực cao. |

**3. Nguyên tắc "district làm feature thay vì model riêng":**
Thay vì huấn luyện 12 model cho 12 quận (mỗi model thiếu dữ liệu, khó bảo trì), hãy huấn luyện **một model duy nhất** với `quan` và `phuong` là đặc trưng đầu vào. Cách này giúp các quận ít dữ liệu **"mượn" được thông tin chung** (ví dụ quan hệ diện tích–giá) từ các quận nhiều dữ liệu, đồng thời model vẫn học được chênh lệch mặt bằng giá giữa các khu vực qua chính đặc trưng `quan`/`phuong`. Đây là lựa chọn thực tế và bền vững nhất cho phạm vi một đồ án tốt nghiệp làm một mình.


---


## Lưu ý pháp lý & đạo đức khi crawl dữ liệu (Việt Nam)

Phần này quy định các ràng buộc bắt buộc khi thu thập tin đăng cho thuê phòng trọ/nhà trọ/căn hộ mini tại Hà Nội. Đây là đồ án tốt nghiệp, dữ liệu **chỉ dùng cho mục đích học thuật/nghiên cứu**, **không thương mại hóa, không bán lại, không tái phát tán**.

### 1. Tôn trọng `robots.txt` và Terms of Service

Trước khi crawl bất kỳ domain nào (ví dụ các trang rao vặt bất động sản/cho thuê), phải đọc `robots.txt` và kiểm tra từng URL có được phép fetch không. Dùng `urllib.robotparser` (chuẩn thư viện) hoặc `Protego` (parser mà Scrapy dùng, hỗ trợ `Crawl-delay`, wildcard tốt hơn).

```python
import urllib.robotparser as robotparser

RP_CACHE = {}

def can_fetch(url: str, user_agent: str = "ThesisRentalBot/1.0") -> bool:
    from urllib.parse import urlparse
    base = "{0.scheme}://{0.netloc}".format(urlparse(url))
    if base not in RP_CACHE:
        rp = robotparser.RobotFileParser()
        rp.set_url(base + "/robots.txt")
        rp.read()
        RP_CACHE[base] = rp
    return RP_CACHE[base].can_fetch(user_agent, url)

# Đọc Crawl-delay nếu site khai báo
def crawl_delay(url: str, user_agent="ThesisRentalBot/1.0", default=3.0) -> float:
    from urllib.parse import urlparse
    base = "{0.scheme}://{0.netloc}".format(urlparse(url))
    d = RP_CACHE[base].crawl_delay(user_agent) if base in RP_CACHE else None
    return float(d) if d else default
```

Nếu `robots.txt` `Disallow` đường dẫn tin đăng, hoặc **Terms of Service (Điều khoản sử dụng)** của site cấm thu thập tự động/scraping, thì **không crawl** khu vực đó. Đọc mục "Điều khoản", "Quy chế", "Thỏa thuận người dùng" của từng site để xác nhận. Đặt `User-Agent` trung thực, mô tả rõ bot học thuật kèm email liên hệ (`neorain0011@gmail.com`) để chủ site có thể yêu cầu dừng.

### 2. Rate limit / delay hợp lý — không gây quá tải (DoS)

Crawl với tốc độ thấp, tránh dồn request gây tải cho máy chủ (hành vi làm nghẽn dịch vụ có thể bị coi là cản trở hoạt động hệ thống thông tin theo Luật An ninh mạng 2018). Nguyên tắc: **1 request tuần tự mỗi 3–5 giây, không chạy song song nhiều luồng vào cùng một domain**, ưu tiên crawl vào giờ thấp điểm.

```python
import time, random, requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry

session = requests.Session()
session.headers.update({"User-Agent": "ThesisRentalBot/1.0 (+neorain0011@gmail.com)"})
# Backoff khi gặp 429/503 thay vì hammer server
retry = Retry(total=3, backoff_factor=2, status_forcelist=[429, 500, 502, 503, 504])
session.mount("https://", HTTPAdapter(max_retries=retry))

def polite_get(url):
    if not can_fetch(url):
        return None
    resp = session.get(url, timeout=15)
    # tôn trọng Crawl-delay + jitter để không tạo pattern đều đặn
    time.sleep(crawl_delay(url) + random.uniform(0.5, 1.5))
    return resp
```

Với Scrapy, cấu hình tương đương trong `settings.py`:

```python
ROBOTSTXT_OBEY = True
DOWNLOAD_DELAY = 3
CONCURRENT_REQUESTS_PER_DOMAIN = 1
AUTOTHROTTLE_ENABLED = True          # tự điều tiết theo độ trễ phản hồi của server
AUTOTHROTTLE_TARGET_CONCURRENCY = 1.0
HTTPCACHE_ENABLED = True             # cache để không fetch lại URL đã tải
```

Bật `HTTPCACHE`/lưu HTML thô một lần rồi parse offline, tránh gọi lại nhiều lần trong quá trình debug/thử nghiệm mô hình.

### 3. Không thu thập dữ liệu cá nhân — Nghị định 13/2023/NĐ-CP (PDPD)

**Nghị định 13/2023/NĐ-CP** về bảo vệ dữ liệu cá nhân (có hiệu lực 01/07/2023) định nghĩa dữ liệu cá nhân là thông tin gắn với/giúp xác định một cá nhân. Với bài toán dự đoán giá thuê, **các biến này không phải feature dự đoán** và phải loại bỏ ngay khi parse:

- Số điện thoại, Zalo, email của người đăng.
- Tên chủ trọ/người liên hệ, tài khoản mạng xã hội.
- Địa chỉ chi tiết đến số nhà (chỉ cần giữ tới cấp **phường/quận** để mô hình học yếu tố vị trí).

Bài toán chỉ cần: giá thuê, diện tích (m²), số phòng ngủ/WC, loại hình (phòng trọ/nhà trọ/căn hộ mini), tiện ích (điều hòa, gác xép, khép kín...), quận/phường, tầng. Việc **không xử lý dữ liệu cá nhân** giúp tránh nghĩa vụ về sự đồng ý (consent), thông báo và các trách nhiệm theo Nghị định 13. Tuyệt đối **không tái phát tán** thông tin liên hệ của người đăng.

### 4. Ẩn danh hóa dữ liệu cá nhân TRƯỚC khi lưu

Kể cả khi thông tin cá nhân lẫn trong phần mô tả text, phải lọc/che (redaction) trước khi ghi vào dataset. Không lưu số điện thoại thô; nếu cần khử trùng lặp tin đăng theo người bán thì thay bằng hash một chiều có salt.

```python
import re, hashlib, os

PHONE_RE = re.compile(r'(0|\+?84)(\s|\.|-)?(\d(\s|\.|-)?){8,10}')
EMAIL_RE = re.compile(r'[\w.+-]+@[\w-]+\.[\w.-]+')
SALT = os.environ["PII_SALT"].encode()   # không hardcode salt vào source

def redact_pii(text: str) -> str:
    text = PHONE_RE.sub('[PHONE]', text)
    text = EMAIL_RE.sub('[EMAIL]', text)
    return text

def pseudonymize(value: str) -> str:
    # hash 1 chiều để dedup, KHÔNG thể suy ngược ra SĐT gốc
    return hashlib.sha256(SALT + value.encode()).hexdigest()[:16]
```

Ưu tiên **không lưu** thay vì hash. Chỉ pseudonymize khi thực sự cần dedup, và không lưu kèm bảng ánh xạ ngược.

### 5. Bản quyền nội dung tin đăng & ghi nguồn

Nội dung mô tả và **ảnh** trong tin đăng thuộc quyền tác giả của người đăng/nền tảng (Luật Sở hữu trí tuệ). Trong đồ án:

- **Không sao chép nguyên văn** mô tả hay tải/redistribute ảnh; chỉ trích xuất **feature số/hạng mục** (diện tích, giá, tiện ích) — đây là dữ kiện, không phải tác phẩm.
- Mỗi bản ghi lưu kèm **`source_url`** và **`source_name`** để truy vết nguồn và trích dẫn trong báo cáo.

```python
record = {
    "price_vnd": 3_500_000,
    "area_m2": 20.0,
    "district": "Cầu Giấy",
    "ward": "Dịch Vọng",
    "type": "phong_tro",
    "amenities": ["dieu_hoa", "khep_kin", "gac_xep"],
    "description": redact_pii(raw_desc),   # đã che PII
    "source_name": "tên_trang_rao_vặt",
    "source_url": "https://.../tin-dang/12345",
    "crawled_at": "2026-07-05T10:00:00+07:00",
}
```

### 6. Ưu tiên API chính thức

Nếu nền tảng có **API chính thức** hoặc cổng dữ liệu mở, dùng API thay vì scraping HTML — hợp pháp, ổn định và đúng điều khoản hơn. Trường hợp không có API nhưng cần lượng lớn dữ liệu, nên **email liên hệ ban quản trị site** xin phép thu thập cho mục đích nghiên cứu và tuân theo giới hạn họ đưa ra.

### 7. Checklist tuân thủ (rút gọn)

| Hạng mục | Yêu cầu |
|---|---|
| `robots.txt` | Kiểm tra `can_fetch` mỗi URL; tôn trọng `Crawl-delay` |
| Terms of Service | Đọc & xác nhận không cấm scraping trước khi crawl |
| Tốc độ | ≥ 3s/request, 1 luồng/domain, có backoff cho 429/503 |
| Dữ liệu cá nhân | Không thu SĐT/tên/email; địa chỉ chỉ tới phường |
| Ẩn danh | `redact_pii()` / hash có salt trước khi lưu |
| Nghị định 13/2023 | Không xử lý dữ liệu cá nhân, không tái phát tán |
| Bản quyền | Không copy mô tả/ảnh; chỉ lấy feature dữ kiện |
| Nguồn | Lưu `source_url`, `source_name`, `crawled_at` |
| Mục đích | Học thuật/đồ án; không bán, không thương mại hóa |
| API | Ưu tiên API chính thức; xin phép nếu cần |


---

