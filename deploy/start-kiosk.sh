#!/data/data/com.termux/files/usr/bin/sh
# Termux:Boot 가 부팅 시 실행하는 스크립트. ~/.termux/boot/ 에 복사해 둔다.
# 로그는 ~/kiosk/kiosk.log 에 쌓인다.

termux-wake-lock

KIOSK_HOME="$HOME/kiosk"
cd "$KIOSK_HOME" || exit 1

export KIOSK_DB="$KIOSK_HOME/data/kiosk.db"
# PIN/계좌 같은 운영 값은 ~/kiosk/config/application.yml 에 있다 (deploy/README.md 3번).
# 여기서 cd 해 두었으므로 Spring 이 ./config/application.yml 을 알아서 읽는다.
# 환경변수로 덮어쓰고 싶으면 ~/kiosk/env.sh 에 export 문을 두면 된다.
[ -f "$KIOSK_HOME/env.sh" ] && . "$KIOSK_HOME/env.sh"

# 이미 떠 있으면 다시 띄우지 않는다.
if pgrep -f kiosk-server.jar >/dev/null 2>&1; then
  exit 0
fi

exec java -Xmx512m -jar "$KIOSK_HOME/kiosk-server.jar" >> "$KIOSK_HOME/kiosk.log" 2>&1
