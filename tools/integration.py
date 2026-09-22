# -*- coding: utf-8 -*-
"""
실제 서버에 대고 도는 통합 시나리오 (34 케이스). 키오스크 주문 → 스태프 처리 → 쿠폰/매출까지 한 흐름으로 검사한다.

주문과 쿠폰을 실제로 만들므로 반드시 빈 DB 로 띄운 서버에 돌린다 (실서비스 DB 금지):
  KIOSK_PORT=8092 KIOSK_DB=/tmp/it/kiosk.db KIOSK_STAFF_PIN=1234 KIOSK_BACKUP_DIR=/tmp/it/bk java -jar server/build/libs/kiosk-server.jar &
  python tools/integration.py http://localhost:8092 1234
같은 DB 에 두 번 돌리면 주문 번호·동일 번호 쿠폰 때문에 실패한다.
"""
import json, sys, urllib.request, urllib.error, uuid, csv, io

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:8092'
PIN = sys.argv[2] if len(sys.argv) > 2 else '1234'
TOKEN = None
results = []   # (group, name, ok, detail)

def call(method, path, body=None, staff=False, raw=False):
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode('utf-8')
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header('Content-Type', 'application/json; charset=utf-8')
    if staff and TOKEN: req.add_header('X-Staff-Token', TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            b = r.read()
            if raw: return r.status, b
            return r.status, (json.loads(b.decode('utf-8')) if b else None)
    except urllib.error.HTTPError as e:
        b = e.read()
        try: j = json.loads(b.decode('utf-8'))
        except Exception: j = {'message': b.decode('utf-8', 'replace')}
        return e.code, j

def msg(j): return (j or {}).get('message', '') if isinstance(j, dict) else ''

class Case:
    def __init__(self, group, name):
        self.group, self.name, self.fails = group, name, []
    def check(self, cond, what):
        if not cond: self.fails.append(what)
        return cond
    def eq(self, actual, expected, what):
        return self.check(actual == expected, f'{what}: expected {expected!r}, got {actual!r}')
    def contains(self, text, part, what):
        return self.check(part in (text or ''), f'{what}: {part!r} not in {text!r}')
    def __enter__(self): return self
    def __exit__(self, et, ev, tb):
        if et: self.fails.append(f'EXCEPTION {et.__name__}: {ev}')
        results.append((self.group, self.name, not self.fails, '; '.join(self.fails)))
        return True

# ── 도우미 ──
def login():
    global TOKEN
    s, j = call('POST', '/api/staff-auth/login', {'pin': PIN}); assert s == 200, j
    TOKEN = j['token']

def line(vid, qty=1, opts=None, staff_free=0):
    return {'variantId': vid, 'quantity': qty, 'optionIds': opts or [], 'staffFreeQty': staff_free}

def order(name, lines, pay='CASH', receive='STORE', place=None, coupon=None, free=None, remainder=None, memo=None, rid=None, cash_given=None):
    body = {'customerName': name, 'receiveType': receive, 'placeId': place, 'payMethod': pay, 'couponId': coupon,
            'useFreeDrink': free, 'remainderMethod': remainder, 'lines': lines, 'memo': memo, 'cashGiven': cash_given,
            'clientRequestId': rid or str(uuid.uuid4())}
    return call('POST', '/api/orders', body)

def preview(coupon, lines, free=False):
    return call('POST', '/api/orders/coupon-preview', {'couponId': coupon, 'useFreeDrink': free, 'lines': lines})

_phone = 1000
def coupon(name, amount=20000, phone=None):
    global _phone
    _phone += 1
    s, j = call('POST', '/api/staff/coupons', {'name': name, 'amount': amount, 'phone': phone or f'0109{_phone:07d}'}, staff=True)
    assert s == 200, j
    return j

def get_coupon(cid): return call('GET', f'/api/staff/coupons/{cid}', staff=True)[1]
def get_order(oid):
    s, j = call('GET', '/api/staff/orders', staff=True)
    for o in j:
        if o['id'] == oid: return o
    for st in ('DONE', 'CANCELED'):
        s, j = call('GET', f'/api/staff/orders?status={st}', staff=True)
        for o in (j or []):
            if o['id'] == oid: return o
    return None

def act(oid, action, body=None):
    """done/settle/reopen/cancel 은 본문 없이 끝난다 → 상태와 다시 읽은 주문을 돌려준다"""
    s, j = staff('POST', f'/api/staff/orders/{oid}/{action}', body)
    return s, (get_order(oid) if s == 200 else j)
def staff(method, path, body=None): return call(method, path, body, staff=True)
def summary(): return call('GET', '/api/staff/orders/summary', staff=True)[1]

AME_ICE, AME_HOT, LATTE_ICE, VAN_ICE, ICE_LATTE, AFFO = 1001, 1002, 1101, 1201, 1401, 3101
CHOCO, PEACH, CONE, CUP = 2001, 2101, 3001, 3002
SHOT, MILD = 1, 2
PLACE_DINING = 101

# ══════════════════════════════════════════════════════════════════
login()
s, j = call('GET', '/api/menu')
assert s == 200 and j, '메뉴 없음'

# ── A. 결제 수단별 기본 주문 ─────────────────────────────────────
with Case('A 결제', '현금 · 매장 · 2잔 → 현금 금액 = 합계, 정산 완료 상태로 시작') as c:
    s, o = order('김현금', [line(AME_ICE, 2)], 'CASH')
    c.eq(s, 200, 'status'); c.eq(o['totalAmount'], 2000, 'total'); c.eq(o['cashAmount'], 2000, 'cash')
    c.eq(o['settledCash'], 2000, 'settledCash'); c.eq(o['status'], 'PENDING', 'status'); c.eq(o['orderNo'], 1, 'orderNo')

with Case('A 결제', '계좌이체 · 배달(1층 식당) → placeName, transferAmount') as c:
    s, o = order('김이체', [line(LATTE_ICE)], 'TRANSFER', 'DELIVERY', PLACE_DINING)
    c.eq(s, 200, msg(o)); c.eq(o['placeName'], '식당', 'place'); c.eq(o['transferAmount'], 2000, 'transfer'); c.eq(o['orderNo'], 2, 'orderNo')

with Case('A 결제', '배달인데 장소 없음 → 거부') as c:
    s, o = order('김배달', [line(LATTE_ICE)], 'CASH', 'DELIVERY', None)
    c.eq(s, 400, 'status')

with Case('A 결제', '없는 메뉴/품절 메뉴 주문 → 거부') as c:
    s, o = order('김없음', [line(99999)], 'CASH'); c.eq(s, 400, 'unknown variant')
    staff('PUT', '/api/staff/menu/30/available', {'available': False})   # 아이스크림 품절
    s, o = order('김품절', [line(CONE)], 'CASH'); c.eq(s, 400, 'sold out')
    staff('PUT', '/api/staff/menu/30/available', {'available': True})
    s, o = order('김재개', [line(CONE)], 'CASH'); c.eq(s, 200, 'restored')

with Case('A 결제', '옵션: 샷 추가 +500 은 한 잔 값에 포함, 논커피에 커피 옵션 → 거부, 샷추가+연하게 동시 → 거부') as c:
    s, o = order('김옵션', [line(AME_ICE, 2, [SHOT])], 'CASH')
    c.eq(s, 200, msg(o)); c.eq(o['totalAmount'], 3000, 'total'); c.eq(o['lines'][0]['unitPrice'], 1500, 'unit')
    s, o = order('김옵션', [line(CHOCO, 1, [SHOT])], 'CASH'); c.eq(s, 400, 'option on non-coffee')
    s, o = order('김옵션', [line(AME_ICE, 1, [SHOT, MILD])], 'CASH'); c.eq(s, 400, 'both in group')

with Case('A 결제', '같은 요청번호 두 번 → 주문 하나만 (멱등)') as c:
    rid = str(uuid.uuid4())
    s1, o1 = order('김중복', [line(AME_ICE)], 'CASH', rid=rid)
    s2, o2 = order('김중복', [line(AME_ICE)], 'CASH', rid=rid)
    c.eq(s2, 200, 'second'); c.eq(o2['id'], o1['id'], 'same id')

# ── B. 사역자 무료 ───────────────────────────────────────────────
with Case('B 사역자', '3잔 중 2잔 사역자 → 1잔만 현금, staffFreeAmount') as c:
    s, o = order('전도사', [line(VAN_ICE, 3, [], staff_free=2)], 'CASH')
    c.eq(s, 200, msg(o)); c.eq(o['totalAmount'], 2500, 'total'); c.eq(o['staffFreeAmount'], 5000, 'staffFree'); c.eq(o['cashAmount'], 2500, 'cash')

with Case('B 사역자', '전부 사역자 → NONE 으로 결제 없음, 현금으로 0원 주문은 거부') as c:
    s, o = order('목사님', [line(AME_HOT, 1, [], staff_free=1)], 'NONE')
    c.eq(s, 200, msg(o)); c.eq(o['totalAmount'], 0, 'total'); c.eq(o['payMethod'], 'NONE', 'pay')
    s, o = order('목사님', [line(AME_HOT, 1)], 'NONE'); c.eq(s, 400, 'NONE with amount')

with Case('B 사역자', '사역자 잔 수 > 수량 → 거부') as c:
    s, o = order('전도사', [line(AME_HOT, 1, [], staff_free=2)], 'CASH'); c.eq(s, 400, 'status')

# ── C. 쿠폰 ─────────────────────────────────────────────────────
with Case('C 쿠폰', '등록 20,000 → 무료 1잔 / 30,000 → 무료 2잔 / 30,001 → 거부 / 번호 없음 → 거부') as c:
    a = coupon('이영희', 20000); c.eq(a['freeDrinks'], 1, '20k free'); c.eq(a['balance'], 20000, 'bal')
    b = coupon('박철수', 30000); c.eq(b['freeDrinks'], 2, '30k free')
    s, j = staff('POST', '/api/staff/coupons', {'name': '최상한', 'amount': 30001, 'phone': '01011112222'}); c.eq(s, 400, 'over max'); c.contains(msg(j), '30,000', 'msg')
    s, j = staff('POST', '/api/staff/coupons', {'name': '최번호', 'amount': 20000}); c.eq(s, 400, 'no phone')
    s, j = staff('POST', '/api/staff/coupons', {'name': '최번호', 'amount': 20000, 'phone': '02-123-4567'}); c.eq(s, 400, 'landline'); c.contains(msg(j), '휴대폰', 'msg')

with Case('C 쿠폰', '전액 차감 + 무료 1잔: 라떼 2,000 + 아메 샷추가 1,500 → 무료는 가장 비싼 라떼, 잔액 20,000-1,500') as c:
    cp = coupon('이영희', 20000)
    lines = [line(LATTE_ICE), line(AME_ICE, 1, [SHOT])]
    s, p = preview(cp['id'], lines, True)
    c.eq(s, 200, msg(p)); c.eq(p['freeAmount'], 2000, 'free'); c.eq(p['freeItemName'], '라떼 ICE', 'freeName'); c.eq(p['couponAmount'], 1500, 'couponAmt'); c.eq(p['remainder'], 0, 'rem')
    s, o = order('이영희', lines, 'COUPON', coupon=cp['id'], free=True)
    c.eq(s, 200, msg(o)); c.eq(o['couponAmount'], 1500, 'order couponAmt'); c.eq(o['freeAmount'], 2000, 'order free')
    cp2 = get_coupon(cp['id']); c.eq(cp2['balance'], 18500, 'balance'); c.eq(cp2['freeDrinks'], 0, 'freeDrinks')
    # 쿠폰 주문은 이름이 쿠폰 주인
    c.eq(o['customerName'], '이영희', 'name')

with Case('C 쿠폰', '무료잔 없는데 useFreeDrink → 거부') as c:
    cp = coupon('김무료', 10000)   # 무료잔 0
    s, o = order('김무료', [line(AME_ICE)], 'COUPON', coupon=cp['id'], free=True); c.eq(s, 400, 'status'); c.contains(msg(o), '무료', 'msg')

with Case('C 쿠폰', '잔액 부족: 3,000 남았는데 5,000 주문 → 3,000 쿠폰 + 2,000 현금, 메모에 거스름돈 안내') as c:
    cp = coupon('김부족', 3000)
    lines = [line(AFFO), line(LATTE_ICE)]
    s, p = preview(cp['id'], lines); c.eq(p['remainder'], 2000, 'remainder')
    s, o = order('김부족', lines, 'COUPON', coupon=cp['id'], remainder='CASH')
    c.eq(s, 200, msg(o)); c.eq(o['couponAmount'], 3000, 'coupon'); c.eq(o['cashAmount'], 2000, 'cash'); c.eq(o['remainderMethod'], 'CASH', 'rem')
    c.eq(get_coupon(cp['id'])['balance'], 0, 'balance 0')
    # 잔액 0 인 쿠폰으로 remainder 없이 주문 → 거부
    s, o2 = order('김부족', [line(AME_ICE)], 'COUPON', coupon=cp['id']); c.eq(s, 400, 'no remainder')

with Case('C 쿠폰', '잔액 부족 나머지를 계좌이체로') as c:
    cp = coupon('김이체', 1000)
    s, o = order('김이체', [line(LATTE_ICE)], 'COUPON', coupon=cp['id'], remainder='TRANSFER')
    c.eq(s, 200, msg(o)); c.eq(o['couponAmount'], 1000, 'coupon'); c.eq(o['transferAmount'], 1000, 'transfer')

with Case('C 쿠폰', '키오스크 조회: 이름만 → FOUND, 동명이인 → NEED_PHONE → 뒤4자리 → FOUND, 공개 응답엔 전화번호 없음') as c:
    coupon('동명이', 20000, '01011110001'); coupon('동명이', 20000, '01011110002')
    s, j = call('POST', '/api/coupons/lookup', {'name': '동명이'}); c.eq(j['status'], 'NEED_PHONE', 'need'); c.eq(j['candidateCount'], 2, 'count')
    s, j = call('POST', '/api/coupons/lookup', {'name': '동명이', 'phoneLast4': '0002'}); c.eq(j['status'], 'FOUND', 'found'); c.check('phone' not in j['coupon'], 'phone leaked'); c.eq(j['coupon']['phoneLast4'], '0002', 'last4')
    s, j = call('POST', '/api/coupons/lookup', {'name': '동명이', 'phoneLast4': '9999'}); c.eq(j['status'], 'NOT_FOUND', 'wrong last4')
    s, j = call('POST', '/api/coupons/lookup', {'name': '없는사람'}); c.eq(j['status'], 'NOT_FOUND', 'none')

with Case('C 쿠폰', '충전 누적: 20,000 등록 + 10,000 충전(무료 없음) + 30,000 충전(+2) = 60,000 / 3잔, 이력 3줄') as c:
    cp = coupon('김충전', 20000)
    s, j = staff('POST', f"/api/staff/coupons/{cp['id']}/charge", {'amount': 10000}); c.eq(j['freeDrinks'], 1, 'after 10k')
    s, j = staff('POST', f"/api/staff/coupons/{cp['id']}/charge", {'amount': 30000}); c.eq(j['balance'], 60000, 'bal'); c.eq(j['freeDrinks'], 3, 'free')
    s, h = staff('GET', f"/api/staff/coupons/{cp['id']}/history"); c.eq(len(h), 3, 'history rows'); c.check(all(x['reason'] == 'CHARGE' for x in h), 'reasons')

with Case('C 쿠폰', '번호 변경 / 이름 변경 / 정정 / 사용된 쿠폰 삭제 불가 / 안 쓴 쿠폰 삭제') as c:
    cp = coupon('김수정', 20000)
    s, j = staff('PUT', f"/api/staff/coupons/{cp['id']}/phone", {'phone': '010-5555-6666'}); c.eq(j['phone'], '01055556666', 'phone')
    s, j = staff('PUT', f"/api/staff/coupons/{cp['id']}/name", {'name': '김수정됨'}); c.eq(j['name'], '김수정됨', 'name')
    s, j = staff('POST', f"/api/staff/coupons/{cp['id']}/adjust", {'balance': 15000, 'freeDrinks': 0}); c.eq(j['balance'], 15000, 'adjust')
    s, o = order('김수정됨', [line(AME_ICE)], 'COUPON', coupon=cp['id']); c.eq(s, 200, msg(o))
    s, j = staff('DELETE', f"/api/staff/coupons/{cp['id']}"); c.eq(s, 400, 'delete used')
    cp2 = coupon('김삭제', 20000)
    s, j = staff('DELETE', f"/api/staff/coupons/{cp2['id']}"); c.eq(s, 200, 'delete unused')
    s, j = staff('GET', f"/api/staff/coupons/{cp2['id']}"); c.eq(s, 400, 'gone')

# ── D. 스태프 처리: 완료/되돌리기/취소 ───────────────────────────
with Case('D 처리', '현금 주문 완료 → DONE 목록, 되돌리기 → PENDING') as c:
    s, o = order('김완료', [line(AME_ICE)], 'CASH')
    s, d = act(o['id'], 'done'); c.eq(d['status'], 'DONE', 'done'); c.check(d.get('completedAt'), 'completedAt')
    s, lst = staff('GET', '/api/staff/orders?status=DONE'); c.check(any(x['id'] == o['id'] for x in lst), 'in DONE list')
    s, r = act(o['id'], 'reopen'); c.eq(r['status'], 'PENDING', 'reopen')

with Case('D 처리', '쿠폰 주문 취소 → 잔액·무료잔 복구, 이력에 REFUND') as c:
    cp = coupon('김취소', 20000)
    s, o = order('김취소', [line(LATTE_ICE), line(AME_ICE)], 'COUPON', coupon=cp['id'], free=True)
    c.eq(get_coupon(cp['id'])['balance'], 19000, 'after order')
    s, x = act(o['id'], 'cancel', {}); c.eq(x['status'], 'CANCELED', 'canceled')
    after = get_coupon(cp['id']); c.eq(after['balance'], 20000, 'balance back'); c.eq(after['freeDrinks'], 1, 'free back')
    s, h = staff('GET', f"/api/staff/coupons/{cp['id']}/history"); c.check(any(t['reason'] == 'REFUND' for t in h), 'REFUND row')
    s, y = act(o['id'], 'done'); c.eq(s, 400, 'done on canceled')

with Case('D 처리', '현금 주문 취소하면서 받은 돈을 쿠폰에 넣기 → 쿠폰 잔액 +, settled 0') as c:
    cp = coupon('김환불', 20000)
    s, o = order('김환불', [line(AFFO)], 'CASH')
    s, x = act(o['id'], 'cancel', {'refundToCouponId': cp['id']}); c.eq(s, 200, msg(x))
    c.eq(get_coupon(cp['id'])['balance'], 23000, 'coupon +3000'); c.eq(x['settledCash'], 0, 'settled 0')

with Case('D 처리', '쿠폰+현금 주문 취소 시 받은 현금을 다른 사람 쿠폰엔 못 넣고, 주문 쿠폰엔 넣는다') as c:
    a = coupon('김주인', 1000); b = coupon('김남', 20000)
    s, o = order('김주인', [line(LATTE_ICE)], 'COUPON', coupon=a['id'], remainder='CASH')   # 쿠폰 1000 + 현금 1000
    s, x = act(o['id'], 'cancel', {'refundToCouponId': b['id']}); c.eq(s, 400, 'other coupon'); c.contains(msg(x), '이 주문에 쓴 쿠폰', 'msg')
    c.eq(get_coupon(b['id'])['balance'], 20000, 'b untouched')
    s, x = act(o['id'], 'cancel', {'refundToCouponId': a['id']}); c.eq(s, 200, msg(x))
    c.eq(get_coupon(a['id'])['balance'], 2000, 'a: 1000 refund + 1000 cash into coupon')

# ── E. 수정과 정산 ──────────────────────────────────────────────
with Case('E 정산', '현금 3,000 → 2,500 으로 수정: 돌려줄 500, 완료 막힘("정산"), 현금 정산 후 완료') as c:
    s, o = order('김차액', [line(AFFO)], 'CASH')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김차액', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(VAN_ICE)], 'memo': None})
    c.eq(s, 200, msg(u)); c.eq(u['cashAmount'], 2500, 'new cash'); c.eq(u['settledCash'], 3000, 'settled kept'); c.check(u.get('editedAt'), 'editedAt'); c.contains(u.get('editNote'), '아포카토', 'editNote')
    s, d = act(o['id'], 'done'); c.eq(s, 400, 'done blocked'); c.contains(msg(d), '정산', 'msg')
    s, st = act(o['id'], 'settle', {}); c.eq(st['settledCash'], 2500, 'settled')
    s, d = act(o['id'], 'done'); c.eq(s, 200, 'done ok')

with Case('E 정산', '현금 → 더 비싸게 수정: 더 받을 1,000, 받았어요 후 완료') as c:
    s, o = order('김추가', [line(AME_ICE)], 'CASH')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김추가', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(LATTE_ICE)], 'memo': None})
    c.eq(u['cashAmount'] - u['settledCash'], 1000, 'extra')
    s, d = act(o['id'], 'done'); c.eq(s, 400, 'blocked')
    act(o['id'], 'settle', {})
    s, d = act(o['id'], 'done'); c.eq(s, 200, 'done')

with Case('E 정산', '현금 주문 차액을 주문자 쿠폰에 넣기') as c:
    cp = coupon('김쿠폰차액', 20000)
    s, o = order('김쿠폰차액', [line(AFFO)], 'CASH')
    staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김쿠폰차액', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AME_ICE)], 'memo': None})
    s, st = act(o['id'], 'settle', {'couponId': cp['id']}); c.eq(s, 200, msg(st))
    c.eq(get_coupon(cp['id'])['balance'], 22000, 'coupon +2000'); c.eq(st['settledCash'], 1000, 'settled')

with Case('E 정산', '쿠폰 주문이 커지면 더 받을 돈으로 남고, 무료잔 선택 유지, 쿠폰에서 빼기로 정산') as c:
    cp = coupon('김쿠폰수정', 20000)
    s, o = order('김쿠폰수정', [line(AFFO), line(AME_ICE)], 'COUPON', coupon=cp['id'], free=True)
    c.eq(get_coupon(cp['id'])['balance'], 19000, 'before')     # 무료=아포카토 3000, 쿠폰 1000
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김쿠폰수정', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AFFO), line(LATTE_ICE)], 'memo': None})
    c.eq(s, 200, msg(u)); c.eq(u['freeAmount'], 3000, 'free kept'); c.eq(u['couponAmount'], 1000, 'coupon capped'); c.eq(u['cashAmount'], 1000, 'extra shown as cash')
    c.eq(get_coupon(cp['id'])['balance'], 19000, 'not auto-deducted'); c.eq(get_coupon(cp['id'])['freeDrinks'], 0, 'free used once')
    s, d = act(o['id'], 'done'); c.eq(s, 400, 'done blocked until settled')
    s, st = act(o['id'], 'settle', {'method': 'COUPON', 'couponId': cp['id']}); c.eq(s, 200, msg(st))
    c.eq(st['couponAmount'], 2000, 'coupon after'); c.eq(st['cashAmount'], 0, 'cash 0'); c.eq(get_coupon(cp['id'])['balance'], 18000, 'balance after')
    s, d = act(o['id'], 'done'); c.eq(s, 200, 'done')

with Case('E 정산', '더 받을 돈을 계좌이체로 / 다른 사람 쿠폰에서 / 잔액 부족 쿠폰 → 거부') as c:
    s, o = order('김더받기', [line(AME_ICE)], 'CASH')
    staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김더받기', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AFFO)], 'memo': None})
    poor = coupon('김더받기', 1000)
    s, x = act(o['id'], 'settle', {'method': 'COUPON', 'couponId': poor['id']}); c.eq(s, 400, 'poor coupon'); c.contains(msg(x), '잔액이', 'msg')
    s, st = act(o['id'], 'settle', {'method': 'TRANSFER'}); c.eq(s, 200, msg(st))
    c.eq(st['cashAmount'], 1000, 'cash'); c.eq(st['transferAmount'], 2000, 'transfer'); c.eq(st['settledTransfer'], 2000, 'settled')

with Case('E 정산', '쿠폰+현금 주문에서 쿠폰 부분 차액을 쿠폰 잔액으로 정산') as c:
    cp = coupon('김혼합', 2000)
    s, o = order('김혼합', [line(AFFO)], 'COUPON', coupon=cp['id'], remainder='CASH')   # 쿠폰 2000 + 현금 1000
    c.eq(o['cashAmount'], 1000, 'cash part')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김혼합', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AME_ICE)], 'memo': None})
    c.eq(s, 200, msg(u))
    # 1000원 주문: 쿠폰(다시 2000)에서 1000 → 현금 0. 받은 현금 1000 은 돌려줄 돈
    c.eq(u['couponAmount'], 1000, 'coupon'); c.eq(u['cashAmount'], 0, 'cash'); c.eq(u['settledCash'], 1000, 'settled')
    s, st = act(o['id'], 'settle', {'couponId': cp['id']}); c.eq(s, 200, msg(st))
    c.eq(get_coupon(cp['id'])['balance'], 2000, 'coupon: 2000-1000+1000')

with Case('E 정산', '수정 검증: 메뉴 없음/이름 없음/이름 21자/수량 0 → 거부') as c:
    s, o = order('김검증', [line(AME_ICE)], 'CASH')
    base = {'customerName': '김검증', 'receiveType': 'STORE', 'placeId': None, 'memo': None}
    c.eq(staff('PUT', f"/api/staff/orders/{o['id']}", {**base, 'lines': []})[0], 400, 'no lines')
    c.eq(staff('PUT', f"/api/staff/orders/{o['id']}", {**base, 'customerName': ' ', 'lines': [line(AME_ICE)]})[0], 400, 'blank name')
    c.eq(staff('PUT', f"/api/staff/orders/{o['id']}", {**base, 'customerName': '가' * 21, 'lines': [line(AME_ICE)]})[0], 400, 'long name')
    c.eq(staff('PUT', f"/api/staff/orders/{o['id']}", {**base, 'lines': [line(AME_ICE, 0)]})[0], 400, 'qty 0')

with Case('E 정산', '낸 현금이 있으면 늘어난 몫은 거스름돈에서 제하고, 잔돈은 쿠폰에 넣는다') as c:
    cp = coupon('김잔돈', 20000)
    s, o = order('김잔돈', [line(AME_ICE)], 'CASH', cash_given=5000)
    c.eq(s, 200, msg(o)); c.eq(o['payNote'], '현금 5,000원 받음 → 거스름돈 4,000원', 'note')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김잔돈', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AFFO), line(AME_ICE)], 'memo': None})   # 4,000
    c.eq(s, 200, msg(u)); c.eq(u['settledCash'], 4000, 'absorbed'); c.eq(u['payNote'], '현금 5,000원 받음 → 거스름돈 1,000원', 'note2')
    s, d = act(o['id'], 'done'); c.eq(s, 200, 'done without settle'); act(o['id'], 'reopen')
    s, x = staff('POST', f"/api/staff/orders/{o['id']}/change-to-coupon", {'couponId': cp['id']}); c.eq(s, 200, msg(x))
    c.eq(get_coupon(cp['id'])['balance'], 21000, 'coupon +1000'); c.eq(get_order(o['id'])['payNote'], '현금 4,000원 딱 맞게', 'note3')
    s, u2 = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김잔돈', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AFFO, 2)], 'memo': None})   # 6,000
    c.eq(u2['cashAmount'] - u2['settledCash'], 2000, 'extra 2000'); c.eq(u2['payNote'], '현금 4,000원 받음 → 2,000원 더 받아야', 'note4')

# ── F. 메뉴/옵션/카테고리/장소 관리가 키오스크에 반영 ─────────────
with Case('F 설정', '카테고리 추가 → 메뉴 추가 → 키오스크 메뉴에 등장 → 주문 가능 → 삭제 후 주문 거부, 지난 주문 유지') as c:
    s, cat = staff('POST', '/api/staff/categories', {'name': '디저트'}); c.eq(s, 200, msg(cat))
    s, item = staff('POST', '/api/staff/menu', {'name': '치즈케이크', 'category': '디저트', 'variants': [{'label': None, 'price': 4500, 'available': True}], 'available': True})
    c.eq(s, 200, msg(item))
    vid = item['variants'][0]['id']
    s, menu = call('GET', '/api/menu'); c.check(any(m['name'] == '치즈케이크' for m in menu), 'in kiosk menu')
    s, o = order('김케이크', [line(vid)], 'CASH'); c.eq(s, 200, msg(o)); c.eq(o['totalAmount'], 4500, 'price')
    c.eq(staff('DELETE', '/api/staff/categories/' + str(cat['id']))[0], 400, 'category with items')
    c.eq(staff('DELETE', f"/api/staff/menu/{item['id']}")[0], 200, 'delete item')
    s, o2 = order('김케이크', [line(vid)], 'CASH'); c.eq(s, 400, 'deleted variant')
    c.eq(get_order(o['id'])['lines'][0]['menuName'], '치즈케이크', 'old order keeps name')
    c.eq(staff('DELETE', '/api/staff/categories/' + str(cat['id']))[0], 200, 'delete empty category')

with Case('F 설정', '옵션 추가(휘핑 +300, 논커피) → 논커피에만 붙고 커피엔 거부') as c:
    s, opt = staff('POST', '/api/staff/menu/options', {'name': '휘핑', 'price': 300, 'category': '논커피', 'available': True}); c.eq(s, 200, msg(opt))
    s, o = order('김휘핑', [line(CHOCO, 1, [opt['id']])], 'CASH'); c.eq(s, 200, msg(o)); c.eq(o['totalAmount'], 2300, 'total')
    s, o = order('김휘핑', [line(AME_ICE, 1, [opt['id']])], 'CASH'); c.eq(s, 400, 'coffee')
    staff('DELETE', f"/api/staff/menu/options/{opt['id']}")

with Case('F 설정', '장소 숨김 → 키오스크 목록에서 사라지고 주문 거부') as c:
    s, p = staff('PUT', '/api/staff/places/102', {'floor': 1, 'name': '전도사님실', 'active': False}); c.eq(s, 200, msg(p))
    s, groups = call('GET', '/api/places'); c.check(not any(pl['id'] == 102 for g in groups for pl in g['places']), 'hidden')
    s, o = order('김장소', [line(AME_ICE)], 'CASH', 'DELIVERY', 102); c.eq(s, 400, 'inactive place')
    staff('PUT', '/api/staff/places/102', {'floor': 1, 'name': '전도사님실', 'active': True})

# ── G. 매출/집계/CSV/백업 ───────────────────────────────────────
with Case('G 매출', '오늘 집계 = PENDING+DONE 합 (취소 제외), 현금+이체+쿠폰+무료 = 총액') as c:
    sm = summary()
    s, orders = staff('GET', '/api/staff/orders'); s, done = staff('GET', '/api/staff/orders?status=DONE')
    alive = [o for o in orders + done if o['status'] != 'CANCELED']
    c.eq(sm['orderCount'], len(alive), 'count')
    c.eq(sm['totalAmount'], sum(o['totalAmount'] for o in alive), 'total')
    c.eq(sm['cashAmount'] + sm['transferAmount'] + sm['couponAmount'] + sm['freeAmount'], sm['totalAmount'], 'parts add up')
    c.eq(sm['staffFreeAmount'], sum(o['staffFreeAmount'] for o in alive), 'staffFree')

with Case('G 매출', '날짜별 집계와 CSV 가 오늘 집계와 같다, 백업 파일은 SQLite') as c:
    s, days = staff('GET', '/api/staff/reports/days'); today = days[0]; sm = summary()
    c.eq(today['orderCount'], sm['orderCount'], 'days count'); c.eq(today['totalAmount'], sm['totalAmount'], 'days total')
    s, b = call('GET', '/api/staff/reports/days.csv', staff=True, raw=True)
    rows = list(csv.reader(io.StringIO(b.decode('utf-8-sig'))))
    c.check(len(rows) >= 2, 'csv rows'); c.check(str(sm['totalAmount']) in ','.join(rows[1]), 'csv total')
    s, b = call('GET', '/api/staff/reports/backup.db', staff=True, raw=True)
    c.check(b.startswith(b'SQLite format 3'), 'sqlite header'); c.check(len(b) > 10000, 'size')

# ── H. 인증 ─────────────────────────────────────────────────────
with Case('H 인증', '토큰 없이 스태프 API → 401, 틀린 PIN → 400, 로그아웃 후 토큰 무효') as c:
    s, j = call('GET', '/api/staff/orders'); c.eq(s, 401, 'no token')
    s, j = call('POST', '/api/staff-auth/login', {'pin': '0000'}); c.eq(s, 400, 'wrong pin')
    s, j = call('POST', '/api/staff-auth/login', {'pin': PIN}); t2 = j['token']
    req_ok = call('GET', '/api/staff-auth/check', staff=True)[1]['valid']; c.eq(req_ok, True, 'valid')
    saved = TOKEN
    globals()['TOKEN'] = t2; call('POST', '/api/staff-auth/logout', staff=True)
    s, j = call('GET', '/api/staff/orders', staff=True); c.eq(s, 401, 'revoked')
    globals()['TOKEN'] = saved

# ── I. 단골 명단 ────────────────────────────────────────────────
with Case('I 단골', '주문한 이름이 단골 명단에 오름 (쿠폰 주문 이름 포함), 사역자 무료도 포함') as c:
    s, names = call('GET', '/api/customers/regulars')
    for n in ['김현금', '이영희', '목사님']: c.check(n in names, f'{n} in regulars')

# ── J. 상태 전이 / 잘못된 호출 ───────────────────────────────────
with Case('J 상태', '완료된 주문: 수정 거부, 두 번 완료 거부, 완료 상태에서 취소, 취소된 주문 되돌리기 거부, 없는 주문') as c:
    s, o = order('김상태', [line(AME_ICE)], 'CASH')
    act(o['id'], 'done')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김상태', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(LATTE_ICE)], 'memo': None}); c.eq(s, 400, 'edit done'); c.contains(msg(u), '이미', 'msg')
    s, d = act(o['id'], 'done'); c.eq(s, 400, 'done twice')
    s, x = act(o['id'], 'cancel', {}); c.eq(s, 200, 'cancel from DONE')
    c.eq(get_order(o['id'])['status'], 'CANCELED', 'status')
    s, r = act(o['id'], 'reopen'); c.eq(s, 400, 'reopen canceled')
    s, x2 = act(o['id'], 'cancel', {}); c.eq(s, 200, 'cancel twice is no-op')
    c.check(not any(x['id'] == o['id'] for x in staff('GET', '/api/staff/orders')[1]), 'not in pending')
    s, j = staff('POST', '/api/staff/orders/999999/done'); c.eq(s, 400, 'unknown order')

with Case('J 상태', '정산할 게 없을 때 settle → 그냥 200, couponId 만 주면 거부') as c:
    s, o = order('김정산없음', [line(AME_ICE)], 'CASH')
    s, st = act(o['id'], 'settle', {}); c.eq(s, 200, 'noop settle')
    cp = coupon('김정산없음', 20000)
    s, st = act(o['id'], 'settle', {'couponId': cp['id']}); c.eq(s, 400, 'refund to coupon with nothing due'); c.contains(msg(st), '돌려줄 금액이 없', 'msg')

# ── K. 쿠폰 경계 ─────────────────────────────────────────────────
with Case('K 쿠폰경계', '잔액 0 + 무료 1잔만으로 결제 (1잔이면 낼 돈 0), 2잔이면 나머지 수단 필요') as c:
    cp = coupon('김무료만', 20000)
    staff('POST', f"/api/staff/coupons/{cp['id']}/adjust", {'balance': 0, 'freeDrinks': 1})
    s, p = preview(cp['id'], [line(AFFO)], True); c.eq(p['freeAmount'], 3000, 'free'); c.eq(p['remainder'], 0, 'rem')
    s, o = order('김무료만', [line(AFFO)], 'COUPON', coupon=cp['id'], free=True); c.eq(s, 200, msg(o)); c.eq(o['couponAmount'], 0, 'coupon 0'); c.eq(o['cashAmount'], 0, 'cash 0')
    s, o2 = order('김무료만', [line(AFFO), line(AME_ICE)], 'COUPON', coupon=cp['id'], free=True); c.eq(s, 400, 'remainder needed')

with Case('K 쿠폰경계', '무료 1잔은 돈 내는 잔 중 가장 비싼 잔 — 사역자 무료잔은 후보 아님, 옵션 포함가로 비교') as c:
    cp = coupon('김무료후보', 20000)
    lines = [line(AFFO, 1, [], staff_free=1), line(AME_ICE, 1, [SHOT]), line(AME_ICE, 1)]
    s, p = preview(cp['id'], lines, True)
    c.eq(s, 200, msg(p)); c.eq(p['freeAmount'], 1500, 'free = 아메+샷'); c.eq(p['total'], 2500, 'total excludes staff free')
    s, o = order('김무료후보', lines, 'COUPON', coupon=cp['id'], free=True)
    c.eq(s, 200, msg(o)); c.eq(o['freeAmount'], 1500, 'order free'); c.eq(o['couponAmount'], 1000, 'coupon'); c.eq(o['staffFreeAmount'], 3000, 'staffFree')

with Case('K 쿠폰경계', '쿠폰에서 뺀 현금 주문을 다시 수정/취소해도 잔액이 맞는다') as c:
    cp = coupon('김재수정', 20000)
    s, o = order('김재수정', [line(AME_ICE)], 'CASH')
    staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김재수정', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(AFFO)], 'memo': None})
    s, st = act(o['id'], 'settle', {'method': 'COUPON', 'couponId': cp['id']}); c.eq(s, 200, msg(st))
    c.eq(get_coupon(cp['id'])['balance'], 18000, 'after coupon settle')
    s, u = staff('PUT', f"/api/staff/orders/{o['id']}", {'customerName': '김재수정', 'receiveType': 'STORE', 'placeId': None, 'lines': [line(LATTE_ICE)], 'memo': None})
    # 규칙: 쿠폰이 원래 뺐던 한도(2,000) 안에서 먼저 내고, 남은 현금 1,000 은 돌려줄 돈
    c.eq(s, 200, msg(u)); c.eq(u['couponAmount'], 2000, 'coupon first'); c.eq(u['cashAmount'], 0, 'cash 0'); c.eq(get_coupon(cp['id'])['balance'], 18000, 'balance')
    c.eq(u['settledCash'], 1000, 'refund 1000 due')
    s, st = act(o['id'], 'settle', {'couponId': cp['id']}); c.eq(s, 200, msg(st)); c.eq(get_coupon(cp['id'])['balance'], 19000, 'refund into coupon')
    s, x = act(o['id'], 'cancel', {}); c.eq(s, 200, msg(x))
    c.eq(get_coupon(cp['id'])['balance'], 21000, 'cancel: coupon 2000 back → 21000 (원래 20000 + 현금 1000 이 쿠폰으로)')

with Case('K 쿠폰경계', '삭제된 쿠폰으로 주문 → 거부, 잔액 정확히 0 되는 주문 OK') as c:
    cp = coupon('김삭제쿠폰', 20000)
    staff('DELETE', f"/api/staff/coupons/{cp['id']}")
    s, o = order('김삭제쿠폰', [line(AME_ICE)], 'COUPON', coupon=cp['id']); c.eq(s, 400, 'deleted coupon')
    cp2 = coupon('김딱맞게', 3000)
    s, o = order('김딱맞게', [line(AFFO)], 'COUPON', coupon=cp2['id']); c.eq(s, 200, msg(o)); c.eq(get_coupon(cp2['id'])['balance'], 0, 'zero')

# ── L. 지난 주문은 스냅샷 ────────────────────────────────────────
with Case('L 스냅샷', '옵션/장소/메뉴 이름·가격을 바꾸거나 지워도 지난 주문 표시는 그대로') as c:
    s, opt = staff('POST', '/api/staff/menu/options', {'name': '시나몬', 'price': 200, 'category': '커피', 'available': True})
    s, o = order('김스냅', [line(AME_ICE, 1, [opt['id']])], 'CASH', 'DELIVERY', 102)
    c.eq(o['placeName'], '전도사님실', 'place'); c.eq(o['lines'][0]['options'][0]['name'], '시나몬', 'opt')
    staff('DELETE', f"/api/staff/menu/options/{opt['id']}")
    staff('PUT', '/api/staff/places/102', {'floor': 1, 'name': '전도사님실(구)', 'active': True})
    variants = lambda price: [{'id': AME_ICE, 'label': 'ICE', 'price': price, 'available': True}, {'id': AME_HOT, 'label': 'HOT', 'price': 1000, 'available': True}]
    s, item = staff('PUT', '/api/staff/menu/10', {'name': '아메리카노2', 'category': '커피', 'variants': variants(1100), 'available': True})
    c.eq(s, 200, msg(item))
    after = get_order(o['id'])
    c.eq(after['placeName'], '전도사님실', 'place snapshot'); c.eq(after['lines'][0]['options'][0]['name'], '시나몬', 'option snapshot')
    c.eq(after['lines'][0]['menuName'], '아메리카노', 'menu name snapshot'); c.eq(after['lines'][0]['unitPrice'], 1200, 'price snapshot')
    staff('PUT', '/api/staff/menu/10', {'name': '아메리카노', 'category': '커피', 'variants': variants(1000), 'available': True})
    staff('PUT', '/api/staff/places/102', {'floor': 1, 'name': '전도사님실', 'active': True})

with Case('L 스냅샷', '카테고리 이름 변경 → 메뉴/옵션 따라감, 키오스크 탭도 바뀜, 옵션 규칙 유지') as c:
    s, cats = staff('GET', '/api/staff/categories'); coffee = next(x for x in cats if x['name'] == '커피')
    s, r = staff('PUT', f"/api/staff/categories/{coffee['id']}", {'name': '커피류'}); c.eq(s, 200, msg(r))
    s, menu = call('GET', '/api/menu'); c.check(any(m['category'] == '커피류' for m in menu) and not any(m['category'] == '커피' for m in menu), 'items renamed')
    s, opts = call('GET', '/api/menu/options'); c.check(all(x['category'] == '커피류' for x in opts if x['name'] in ('샷 추가', '연하게')), 'options renamed')
    s, tabs = call('GET', '/api/menu/categories'); c.eq(tabs[0], '커피류', 'kiosk tab')
    s, o = order('김카테', [line(AME_ICE, 1, [SHOT])], 'CASH'); c.eq(s, 200, 'option still applies after rename ' + msg(o))
    staff('PUT', f"/api/staff/categories/{coffee['id']}", {'name': '커피'})

# ── M. 동시 주문 ─────────────────────────────────────────────────
import threading
with Case('M 동시', '키오스크 20대가 동시에 주문해도 번호가 겹치지 않고 전부 접수') as c:
    results_ = []
    def go(i):
        s, o = order(f'동시{i}', [line(AME_ICE)], 'CASH')
        results_.append((s, o.get('orderNo') if isinstance(o, dict) else None, msg(o)))
    ts = [threading.Thread(target=go, args=(i,)) for i in range(20)]
    for t in ts: t.start()
    for t in ts: t.join()
    c.check(all(r[0] == 200 for r in results_), f'all accepted: {[r for r in results_ if r[0] != 200][:3]}')
    nos = [r[1] for r in results_ if r[0] == 200]
    c.eq(len(set(nos)), len(nos), f'order numbers unique {sorted(nos)}')

with Case('M 동시', '같은 쿠폰으로 동시에 두 주문 → 잔액이 음수가 되지 않는다') as c:
    cp = coupon('김동시쿠폰', 3000)
    res = []
    def go2(i):
        s, o = order('김동시쿠폰', [line(AFFO)], 'COUPON', coupon=cp['id'])
        res.append(s)
    ts = [threading.Thread(target=go2, args=(i,)) for i in range(2)]
    for t in ts: t.start()
    for t in ts: t.join()
    bal = get_coupon(cp['id'])['balance']
    c.check(bal >= 0, f'balance not negative: {bal}'); c.eq(sorted(res), [200, 400], f'one ok one rejected {res}')

# ── N. 리포트/백업 파일 ──────────────────────────────────────────
with Case('N 파일', '백업 zip 안에 db + CSV 3개 + 안내문, 쿠폰 CSV 에 이름/번호/잔액') as c:
    import zipfile
    s, b = call('GET', '/api/staff/reports/backup.zip', staff=True, raw=True)
    z = zipfile.ZipFile(io.BytesIO(b)); names = z.namelist()
    for n in ['kiosk.db', '쿠폰 잔액.csv', '매출-일별.csv', '주문-전체.csv', '읽어주세요.txt']: c.check(n in names, f'{n} in zip {names}')
    c.check(z.read('kiosk.db').startswith(b'SQLite format 3'), 'db valid')
    cc = z.read('쿠폰 잔액.csv').decode('utf-8-sig'); c.check('김딱맞게' in cc, 'coupon csv content')
    oc = z.read('주문-전체.csv').decode('utf-8-sig'); c.check(oc.count('\n') > 20, f'orders csv rows {oc.count(chr(10))}')
    s, b = call('GET', '/api/staff/reports/orders.csv?date=2000-01-01', staff=True, raw=True); c.eq(b.decode('utf-8-sig').count('\n'), 1, 'empty day csv = header only')
    s, j = call('GET', '/api/staff/reports/orders.csv?date=abc', staff=True); c.eq(s, 400, 'bad date')

with Case('N 파일', '단골 명단: 최근 주문한 이름, 중복 없음') as c:
    s, names = call('GET', '/api/customers/regulars')
    c.eq(len(names), len(set(names)), 'unique'); c.check('동시1' in names, 'recent order name')

# ══════════════════════════════════════════════════════════════════
print()
ok = sum(1 for r in results if r[2]); total = len(results)
cur = None
for g, n, passed, detail in results:
    if g != cur:
        cur = g; print(f'\n[{g}]')
    print(f"  {'PASS' if passed else 'FAIL'}  {n}")
    if not passed: print(f"        -> {detail}")
print(f'\n{ok}/{total} passed')
sys.exit(0 if ok == total else 1)
