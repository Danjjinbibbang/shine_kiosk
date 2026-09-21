-- 교회 카페 키오스크 스키마 (SQLite)
-- 매 기동 시 실행되므로 모든 DDL은 IF NOT EXISTS 로 작성한다.

CREATE TABLE IF NOT EXISTS menu_item (
    id          INTEGER PRIMARY KEY,
    name        TEXT    NOT NULL,
    category    TEXT    NOT NULL,              -- 커피 | 논커피 | 아이스크림
    sort_order  INTEGER NOT NULL DEFAULT 0,
    available   INTEGER NOT NULL DEFAULT 1
);

-- 한 메뉴의 선택지. label 이 NULL 이면 선택지 없는 단일 메뉴.
-- 예) 아메리카노 -> ICE / HOT,  아이스크림 -> 콘 / 컵
CREATE TABLE IF NOT EXISTS menu_variant (
    id           INTEGER PRIMARY KEY,
    menu_item_id INTEGER NOT NULL REFERENCES menu_item(id) ON DELETE CASCADE,
    label        TEXT,
    price        INTEGER NOT NULL,
    sort_order   INTEGER NOT NULL DEFAULT 0,
    available    INTEGER NOT NULL DEFAULT 1
);
CREATE INDEX IF NOT EXISTS idx_menu_variant_item ON menu_variant(menu_item_id);

-- 잔 단위 옵션 (샷 추가 +500, 연하게 0원). category 가 같은 메뉴에만 붙는다.
CREATE TABLE IF NOT EXISTS menu_option (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    price      INTEGER NOT NULL DEFAULT 0,
    category   TEXT    NOT NULL,              -- 이 카테고리 메뉴에만 표시
    option_group TEXT,                         -- 같은 그룹은 한 잔에 하나만 (샷 추가 / 연하게 = '농도')
    sort_order INTEGER NOT NULL DEFAULT 0,
    available  INTEGER NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS delivery_place (
    id         INTEGER PRIMARY KEY,
    floor      INTEGER NOT NULL,
    name       TEXT    NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    active     INTEGER NOT NULL DEFAULT 1
);

-- 선불 쿠폰. 잔액에서 주문 금액 전액이 차감되고,
-- 20,000원 충전마다 '무료 1잔' 이 적립되어 원하는 주문에서 가장 비싼 한 잔을 무료로 뺀다.
CREATE TABLE IF NOT EXISTS coupon (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT    NOT NULL,
    phone       TEXT,                          -- 전체 번호 (숫자만). 잔액 문자 발송용. 없으면 NULL
    phone_last4 TEXT,                          -- phone 의 뒤 4자리. 동명이인 구분용
    balance     INTEGER NOT NULL DEFAULT 0,
    free_drinks INTEGER NOT NULL DEFAULT 0,    -- 남은 무료 1잔 개수
    created_at  TEXT    NOT NULL,
    updated_at  TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_coupon_name ON coupon(name);

-- 쿠폰 변동 이력. 충전(+) / 사용(-) / 주문취소 복원(+) / 스태프 정정(±). 무료잔 개수 변동은 free_delta.
CREATE TABLE IF NOT EXISTS coupon_tx (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    coupon_id     INTEGER NOT NULL REFERENCES coupon(id),
    order_id      INTEGER,
    delta         INTEGER NOT NULL,
    free_delta    INTEGER NOT NULL DEFAULT 0,
    reason        TEXT    NOT NULL,            -- CHARGE | USE | REFUND | ADJUST
    balance_after INTEGER NOT NULL,
    created_at    TEXT    NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_coupon_tx_coupon ON coupon_tx(coupon_id);

-- 단골 명단. 주문할 때마다 갱신되어 이름 선택 버튼의 후보가 된다.
CREATE TABLE IF NOT EXISTS customer (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    name            TEXT    NOT NULL UNIQUE,
    order_count     INTEGER NOT NULL DEFAULT 0,
    last_ordered_at TEXT    NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    order_date      TEXT    NOT NULL,          -- YYYY-MM-DD
    order_no        INTEGER NOT NULL,          -- 당일 순번
    customer_name   TEXT    NOT NULL,
    receive_type    TEXT    NOT NULL,          -- STORE | DELIVERY
    place_id        INTEGER REFERENCES delivery_place(id),
    place_name      TEXT,                      -- 주문 시점 장소명 스냅샷
    total_amount    INTEGER NOT NULL,          -- 사역자 무료를 뺀, 실제로 받을 금액
    staff_free_amount INTEGER NOT NULL DEFAULT 0, -- 사역자 무료로 뺀 금액 (장바구니에서 잔마다 '사역자' 체크)
    pay_method      TEXT    NOT NULL,          -- TRANSFER | COUPON | CASH | NONE(전부 무료라 결제 없음)
    remainder_method TEXT,                     -- 쿠폰 잔액 부족 시 나머지 결제 수단 (CASH | TRANSFER)
    coupon_id       INTEGER REFERENCES coupon(id),
    coupon_amount   INTEGER NOT NULL DEFAULT 0, -- 잔액에서 차감한 금액
    free_amount     INTEGER NOT NULL DEFAULT 0, -- 무료 1잔으로 뺀 금액 (0 이면 안 씀)
    free_item_name  TEXT,                       -- 무료로 처리한 메뉴명 스냅샷
    cash_amount     INTEGER NOT NULL DEFAULT 0,
    transfer_amount INTEGER NOT NULL DEFAULT 0,
    status          TEXT    NOT NULL,          -- PENDING | DONE | CANCELED
    memo            TEXT,
    -- 스태프 수정: 언제, 이전엔 뭐였는지. 실제 받은 현금/이체(settled_*)와 현재 금액의 차이가 돌려주거나 더 받을 돈.
    edited_at       TEXT,
    edit_note       TEXT,
    settled_cash    INTEGER NOT NULL DEFAULT 0, -- 실제로 받은 현금 (정산 버튼을 누르면 현재 금액으로 맞춰짐)
    settled_transfer INTEGER NOT NULL DEFAULT 0,
    client_request_id TEXT,                    -- 키오스크가 붙인 요청 번호. 와이파이가 끊겨 다시 보내도 한 번만 접수
    created_at      TEXT    NOT NULL,
    completed_at    TEXT,
    canceled_at     TEXT
);
CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_date_no ON orders(order_date, order_no);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
CREATE INDEX IF NOT EXISTS idx_orders_date ON orders(order_date);
-- (client_request_id 의 UNIQUE 인덱스는 컬럼이 나중에 추가된 DB 도 있어서 SchemaMigration 이 만든다)

CREATE TABLE IF NOT EXISTS order_line (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id      INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    menu_item_id  INTEGER,
    variant_id    INTEGER,
    menu_name     TEXT    NOT NULL,            -- 주문 시점 스냅샷 (메뉴가 바뀌어도 영수증은 유지)
    variant_label TEXT,
    unit_price    INTEGER NOT NULL,
    quantity      INTEGER NOT NULL,
    staff_free_qty INTEGER NOT NULL DEFAULT 0  -- 이 중 사역자 무료 잔 수
);
CREATE INDEX IF NOT EXISTS idx_order_line_order ON order_line(order_id);

-- 주문 항목에 붙은 옵션 스냅샷. unit_price 에는 이미 옵션 가격이 더해져 있다.
CREATE TABLE IF NOT EXISTS order_line_option (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    order_line_id INTEGER NOT NULL REFERENCES order_line(id) ON DELETE CASCADE,
    option_id     INTEGER,
    name          TEXT    NOT NULL,
    price         INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_order_line_option_line ON order_line_option(order_line_id);
