-- 메뉴/장소 원본. 이 파일이 기준이며 매 기동마다 id 로 UPSERT 되므로
-- 이름/카테고리/가격을 여기서 고치고 재시작하면 DB 에 반영된다.
-- (available/active 는 운영 중 DB 에서 끄고 켤 수 있도록 건드리지 않는다)

-- ── 커피 ──────────────────────────────────────────────
INSERT INTO menu_item (id, name, category, sort_order) VALUES
    (10, '아메리카노',     '커피', 10),
    (11, '라떼',           '커피', 20),
    (12, '바닐라라떼',     '커피', 30),
    (13, '아샷추',         '커피', 40),
    (14, '아이스크림 라떼', '커피', 50),
    (31, '아포카토',       '커피', 60)
ON CONFLICT(id) DO UPDATE SET name = excluded.name, category = excluded.category, sort_order = excluded.sort_order;

INSERT INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (1001, 10, 'ICE', 1000, 1),
    (1002, 10, 'HOT', 1000, 2),
    (1101, 11, 'ICE', 2000, 1),
    (1102, 11, 'HOT', 2000, 2),
    (1201, 12, 'ICE', 2500, 1),
    (1202, 12, 'HOT', 2500, 2),
    (1301, 13, NULL, 2500, 1),
    (1401, 14, NULL, 3000, 1),
    (3101, 31, NULL, 3000, 1)
ON CONFLICT(id) DO UPDATE SET menu_item_id = excluded.menu_item_id, label = excluded.label, price = excluded.price, sort_order = excluded.sort_order;

-- ── 논커피 ────────────────────────────────────────────
INSERT INTO menu_item (id, name, category, sort_order) VALUES
    (20, '초코라떼',       '논커피', 10),
    (21, '복숭아 아이스티', '논커피', 20),
    (22, '오미자',         '논커피', 30)
ON CONFLICT(id) DO UPDATE SET name = excluded.name, category = excluded.category, sort_order = excluded.sort_order;

INSERT INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (2001, 20, 'ICE', 2000, 1),
    (2002, 20, 'HOT', 2000, 2),
    (2101, 21, NULL, 2000, 1),
    (2201, 22, 'ICE', 2000, 1),
    (2202, 22, 'HOT', 2000, 2)
ON CONFLICT(id) DO UPDATE SET menu_item_id = excluded.menu_item_id, label = excluded.label, price = excluded.price, sort_order = excluded.sort_order;

-- ── 아이스크림 ────────────────────────────────────────
INSERT INTO menu_item (id, name, category, sort_order) VALUES
    (30, '아이스크림', '아이스크림', 10)
ON CONFLICT(id) DO UPDATE SET name = excluded.name, category = excluded.category, sort_order = excluded.sort_order;

INSERT INTO menu_variant (id, menu_item_id, label, price, sort_order) VALUES
    (3001, 30, '콘', 1000, 1),
    (3002, 30, '컵', 3000, 2)
ON CONFLICT(id) DO UPDATE SET menu_item_id = excluded.menu_item_id, label = excluded.label, price = excluded.price, sort_order = excluded.sort_order;

-- ── 배달 장소 ─────────────────────────────────────────
-- 배달은 1층만. 2층 이상은 만드는 사람이 자리를 비워야 해서 인원 절감 취지와 맞지 않는다.
INSERT INTO delivery_place (id, floor, name, sort_order) VALUES
    (101, 1, '식당',      1),
    (102, 1, '전도사님실', 2)
ON CONFLICT(id) DO UPDATE SET floor = excluded.floor, name = excluded.name, sort_order = excluded.sort_order;

-- 예전 시드에 있던 장소(2~4층)는 지난 주문이 참조하고 있을 수 있어 지우지 않고 화면에서만 뺀다.
UPDATE delivery_place SET active = 0 WHERE id NOT IN (101, 102);
