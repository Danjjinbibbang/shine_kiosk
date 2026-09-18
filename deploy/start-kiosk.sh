#!/data/data/com.termux/files/usr/bin/sh
# Termux:Boot 가 부팅 시 실행하는 스크립트. ~/.termux/boot/ 에 복사해 둔다.
# 로그는 ~/kiosk/kiosk.log 에 쌓인다.

termux-wake-lock

KIOSK_HOME="$HOME/kiosk"
cd "$KIOSK_HOME" || exit 1

export KIOSK_DB="$KIOSK_HOME/data/kiosk.db"
# 운영 값은 ~/kiosk/env.sh 에 넣는다 (git 에 올리지 않음). 예:
#   export KIOSK_STAFF_PIN=4821
#   export KIOSK_BANK="국민 123-45-678901 (샤인교회)"
[ -f "$KIOSK_HOME/env.sh" ] && . "$KIOSK_HOME/env.sh"

# 이미 떠 있으면 다시 띄우지 않는다.
if pgrep -f kiosk-server.jar >/dev/null 2>&1; then
  exit 0
fi

exec java -Xmx512m -jar "$KIOSK_HOME/kiosk-server.jar" >> "$KIOSK_HOME/kiosk.log" 2>&1
