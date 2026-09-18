# shine_kiosk

교회 카페 주일 점심 셀프 주문 키오스크. 주문받는 사람 없이 만드는 사람 3명만으로 운영하기 위한 시스템.

- **고객 태블릿**: `http://localhost:8080/kiosk` — 메뉴 → 장바구니 → 받는 방법 → 결제 → 이름 → 완료
- **스태프 폰**: `http://<태블릿 IP>:8080/staff` — PIN 로그인 후 실시간 주문 목록 (완료/수정/취소), 쿠폰 등록/충전

## 구조

```
server/   Spring Boot 4 · Java 21 · SQLite (JdbcClient, JPA 없음) · WebSocket
client/   React 19 · Vite · TypeScript  (/kiosk, /staff 두 화면이 한 앱)
```

`./gradlew bootJar` 한 번이면 프론트를 빌드해서 jar 안에 넣고 `server/build/libs/kiosk-server.jar` 하나가 나온다.
태블릿에는 이 파일 하나만 옮기면 된다.

## 개발

```sh
# 서버 (8080)
./gradlew :server:bootRun

# 프론트 개발 서버 (5173, /api 와 /ws 는 8080 으로 프록시)
cd client && npm install && npm run dev
```

DB 파일은 `./data/kiosk.db` 에 자동 생성된다. 스키마/시드는 매 기동마다 실행되지만 멱등이라 데이터가 지워지지 않는다.
메뉴 가격을 바꾸려면 `server/src/main/resources/data.sql` 을 고치고 **기존 DB 의 해당 행도 직접 고치거나 DB 파일을 지운다**
(`INSERT OR IGNORE` 라 이미 있는 행은 덮어쓰지 않음).

## 설정 (환경변수)

| 변수 | 기본값 | 설명 |
|---|---|---|
| `KIOSK_PORT` | `8080` | 서버 포트 |
| `KIOSK_DB` | `./data/kiosk.db` | SQLite 파일 경로 |
| `KIOSK_STAFF_PIN` | `1234` | 스태프 화면 PIN 4자리 — **운영 전에 반드시 바꿀 것** |
| `KIOSK_BANK` | (안내 문구) | 계좌이체 화면에 그대로 보이는 계좌 안내. 예: `국민 123-45-678901 (샤인교회)` |

## 결제 규칙

- 결제 수단은 계좌이체 / 쿠폰 / 현금 세 가지.
- **쿠폰**은 선불 잔액(기본 20,000원 충전)이고 "한 잔 무료" 개념이라, 주문한 것 중 **가장 비싼 한 잔 값**이 차감된다.
  잔액이 그보다 적으면 잔액을 전부 쓰고 나머지는 현금/계좌이체로 받는다.
- 쿠폰 조회는 이름으로. 같은 이름이 둘 이상일 때만 전화번호 뒤 4자리를 묻는다.
- 계좌이체는 확인 절차 없이 계좌를 보여주고 다음으로 넘어간다 (신뢰 기반). 외상은 없다.
- 스태프가 주문을 취소하거나 수정하면 쿠폰 차감은 자동으로 되돌리고 다시 계산한다.

## 태블릿 배포 (Termux)

자세한 절차는 [deploy/README.md](deploy/README.md).
