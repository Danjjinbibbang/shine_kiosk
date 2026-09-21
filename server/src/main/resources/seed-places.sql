-- 처음 설치할 때 한 번만 들어가는 배달 장소. (delivery_place 가 비어 있을 때만 — SeedData 참고)
-- 배달은 1층만. 2층 이상은 만드는 사람이 자리를 비워야 해서 인원 절감 취지와 맞지 않는다.
INSERT INTO delivery_place (id, floor, name, sort_order) VALUES
    (101, 1, '식당',      1),
    (102, 1, '전도사님실', 2);
