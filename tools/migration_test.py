# -*- coding: utf-8 -*-
"""
업데이트 시나리오: 옛 버전(v0.1.1) jar 로 실데이터 비슷한 걸 만든 뒤, 같은 DB 에 새 jar 를 올려서
스키마 마이그레이션과 데이터/동작이 유지되는지 검사한다. 태블릿 업데이트 전에 돌려본다.

  gh release download v0.1.1 -p kiosk-server.jar -D /tmp/old
  python tools/migration_test.py /tmp/old/kiosk-server.jar server/build/libs/kiosk-server.jar /tmp/migwork
"""
import json, os, subprocess, sys, time, urllib.request, urllib.error, urllib.parse, sqlite3, shutil

OLD_JAR = sys.argv[1]
NEW_JAR = sys.argv[2]
S = os.path.abspath(sys.argv[3] if len(sys.argv) > 3 else 'build/migration-test')
os.makedirs(S, exist_ok=True)
DBDIR = os.path.join(S, 'migdb'); DB = os.path.join(DBDIR, 'kiosk.db')
PORT = 8093; BASE = f'http://localhost:{PORT}'
fails = []

def call(method, path, body=None, token=None):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + urllib.parse.quote(path, safe='/?=&'), data=data, method=method)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    if token: req.add_header('X-Staff-Token', token)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            b = r.read(); return r.status, (json.loads(b) if b else None)
    except urllib.error.HTTPError as e:
        b = e.read()
        try: return e.code, json.loads(b)
        except Exception: return e.code, {'message': b.decode('utf-8', 'replace')}

def start(jar, log):
    env = dict(os.environ, KIOSK_PORT=str(PORT), KIOSK_DB=DB, KIOSK_STAFF_PIN='1234', KIOSK_BACKUP_DIR=os.path.join(DBDIR, 'bk'))
    p = subprocess.Popen(['java', '-jar', jar], env=env, stdout=open(log, 'w'), stderr=subprocess.STDOUT)
    for _ in range(60):
        time.sleep(1)
        try:
            urllib.request.urlopen(BASE + '/api/menu', timeout=2); return p
        except Exception: pass
    p.kill(); raise SystemExit('server did not start: ' + log)

def stop(p):
    p.terminate()
    try: p.wait(10)
    except Exception: p.kill()
    time.sleep(1)

def check(cond, what):
    print(('  ok   ' if cond else '  FAIL ') + what)
    if not cond: fails.append(what)

shutil.rmtree(DBDIR, ignore_errors=True); os.makedirs(DBDIR)

# ── 1. 옛 버전으로 데이터 만들기 ──
print('[v0.1.1] 데이터 만들기')
p = start(OLD_JAR, os.path.join(S, 'mig_old.log'))
s, j = call('POST', '/api/staff-auth/login', {'pin': '1234'}); tok = j['token']
s, c1 = call('POST', '/api/staff/coupons', {'name': '이영희', 'phoneLast4': '1111', 'amount': 20000}, tok); check(s == 200, f'옛 쿠폰 등록(뒤4자리) {c1}')
s, c2 = call('POST', '/api/staff/coupons', {'name': '박철수', 'amount': 50000}, tok); check(s == 200, f'옛 쿠폰 등록(번호 없음) {c2}')
s, c3 = call('POST', '/api/staff/coupons', {'name': '이영희', 'phoneLast4': '2222', 'amount': 5000}, tok); check(s == 200, '옛 동명이인 쿠폰')
call('POST', f"/api/staff/coupons/{c2['id']}/charge", {'amount': 15000}, tok)
def line(v, q=1): return {'variantId': v, 'quantity': q}
s, o1 = call('POST', '/api/orders', {'customerName': '김현금', 'receiveType': 'STORE', 'payMethod': 'CASH', 'lines': [line(1001, 2)], 'memo': '현금 5,000원 받음 → 거스름돈 3,000원'}); check(s == 200, '옛 현금 주문 (옛 방식 메모)')
s, o2 = call('POST', '/api/orders', {'customerName': '이영희', 'receiveType': 'DELIVERY', 'placeId': 101, 'payMethod': 'COUPON', 'couponId': c1['id'], 'useFreeDrink': True, 'lines': [line(3002, 1), line(1101, 1)]}); check(s == 200, f'옛 쿠폰 주문(무료 1잔) {o2.get("message")}')
s, o3 = call('POST', '/api/orders', {'customerName': '김이체', 'receiveType': 'STORE', 'payMethod': 'TRANSFER', 'lines': [line(2001, 1)]}); check(s == 200, '옛 이체 주문')
call('POST', f"/api/staff/orders/{o3['id']}/done", None, tok)
s, old_orders = call('GET', '/api/staff/orders', None, tok)
s, old_done = call('GET', '/api/staff/orders?status=DONE', None, tok)
s, old_c1 = call('POST', '/api/coupons/lookup', {'name': '이영희', 'phoneLast4': '1111'})
old_balance = old_c1['coupon']['balance']; old_free = old_c1['coupon']['freeDrinks']
print(f'  옛 상태: 대기 {len(old_orders)}건, 완료 {len(old_done)}건, 이영희(1111) 잔액 {old_balance} 무료 {old_free}')
stop(p)

con = sqlite3.connect(DB)
old_cols = {t: [r[1] for r in con.execute(f'PRAGMA table_info({t})')] for t in ('coupon', 'orders', 'menu_option')}
check('phone_last4' in old_cols['coupon'] and 'phone' not in old_cols['coupon'], '옛 스키마: coupon.phone_last4 있고 phone 없음')
check('settled_cash' not in old_cols['orders'], '옛 스키마: orders.settled_cash 없음')
con.close()
shutil.copy(DB, DB + '.before-migration')

# ── 2. 새 jar 로 같은 DB 열기 ──
print('[새 jar] 같은 DB 로 기동')
p = start(NEW_JAR, os.path.join(S, 'mig_new.log'))
con = sqlite3.connect(DB)
cols = {t: [r[1] for r in con.execute(f'PRAGMA table_info({t})')] for t in ('coupon', 'orders', 'menu_option', 'coupon_tx', 'order_line', 'menu_category')}
check('phone' in cols['coupon'] and 'phone_last4' not in cols['coupon'], 'coupon: phone 추가, phone_last4 제거')
check(all(c in cols['orders'] for c in ('settled_cash', 'settled_transfer', 'edited_at', 'edit_note', 'client_request_id', 'free_amount', 'staff_free_amount')), 'orders 새 컬럼')
check('option_group' in cols['menu_option'], 'menu_option.option_group')
check('staff_free_qty' in cols['order_line'], 'order_line.staff_free_qty')
cats = [r[0] for r in con.execute('SELECT name FROM menu_category ORDER BY sort_order')]
check(cats == ['커피', '논커피', '아이스크림'], f'카테고리 표가 기존 메뉴에서 만들어짐 {cats}')
groups = dict(con.execute('SELECT name, option_group FROM menu_option'))
check(groups.get('샷 추가') == '농도' and groups.get('연하게') == '농도', f'샷 추가/연하게 → 농도 그룹 {groups}')
settled = list(con.execute('SELECT id, cash_amount, settled_cash, transfer_amount, settled_transfer FROM orders'))
check(all(r[1] == r[2] and r[3] == r[4] for r in settled), f'옛 주문의 settled 가 받은 금액으로 채워짐 {settled}')
con.close()

s, j = call('POST', '/api/staff-auth/login', {'pin': '1234'}); tok = j['token']
s, orders = call('GET', '/api/staff/orders', None, tok)
check(s == 200 and len(orders) == len(old_orders), f'대기 주문 {len(orders)}건 유지')
s, done = call('GET', '/api/staff/orders?status=DONE', None, tok)
check(len(done) == len(old_done), f'완료 주문 {len(done)}건 유지')
o2n = next(o for o in orders if o['id'] == o2['id'])
check(o2n['freeAmount'] == 3000 and o2n['freeItemName'] and o2n['couponAmount'] == 2000, f'옛 쿠폰 주문의 무료 1잔/쿠폰액 표시 {o2n.get("freeAmount")} {o2n.get("freeItemName")} {o2n.get("couponAmount")}')
check(o2n['placeName'] == '식당', '배달 장소 유지')
s, cs = call('GET', '/api/staff/coupons?name=이영희', None, tok)
check(len(cs) == 2, f'동명이인 쿠폰 2개 유지 {[(c["name"], c.get("phone"), c.get("phoneLast4")) for c in cs]}')
c1n = next(c for c in cs if c['id'] == c1['id'])
check(c1n['balance'] == old_balance and c1n['freeDrinks'] == old_free, f'잔액/무료잔 유지 {c1n["balance"]}/{c1n["freeDrinks"]}')
check(c1n.get('phone') is None, '옛 쿠폰은 phone 없음 (뒤 4자리는 삭제됨 — 번호 다시 넣어야)')
s, k = call('POST', '/api/coupons/lookup', {'name': '박철수'})
check(k['status'] == 'FOUND' and k['coupon']['balance'] == 65000, f'번호 없는 옛 쿠폰 키오스크 조회 {k}')
s, k2 = call('POST', '/api/coupons/lookup', {'name': '이영희'})
check(k2['status'] == 'NEED_PHONE' and len(k2['candidates']) == 2, f'옛 동명이인: 후보 목록 {k2}')
check(all(c.get('phoneLast4') is None for c in k2['candidates']), '[주의] 옛 동명이인 후보의 뒤 4자리 없음 → 키오스크에서 고를 수 없음, 번호 등록 필요')
s, ch = call('POST', f"/api/staff/coupons/{c2['id']}/charge", {'amount': 10000}, tok)
check(s == 400 and '전화번호' in ch.get('message', ''), f'번호 없는 옛 쿠폰 충전 잠김: {ch.get("message")}')
s, ph = call('PUT', f"/api/staff/coupons/{c2['id']}/phone", {'phone': '010-9999-8888'}, tok); check(s == 200, '번호 넣기')
s, ch = call('POST', f"/api/staff/coupons/{c2['id']}/charge", {'amount': 20000}, tok); check(s == 200 and ch['freeDrinks'] == 3, f'번호 넣은 뒤 충전 OK, 무료 +1 → {ch.get("freeDrinks")}')
s, h = call('GET', f"/api/staff/coupons/{c2['id']}/history", None, tok); check(len(h) == 3 and all('freeDelta' in x for x in h), f'옛 이력 + 새 이력 {len(h)}줄')
# 옛 주문 수정/정산/취소가 되는지
s, u = call('PUT', f"/api/staff/orders/{o1['id']}", {'customerName': '김현금', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(1001, 1)], 'memo': None}, tok)
check(s == 200 and u['settledCash'] == 2000 and u['cashAmount'] == 1000, f'옛 현금 주문 수정 → 돌려줄 1,000 {u.get("settledCash")}/{u.get("cashAmount")}')
check(u.get('cashGiven') == 5000 and u.get('payNote') == '현금 5,000원 받음 → 거스름돈 4,000원' and u.get('memo') is None, f'옛 현금 메모가 낸 돈으로 옮겨지고 수정 뒤 거스름돈이 다시 계산됨: {u.get("cashGiven")} / {u.get("payNote")} / memo={u.get("memo")}')
s, _ = call('POST', f"/api/staff/orders/{o1['id']}/done", None, tok); check(s == 400, '정산 전 완료 불가')
s, _ = call('POST', f"/api/staff/orders/{o1['id']}/settle", {}, tok); check(s == 200, '현금 정산')
s, _ = call('POST', f"/api/staff/orders/{o1['id']}/done", None, tok); check(s == 200, '완료')
s, _ = call('POST', f"/api/staff/orders/{o2['id']}/cancel", {}, tok); check(s == 200, '옛 쿠폰 주문 취소')
s, c1b = call('GET', f"/api/staff/coupons/{c1['id']}", None, tok); check(c1b['balance'] == 20000 and c1b['freeDrinks'] == 1, f'취소 후 잔액/무료잔 복원 {c1b["balance"]}/{c1b["freeDrinks"]}')
s, o4 = call('POST', '/api/orders', {'customerName': '새손님', 'receiveType': 'STORE', 'payMethod': 'CASH', 'lines': [{'variantId': 1001, 'quantity': 1, 'optionIds': [1]}], 'clientRequestId': 'mig-1'})
check(s == 200 and o4['totalAmount'] == 1500 and o4['orderNo'] == 4, f'새 주문 번호 이어짐 #{o4.get("orderNo")} 옵션 가격 {o4.get("totalAmount")}')
s, days = call('GET', '/api/staff/reports/days', None, tok); check(len(days) == 1 and days[0]['orderCount'] == 3, f'매출 집계 {days}')
s, sm = call('GET', '/api/staff/orders/summary', None, tok); check(sm['orderCount'] == 3, f'오늘 집계 취소 제외 {sm}')
bk = os.listdir(os.path.join(DBDIR, 'bk')) if os.path.isdir(os.path.join(DBDIR, 'bk')) else []
check(any(f.startswith('kiosk-backup-') for f in bk), f'기동 시 자동 백업 {bk}')
stop(p)

# ── 3. 새 jar 두 번째 기동 (마이그레이션이 다시 돌아도 문제 없어야) ──
print('[새 jar] 재기동')
p = start(NEW_JAR, os.path.join(S, 'mig_new2.log'))
s, j = call('POST', '/api/staff-auth/login', {'pin': '1234'}); tok = j['token']
s, orders = call('GET', '/api/staff/orders', None, tok); check(s == 200 and len(orders) == 1, f'재기동 후 대기 {len(orders)}건 (새손님)')
s, cs = call('GET', '/api/staff/coupons?name=박철수', None, tok); check(cs[0]['balance'] == 85000 and cs[0]['phone'] == '01099998888', f'재기동 후 쿠폰 유지 {cs}')
log = open(os.path.join(S, 'mig_new2.log'), encoding='utf-8', errors='replace').read()
check('ERROR' not in log and 'Exception' not in log, '재기동 로그에 오류 없음')
stop(p)

print()
print('FAILS:', fails if fails else '없음')
sys.exit(1 if fails else 0)
