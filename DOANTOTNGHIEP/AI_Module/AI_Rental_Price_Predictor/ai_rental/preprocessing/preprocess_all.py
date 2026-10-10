# -*- coding: utf-8 -*-
"""
Gộp TẤT CẢ raw jsonl (phongtro123 nhiều quận + mogi) -> dataset sạch toàn Hà Nội.
- Chuẩn hoá giá/diện tích/phường/quận/loại phòng/tiện ích (normalizers.py)
- distance_to_center_km: toạ độ thật (mogi) > tâm PHƯỜNG (suy từ mogi) > tâm QUẬN
- Lọc trùng + outlier (nghiệp vụ + đơn giá + IQR theo quận)
Ra: data/processed/hanoi_all_clean.csv (+ mỗi quận 1 file)

Chạy:  python preprocess_all.py
"""
import glob, json, sys
from pathlib import Path
import numpy as np
import pandas as pd
import normalizers as N

sys.stdout.reconfigure(encoding="utf-8")
ROOT = Path(__file__).resolve().parents[1]   # thư mục ai_rental (data/ nằm ở đây)
AMEN = list(N.AMENITY_PATTERNS)
POPULATION_DENSITY_FILE = ROOT / "data" / "reference" / "hanoi_population_density_2024.csv"
FRECENCY_HALF_LIFE_DAYS = 180.0
MARKET_HALF_LIFE_DAYS = 90.0
MARKET_FEATURES = [
    "market_unit_price_million_m2",
    "market_price_million",
    "market_sample_count",
    "market_frecency_score",
    "market_freshness_days",
    "market_scope_level",
]
POPULATION_FEATURES = ["population_density_km2"]


def _weighted_mean(values, weights):
    values = pd.to_numeric(values, errors="coerce")
    weights = pd.to_numeric(weights, errors="coerce")
    ok = values.notna() & weights.notna() & (weights > 0)
    if not ok.any():
        return np.nan
    return float(np.average(values.loc[ok], weights=weights.loc[ok]))


def _market_key(scope: str, district="", ward="", room_type="") -> str:
    return "||".join([scope, str(district or ""), str(ward or ""), str(room_type or "")])


def add_population_density(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    if not POPULATION_DENSITY_FILE.exists():
        df["population_density_km2"] = np.nan
        df["population_thousand"] = np.nan
        df["district_area_km2"] = np.nan
        return df

    ref = pd.read_csv(POPULATION_DENSITY_FILE)
    ref = ref.rename(columns={"area_km2": "district_area_km2"})
    df = df.merge(
        ref[["district", "district_area_km2", "population_thousand", "population_density_km2"]],
        on="district",
        how="left",
    )
    for col in ["district_area_km2", "population_thousand", "population_density_km2"]:
        df[col] = pd.to_numeric(df[col], errors="coerce")
        df[col] = df[col].fillna(df[col].median())
    return df


def _loo_market_scope(df: pd.DataFrame, keys: list[str], min_count: int, level: int) -> pd.DataFrame:
    out = pd.DataFrame(index=df.index)
    w = df["_market_weight"]
    price_w = df["_market_price_w"]
    unit_w = df["_market_unit_w"]

    if keys:
        grouped = df.groupby(keys, dropna=False)
        count = grouped["_market_weight"].transform("count") - 1
        w_sum = grouped["_market_weight"].transform("sum") - w
        price_sum = grouped["_market_price_w"].transform("sum") - price_w
        unit_sum = grouped["_market_unit_w"].transform("sum") - unit_w
        freshness = grouped["listing_age_days"].transform("min")
    else:
        count = pd.Series(len(df) - 1, index=df.index)
        w_sum = pd.Series(w.sum(), index=df.index) - w
        price_sum = pd.Series(price_w.sum(), index=df.index) - price_w
        unit_sum = pd.Series(unit_w.sum(), index=df.index) - unit_w
        freshness = pd.Series(df["listing_age_days"].min(), index=df.index)

    valid = (count >= min_count) & (w_sum > 0)
    out["market_unit_price_million_m2"] = np.where(valid, unit_sum / w_sum, np.nan)
    out["market_price_million"] = np.where(valid, price_sum / w_sum, np.nan)
    out["market_sample_count"] = np.where(valid, count, np.nan)
    out["market_frecency_score"] = np.where(valid, w_sum, np.nan)
    out["market_freshness_days"] = np.where(valid, freshness, np.nan)
    out["market_scope_level"] = np.where(valid, level, np.nan)
    return out


def _market_record(group: pd.DataFrame, scope: str, level: int) -> dict:
    w = group["_market_weight"]
    return {
        "scope": scope,
        "scope_level": level,
        "district": "" if scope == "global" else str(group["district"].iloc[0]),
        "ward": str(group["ward"].iloc[0]) if "ward" in group and "ward" in scope else "",
        "room_type": str(group["room_type"].iloc[0]) if "room_type" in group and "room_type" in scope else "",
        "market_unit_price_million_m2": round(_weighted_mean(group["unit_price"], w), 4),
        "market_price_million": round(_weighted_mean(group["price_million"], w), 4),
        "market_sample_count": int(len(group)),
        "market_frecency_score": round(float(w.sum()), 4),
        "market_freshness_days": int(group["listing_age_days"].min()),
    }


def _export_market_stats(df: pd.DataFrame, outdir: Path) -> None:
    scopes = [
        ("global", [], 0),
        ("district", ["district"], 1),
        ("district_room_type", ["district", "room_type"], 2),
        ("district_ward", ["district", "ward"], 3),
        ("district_ward_room_type", ["district", "ward", "room_type"], 4),
    ]
    stats = {}
    for scope, keys, level in scopes:
        if not keys:
            rec = _market_record(df, scope, level)
            stats[_market_key(scope)] = rec
            continue
        for _, group in df.groupby(keys, dropna=False):
            rec = _market_record(group, scope, level)
            stats[_market_key(scope, rec["district"], rec["ward"], rec["room_type"])] = rec

    payload = {
        "generated_at": pd.Timestamp.utcnow().isoformat(),
        "market_half_life_days": MARKET_HALF_LIFE_DAYS,
        "stats": stats,
    }
    json.dump(payload, (outdir / "market_stats.json").open("w", encoding="utf-8"),
              ensure_ascii=False, indent=2)


def add_market_features(df: pd.DataFrame, outdir: Path) -> pd.DataFrame:
    df = df.copy()
    confidence_weight = df["location_confidence"].map({"high": 1.0, "medium": 0.8, "low": 0.6}).fillna(0.6)
    df["_market_weight"] = (
        np.exp(-np.log(2) * df["listing_age_days"] / MARKET_HALF_LIFE_DAYS).clip(0.03, 1.0)
        * confidence_weight
    )
    df["_market_price_w"] = df["price_million"] * df["_market_weight"]
    df["_market_unit_w"] = df["unit_price"] * df["_market_weight"]

    base = _loo_market_scope(df, [], min_count=1, level=0)
    fallback = {
        "market_unit_price_million_m2": float(df["unit_price"].median()),
        "market_price_million": float(df["price_million"].median()),
        "market_sample_count": int(len(df)),
        "market_frecency_score": float(df["_market_weight"].sum()),
        "market_freshness_days": int(df["listing_age_days"].min()),
        "market_scope_level": 0,
    }
    for col, value in fallback.items():
        base[col] = base[col].fillna(value)

    scopes = [
        (["district"], 5, 1),
        (["district", "room_type"], 5, 2),
        (["district", "ward"], 5, 3),
        (["district", "ward", "room_type"], 3, 4),
    ]
    for keys, min_count, level in scopes:
        candidate = _loo_market_scope(df, keys, min_count=min_count, level=level)
        mask = candidate["market_unit_price_million_m2"].notna()
        base.loc[mask, MARKET_FEATURES] = candidate.loc[mask, MARKET_FEATURES]

    for col in MARKET_FEATURES:
        df[col] = base[col].astype(float)

    _export_market_stats(df, outdir)
    return df.drop(columns=["_market_weight", "_market_price_w", "_market_unit_w"])


def load_raw() -> pd.DataFrame:
    recs = []
    for fp in glob.glob(str(ROOT / "data" / "raw" / "*.jsonl")):
        for line in open(fp, encoding="utf-8"):
            line = line.strip()
            if line:
                recs.append(json.loads(line))
    return pd.DataFrame(recs)


def normalize(df: pd.DataFrame) -> pd.DataFrame:
    df = df.drop_duplicates(subset=["source_url"]).copy()
    for c in ["latitude", "longitude", "district", "description", "address",
              "title", "full_description", "posted_date", "floor"]:
        if c not in df:
            df[c] = None

    outside_hanoi = df.apply(
        lambda row: N.is_location_outside_hanoi(
            row["address"], row["latitude"], row["longitude"]),
        axis=1,
    )
    removed = int(outside_hanoi.sum())
    if removed:
        print(f"Removed {removed} listings with an address or coordinates outside Ha Noi")
    df = df.loc[~outside_hanoi].copy()
    df["location_confidence"] = df.apply(
        lambda row: N.location_confidence(row["address"], row["latitude"], row["longitude"]),
        axis=1,
    )

    df["price_million"] = df["price_raw"].apply(N.parse_price_million)
    df["area_m2"] = df["area_raw"].apply(N.parse_area)
    df["ward"] = df["address"].apply(N.extract_ward)
    df["district"] = [N.extract_district(a, h if pd.notna(h) else None)
                      for a, h in zip(df["address"], df["district"])]

    # ưu tiên mô tả ĐẦY ĐỦ (phongtro123 detail / mogi); fallback snippet danh sách
    body = df["full_description"].fillna("").astype(str)
    body = body.mask(body.str.strip() == "", df["description"].fillna("").astype(str))
    corpus = (df["title"].fillna("") + " " + body.map(N.strip_html)).str.lower()
    df["room_type"] = corpus.apply(N.detect_room_type)
    for col, pat in N.AMENITY_PATTERNS.items():
        df[col] = corpus.str.contains(pat, regex=True, na=False).astype("int8")
    df["number_of_amenities"] = df[AMEN].sum(axis=1)

    # tầng + tháng đăng (best-effort)
    df["floor"] = pd.to_numeric(df["floor"], errors="coerce")
    df["posted_month"] = pd.to_datetime(df["posted_date"], format="%d/%m/%Y",
                                        errors="coerce").dt.month
    df["latitude"] = pd.to_numeric(df["latitude"], errors="coerce")
    df["longitude"] = pd.to_numeric(df["longitude"], errors="coerce")
    # Keep this separate from imputed coordinates: a real GPS point provides
    # more location information than a district/ward fallback.
    df["has_coordinates"] = (df["latitude"].notna() & df["longitude"].notna()).astype("int8")
    listing_date = pd.to_datetime(df["posted_date"], format="%d/%m/%Y", errors="coerce")
    crawled_date = pd.to_datetime(df.get("crawled_at"), errors="coerce", utc=True)
    reference_date = crawled_date.max()
    reference_date = reference_date.tz_localize(None).normalize() if pd.notna(reference_date) else pd.Timestamp.today().normalize()
    df["listing_age_days"] = (reference_date - listing_date).dt.days.clip(lower=0)
    df["listing_age_days"] = df["listing_age_days"].fillna(df["listing_age_days"].median()).astype("int16")
    # Keep historical rows for coverage while making recent market prices dominant.
    df["recency_weight"] = np.exp(-np.log(2) * df["listing_age_days"] / 365.0).clip(0.05, 1.0)
    confidence_weight = df["location_confidence"].map({"high": 1.0, "medium": 0.8, "low": 0.6}).fillna(0.6)
    df["frecency_weight"] = (
        np.exp(-np.log(2) * df["listing_age_days"] / FRECENCY_HALF_LIFE_DAYS).clip(0.03, 1.0)
        * confidence_weight
    ).round(4)
    df["sample_weight"] = df["frecency_weight"]
    df["listing_group"] = [N.listing_group_key(a, lat, lon, phone, url)
                           for a, lat, lon, phone, url in zip(
                               df["address"], df["latitude"], df["longitude"],
                               df.get("phone", pd.Series(index=df.index)), df["source_url"])]
    return df.dropna(subset=["price_million", "area_m2"]).reset_index(drop=True)


def add_distance(df: pd.DataFrame) -> pd.DataFrame:
    # tâm PHƯỜNG suy từ các tin có toạ độ thật (mogi)
    geo = df.dropna(subset=["latitude", "longitude"])
    ward_c = (geo.groupby(["district", "ward"])[["latitude", "longitude"]]
                 .mean().to_dict("index"))

    def dist(r):
        if pd.notna(r["latitude"]) and pd.notna(r["longitude"]):
            return N.haversine_km(r["latitude"], r["longitude"])         # toạ độ thật
        c = ward_c.get((r["district"], r["ward"]))
        if c:
            return N.haversine_km(c["latitude"], c["longitude"])         # tâm phường
        dc = N.DISTRICT_CENTER.get(r["district"])
        if dc:
            return N.haversine_km(dc[0], dc[1])                          # tâm quận
        return np.nan

    df["distance_to_center_km"] = df.apply(dist, axis=1)
    df["distance_to_center_km"] = df["distance_to_center_km"].fillna(
        df["distance_to_center_km"].median())
    return df


def filter_outliers(df: pd.DataFrame) -> pd.DataFrame:
    df = df.drop_duplicates(subset=["title", "price_million", "area_m2", "district"]).copy()
    # chỉ giữ 12 quận Hà Nội đã biết (bỏ tin nhiễu địa chỉ tỉnh khác)
    df = df[df["district"].isin(N.DISTRICT_CENTER)]
    df = df[df["price_million"].between(1.0, 20.0) & df["area_m2"].between(8, 80)].copy()
    df["unit_price"] = df["price_million"] / df["area_m2"]
    df = df[df["unit_price"].between(0.05, 0.6)].copy()
    # IQR clip theo từng quận bằng transform (giữ nguyên cột district)
    q1 = df.groupby("district")["unit_price"].transform(lambda s: s.quantile(0.25))
    q3 = df.groupby("district")["unit_price"].transform(lambda s: s.quantile(0.75))
    iqr = q3 - q1
    df = df[(df["unit_price"] >= q1 - 1.5 * iqr) & (df["unit_price"] <= q3 + 1.5 * iqr)]
    return df.reset_index(drop=True)


def main():
    raw = load_raw()
    print(f"Đọc {len(raw)} tin thô từ {raw['source_name'].nunique()} nguồn")
    outdir = ROOT / "data" / "processed"; outdir.mkdir(parents=True, exist_ok=True)
    df = filter_outliers(add_distance(normalize(raw)))
    df = add_population_density(df)
    df = add_market_features(df, outdir)
    print(f"Sau làm sạch: {len(df)} bản ghi hợp lệ")

    df.to_csv(outdir / "hanoi_all_clean.csv", index=False, encoding="utf-8-sig")

    # lưu tâm phường (cho API tính distance nhất quán với lúc train)
    geo = df.dropna(subset=["latitude", "longitude"])
    wc = {f"{d}||{w}": [round(row["latitude"], 6), round(row["longitude"], 6)]
          for (d, w), row in geo.groupby(["district", "ward"])[["latitude", "longitude"]].mean().iterrows()}
    json.dump(wc, (outdir / "ward_centroids.json").open("w", encoding="utf-8"), ensure_ascii=False)
    for dist, g in df.groupby("district"):
        if len(g) >= 100:
            slug = {v: k for k, v in N.SLUG2NAME.items()}.get(dist, dist).replace("quan-", "")
            g.to_csv(outdir / f"{slug}.csv", index=False, encoding="utf-8-sig")

    print("\n== Theo quận (giá TB triệu | n) ==")
    print(df.groupby("district")["price_million"].agg(["mean", "count"]).round(2)
            .sort_values("count", ascending=False).to_string())
    print(f"\n== Nguồn ==\n{df['source_name'].value_counts().to_string()}")
    print(f"\n== distance_to_center_km ==\n{df['distance_to_center_km'].describe().round(2).to_string()}")
    print(f"\nĐÃ LƯU -> {outdir/'hanoi_all_clean.csv'}")


if __name__ == "__main__":
    main()
