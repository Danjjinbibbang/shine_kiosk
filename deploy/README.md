# 태블릿 배포 (Termux)

태블릿 한 대에서 서버가 돌고, 같은 교회 와이파이의 스태프 폰이 접속한다.

## 1. 앱 설치 (Play 스토어 버전 금지)

Play 스토어의 Termux 는 2020년에 멈춘 버전이라 패키지 설치가 안 된다.
**F-Droid** 또는 **GitHub 릴리스** 에서 받되, 본체와 애드온은 같은 곳에서 받아야 서로 연동된다.

- Termux 본체: https://github.com/termux/termux-app/releases
- Termux:Boot (부팅 시 자동 실행): https://github.com/termux/termux-boot/releases

설치 후 안드로이드 설정에서 두 앱 모두 **배터리 최적화 제외** 로 바꾼다. 안 하면 화면 꺼지고 얼마 뒤 서버가 죽는다.

## 2. Termux 초기 설정

```sh
pkg update && pkg upgrade -y
pkg install -y openjdk-21
termux-setup-storage      # 다운로드 폴더에서 jar 를 가져오기 위해
mkdir -p ~/kiosk/data ~/.termux/boot
```

## 3. jar 와 스크립트 옮기기

PC 에서 `./gradlew bootJar` 로 만든 `server/build/libs/kiosk-server.jar` 와
이 폴더의 `start-kiosk.sh` 를 태블릿 다운로드 폴더에 넣은 뒤:

```sh
cp ~/storage/downloads/kiosk-server.jar ~/kiosk/
cp ~/storage/downloads/start-kiosk.sh ~/.termux/boot/
chmod +x ~/.termux/boot/start-kiosk.sh
```

운영용 PIN 과 계좌 안내는 `~/kiosk/config/application.yml` 에 적는다 (레포의 `config/application.example.yml` 을 복사해서 채우면 된다):

```sh
mkdir -p ~/kiosk/config
cat > ~/kiosk/config/application.yml <<'YML'
kiosk:
  staff-pin: "0000"
  bank-account: "국민 123-45-678901 (열린교회)"
YML
```

## 4. 확인

```sh
~/.termux/boot/start-kiosk.sh &     # 처음엔 수동으로 한 번
tail -f ~/kiosk/kiosk.log            # "Started KioskServerApplication" 이 보이면 됨 (30~40초)
ifconfig wlan0 | grep inet           # 태블릿 IP 확인
```

- 태블릿 브라우저: `http://localhost:8080/kiosk` → 크롬 메뉴 "홈 화면에 추가" 로 전체화면 아이콘을 만들어 둔다.
- 스태프 폰: `http://<태블릿 IP>:8080/staff` → 같은 방법으로 홈 화면에 추가.

이후로는 태블릿을 껐다 켜면 Termux:Boot 가 스크립트를 실행해 서버가 알아서 뜬다.
Termux:Boot 는 **설치 후 한 번은 직접 열어줘야** 부팅 시 실행 권한이 잡힌다.

## 5. 업데이트

새 jar 를 `~/kiosk/kiosk-server.jar` 에 덮어쓰고 태블릿을 재부팅하거나:

```sh
pkill -f kiosk-server.jar; ~/.termux/boot/start-kiosk.sh &
```

DB(`~/kiosk/data/kiosk.db`)와 설정(`~/kiosk/config/application.yml`)은 그대로 남는다. 백업은 DB 파일 하나만 복사하면 된다.

## 자주 생기는 문제

| 증상 | 원인 / 해결 |
|---|---|
| 폰에서 접속이 안 됨 | 태블릿과 폰이 같은 와이파이인지, 와이파이의 AP 격리(클라이언트 간 통신 차단)가 꺼져 있는지 확인. IP 가 바뀌었을 수 있으니 `ifconfig` 다시 확인 |
| 몇 분 뒤 서버가 죽음 | 배터리 최적화 제외가 안 됨. `termux-wake-lock` 이 스크립트에 있는지 확인 |
| 스태프 화면이 PIN 을 다시 물음 | 서버가 재시작되면 토큰이 초기화된다. 정상 |
| 주문 목록이 안 바뀜 | 화면 위 "서버와 연결이 끊겼습니다" 띠가 있으면 와이파이 문제. 20초마다 자동 재시도한다 |
