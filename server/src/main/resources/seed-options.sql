-- 처음 설치할 때 한 번만 들어가는 기본 옵션. (menu_option 이 비어 있을 때만 — SeedData 참고)
INSERT INTO menu_option (id, name, price, category, sort_order) VALUES
    (1, '샷 추가', 500, '커피', 1),
    (2, '연하게',    0, '커피', 2);
