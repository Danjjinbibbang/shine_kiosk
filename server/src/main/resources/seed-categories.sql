-- 처음 설치할 때 한 번만 들어가는 카테고리. (menu_category 가 비어 있을 때만 — SeedData 참고)
INSERT INTO menu_category (id, name, sort_order) VALUES
    (1, '커피', 10),
    (2, '논커피', 20),
    (3, '아이스크림', 30);
