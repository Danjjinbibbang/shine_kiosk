# -*- coding: utf-8 -*-
"""
사용자 매뉴얼 / 운영자 매뉴얼 PPT 생성.
  python tools/build_manuals.py <캡처 폴더> <출력 폴더>
캡처는 client/e2e/_capture.mjs 로 만든다 (빈 DB 서버 8094 에 예시 데이터 → 32장).
"""
import os, sys
from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import PP_ALIGN, MSO_ANCHOR
from PIL import Image

SHOTS = sys.argv[1]
OUT = sys.argv[2]
os.makedirs(OUT, exist_ok=True)

FONT = 'Malgun Gothic'
BROWN = RGBColor(0x6B, 0x45, 0x2B)
INK = RGBColor(0x2B, 0x22, 0x1C)
MUTED = RGBColor(0x7A, 0x6F, 0x66)
BG = RGBColor(0xF6, 0xF1, 0xEA)
PANEL = RGBColor(0xFF, 0xFF, 0xFF)
LINE = RGBColor(0xE2, 0xD9, 0xCE)
OK = RGBColor(0x2E, 0x7D, 0x4F)
RED = RGBColor(0xB0, 0x3A, 0x2E)
W, H = Inches(13.333), Inches(7.5)


class Deck:
    def __init__(self, title, subtitle, accent=BROWN):
        self.prs = Presentation()
        self.prs.slide_width, self.prs.slide_height = W, H
        self.blank = self.prs.slide_layouts[6]
        self.accent = accent
        self.n = 0
        self.cover(title, subtitle)

    # ── 기본 요소 ──
    def _slide(self):
        s = self.prs.slides.add_slide(self.blank)
        bg = s.background.fill
        bg.solid(); bg.fore_color.rgb = BG
        self.n += 1
        return s

    def _text(self, slide, x, y, w, h, text, size=14, bold=False, color=INK, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, font=FONT):
        tb = slide.shapes.add_textbox(x, y, w, h)
        tf = tb.text_frame
        tf.word_wrap = True
        tf.vertical_anchor = anchor
        tf.margin_left = tf.margin_right = Inches(0.05)
        tf.margin_top = tf.margin_bottom = Inches(0.03)
        lines = text if isinstance(text, list) else [text]
        for i, line in enumerate(lines):
            p = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
            p.alignment = align
            r = p.add_run()
            r.text = line
            r.font.size = Pt(size); r.font.bold = bold; r.font.color.rgb = color; r.font.name = font
        return tb

    def _bullets(self, slide, x, y, w, h, items, size=15):
        """items: str | (str, level) | ('#', str)=소제목"""
        tb = slide.shapes.add_textbox(x, y, w, h)
        tf = tb.text_frame; tf.word_wrap = True
        tf.margin_left = tf.margin_right = Inches(0.05)
        first = True
        for it in items:
            p = tf.paragraphs[0] if first else tf.add_paragraph()
            first = False
            level, text, head = 0, it, False
            if isinstance(it, tuple):
                if it[0] == '#': head, text = True, it[1]
                else: text, level = it
            p.level = level
            p.space_after = Pt(5 if level == 0 else 2)
            r = p.add_run()
            if head:
                r.text = text; r.font.bold = True; r.font.size = Pt(size + 1); r.font.color.rgb = self.accent
            else:
                r.text = ('•  ' if level == 0 else '–  ') + text
                r.font.size = Pt(size - 2 * level); r.font.color.rgb = INK
            r.font.name = FONT
        return tb

    def _rect(self, slide, x, y, w, h, fill, line=None, radius=False):
        shp = slide.shapes.add_shape(MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE, x, y, w, h)
        shp.fill.solid(); shp.fill.fore_color.rgb = fill
        if line is None: shp.line.fill.background()
        else: shp.line.color.rgb = line; shp.line.width = Pt(1)
        shp.shadow.inherit = False
        if radius: shp.adjustments[0] = 0.08
        return shp

    def _header(self, slide, title, kicker=None):
        self._rect(slide, 0, 0, W, Inches(0.95), self.accent)
        self._text(slide, Inches(0.5), Inches(0.12), Inches(11), Inches(0.75), title, size=26, bold=True, color=PANEL, anchor=MSO_ANCHOR.MIDDLE)
        if kicker:
            self._text(slide, Inches(9.3), Inches(0.25), Inches(3.6), Inches(0.5), kicker, size=13, color=RGBColor(0xEE, 0xE3, 0xD6), align=PP_ALIGN.RIGHT, anchor=MSO_ANCHOR.MIDDLE)
        self._text(slide, Inches(12.3), Inches(7.05), Inches(0.9), Inches(0.35), str(self.n), size=10, color=MUTED, align=PP_ALIGN.RIGHT)

    def _image(self, slide, path, x, y, max_w, max_h, frame=True):
        im = Image.open(path)
        ratio = min(max_w / im.width, max_h / im.height)
        w, h = int(im.width * ratio), int(im.height * ratio)
        if frame:
            self._rect(slide, x - Inches(0.06), y - Inches(0.06), w + Inches(0.12), h + Inches(0.12), PANEL, LINE, radius=True)
        slide.shapes.add_picture(path, x, y, w, h)
        return w, h

    # ── 슬라이드 종류 ──
    def cover(self, title, subtitle):
        s = self._slide()
        self._rect(s, 0, 0, W, H, self.accent)
        self._text(s, Inches(1), Inches(2.3), Inches(11.3), Inches(1.4), title, size=44, bold=True, color=PANEL)
        self._text(s, Inches(1), Inches(3.8), Inches(11.3), Inches(1.2), subtitle, size=20, color=RGBColor(0xEE, 0xE3, 0xD6))
        self._text(s, Inches(1), Inches(6.4), Inches(11.3), Inches(0.5), '열린 카페 주문 키오스크 · 2026-09', size=14, color=RGBColor(0xEE, 0xE3, 0xD6))

    def section(self, title, subtitle=''):
        s = self._slide()
        self._rect(s, 0, Inches(2.6), W, Inches(2.3), self.accent)
        self._text(s, Inches(1), Inches(2.8), Inches(11.3), Inches(1), title, size=36, bold=True, color=PANEL, anchor=MSO_ANCHOR.MIDDLE)
        self._text(s, Inches(1), Inches(3.8), Inches(11.3), Inches(0.9), subtitle, size=18, color=RGBColor(0xEE, 0xE3, 0xD6))

    def shot(self, title, image, bullets, kicker=None, note=None, second=None):
        """왼쪽 캡처(세로 화면은 폭이 좁아 두 장까지), 오른쪽 설명"""
        s = self._slide(); self._header(s, title, kicker)
        x, y = Inches(0.5), Inches(1.25)
        max_h = Inches(5.8)
        imgs = [image] + ([second] if second else [])
        cur = x
        for p in imgs:
            w, h = self._image(s, os.path.join(SHOTS, p + '.png'), cur, y, Inches(3.4) if len(imgs) > 1 else Inches(4.6), max_h)
            cur += w + Inches(0.35)
        self._bullets(s, cur + Inches(0.1), Inches(1.25), W - cur - Inches(0.6), Inches(5.3), bullets)
        if note:
            self._rect(s, cur + Inches(0.1), Inches(6.35), W - cur - Inches(0.6), Inches(0.75), RGBColor(0xFB, 0xF0, 0xDC), LINE, radius=True)
            self._text(s, cur + Inches(0.2), Inches(6.4), W - cur - Inches(0.8), Inches(0.65), note, size=12, color=INK, anchor=MSO_ANCHOR.MIDDLE)

    def bullets(self, title, items, kicker=None, cols=1, note=None, size=15):
        s = self._slide(); self._header(s, title, kicker)
        if cols == 1:
            self._bullets(s, Inches(0.6), Inches(1.25), Inches(12.1), Inches(5.6), items, size)
        else:
            half = (len(items) + 1) // 2
            self._bullets(s, Inches(0.6), Inches(1.25), Inches(6), Inches(5.6), items[:half], size)
            self._bullets(s, Inches(6.9), Inches(1.25), Inches(6), Inches(5.6), items[half:], size)
        if note:
            self._rect(s, Inches(0.6), Inches(6.3), Inches(12.1), Inches(0.8), RGBColor(0xFB, 0xF0, 0xDC), LINE, radius=True)
            self._text(s, Inches(0.75), Inches(6.35), Inches(11.8), Inches(0.7), note, size=12, anchor=MSO_ANCHOR.MIDDLE)

    def table(self, title, header, rows, kicker=None, widths=None, size=12, note=None):
        s = self._slide(); self._header(s, title, kicker)
        n_rows, n_cols = len(rows) + 1, len(header)
        x, y = Inches(0.5), Inches(1.2)
        tw = W - Inches(1.0)
        th = min(Inches(5.9), Inches(0.36) * n_rows + Inches(0.2))
        shape = s.shapes.add_table(n_rows, n_cols, x, y, tw, th)
        tbl = shape.table
        widths = widths or [1] * n_cols
        total = sum(widths)
        for i, wgt in enumerate(widths):
            tbl.columns[i].width = int(tw * wgt / total)
        def cell(c, text, bold=False, fill=None, color=INK):
            c.text = ''
            tf = c.text_frame; tf.word_wrap = True
            c.margin_left = c.margin_right = Inches(0.06); c.margin_top = c.margin_bottom = Inches(0.03)
            p = tf.paragraphs[0]; r = p.add_run(); r.text = str(text)
            r.font.size = Pt(size); r.font.bold = bold; r.font.name = FONT; r.font.color.rgb = color
            if fill is not None:
                c.fill.solid(); c.fill.fore_color.rgb = fill
        for j, htxt in enumerate(header):
            cell(tbl.cell(0, j), htxt, bold=True, fill=self.accent, color=PANEL)
        for i, row in enumerate(rows, start=1):
            for j, v in enumerate(row):
                cell(tbl.cell(i, j), v, fill=PANEL if i % 2 else RGBColor(0xFA, 0xF6, 0xF0))
        if note:
            self._text(s, Inches(0.5), Inches(6.6), Inches(12.3), Inches(0.6), note, size=12, color=MUTED)

    def code(self, title, text, kicker=None, right=None, size=10.5):
        """왼쪽 고정폭 텍스트(파일 구조 등), 오른쪽 설명"""
        s = self._slide(); self._header(s, title, kicker)
        w = Inches(8.4) if right else Inches(12.3)
        self._rect(s, Inches(0.5), Inches(1.2), w, Inches(5.9), RGBColor(0x2B, 0x22, 0x1C), radius=True)
        tb = self._text(s, Inches(0.62), Inches(1.28), w - Inches(0.25), Inches(5.75), text.split('\n'), size=size, color=RGBColor(0xF1, 0xE9, 0xDD), font='Consolas')
        for p in tb.text_frame.paragraphs: p.space_after = Pt(0); p.line_spacing = 1.0
        if right:
            self._bullets(s, Inches(9.15), Inches(1.25), Inches(3.9), Inches(5.8), right, 12)

    def diagram(self, title, boxes, arrows, kicker=None, notes=None):
        """boxes: {key: (x, y, w, h, 제목, 설명, 색)} (inch), arrows: [(from, to, label)]"""
        s = self._slide(); self._header(s, title, kicker)
        def edge(k, other):
            x, y, w, h = [Inches(v) for v in boxes[k][:4]]
            ox, oy, ow, oh = [Inches(v) for v in boxes[other][:4]]
            cx, cy, ocx, ocy = x + w // 2, y + h // 2, ox + ow // 2, oy + oh // 2
            if abs(ocx - cx) > abs(ocy - cy):
                return int(x + w if ocx > cx else x), int(cy)
            return int(cx), int(y + h if ocy > cy else y)
        labels = []
        for a, b, label in arrows:
            ax, ay = edge(a, b); bx, by = edge(b, a)
            ln = s.shapes.add_connector(1, Emu(ax), Emu(ay), Emu(bx), Emu(by))
            ln.line.color.rgb = self.accent; ln.line.width = Pt(2.5)
            if label:
                labels.append((int((ax + bx) / 2 - Inches(1.2)), int((ay + by) / 2 - Inches(0.42)), label))
        centers = {}
        for key, (x, y, w, h, head, body, color) in boxes.items():
            shp = self._rect(s, Inches(x), Inches(y), Inches(w), Inches(h), color, LINE, radius=True)
            tf = shp.text_frame; tf.word_wrap = True; tf.vertical_anchor = MSO_ANCHOR.MIDDLE
            tf.margin_left = tf.margin_right = Inches(0.08)
            p = tf.paragraphs[0]; p.alignment = PP_ALIGN.CENTER
            r = p.add_run(); r.text = head; r.font.bold = True; r.font.size = Pt(14); r.font.color.rgb = INK; r.font.name = FONT
            for line in body:
                p2 = tf.add_paragraph(); p2.alignment = PP_ALIGN.CENTER
                r2 = p2.add_run(); r2.text = line; r2.font.size = Pt(11); r2.font.color.rgb = MUTED; r2.font.name = FONT
            centers[key] = key
        for lx, ly, label in labels:
            self._text(s, Emu(lx), Emu(ly), Inches(2.4), Inches(0.4), label, size=11, bold=True, color=self.accent, align=PP_ALIGN.CENTER)
        if notes:
            self._bullets(s, Inches(0.6), Inches(5.6), Inches(12.1), Inches(1.5), notes, 13)

    def save(self, name):
        path = os.path.join(OUT, name)
        self.prs.save(path)
        print('saved', path, self.n, 'slides')


# ══════════════════════════════════════════════════════════════════════
# 1. 사용자 매뉴얼
# ══════════════════════════════════════════════════════════════════════
u = Deck('열린 카페 키오스크 사용자 매뉴얼', '손님 주문 화면(태블릿)과 스태프 화면(폰) 사용법')

u.bullets('이 매뉴얼의 구성', [
    ('#', '두 개의 화면'),
    '주문 화면 (키오스크) — 카페 앞 태블릿. 손님이 직접 메뉴를 고르고 결제 방법을 정한다',
    '스태프 화면 — 만드는 사람의 폰. 들어온 주문을 보고, 완료·수정·취소하고, 쿠폰과 매출을 관리한다',
    ('#', '접속 주소'),
    '태블릿: 홈 화면의 "열린 카페" 아이콘 (또는 크롬에서 http://localhost:8080/kiosk)',
    '폰: 홈 화면의 "열린 카페 스태프" 아이콘 (또는 http://<태블릿 IP>:8080/staff). 태블릿과 같은 와이파이여야 한다',
    ('#', '기본 흐름'),
    '손님이 태블릿에서 주문 → 현금은 바구니에 → 폰에 카드가 바로 뜸 → 음료를 만들어 이름을 부르고 거스름돈과 함께 건넴 → 완료',
], note='화면이 이상하면 먼저 새로고침. 새 버전이 배포되면 화면은 1분 안에 스스로 새로고침된다.')

u.section('1부. 주문 화면 (태블릿)', '손님이 보는 화면. 크고 단순하게, 한 화면에 한 가지만 묻는다')

u.shot('메뉴 고르기', 'k01-menu', [
    '위쪽 탭이 카테고리 (커피 / 논커피 / 아이스크림 …). 설정에서 바꾼 순서 그대로',
    '메뉴 줄의 ICE / HOT 버튼을 누르면 1잔 담김. 같은 버튼을 다시 누르면 1잔 더',
    '담긴 뒤엔 − 수량 + 가 나타난다 (한 줄 최대 99잔)',
    '탭의 숫자 배지 = 그 카테고리에서 담은 잔 수',
    '아래 바에 "n개 · 합계" 와 [주문 확인 ›]',
], kicker='주문 1/8', second='k02-menu-added')

u.shot('주문 내용 확인 — 잔마다 옵션', 'k03-cart', [
    '한 줄 = 같은 메뉴·같은 옵션의 잔들. 줄마다 − + 로 수량 조절',
    ('#', '칩은 한 잔씩'),
    '샷 추가 (+500) / 연하게 — 한 번 누르면 한 잔만 바뀐다. 2잔 중 1잔만 샷 추가 가능',
    '샷 추가와 연하게는 같은 잔에 동시에 안 된다 (하나 켜면 다른 게 잠김)',
    '커피에만 칩이 보인다 (옵션은 카테고리별)',
    ('#', '사역자'),
    '사역자 칩을 누르면 그 잔은 무료 (사역자 명단 없이 칩으로만)',
    '[더 담기] 로 메뉴로, [주문하기 ›] 로 다음',
], kicker='주문 2/8', second='k16-cart-staff', note='오른쪽: 바닐라라떼 3잔 중 2잔에 사역자 → 낼 돈은 1잔 값만. 전부 사역자면 결제 없이 이름만 묻는다.')

u.shot('받는 방법', 'k04-receive', [
    '☕ 카페에서 받기 — 음료가 되면 이름을 불러 준다',
    '🚶 배달 — 1층만 (식당, 전도사님실). 2층 이상은 만드는 사람이 자리를 비워야 해서 안 한다',
    '배달 장소 목록은 설정 > 배달 장소에서 바꾼다',
], kicker='주문 3/8', second='k05-place')

u.shot('결제 수단', 'k06-payment', [
    '계좌이체 / 쿠폰 / 현금 세 가지',
    ('#', '계좌이체'),
    '계좌번호가 크게 보인다. 손님이 폰으로 보낸 뒤 [보냈어요]',
    '12초 동안 아무것도 안 누르면 자동으로 다음(이름)으로 넘어간다',
    '스태프는 폰 카드에서 입금을 확인하고 완료한다',
], kicker='주문 4/8', second='k07-transfer')

u.shot('현금', 'k08-cash', [
    '"현금은 바구니에 넣어주세요" — 손님이 얼마를 냈는지 고른다',
    '[딱 맞게] 또는 총액보다 큰 지폐 단위 (예: 4,500원이면 5,000 / 10,000 / 50,000)',
    '거스름돈이 바로 계산돼 보인다 (10,000 내면 5,500)',
    '거스름돈은 스태프 폰 카드에 💵 로 표시되고, 음료와 함께 건넨다',
    '쿠폰 잔액이 모자라 나머지를 현금으로 낼 때도 같은 화면 (나머지 금액 기준)',
], kicker='주문 5/8')

u.shot('쿠폰 (선불)', 'k12-coupon-name', [
    '단골 명단에서 이름을 고르거나 직접 입력 → [쿠폰 조회]',
    '같은 이름이 둘 이상이면 전화번호 뒤 4자리 버튼(010-****-5678)이 떠서 본인 것을 고른다. 숫자를 치는 키패드는 없다',
    '전화번호가 등록되지 않은 쿠폰이 섞여 있으면 "스태프에게 말씀해 주세요" 안내',
    '쿠폰으로 결제하면 이름 화면은 건너뛴다 (쿠폰 주인 이름으로 접수)',
], kicker='주문 6/8', second='k13-coupon-pick')

u.shot('쿠폰 — 잔액과 무료 1잔', 'k14-coupon-found', [
    '잔액이 크게 보이고, 주문 금액 전액이 잔액에서 빠진다',
    ('#', '무료 1잔 쓰기'),
    '남은 무료 1잔이 있으면 체크박스. 켜면 이번 주문에서 가장 비싼 한 잔(옵션 포함가)이 무료',
    '사역자 무료로 표시한 잔은 무료 1잔 후보가 아니다',
    ('#', '잔액 부족'),
    '잔액을 다 쓰고 남는 금액은 현금 또는 계좌이체 중 골라서 낸다',
    '현금이면 낸 돈/거스름돈 화면을 한 번 더 거친다',
], kicker='주문 7/8', second='k15-coupon-free')

u.shot('이름 → 완료', 'k09-name', [
    '최근 3주 안에 주문한 이름이 가나다순 버튼으로. 없으면 [이름 직접 입력]',
    '이름은 20자까지. 음료가 되면 이 이름으로 부른다',
    '[주문 완료] 를 누르면 접수. 주문 번호가 크게 보인다 (당일 1번부터)',
    '[처음으로] 를 누르거나 2분 동안 안 건드리면 첫 화면으로 돌아간다',
    '와이파이가 잠깐 끊겨 두 번 눌러도 주문은 한 번만 들어간다',
], kicker='주문 8/8', second='k11-done')

u.section('2부. 스태프 화면 (폰)', '만드는 사람이 보는 화면. 주문 처리 · 쿠폰 · 매출 · 설정')

u.shot('로그인', 's01-login', [
    'PIN 4자리 (운영자가 정한 값). 틀리면 오류',
    '한 번 로그인하면 폰에 저장돼서 브라우저를 닫아도 유지된다',
    '서버(태블릿)가 재시작되면 다시 PIN 을 묻는다 — 정상',
    '오른쪽 위 [나가기] 로 로그아웃',
    ('#', '상단 집계'),
    '오늘 주문 수 · 합계 · 현금/이체/쿠폰/무료잔/사역자 — 취소한 주문은 빠진다',
    '탭: 만들 것 / 완료 / 쿠폰 / 매출 / 설정',
], kicker='스태프 1/9')

u.shot('만들 것 — 카드 읽는 법', 's02-orders', [
    '#번호 이름 · 받는 곳(☕ 카페 / 🚶 식당) · 시각. 새 주문은 손대지 않아도 바로 나타난다',
    '메뉴 줄: 메뉴 ×잔수, 옵션(샷 추가), (사역자 n) 표시',
    '결제 줄: "현금 4,500원" 또는 "10,500원 = 쿠폰 4,500원 + 현금 6,000원" 처럼 구성대로',
    '💵 현금 10,000원 받음 → 거스름돈 5,500원 — 음료와 함께 줄 거스름돈. 옆의 [잔돈 쿠폰에 넣기] 로 잔돈을 쿠폰에 충전할 수도 있다',
    '📝 메모 (스태프가 수정 화면에서 적은 것)',
    '수정됨 배지 + 시각 + 이전 내용: 스태프가 고친 주문',
    '오늘 것이 아니면 번호 앞에 날짜가 붙는다 (9/21 #3)',
], kicker='스태프 2/9')

u.shot('완료 · 되돌리기 · 잔액 문자', 's06-done', [
    '[완료 ✓] — 음료를 건넸을 때. 완료 탭으로 이동하고 상단 집계는 그대로',
    '완료를 누르는 순간 거스름돈은 "준 것"으로 확정된다',
    '쿠폰 주문을 완료하면 잔액 안내 문자 앱이 열린다 (내용이 채워진 채, 보내기만 누르면 됨). 번호 없는 쿠폰은 문자 생략',
    '완료 탭에서 [↩ 되돌리기] 로 다시 만들 것으로. [📩 잔액 문자] 로 문자 다시',
    '돌려줄 돈/더 받을 돈이 남아 있으면 완료 버튼이 "정산 먼저" 로 잠긴다',
], kicker='스태프 3/9')

u.shot('주문 수정', 's04-edit', [
    '[수정] → 이름 · 받는 곳 · 메뉴/수량 · 잔별 옵션(샷 추가/연하게/사역자) · 메모를 고친다. 결제 수단은 못 바꾼다',
    '칩은 키오스크와 같이 한 잔씩 바뀐다',
    '저장하면 카드에 "수정됨" 과 이전 내용이 남고, 금액 차이가 있으면 정산 안내가 뜬다 (다음 장)',
    '메뉴를 다 빼면 저장 불가. [닫기] 는 변경 없음',
], kicker='스태프 4/9')

u.shot('정산 — 차액이 생겼을 때', 's03-order-settle', [
    ('#', '더 받을 돈 (금액이 늘었을 때)'),
    '낸 현금에 거스름돈이 남아 있으면 거기서 먼저 제한다 → 안내 없이 바로 완료 가능',
    '거스름돈을 넘는 만큼만 "n원 더 받기 — 어떻게 받았나요?" [현금으로 받았어요] [계좌이체로 받았어요] [쿠폰에서 빼기]',
    '쿠폰에서 빼기: 쿠폰 주문이면 그 쿠폰, 아니면 주문자 이름의 쿠폰 (동명이인이면 고름). 잔액 부족이면 거부',
    ('#', '돌려줄 돈 (금액이 줄었을 때, 낸 현금 기록이 없는 주문)'),
    '"n원 돌려주기" [현금으로 줬어요] [계좌이체로 보냈어요] [쿠폰에 넣기] → 카드에 ↩ 돌려줌 기록',
    '낸 현금이 있는 주문은 거스름돈이 커질 뿐 — 💵 줄에 반영',
    '쿠폰 주문이 줄면 차액은 쿠폰 잔액으로 자동 복귀',
], kicker='스태프 5/9', note='정산을 마쳐야 완료할 수 있다. 완료 뒤 되돌려서 고치면 그 뒤 차액만 주고받는다 (이미 준 거스름돈은 확정).')

u.shot('취소', 's05-cancel', [
    '[취소] → 카드 안에 확인 패널. [취소 안 함] 으로 그대로',
    '받은 돈이 있으면 어떻게 돌려줬는지: [현금으로 돌려주고 취소] [계좌이체로 돌려주고 취소] [쿠폰에 넣고 취소]',
    '쿠폰으로 낸 몫(잔액·무료 1잔)은 자동으로 되돌아간다',
    '낸 현금이 있으면 거스름돈은 어차피 현금으로 드린다는 안내가 같이 뜬다',
    '취소한 주문은 매출에서 빠지고, 매출 탭 목록에 "취소" 로 남는다',
], kicker='스태프 6/9')

u.shot('쿠폰 관리 — 조회 · 등록', 's07-coupon', [
    '이름(+전화번호 뒤 4자리 또는 전체)으로 [조회]. 동명이인이면 후보 목록에서 고른다. 이름을 고치면 번호 칸은 비워진다',
    ('#', '등록'),
    '없는 이름이면 "새로 등록할까요?" — 전화번호 13자리(010-1234-5678)를 다 넣어야 버튼이 열린다 (문자 발송용)',
    '자동으로 하이픈이 붙고 13자 이상은 안 들어간다. 유선번호(02-…)는 거부',
    '[20,000원 등록] → 무료 1잔 / [30,000원 등록] → 무료 2잔 / 다른 금액 (30,000원까지, 무료잔은 20,000 이상 1잔)',
    '같은 이름에 뒤 4자리까지 같은 번호는 등록 안 된다',
], kicker='스태프 7/9', second='s09-coupon-register')

u.shot('쿠폰 관리 — 카드', 's08-coupon-card', [
    '잔액 · 무료 1잔 수 · 전화번호 · [이름 변경] [번호 변경] (번호는 버튼을 눌러야 칸이 열림)',
    '충전: [20,000원 충전] [30,000원 충전] 또는 다른 금액. 한 번에 30,000원까지. 번호 없는 옛 쿠폰은 번호를 넣어야 충전된다',
    '[잔액 정정] — 잘못 넣었을 때 잔액/무료잔을 직접 고침 (이력에 "정정" 으로 남음)',
    '[쿠폰 삭제] — 주문에 쓰인 적 없는 쿠폰만. 쓰였으면 정정으로 0원 처리',
    '최근 한 달 이력: 충전 / 사용(주문 번호) / 환불 / 정정 / 잔돈 충전',
], kicker='스태프 8/9')

u.shot('매출 · 백업 · 설정', 's10-report', [
    ('#', '매출'),
    '월별 → 일별 집계. 줄을 펼치면 그날 주문 목록 (취소 포함, 결제 구성대로)',
    '현금 / 이체 / 쿠폰 사용 / 무료잔 / 사역자 / 쿠폰 충전 입금 / 돌려준 돈',
    '[일별 CSV] [이날 주문 CSV] — 엑셀에서 열린다',
    '[백업 내려받기] — zip 하나에 복구용 DB + 쿠폰 잔액·일별 매출·전체 주문 CSV. 폰 파일 앱에서 바로 열림. 한 달에 한 번쯤 받아 두기',
    ('#', '설정'),
    '메뉴(카테고리별 접기, 품절 스위치, 순서, 가격) · 옵션 · 카테고리(키오스크 탭 순서) · 배달 장소',
    '바꾸면 키오스크에 바로 반영. 메뉴가 없는 카테고리도 탭은 뜬다',
], kicker='스태프 9/9', second='s11-settings')

u.shot('설정 — 메뉴 관리', 's12-settings-menu', [
    '카테고리 그룹을 눌러 펼치기. 개수와 품절 수가 보인다',
    '판매중/품절 스위치 — 품절이면 키오스크에서 사라진다',
    '↑↓ 는 같은 카테고리 안에서 순서 이동',
    '[수정] — 이름(30자) · 카테고리 · ICE/HOT 선택지와 가격(100원 단위, 10만원 이하) · 판매 여부',
    '[＋ 새 메뉴] 또는 그룹 안의 [＋ 커피에 메뉴 추가]',
    '삭제해도 지난 주문 기록은 남는다',
], kicker='설정', second='s13-menu-modal')

u.shot('설정 — 옵션 · 카테고리 · 배달 장소', 's14-settings-options', [
    ('#', '옵션'),
    '이름 · 추가 금액 · 적용 카테고리 · 그룹(같은 그룹은 한 잔에 하나만, 예: 농도 = 샷 추가/연하게)',
    '사용중/숨김 스위치',
    ('#', '카테고리'),
    '추가 · 이름 변경(메뉴/옵션에 따라감) · 순서(키오스크 탭 순서) · 삭제(메뉴가 없어야)',
    ('#', '배달 장소'),
    '층 + 이름. 표시중/숨김. 숨기면 키오스크 배달 목록에서 빠진다',
], kicker='설정', second='s15-settings-categories')

u.bullets('자주 생기는 상황', [
    ('#', '폰에서 접속이 안 돼요'),
    '태블릿과 같은 와이파이인지 확인. 태블릿 IP 가 바뀌었을 수 있다 (운영자에게)',
    ('#', '주문이 안 들어와요 / "서버와 연결이 끊겼습니다" 띠'),
    '와이파이 문제. 20초마다 자동으로 다시 시도한다. 태블릿에서 주문이 됐는지 확인',
    ('#', '완료 버튼이 "정산 먼저" 로 잠겨요'),
    '수정으로 돌려줄 돈/더 받을 돈이 생긴 것. 카드의 정산 버튼을 먼저 누른다',
    ('#', '쿠폰이 조회되는데 충전이 안 돼요'),
    '전화번호가 없는 옛 쿠폰. 번호를 넣고 저장하면 된다',
    ('#', '같은 이름 쿠폰인데 키오스크에서 고를 수 없대요'),
    '동명이인 중 번호 없는 쿠폰이 있는 것. 스태프 쿠폰 화면에서 번호를 넣어 준다',
    ('#', '태블릿을 껐다 켰어요'),
    '1~2분 뒤 서버가 혼자 올라온다. 키오스크 아이콘을 다시 열면 된다. 폰은 PIN 을 다시 묻는다',
    ('#', '화면이 옛날 것 같아요'),
    '새 버전이 나오면 1분 안에 스스로 새로고침된다. 급하면 직접 새로고침',
], cols=2, size=13)

u.save('열린카페-사용자매뉴얼.pptx')


# ══════════════════════════════════════════════════════════════════════
# 2. 운영자 매뉴얼
# ══════════════════════════════════════════════════════════════════════
o = Deck('열린 카페 키오스크 운영자 매뉴얼', '구조 · 기술 스택 · 데이터 · API · 배포 · 백업/복구 · 테스트 · 문제 해결', accent=RGBColor(0x2F, 0x3E, 0x4E))

o.bullets('시스템 한눈에', [
    ('#', '목적'),
    '주일 점심 카페를 만드는 사람 3명만으로 돌린다 — 주문받는 사람 없이 손님이 태블릿에서 직접 주문',
    ('#', '구성'),
    '태블릿(갤럭시탭 S6 Lite) 한 대가 서버이자 키오스크. Termux 안에서 Spring Boot jar 가 돌고 SQLite 파일에 저장',
    '스태프 폰은 같은 와이파이로 태블릿의 8080 포트에 접속 (설치 없이 브라우저, 홈 화면 아이콘)',
    '외부 서버·클라우드·인터넷 의존 없음. 와이파이만 있으면 된다',
    ('#', '저장소'),
    'GitHub: fasol/shine_kiosk (main). 릴리스에 jar 와 부팅 스크립트를 올려 태블릿이 받는다',
    ('#', '규모'),
    '소스 약 7,500줄 (Java 3.4k · TS/TSX 3.7k · CSS/SQL 0.4k), 테스트 4종 (JUnit 99 · E2E 41 · 통합 50 · 마이그레이션 35)',
])

o.diagram('구성도', {
    'tablet': (0.6, 1.4, 4.4, 2.9, '태블릿 (갤럭시탭 S6 Lite)', ['Termux + OpenJDK 21', 'kiosk-server.jar (Spring Boot, :8080)', 'SQLite ~/kiosk/data/kiosk.db', '크롬 → /kiosk (키오스크 화면)', '자동 백업 → Download/kiosk-backups'], RGBColor(0xE8, 0xEF, 0xF6)),
    'phone': (8.3, 1.4, 4.4, 2.0, '스태프 폰 (1~3대)', ['브라우저 → http://<태블릿IP>:8080/staff', 'WebSocket 으로 주문 변경 즉시 반영 + 20초 폴링'], RGBColor(0xE6, 0xF3, 0xEA)),
    'wifi': (5.5, 1.9, 2.3, 1.1, '교회 와이파이', ['AP 격리 꺼짐'], RGBColor(0xFB, 0xF0, 0xDC)),
    'github': (0.6, 4.55, 4.4, 1.0, 'GitHub 릴리스', ['kiosk-server.jar · start-kiosk.sh', '태블릿에서 curl 로 받아 교체'], RGBColor(0xF1, 0xEC, 0xE4)),
    'note': (5.5, 3.6, 7.2, 1.9, '한 프로세스 = API + 정적 파일(React) + WebSocket + 백업 스케줄러', ['태블릿 크롬은 localhost 로 자기 서버에 붙고, 폰만 와이파이를 탄다', '외부 서버·인터넷 없음. 릴리스 jar 는 업데이트 때만 curl 로 받는다'], RGBColor(0xFB, 0xF0, 0xDC)),
}, [('tablet', 'wifi', ''), ('wifi', 'phone', 'HTTP / WebSocket'), ('github', 'tablet', '')])

o.table('기술 스택', ['영역', '기술', '버전 / 비고'], [
    ['백엔드', 'Java · Spring Boot (Web MVC, WebSocket, Scheduling)', 'Java 21 · Spring Boot 4.1.1'],
    ['DB', 'SQLite (sqlite-jdbc) + HikariCP', '파일 하나. WAL 모드, 커넥션 풀 1개 (SQLite 는 쓰기 직렬화)'],
    ['DB 접근', 'Spring JdbcClient + SQL 직접', 'JPA/ORM 없음. Repository 클래스에 SQL 이 보인다'],
    ['JSON', 'Jackson 3', 'null 필드는 생략(non_null) → 클라이언트는 != null 로 비교'],
    ['프론트', 'React 19 · TypeScript · Vite 6', '단일 SPA. /kiosk 와 /staff 두 라우트'],
    ['스타일', '순수 CSS (styles.css 하나)', '프레임워크 없음. 어르신용 큰 버튼, 폰용 작은 컴포넌트'],
    ['빌드', 'Gradle (멀티 프로젝트) + npm', './gradlew bootJar 가 npm build 까지 돌려 jar 안 static/ 에 넣는다'],
    ['테스트', 'JUnit 5 + MockMvc / Playwright / Python 시나리오', '서버 99 · 브라우저 41 · 통합 50 · 마이그레이션'],
    ['배포', 'Termux + Termux:Boot (안드로이드)', '부팅 시 스크립트가 jar 실행. termux-wake-lock'],
    ['런타임 설정', 'config/application.yml + 환경변수', 'PIN · 계좌 · DB 경로 · 백업 폴더 · 포트'],
], widths=[1.2, 3, 3.2])

o.code('저장소 구조', """shine_kiosk/
├─ build.gradle, settings.gradle    루트. bootJar 가 client 빌드를 먼저 돌림
├─ server/                           Spring Boot (Java 21)
│  └─ src/main/java/church/kiosk/    (다음 장)
│     src/main/resources/
│       application.yml              기본 설정 (환경변수로 덮어씀)
│       schema.sql                   테이블 (IF NOT EXISTS, 매 기동 실행)
│       seed-*.sql                   첫 설치 때만 들어가는 기본 데이터
│     src/test/java/church/kiosk/    JUnit + MockMvc 99개
├─ client/                           React + Vite + TypeScript
│  ├─ src/kiosk/                     주문 화면
│  ├─ src/staff/                     스태프 화면
│  ├─ src/shared/                    API · 타입 · 규칙 · 장바구니 · 문자
│  ├─ e2e/                           Playwright 41개, _capture.mjs(매뉴얼 캡처)
│  └─ public/                        아이콘, manifest
├─ config/application.example.yml   실제 값은 config/application.yml (git 제외)
├─ deploy/start-kiosk.sh, README.md 태블릿 부팅 스크립트 · 설치 가이드
├─ tools/integration.py             통합 시나리오 50 (빈 DB 서버에)
│  tools/migration_test.py          옛 jar → 새 jar 업데이트 검증
│  tools/build_manuals.py           이 매뉴얼 생성
├─ docs/manual-test.md              직접 해보는 테스트 시나리오
└─ README.md""", right=[
    ('#', '빌드 산출물'),
    'server/build/libs/kiosk-server.jar (약 38MB, 프론트 포함)',
    ('#', 'git 에 없는 것'),
    'config/application.yml (PIN·계좌)',
    'data/kiosk.db, backups/ (로컬 실행 데이터)',
    'client/node_modules, dist',
    ('#', '줄 끝'),
    '.gitattributes 로 *.sh, *.yml 은 LF 고정 (Termux 에서 CRLF 스크립트가 안 돌기 때문)',
])

o.code('백엔드 패키지 구조', """church.kiosk
├─ KioskServerApplication   진입점. 시간대 Asia/Seoul 고정, @EnableScheduling
├─ config/
│  ├─ DataSourceConfig      SQLite 경로, WAL, 커넥션 풀 1
│  ├─ SchemaMigration       기동 시 컬럼 추가 · 데이터 보정
│  ├─ SeedData              빈 테이블에만 seed-*.sql
│  ├─ BackupService         VACUUM INTO 백업 (기동 시 + 04:00, 10개 유지)
│  ├─ KioskProperties       kiosk.* 설정 바인딩
│  └─ WebConfig · WebSocketConfig · SpaForwardController
├─ staff/     StaffController(/api/staff-auth) · StaffTokenStore
│             StaffAuthInterceptor(X-Staff-Token 검사)
├─ menu/      MenuController(공개) · MenuAdminController
│             MenuCategoryController · Menu*Repository · MenuDtos
├─ place/     PlaceController(공개) · PlaceAdminController · PlaceRepository
├─ coupon/    CouponController · CouponService · CouponRepository
│             Coupon · ChargePolicy(충전 등급/상한)
├─ order/     OrderController(공개) · StaffOrderController
│             OrderService(돈 계산 전부) · OrderRepository · OrderDtos
├─ customer/  단골 명단 (최근 21일 주문 이름)
├─ report/    ReportController(집계 · CSV · 백업 zip) · ReportRepository
├─ realtime/  KioskEventHandler (WebSocket /ws, ORDERS_CHANGED)
└─ support/   Validation(입력 규칙) · BusinessException(400)
              ApiExceptionHandler""", right=[
    ('#', '계층'),
    'Controller: 요청/응답 DTO 만. 검증 어노테이션',
    'Service: 규칙과 트랜잭션 (@Transactional). 돈 계산은 전부 OrderService · CouponService',
    'Repository: JdbcClient + SQL. 결과는 record 로',
    ('#', '오류'),
    'BusinessException → 400 {"message": "…"} 한글 그대로 화면에',
    ('#', '인증'),
    '/api/staff/** 는 X-Staff-Token 헤더. PIN 으로 /api/staff-auth/login 에서 발급. 서버 재시작 시 토큰 초기화',
])

o.table('백엔드 핵심 클래스와 책임', ['클래스', '하는 일'], [
    ['OrderService', '주문 생성(가격은 서버가 메뉴표에서), 멱등(clientRequestId), 쿠폰 적용, 수정(환불→재차감, 거스름돈 흡수), 정산(settle), 잔돈 쿠폰 충전, 완료(거스름돈 확정), 되돌리기, 취소(환불 수단 기록)'],
    ['CouponService', '조회(동명이인 후보), 등록/충전(ChargePolicy: 30,000 상한, 20,000→1잔·30,000→2잔), 번호/이름 변경, 정정, 삭제 가드, 주문 차감/환불/잔돈 충전, 한 달 이력'],
    ['ChargePolicy', '충전 등급과 상한 한 곳. /api/staff/coupons/preset 으로 화면에 내려준다'],
    ['Validation', '휴대폰 11자리, 이름 20/메뉴 30/선택지 10/메모 200자, 가격 100원 단위·10만 이하, 잔액 100만, 수량 99, 줄 50, 층 1~99'],
    ['SchemaMigration', 'COLUMNS 목록에 없는 컬럼을 ALTER 로 추가 + 보정(settled 채우기, 옛 메모→낸 돈), phone_last4 제거, 카테고리 표 생성, 옵션 그룹'],
    ['BackupService', 'VACUUM INTO 로 일관된 스냅샷. backup-dir 에 kiosk-backup-날짜.db, 10개 유지. 다운로드용 임시 스냅샷'],
    ['ReportRepository', '일별 집계(취소 제외) + 쿠폰 충전 입금 + 돌려준 돈(취소 포함)'],
    ['KioskEventHandler', '/ws 접속 목록 유지, 주문 변경 시 ORDERS_CHANGED 전송'],
    ['StaffTokenStore', '메모리 토큰. 검사/발급/폐기'],
], widths=[1.4, 5])

o.code('프론트 구조', """client/src/
├─ main.tsx                경로로 KioskApp / StaffApp 분기
├─ styles.css              전체 스타일 (CSS 변수, 큰 버튼, 폰 카드)
├─ shared/
│  ├─ api.ts               fetch 래퍼 + 모든 엔드포인트 + WebSocket 구독
│  ├─ types.ts             서버 DTO 와 1:1 타입
│  ├─ rules.ts             Validation.java 와 같은 값 (형식 · 길이 · 단위)
│  ├─ cart.ts              장바구니: 한 잔씩 옵션/사역자 토글, 그룹 배타
│  ├─ sms.ts               sms: 링크(iOS/Android), 잔액 문자 본문
│  ├─ Switch.tsx           판매중/품절 스위치 (role=switch)
│  └─ useAutoReload.ts     번들 해시 비교 → 새 버전이면 새로고침
├─ kiosk/
│  ├─ KioskApp.tsx         단계 상태기계
│  │                       menu→cart→receive→place→payment
│  │                       →(transfer|coupon|cash)→name→done
│  ├─ MenuStep · CartStep · ChoiceSteps(받기/장소/결제)
│  └─ PaymentSteps(이체/쿠폰/현금) · NameStep · NamePicker · DoneStep
└─ staff/
   ├─ StaffApp.tsx         로그인 상태, 탭, 상단 집계, WS+20초 폴링
   ├─ LoginPage · OrdersTab(카드 · 정산 · 취소) · EditOrderModal
   ├─ CouponTab · ReportTab
   └─ SettingsTab(홈 목록) · MenuAdmin · OptionAdmin
      · CategoryAdmin · PlaceAdmin""", right=[
    ('#', '원칙'),
    '가격은 클라이언트가 보내지 않는다 — variantId/optionIds 만. 서버가 메뉴표로 계산',
    '서버가 null 을 생략하므로 옵션 필드는 != null 로 검사',
    '키오스크는 2분 idle 이면 초기화, 첫 화면에서만 자동 새로고침',
    '주문 요청마다 clientRequestId(UUID) 를 붙여 재전송 시 중복 방지',
    ('#', '빌드'),
    'npm run build → dist/ → jar 의 static/. index.html 은 no-cache, 자산은 해시 파일명',
])

o.table('DB 스키마 (SQLite, 11 테이블)', ['테이블', '주요 컬럼', '메모'], [
    ['menu_category', 'id, name, sort_order', '키오스크 탭 순서. 이름 변경은 menu_item/menu_option.category 에 전파'],
    ['menu_item / menu_variant', 'name, category, sort_order, available / label(ICE·HOT), price, available', '가격은 variant 에. 삭제 시 지난 주문은 스냅샷이라 무관'],
    ['menu_option', 'name, price, category, option_group, available', '같은 그룹은 한 잔에 하나 (샷 추가/연하게 = 농도)'],
    ['delivery_place', 'floor, name, active, sort_order', '1층만 운영'],
    ['coupon', 'name, phone(숫자 11자리), balance, free_drinks', 'phone_last4 는 phone 에서 계산 (컬럼 제거됨)'],
    ['coupon_tx', 'coupon_id, order_id, delta, free_delta, reason(CHARGE·USE·REFUND·ADJUST), balance_after', '잔돈 충전은 CHARGE + order_id'],
    ['customer', 'name, order_count, last_ordered_at', '단골 명단 (21일)'],
    ['orders', 'order_date, order_no, customer_name, receive_type, place_*, total_amount, staff_free_amount, pay_method, remainder_method, coupon_id, coupon_amount, free_amount, free_item_name, cash_amount, transfer_amount, settled_cash, settled_transfer, cash_given, change_credited, change_paid, refund_cash, refund_transfer, status, memo, edited_at, edit_note, client_request_id', '다음 장 "돈 모델"'],
    ['order_line / order_line_option', 'menu_name, variant_label, unit_price, quantity, staff_free_qty / option name, price', '주문 시점 스냅샷'],
], widths=[1.4, 4.2, 2.6], size=11)

o.bullets('주문의 돈 모델 (orders 컬럼 읽는 법)', [
    ('#', '금액 구성 (항상 성립)'),
    'total_amount = free_amount + coupon_amount + cash_amount + transfer_amount   (staff_free_amount 는 total 밖)',
    ('#', '실제로 받은 돈'),
    'settled_cash / settled_transfer = 손에 들어온 현금/이체. cash_amount − settled_cash > 0 이면 "더 받을 돈", < 0 이면 "돌려줄 돈". 둘 다 0 이어야 완료 가능',
    ('#', '키오스크 현금 (cash_given 이 있는 주문)'),
    'cash_given = 손님이 낸 현금. 거스름돈 = cash_given − cash_amount − change_credited(쿠폰에 넣은 잔돈) − change_paid(완료 때 준 거스름돈)',
    '수정 시 settled_cash = min(cash_amount, cash_given − change_credited − change_paid) → 늘어난 몫은 거스름돈에서 흡수, 넘는 만큼만 더 받기',
    '완료 시 change_paid += 남은 거스름돈 (확정). 되돌려 고치면 그 뒤 차액만',
    ('#', '돌려준 돈'),
    'refund_cash / refund_transfer = 정산·취소로 돌려준 금액 (쿠폰에 넣은 건 coupon_tx REFUND). 매출 탭 "돌려준 돈" 은 취소 주문까지 합산',
    ('#', '쿠폰'),
    '쿠폰 주문 수정: 기존 차감을 REFUND 하고 원래 뺐던 금액을 상한으로 다시 USE. 늘어난 몫은 더 받을 돈 (쿠폰에서 빼기 선택 시 USE 추가). 취소하면 coupon_amount 전부 REFUND',
], size=13)

o.table('API — 공개 (키오스크)', ['메서드 · 경로', '용도'], [
    ['GET /api/menu · /api/menu/categories · /api/menu/options', '판매중 메뉴(카테고리→메뉴 순), 탭 목록(빈 카테고리 포함), 옵션'],
    ['GET /api/places', '표시중 배달 장소 (층별)'],
    ['GET /api/customers/regulars', '단골 이름 (가나다순)'],
    ['POST /api/coupons/lookup {name, phoneLast4?}', 'FOUND / NOT_FOUND / NEED_PHONE(candidates: id·뒤4자리). 전체 번호는 내려주지 않음'],
    ['POST /api/orders/coupon-preview', '쿠폰 적용 미리보기 (무료 1잔, 차감, 나머지)'],
    ['POST /api/orders', '주문 생성. lines[{variantId, quantity, optionIds, staffFreeQty}], payMethod, couponId, useFreeDrink, remainderMethod, cashGiven, memo, clientRequestId'],
    ['GET /api/orders/payment-info', '계좌 안내 문구'],
    ['WS /ws', 'ORDERS_CHANGED 알림'],
], widths=[3, 4])

o.table('API — 스태프 (X-Staff-Token)', ['메서드 · 경로', '용도'], [
    ['POST /api/staff-auth/login {pin} · logout · GET check', '토큰 발급/폐기/확인'],
    ['GET /api/staff/orders?status=PENDING|DONE|CANCELED · GET summary', 'PENDING 은 날짜 무관 전부, 나머지는 오늘. 오늘 집계'],
    ['PUT /api/staff/orders/{id}', '수정 (이름·받는 곳·줄·메모). 결제 수단 불변'],
    ['POST …/{id}/done · reopen · cancel {method, refundToCouponId}', '완료(거스름돈 확정) · 되돌리기 · 취소(환불 수단)'],
    ['POST …/{id}/settle {method, couponId}', '돌려줄 돈(CASH/TRANSFER/COUPON) 또는 더 받을 돈(CASH/TRANSFER/COUPON) 처리'],
    ['POST …/{id}/change-to-coupon {couponId}', '거스름돈을 쿠폰에 충전'],
    ['/api/staff/coupons: POST(등록) · lookup · ?name= · GET {id} · {id}/history · PUT {id}/name · {id}/phone · POST {id}/charge · {id}/adjust · DELETE · GET preset', '쿠폰 관리 전부'],
    ['/api/staff/menu (CRUD, {id}/available, PUT order) · /options · /api/staff/categories (CRUD, order) · /api/staff/places', '설정'],
    ['/api/staff/reports: days · orders?date · days.csv · orders.csv?date · backup.zip · backup.db', '매출 · CSV · 백업'],
], widths=[3.4, 3.6], size=11)

o.bullets('결제·쿠폰 규칙 요약', [
    ('#', '결제'),
    '계좌이체 / 쿠폰 / 현금. 전부 사역자 무료면 NONE (결제 없음). 낼 금액이 0 인데 수단이 있으면 거부, 반대도 거부',
    '현금은 cashGiven 을 함께 보내면 거스름돈 안내가 생긴다 (낸 돈 < 낼 돈이면 거부)',
    ('#', '쿠폰'),
    '주문 금액 전액 차감. 잔액 부족분은 remainderMethod(CASH/TRANSFER) 로. 무료 1잔 = 돈 내는 잔 중 가장 비싼 잔(옵션 포함)',
    '등록/충전은 한 번에 30,000원까지. 20,000 이상 1잔, 30,000 이면 2잔 (충전 금액 기준, 누적 아님). 정정은 100만원까지',
    '전화번호 필수(11자리). 번호 없는 옛 쿠폰은 충전 잠김. 같은 이름 + 같은 뒤 4자리 금지. 쓰인 쿠폰은 삭제 불가',
    ('#', '옵션·사역자'),
    '옵션은 카테고리별, 같은 그룹은 한 잔에 하나. staffFreeQty ≤ quantity',
    ('#', '수정·정산'),
    '수정은 PENDING 만. 완료/취소된 주문은 되돌리기 후. 정산 전 완료 불가',
    '늘어남: 거스름돈 흡수 → 넘는 만큼 현금/이체/쿠폰에서 빼기. 줄어듦: 낸 현금 있으면 거스름돈 증가, 없으면 현금/이체/쿠폰으로 돌려주기',
    ('#', '한도'),
    '한 줄 99잔, 한 주문 50줄, 이름 20자, 메뉴 30자, 선택지 10자, 메모 200자, 가격 100원 단위 10만 이하, 층 1~99',
], size=13)

o.table('설정 값', ['키 (application.yml)', '환경변수', '기본값', '설명'], [
    ['server.port', 'KIOSK_PORT', '8080', '태블릿과 폰이 붙는 포트'],
    ['kiosk.db-path', 'KIOSK_DB', './data/kiosk.db', 'SQLite 파일. 태블릿은 ~/kiosk/data/kiosk.db'],
    ['kiosk.staff-pin', 'KIOSK_STAFF_PIN', '1234', '스태프 PIN. 실제 값은 config/application.yml 에만 (git 제외)'],
    ['kiosk.bank-account', 'KIOSK_BANK', '(안내 문구)', '계좌이체 화면에 그대로 보임'],
    ['kiosk.backup-dir', 'KIOSK_BACKUP_DIR', './backups', '자동 백업 폴더. 태블릿은 ~/storage/downloads/kiosk-backups'],
    ['kiosk.regular-customer-days', '—', '21', '단골 명단 기간'],
], widths=[2, 1.6, 1.8, 3.4], note='읽는 순서: jar 안 application.yml → 실행 폴더의 config/application.yml → 환경변수(가장 우선). 태블릿 스크립트는 ~/kiosk/env.sh 가 있으면 source 한다.')

o.code('빌드 · 로컬 실행 · 테스트', """# 빌드 (프론트 포함, 약 1분) → server/build/libs/kiosk-server.jar
./gradlew bootJar

# 로컬 실행 (config/application.yml 의 PIN/계좌 사용)
java -jar server/build/libs/kiosk-server.jar      --spring.config.additional-location=file:./config/
#   http://localhost:8080/kiosk  ·  /staff

# 프론트만 개발 서버 (API 는 8080 으로 프록시)
cd client && npm run dev

# 테스트
./gradlew :server:test          # JUnit 99 (임시 SQLite, 테스트마다 초기화)
cd client && npm run e2e        # Playwright 41 (jar 를 8091 에 띄움)

# 통합 시나리오 50 — 반드시 빈 DB 서버에
KIOSK_PORT=8092 KIOSK_DB=/tmp/it/kiosk.db KIOSK_STAFF_PIN=1234   java -jar server/build/libs/kiosk-server.jar &
python tools/integration.py http://localhost:8092 1234

# 업데이트 검증 (옛 릴리스 jar → 새 jar)
gh release download v0.1.1 -p kiosk-server.jar -D /tmp/old
python tools/migration_test.py /tmp/old/kiosk-server.jar        server/build/libs/kiosk-server.jar /tmp/mig

# 매뉴얼 캡처 → PPT
KIOSK_PORT=8094 KIOSK_STAFF_PIN=1234 KIOSK_DB=/tmp/cap/kiosk.db java -jar … &
cd client && node e2e/_capture.mjs ../build/shots
python tools/build_manuals.py build/shots docs/manual""", right=[
    ('#', '요구 사항'),
    'JDK 21, Node 20+, 크롬(E2E), Python 3 (도구)',
    ('#', 'Windows 주의'),
    'gh release 는 Git Bash 에서 슬래시 경로로',
    'curl 로 한글 JSON 보내면 CP949 깨짐 → Python 사용',
    ('#', '테스트 데이터'),
    '통합/마이그레이션 스크립트는 주문·쿠폰을 실제로 만든다. 운영 DB 에 절대 돌리지 말 것',
])

o.code('태블릿 배포 (Termux)', """# 1) 앱: Termux 본체 + Termux:Boot 를 GitHub 릴리스(또는 F-Droid)에서
#    Play 스토어 버전 금지. 두 앱 배터리 최적화 제외. Termux:Boot 한 번 열기
# 2) Termux 초기 설정
pkg update && pkg upgrade -y && pkg install -y openjdk-21
termux-setup-storage
mkdir -p ~/kiosk/data ~/kiosk/config ~/.termux/boot
# 3) jar + 부팅 스크립트 (GitHub 릴리스에서)
R=https://github.com/Danjjinbibbang/shine_kiosk/releases/latest/download
curl -L -o ~/kiosk/kiosk-server.jar        $R/kiosk-server.jar
curl -L -o ~/.termux/boot/start-kiosk.sh   $R/start-kiosk.sh
sed -i 's/\\r$//' ~/.termux/boot/start-kiosk.sh
chmod +x ~/.termux/boot/start-kiosk.sh
# 4) 운영 값
cat > ~/kiosk/config/application.yml <<'YML'
kiosk:
  staff-pin: "0000"
  bank-account: "국민 123-45-678901 (열린교회)"
YML
# 5) 첫 실행과 확인
~/.termux/boot/start-kiosk.sh &
tail -f ~/kiosk/kiosk.log     # "Started KioskServerApplication" (30~40초)
ifconfig wlan0 | grep inet    # 폰이 붙을 태블릿 IP
# 태블릿 크롬 http://localhost:8080/kiosk → 홈 화면에 추가 (전체화면)
# 폰 브라우저 http://<IP>:8080/staff     → 홈 화면에 추가""", right=[
    ('#', 'start-kiosk.sh 가 하는 일'),
    'termux-wake-lock (화면 꺼져도 유지)',
    'KIOSK_DB, KIOSK_BACKUP_DIR 설정, ~/kiosk/env.sh 있으면 source',
    '이미 떠 있으면 종료 (pgrep 가드)',
    'java -Xmx512m -jar … >> ~/kiosk/kiosk.log',
    ('#', '주의'),
    '스크립트가 CRLF 면 "no such file" — sed 로 LF',
    'AP 격리(클라이언트 간 통신 차단)가 켜진 와이파이면 폰이 못 붙는다',
    'JVM 시간대는 코드에서 Asia/Seoul 고정 (Termux 기본 UTC)',
], size=10.5)

o.bullets('업데이트 절차 (PC → 태블릿)', [
    ('#', 'PC'),
    '1. 테스트: ./gradlew :server:test → npm run e2e → (선택) tools/integration.py, tools/migration_test.py',
    '2. ./gradlew bootJar',
    '3. Git Bash: gh release create v0.x.y server/build/libs/kiosk-server.jar deploy/start-kiosk.sh -t "v0.x.y" -n "변경 요약"',
    ('#', '태블릿 (Termux)'),
    '4. 백업: cp ~/kiosk/data/kiosk.db ~/storage/downloads/kiosk-before-update.db  (또는 폰에서 백업 내려받기)',
    '5. curl -L -o ~/kiosk/kiosk-server.jar https://github.com/Danjjinbibbang/shine_kiosk/releases/latest/download/kiosk-server.jar',
    '6. pkill -f kiosk-server.jar; ~/.termux/boot/start-kiosk.sh &   (또는 재부팅)',
    '7. tail -f ~/kiosk/kiosk.log 에서 "컬럼 추가", "Started" 확인. 폰/태블릿 화면은 1분 안에 자동 새로고침',
    ('#', '되돌리기'),
    '옛 jar 로 바꾸고 백업 DB 를 ~/kiosk/data/kiosk.db 에 복사 후 재시작. 새 jar 가 추가한 컬럼은 옛 jar 가 무시하므로 DB 만으로도 대개 동작',
    ('#', '스키마 변경은 자동'),
    'SchemaMigration 이 기동 때 없는 컬럼을 추가하고 데이터를 보정한다. 수동 SQL 불필요. 옛 릴리스(v0.1.x) DB 그대로 올려도 된다 (tools/migration_test.py 로 검증)',
], size=13)

o.bullets('백업과 복구', [
    ('#', '자동 백업'),
    '기동 시 + 매일 04:00, VACUUM INTO 로 일관된 스냅샷. 태블릿: 내 파일 > 다운로드 > kiosk-backups/kiosk-backup-YYYY-MM-DD.db (10개 유지)',
    ('#', '폰에서 내려받기'),
    '스태프 > 매출 > 백업 내려받기 → kiosk-backup-날짜.zip = kiosk.db(복구용) + 쿠폰 잔액.csv + 매출-일별.csv + 주문-전체.csv + 읽어주세요.txt',
    '태블릿이 고장 나면 태블릿 안의 자동 백업도 같이 사라진다. 한 달에 한 번은 폰이나 드라이브로',
    ('#', '복구 (새 태블릿 또는 초기화)'),
    '1. 위 배포 절차로 Termux + jar 설치',
    '2. 백업의 kiosk.db 를 ~/kiosk/data/kiosk.db 에 복사 (zip 이면 풀어서)',
    '3. 서버 시작. 옛 버전 백업이라도 SchemaMigration 이 맞춰 준다',
    ('#', '일부만 살리기'),
    'CSV(쿠폰 잔액)를 보고 스태프 화면에서 다시 등록 + 정정. 주문 기록은 CSV 로 보관',
    ('#', '무결성 점검'),
    'sqlite3 kiosk.db "PRAGMA integrity_check"  · 백업 파일 앞 16바이트가 "SQLite format 3"',
], size=13)

o.bullets('성능 · 운영 특성', [
    ('#', '용량'),
    '주일 한 번 수십~수백 건. SQLite 파일은 수 MB. 태블릿 저장 공간 걱정 없음. JVM -Xmx512m',
    '기동 30~40초 (태블릿). 요청 응답은 수 ms. 프론트 번들 ~340KB',
    ('#', '동시성'),
    '커넥션 풀 1 + 트랜잭션 → 주문 번호 중복 없음, 같은 쿠폰 동시 사용 시 잔액 음수 없음 (통합 테스트 M)',
    '키오스크 재전송은 client_request_id UNIQUE 로 한 번만',
    ('#', '실시간'),
    '폰은 WebSocket 으로 즉시 + 20초 폴링 백업. 끊기면 화면 위 띠',
    '새 버전 배포 시 화면이 index.html 의 번들 해시를 1분마다 비교해 스스로 새로고침 (키오스크는 첫 화면일 때만)',
    ('#', '보안 범위'),
    '교회 와이파이 안에서만. HTTPS 없음. 스태프 API 는 PIN 토큰, 공개 API 는 메뉴·주문·쿠폰 조회(전화번호는 뒤 4자리만)',
    'PIN 은 config/application.yml 에만. git 이력에 실제 PIN 을 넣지 말 것',
    ('#', '시간'),
    '주문 날짜/번호는 서버 시각(Asia/Seoul 고정). 폰 시간과 무관',
], size=13)

o.table('문제 해결', ['증상', '원인', '조치'], [
    ['폰에서 접속 안 됨', '다른 와이파이 / AP 격리 / IP 변경', 'ifconfig wlan0 로 IP 확인, 공유기 AP 격리 해제, 홈 화면 아이콘 주소 갱신'],
    ['몇 분 뒤 서버 죽음', '배터리 최적화', 'Termux·Termux:Boot 배터리 최적화 제외, 스크립트의 termux-wake-lock 확인'],
    ['부팅 후 서버 안 뜸', 'Termux:Boot 권한 / 스크립트 CRLF / 옛 프로세스', 'Termux:Boot 한 번 열기, sed -i "s/\\r$//", pgrep -f kiosk-server.jar 로 확인 후 pkill'],
    ['업데이트했는데 옛 화면', '옛 프로세스가 살아 있음 (pgrep 가드) / 릴리스가 옛 것', 'pkill 후 재시작, gh release list 로 latest 확인. 화면은 1분 내 자동 새로고침'],
    ['PIN 이 맞는데 틀리다고 함', 'config/application.yml 이 실행 폴더에 없음 / 들여쓰기', '~/kiosk/config/application.yml 위치와 YAML 들여쓰기(공백 2칸) 확인'],
    ['"수정됨" 시각이 9시간 어긋남', 'JVM 시간대', '코드에서 Asia/Seoul 고정됨. 옛 jar 면 업데이트'],
    ['키오스크에서 쿠폰 못 고름 (????)', '동명이인 중 번호 없는 옛 쿠폰', '스태프 > 쿠폰에서 번호 등록'],
    ['옛 방식 메모가 남은 주문', '재빌드 전 열어둔 키오스크 화면', '기동 때마다 낸 돈으로 변환됨. 재시작'],
    ['E2E 가 sms: 에서 멈춤', '헤드리스 크롬', 'window.__smsCapture 훅 (helpers) 사용 — 이미 적용'],
    ['DB 잠김(SQLITE_BUSY)', '외부에서 DB 파일을 동시에 열었음', '서버 실행 중엔 sqlite3 로 쓰기 금지. 백업은 VACUUM INTO 사용'],
], widths=[1.8, 2.2, 3.6], size=11)

o.bullets('알려진 한계 · 향후 과제', [
    '쿠폰 충전이 현금인지 이체인지는 기록하지 않는다 (요청에 따라 보류). 마감 정산이 필요해지면 충전 버튼에 수단 선택 추가',
    '문자는 스태프 폰의 문자 앱을 열어 주는 방식. 자동 발송(API)은 비용/인증이 필요해 미구현',
    '스태프 토큰은 메모리에만 — 서버 재시작 시 재로그인',
    '태블릿 한 대 = 단일 장애점. 고장 시 복구 절차(백업/복구 장)로 새 태블릿에',
    '가로(1333×800) 레이아웃은 세로 기준으로만 맞춰져 있음. 태블릿은 세로 고정 권장',
    '주문 중 메뉴 가격을 바꾸면 그 손님의 장바구니 표시 가격과 서버 계산이 다를 수 있음 (서버가 기준). 영업 중 가격 변경은 피할 것',
], size=14, note='결정 기록: docs/manual-test.md(직접 테스트 시나리오), README.md(규칙·설정), 커밋 메시지(기능별 변경 이유)')

o.save('열린카페-운영자매뉴얼.pptx')
