-- 초기 데이터. INSERT OR IGNORE 이므로 재기동해도 중복되지 않고,
-- 운영 중 DB 에서 가격을 고쳐도 덮어쓰지 않는다.

-- ── 커피 ──────────────────────────────────────────────
INSERT OR IGNORE INTO menu_item (id, name, category, sort_order) VALUES
    (10, '아메리카노',     '커피', 10),
    (11, '라떼',           '커피', 20),
    (12, '바닐라라떼',     '커피', 30),
    (13, '아샷추',         '커피', 40),
    (14, '아이스크림 라떼', '커피', 50);

INSERT OR IGNORE INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (1001, 10, 'ICE', 1000, 1),
    (1002, 10, 'HOT', 1000, 2),
    (1101, 11, 'ICE', 2000, 1),
    (1102, 11, 'HOT', 2000, 2),
    (1201, 12, 'ICE', 2500, 1),
    (1202, 12, 'HOT', 2500, 2),
    (1301, 13, NULL, 2500, 1),
    (1401, 14, NULL, 3000, 1);

-- ── 논커피 ────────────────────────────────────────────
INSERT OR IGNORE INTO menu_item (id, name, category, sort_order) VALUES
    (20, '초코라떼',       '논커피', 10),
    (21, '복숭아 아이스티', '논커피', 20),
    (22, '오미자',         '논커피', 30);

INSERT OR IGNORE INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (2001, 20, 'ICE', 2000, 1),
    (2002, 20, 'HOT', 2000, 2),
    (2101, 21, NULL, 2000, 1),
    (2201, 22, 'ICE', 2000, 1),
    (2202, 22, 'HOT', 2000, 2);

-- ── 아이스크림 ────────────────────────────────────────
INSERT OR IGNORE INTO menu_item (id, name, category, sort_order) VALUES
    (30, '아이스크림', '아이스크림', 10),
    (31, '아포카토',   '아이스크림', 20);

INSERT OR IGNORE INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (3001, 30, '콘', 1000, 1),
    (3002, 30, '컵', 3000, 2),
    (3101, 31, NULL, 3000, 1);

-- ── 배달 장소 ─────────────────────────────────────────
INSERT OR IGNORE INTO delivery_place (id, floor, name, sort_order) VALUES
    (101, 1, '식당',      1),
    (102, 1, '전도사님실', 2),
    (201, 2, '1번방', 1),
    (202, 2, '2번방', 2),
    (203, 2, '3번방', 3),
    (204, 2, '4번방', 4),
    (205, 2, '5번방', 5),
    (206, 2, '6번방', 6),
    (207, 2, '7번방', 7),
    (208, 2, '8번방', 8),
    (301, 3, '유아실', 1),
    (401, 4, '4층',   1);
