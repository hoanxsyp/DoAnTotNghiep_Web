-- =====================================================================================
-- V3__seed_catalog.sql — Seed lookup catalog (categories + amenities)
-- Spring Boot 3.3.5 / Java 21 / MySQL 8.4 / Flyway. Charset utf8mb4, engine InnoDB.
-- Source of truth: docs/00_CANONICAL_DECISIONS.md + docs/02_THIET_KE_DATABASE.md §8.4, §8.5.
--
-- Seeds:
--   * categories — 7 rows (CategoryCode: BOARDING_HOUSE, MINI_APARTMENT, APARTMENT,
--       WHOLE_HOUSE, HOMESTAY, ROOMMATE, SMALL_PREMISES) with Vietnamese name + slug and
--       required_fields / optional_fields JSON per §10.5 / §8.4.
--   * amenities — 29 rows across 4 AmenityGroup (FURNITURE, SECURITY, UTILITY, TRANSPORT)
--       matching filter set §3.7 / §8.5, each with a price_impact_ratio (BETWEEN -1 AND 1).
--
-- Explicit ids so downstream migrations / seed data can reference rows deterministically.
-- The "common required fields" (categoryId, title, description, price, area, provinceId,
-- districtId, wardId, addressDetail, images) are NOT repeated in required_fields (§8.4).
-- Flyway runs this once; no FK violations (both are lookup tables with no outbound FKs).
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- categories (7) — required_fields = fields required BEYOND the common set (§8.4)
-- -------------------------------------------------------------------------------------
INSERT INTO categories (id, code, name, slug, description, icon, required_fields, optional_fields, display_order, is_active) VALUES
  (1, 'BOARDING_HOUSE', 'Phòng trọ', 'phong-tro',
      'Phòng thuê riêng trong dãy trọ hoặc nhà cho thuê. Loại chính của hệ thống.',
      'home',
      '["toiletType","maxOccupants"]',
      '["depositAmount","electricityPrice","waterPrice","availableFrom","curfewType","furnitureStatus","petAllowed","parkingAvailable","latitude","longitude"]',
      1, TRUE),
  (2, 'MINI_APARTMENT', 'Chung cư mini', 'chung-cu-mini',
      'Căn nhỏ trong tòa chung cư mini. Có thể có thang máy, bảo vệ, nội thất.',
      'building',
      '["toiletType","furnitureStatus","maxOccupants"]',
      '["roomCount","depositAmount","electricityPrice","waterPrice","availableFrom","petAllowed","parkingAvailable","curfewType","latitude","longitude"]',
      2, TRUE),
  (3, 'APARTMENT', 'Căn hộ', 'can-ho',
      'Căn hộ chung cư hoặc dịch vụ. Giá thường cao hơn phòng trọ.',
      'apartment',
      '["roomCount","toiletCount","furnitureStatus"]',
      '["toiletType","maxOccupants","depositAmount","electricityPrice","waterPrice","availableFrom","petAllowed","parkingAvailable","latitude","longitude"]',
      3, TRUE),
  (4, 'WHOLE_HOUSE', 'Nhà nguyên căn', 'nha-nguyen-can',
      'Cho thuê cả căn nhà. Có số phòng, số tầng.',
      'house',
      '["roomCount","toiletCount"]',
      '["floorCount","furnitureStatus","maxOccupants","depositAmount","electricityPrice","waterPrice","availableFrom","petAllowed","parkingAvailable","latitude","longitude"]',
      4, TRUE),
  (5, 'HOMESTAY', 'Homestay cho thuê', 'homestay',
      'Thuê theo tháng hoặc dài hạn. Không tập trung thuê theo ngày.',
      'homestay',
      '["furnitureStatus","maxOccupants"]',
      '["roomCount","toiletCount","toiletType","depositAmount","availableFrom","petAllowed","parkingAvailable","curfewType","latitude","longitude"]',
      5, TRUE),
  (6, 'ROOMMATE', 'Ở ghép', 'o-ghep',
      'Người cần tìm phòng để ghép hoặc tìm người ghép. Có thêm giới tính, số người, quy định sinh hoạt.',
      'roommate',
      '["genderRequirement","maxOccupants","currentOccupants","curfewType"]',
      '["toiletType","furnitureStatus","depositAmount","electricityPrice","waterPrice","availableFrom","petAllowed","parkingAvailable","latitude","longitude"]',
      6, TRUE),
  (7, 'SMALL_PREMISES', 'Mặt bằng nhỏ', 'mat-bang-nho',
      'Mặt bằng kinh doanh nhỏ.',
      'store',
      '["toiletCount"]',
      '["roomCount","furnitureStatus","depositAmount","electricityPrice","waterPrice","availableFrom","parkingAvailable","latitude","longitude"]',
      7, TRUE);

-- -------------------------------------------------------------------------------------
-- amenities (29) — 4 groups (§8.5). price_impact_ratio in [-1,1] (ck_amenities_price_impact).
-- -------------------------------------------------------------------------------------

-- Nhóm FURNITURE — Nội thất (9)
INSERT INTO amenities (id, code, name, group_code, icon, is_filterable, price_impact_ratio, display_order, is_active) VALUES
  (1,  'AIR_CONDITIONER', 'Máy lạnh',    'FURNITURE', 'ac',        TRUE, 0.0500, 1, TRUE),
  (2,  'WATER_HEATER',    'Máy nước nóng','FURNITURE','water-heater',TRUE,0.0200, 2, TRUE),
  (3,  'BED',             'Giường',      'FURNITURE', 'bed',       TRUE, 0.0200, 3, TRUE),
  (4,  'WARDROBE',        'Tủ quần áo',  'FURNITURE', 'wardrobe',  TRUE, 0.0100, 4, TRUE),
  (5,  'DESK',            'Bàn làm việc','FURNITURE', 'desk',      TRUE, 0.0100, 5, TRUE),
  (6,  'FRIDGE',          'Tủ lạnh',     'FURNITURE', 'fridge',    TRUE, 0.0300, 6, TRUE),
  (7,  'WASHING_MACHINE', 'Máy giặt',    'FURNITURE', 'washer',    TRUE, 0.0300, 7, TRUE),
  (8,  'TV',              'Tivi',        'FURNITURE', 'tv',        TRUE, 0.0100, 8, TRUE),
  (9,  'KITCHEN_CABINET', 'Kệ bếp',      'FURNITURE', 'kitchen',   TRUE, 0.0200, 9, TRUE);

-- Nhóm SECURITY — An ninh (5)
INSERT INTO amenities (id, code, name, group_code, icon, is_filterable, price_impact_ratio, display_order, is_active) VALUES
  (10, 'SECURITY_GUARD',  'Bảo vệ 24/7',    'SECURITY', 'guard',       TRUE, 0.0500, 10, TRUE),
  (11, 'CCTV',            'Camera an ninh', 'SECURITY', 'cctv',        TRUE, 0.0300, 11, TRUE),
  (12, 'FINGERPRINT_LOCK','Khóa vân tay',   'SECURITY', 'fingerprint', TRUE, 0.0200, 12, TRUE),
  (13, 'FIRE_ALARM',      'Báo cháy / PCCC','SECURITY', 'fire',        TRUE, 0.0200, 13, TRUE),
  (14, 'PRIVATE_ENTRANCE','Lối đi riêng',   'SECURITY', 'entrance',    TRUE, 0.0200, 14, TRUE);

-- Nhóm UTILITY — Sinh hoạt (8)
INSERT INTO amenities (id, code, name, group_code, icon, is_filterable, price_impact_ratio, display_order, is_active) VALUES
  (15, 'BALCONY',          'Ban công',            'UTILITY', 'balcony', TRUE, 0.0400, 15, TRUE),
  (16, 'WINDOW',           'Cửa sổ thoáng',       'UTILITY', 'window',  TRUE, 0.0200, 16, TRUE),
  (17, 'MEZZANINE',        'Gác lửng',            'UTILITY', 'mezzanine',TRUE,0.0300, 17, TRUE),
  (18, 'PRIVATE_KITCHEN',  'Bếp riêng',           'UTILITY', 'cook',    TRUE, 0.0400, 18, TRUE),
  (19, 'WIFI',             'Wifi miễn phí',       'UTILITY', 'wifi',    TRUE, 0.0200, 19, TRUE),
  (20, 'ELECTRIC_METER',   'Điện nước giá dân',   'UTILITY', 'meter',   TRUE, 0.0300, 20, TRUE),
  (21, 'LAUNDRY_AREA',     'Sân phơi',            'UTILITY', 'laundry', TRUE, 0.0100, 21, TRUE),
  (22, 'PET_FRIENDLY_AREA','Khu vực cho thú cưng','UTILITY', 'pet',     FALSE,0.0100, 22, TRUE);

-- Nhóm TRANSPORT — Giao thông (7)
INSERT INTO amenities (id, code, name, group_code, icon, is_filterable, price_impact_ratio, display_order, is_active) VALUES
  (23, 'ELEVATOR',         'Thang máy',        'TRANSPORT', 'elevator', TRUE, 0.0700, 23, TRUE),
  (24, 'MOTORBIKE_PARKING','Chỗ để xe máy',    'TRANSPORT', 'motorbike',TRUE, 0.0500, 24, TRUE),
  (25, 'CAR_PARKING',      'Chỗ để ô tô',      'TRANSPORT', 'car',      TRUE, 0.0800, 25, TRUE),
  (26, 'STREET_FRONT',     'Mặt tiền đường',   'TRANSPORT', 'street',   TRUE, 0.1500, 26, TRUE),
  (27, 'NEAR_BUS_STOP',    'Gần trạm xe buýt', 'TRANSPORT', 'bus',      TRUE, 0.0200, 27, TRUE),
  (28, 'NEAR_MARKET',      'Gần chợ / siêu thị','TRANSPORT','market',   TRUE, 0.0200, 28, TRUE),
  (29, 'NEAR_UNIVERSITY',  'Gần trường đại học','TRANSPORT','school',   TRUE, 0.0300, 29, TRUE);
