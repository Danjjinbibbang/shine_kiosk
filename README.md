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
나중에 추가된 컬럼은 `SchemaMigration` 이 기동 때 없으면 붙이므로, 기존 DB 를 지우지 않고 jar 만 바꿔도 된다.
메뉴 이름/가격/카테고리와 배달 장소는 `server/src/main/resources/data.sql` 이 기준이다. 고치고 재시작하면 id 기준으로 DB 에 덮어써진다.
메뉴를 잠시 빼고 싶으면 DB 에서 `menu_item.available` 또는 `delivery_place.active` 를 0 으로 (이 값은 시드가 건드리지 않는다).

## 테스트

```sh
# 서버 API 테스트 (JUnit + MockMvc, 임시 SQLite). 인증/메뉴/쿠폰/주문/스태프 처리 전 분기
./gradlew :server:test

# 브라우저 E2E (Playwright, 설치된 크롬 사용). jar 를 8091 에 임시 DB 로 띄우고 키오스크/스태프 화면을 실제로 조작
./gradlew bootJar && cd client && npm run e2e
```

E2E 는 태블릿(800×1333)과 폰(412×915) 뷰포트로 돈다. 실패하면 `client/test-results/` 에 스크린샷이 남는다.

## 설정 (PIN, 계좌)

실제 값은 **`config/application.yml`** 에 넣는다. git 에 올라가지 않는 파일이고, 서버를 실행하는 폴더의 `config/` 안에 있으면 자동으로 읽힌다.

```sh
cp config/application.example.yml config/application.yml   # 그 다음 값을 채운다
```

```yaml
kiosk:
  staff-pin: "0000"                            # 스태프 화면 PIN 4자리
  bank-account: "국민 123-45-678901 (샤인교회)"  # 계좌이체 화면 안내 문구
```

환경변수로도 줄 수 있고(파일보다 우선), 둘 다 없으면 `server/src/main/resources/application.yml` 의 기본값이 쓰인다.

| 설정 | 환경변수 | 기본값 | 설명 |
|---|---|---|---|
| `kiosk.staff-pin` | `KIOSK_STAFF_PIN` | `2580` | 스태프 화면 PIN — **운영 전에 반드시 바꿀 것** |
| `kiosk.bank-account` | `KIOSK_BANK` | (안내 문구) | 계좌이체 화면에 그대로 보임 |
| `kiosk.db-path` | `KIOSK_DB` | `./data/kiosk.db` | SQLite 파일 경로 |
| `server.port` | `KIOSK_PORT` | `8080` | 서버 포트 |

## 결제 규칙

- 결제 수단은 계좌이체 / 쿠폰 / 현금 세 가지.
- **쿠폰**은 선불 잔액. 주문 금액 **전액**이 잔액에서 빠지고, 잔액이 모자라면 잔액을 다 쓰고 나머지는 현금/계좌이체.
- 20,000원 충전마다 **무료 1잔**이 적립된다. 고객이 쿠폰 화면에서 "무료 1잔 쓰기"를 체크하면
  그 주문에서 가장 비싼 한 잔 값이 먼저 빠지고 나머지만 잔액에서 차감된다.
- 쿠폰 조회는 이름으로. 같은 이름이 둘 이상일 때만 전화번호 뒤 4자리를 묻는다.
- 배달은 **1층만** (식당/전도사님실). 2층 이상은 만드는 사람이 자리를 비워야 해서 하지 않는다.
- 계좌이체는 확인 절차 없이 계좌를 보여주고 다음으로 넘어간다 (신뢰 기반). 외상은 없다.
- 스태프가 주문을 취소하거나 수정하면 쿠폰 차감(무료 1잔 포함)은 자동으로 되돌리고 다시 계산한다.
- 잘못 등록/충전한 쿠폰은 스태프 화면에서 삭제하거나 잔액·무료잔 개수를 정정할 수 있다.

## 태블릿 배포 (Termux)

자세한 절차는 [deploy/README.md](deploy/README.md).
