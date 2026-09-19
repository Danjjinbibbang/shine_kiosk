package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderApiTests extends ApiTestSupport {

	private Map<String, Object> couponOrder(String name, long couponId, Map<String, Object>... lines) {
		Map<String, Object> m = new HashMap<>();
		m.put("customerName", name);
		m.put("receiveType", "STORE");
		m.put("payMethod", "COUPON");
		m.put("couponId", couponId);
		m.put("lines", List.of(lines));
		return m;
	}

	@Nested
	@DisplayName("주문 생성 — 결제 수단별")
	class Create {

		@Test
		@DisplayName("현금: 총액 전부 cashAmount, 서버 가격으로 계산, 번호는 1부터")
		void cash() throws Exception {
			Response r = createOrder(cashOrder("박민수", line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1)));
			assertThat(r.status()).as(r.body()).isEqualTo(200);
			assertThat(r.<Integer>read("$.orderNo")).isEqualTo(1);
			assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(5000);
			assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(5000);
			assertThat(r.<Integer>read("$.transferAmount")).isZero();
			assertThat(r.<Integer>read("$.couponAmount")).isZero();
			assertThat(r.<String>read("$.status")).isEqualTo("PENDING");
			assertThat(r.<List<String>>read("$.lines[*].menuName")).containsExactly("아메리카노", "아이스크림");
			assertThat(r.<List<String>>read("$.lines[*].variantLabel")).containsExactly("ICE", "컵");
			assertThat(r.<List<Integer>>read("$.lines[*].unitPrice")).containsExactly(1000, 3000);
		}

		@Test
		@DisplayName("계좌이체: 총액 전부 transferAmount")
		void transfer() throws Exception {
			Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(VANILLA_ICE, 1)));
			body.put("payMethod", "TRANSFER");
			Response r = createOrder(body);
			assertThat(r.status()).isEqualTo(200);
			assertThat(r.<Integer>read("$.transferAmount")).isEqualTo(2500);
			assertThat(r.<Integer>read("$.cashAmount")).isZero();
		}

		@Test
		@DisplayName("메모(현금 거스름돈 안내)가 저장된다")
		void memo() throws Exception {
			Map<String, Object> body = new HashMap<>(cashOrder("박민수", line(PEACH_TEA, 1)));
			body.put("memo", "현금 5,000원 받음 → 거스름돈 3,000원");
			assertThat(createOrder(body).<String>read("$.memo")).isEqualTo("현금 5,000원 받음 → 거스름돈 3,000원");
			body.put("memo", "   ");
			assertThat(createOrder(body).body()).doesNotContain("\"memo\"");
		}

		@Test
		@DisplayName("배달: 장소 필수, 비활성 장소(2층) 거부, 장소명 스냅샷")
		void delivery() throws Exception {
			Map<String, Object> body = new HashMap<>(cashOrder("박민수", line(AMERICANO_ICE, 1)));
			body.put("receiveType", "DELIVERY");
			Response noPlace = createOrder(body);
			assertThat(noPlace.status()).isEqualTo(400);
			assertThat(noPlace.message()).contains("장소");

			body.put("placeId", 203); // 2층 3번방 — active = 0
			assertThat(createOrder(body).status()).isEqualTo(400);

			body.put("placeId", PLACE_DINING);
			Response ok = createOrder(body);
			assertThat(ok.status()).isEqualTo(200);
			assertThat(ok.<String>read("$.placeName")).isEqualTo("식당");
			assertThat(ok.<Integer>read("$.placeId")).isEqualTo(101);
		}

		@Test
		@DisplayName("매장 수령이면 placeId 를 보내도 무시된다")
		void storeIgnoresPlace() throws Exception {
			Map<String, Object> body = new HashMap<>(cashOrder("박민수", line(AMERICANO_ICE, 1)));
			body.put("placeId", PLACE_DINING);
			Response r = createOrder(body);
			assertThat(r.status()).isEqualTo(200);
			assertThat(r.body()).doesNotContain("placeName");
		}

		@Test
		@DisplayName("유효성: 이름 공백, 빈 장바구니, 수량 0, 없는 메뉴, 수단 누락")
		void validation() throws Exception {
			assertThat(createOrder(cashOrder("", line(AMERICANO_ICE, 1))).message()).contains("이름");
			assertThat(createOrder(cashOrder("박민수")).message()).contains("메뉴");
			assertThat(createOrder(cashOrder("박민수", line(AMERICANO_ICE, 0))).message()).contains("수량");
			assertThat(createOrder(cashOrder("박민수", line(9999, 1))).message()).contains("없는 메뉴");

			Map<String, Object> noPay = new HashMap<>(cashOrder("박민수", line(AMERICANO_ICE, 1)));
			noPay.remove("payMethod");
			assertThat(createOrder(noPay).status()).isEqualTo(400);

			Map<String, Object> noReceive = new HashMap<>(cashOrder("박민수", line(AMERICANO_ICE, 1)));
			noReceive.remove("receiveType");
			assertThat(createOrder(noReceive).status()).isEqualTo(400);
		}

		@Test
		@DisplayName("주문 번호는 같은 날 안에서 1,2,3… 취소돼도 번호는 안 재사용")
		void orderNumbering() throws Exception {
			long first = ((Number) createOrder(cashOrder("a", line(AMERICANO_ICE, 1))).read("$.id")).longValue();
			assertThat(createOrder(cashOrder("b", line(AMERICANO_ICE, 1))).<Integer>read("$.orderNo")).isEqualTo(2);
			staffPost("/api/staff/orders/" + first + "/cancel", null);
			assertThat(createOrder(cashOrder("c", line(AMERICANO_ICE, 1))).<Integer>read("$.orderNo")).isEqualTo(3);
		}

		@Test
		@DisplayName("주문하면 단골 명단에 이름이 쌓이고 가나다순으로 나온다")
		void regulars() throws Exception {
			createOrder(cashOrder("정다은", line(AMERICANO_ICE, 1)));
			createOrder(cashOrder("김철수", line(AMERICANO_ICE, 1)));
			createOrder(cashOrder("김철수", line(AMERICANO_ICE, 1)));
			createOrder(cashOrder("박민수 ", line(AMERICANO_ICE, 1)));
			assertThat(getJson("/api/customers/regulars").<List<String>>read("$")).containsExactly("김철수", "박민수", "정다은");
			assertThat(jdbc.sql("SELECT order_count FROM customer WHERE name = '김철수'").query(Integer.class).single()).isEqualTo(2);
		}
	}

	@Nested
	@DisplayName("쿠폰 결제")
	class CouponPayment {

		@Test
		@DisplayName("잔액 충분 + 무료 안 씀: 총액 전액 잔액에서 차감")
		void fullDeduction() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Response r = createOrder(couponOrder("이영희", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1)));
			assertThat(r.status()).as(r.body()).isEqualTo(200);
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(5000);
			assertThat(r.<Integer>read("$.freeAmount")).isZero();
			assertThat(r.<Integer>read("$.cashAmount")).isZero();
			assertThat(balanceOf("이영희")).isEqualTo(15000);
			assertThat(freeDrinksOf("이영희")).isEqualTo(1);
		}

		@Test
		@DisplayName("무료 1잔 사용: 가장 비싼 한 잔이 빠지고 나머지만 차감, 무료잔 -1")
		void withFreeDrink() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Map<String, Object> body = couponOrder("이영희", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));
			body.put("useFreeDrink", true);
			Response r = createOrder(body);
			assertThat(r.status()).as(r.body()).isEqualTo(200);
			assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(3000);
			assertThat(r.<String>read("$.freeItemName")).isEqualTo("아이스크림 컵");
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(2000);
			assertThat(balanceOf("이영희")).isEqualTo(18000);
			assertThat(freeDrinksOf("이영희")).isZero();
		}

		@Test
		@DisplayName("무료 1잔은 한 잔만: 같은 메뉴 2잔이어도 1잔 값만 빠진다")
		void freeDrinkIsOneCup() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Map<String, Object> body = couponOrder("이영희", id, line(ICECREAM_CUP, 2));
			body.put("useFreeDrink", true);
			Response r = createOrder(body);
			assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(3000);
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(3000);
		}

		@Test
		@DisplayName("무료 1잔이 주문 전체를 덮으면 잔액 차감 0, 잔액 0이어도 주문 가능")
		void freeDrinkCoversWholeOrder() throws Exception {
			long id = registerCoupon("이영희", 20000);
			staffPost("/api/staff/coupons/" + id + "/adjust", Map.of("balance", 0, "freeDrinks", 1));
			Map<String, Object> body = couponOrder("이영희", id, line(AMERICANO_ICE, 1));
			body.put("useFreeDrink", true);
			Response r = createOrder(body);
			assertThat(r.status()).as(r.body()).isEqualTo(200);
			assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(1000);
			assertThat(r.<Integer>read("$.couponAmount")).isZero();
			assertThat(r.<Integer>read("$.cashAmount")).isZero();
		}

		@Test
		@DisplayName("무료잔 없는데 쓰겠다고 하면 400")
		void freeDrinkUnavailable() throws Exception {
			long id = registerCoupon("김철수", 5000);
			Map<String, Object> body = couponOrder("김철수", id, line(AMERICANO_ICE, 1));
			body.put("useFreeDrink", true);
			Response r = createOrder(body);
			assertThat(r.status()).isEqualTo(400);
			assertThat(r.message()).contains("무료");
		}

		@Test
		@DisplayName("잔액 부족: 나머지 수단 없으면 400, 현금/계좌이체 고르면 잔액 다 쓰고 나머지 배분")
		void insufficientBalance() throws Exception {
			long id = registerCoupon("김철수", 1500);
			Map<String, Object> body = couponOrder("김철수", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));

			Response noMethod = createOrder(body);
			assertThat(noMethod.status()).isEqualTo(400);
			assertThat(noMethod.message()).contains("현금 또는 계좌이체");

			body.put("remainderMethod", "COUPON");
			assertThat(createOrder(body).status()).as("쿠폰으로 나머지는 말이 안 됨").isEqualTo(400);

			body.put("remainderMethod", "CASH");
			Response cash = createOrder(body);
			assertThat(cash.status()).isEqualTo(200);
			assertThat(cash.<Integer>read("$.couponAmount")).isEqualTo(1500);
			assertThat(cash.<Integer>read("$.cashAmount")).isEqualTo(3500);
			assertThat(cash.<String>read("$.remainderMethod")).isEqualTo("CASH");
			assertThat(balanceOf("김철수")).isZero();

			long id2 = registerCoupon("박민수", 1500);
			Map<String, Object> body2 = couponOrder("박민수", id2, line(ICECREAM_CUP, 1));
			body2.put("remainderMethod", "TRANSFER");
			Response transfer = createOrder(body2);
			assertThat(transfer.<Integer>read("$.couponAmount")).isEqualTo(1500);
			assertThat(transfer.<Integer>read("$.transferAmount")).isEqualTo(1500);
			assertThat(transfer.<Integer>read("$.cashAmount")).isZero();
		}

		@Test
		@DisplayName("잔액 부족 + 무료 1잔: 무료 먼저 빼고 남은 것을 잔액, 그래도 남으면 현금")
		void insufficientWithFree() throws Exception {
			long id = registerCoupon("김철수", 20000);
			staffPost("/api/staff/coupons/" + id + "/adjust", Map.of("balance", 500, "freeDrinks", 1));
			Map<String, Object> body = couponOrder("김철수", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));
			body.put("useFreeDrink", true);
			body.put("remainderMethod", "CASH");
			Response r = createOrder(body);
			assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(3000);
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(500);
			assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(1500);
		}

		@Test
		@DisplayName("쿠폰 수단인데 couponId 없거나 없는 쿠폰이면 400")
		void couponRequired() throws Exception {
			Map<String, Object> body = couponOrder("이영희", 0, line(AMERICANO_ICE, 1));
			body.remove("couponId");
			assertThat(createOrder(body).message()).contains("쿠폰");
			assertThat(createOrder(couponOrder("이영희", 999, line(AMERICANO_ICE, 1))).status()).isEqualTo(400);
		}

		@Test
		@DisplayName("미리보기는 잔액을 건드리지 않고 같은 계산을 돌려준다 (useFreeDrink 생략 = 안 씀)")
		void preview() throws Exception {
			long id = registerCoupon("이영희", 20000);
			List<Map<String, Object>> lines = List.of(line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));

			Response off = postJson("/api/orders/coupon-preview", Map.of("couponId", id, "lines", lines));
			assertThat(off.status()).isEqualTo(200);
			assertThat(off.<Boolean>read("$.useFreeDrink")).isFalse();
			assertThat(off.<Integer>read("$.couponAmount")).isEqualTo(5000);
			assertThat(off.<Integer>read("$.balanceAfter")).isEqualTo(15000);
			assertThat(off.<Integer>read("$.freeDrinksAfter")).isEqualTo(1);

			Response on = postJson("/api/orders/coupon-preview", Map.of("couponId", id, "useFreeDrink", true, "lines", lines));
			assertThat(on.<Integer>read("$.freeAmount")).isEqualTo(3000);
			assertThat(on.<String>read("$.freeItemName")).isEqualTo("아이스크림 컵");
			assertThat(on.<Integer>read("$.couponAmount")).isEqualTo(2000);
			assertThat(on.<Integer>read("$.remainder")).isZero();
			assertThat(on.<Integer>read("$.freeDrinksAfter")).isZero();

			assertThat(balanceOf("이영희")).as("미리보기는 차감 안 함").isEqualTo(20000);

			Response poor = postJson("/api/orders/coupon-preview", Map.of("couponId", registerCoupon("가난", 1000), "lines", lines));
			assertThat(poor.<Integer>read("$.couponAmount")).isEqualTo(1000);
			assertThat(poor.<Integer>read("$.remainder")).isEqualTo(4000);
		}

		@Test
		@DisplayName("쿠폰 이력: USE / REFUND 가 주문 id 와 함께 남는다")
		void ledger() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Map<String, Object> body = couponOrder("이영희", id, line(ICECREAM_CUP, 1));
			body.put("useFreeDrink", true);
			long orderId = ((Number) createOrder(body).read("$.id")).longValue();
			staffPost("/api/staff/orders/" + orderId + "/cancel", null);

			List<Map<String, Object>> tx = jdbc.sql("SELECT order_id, delta, free_delta, reason FROM coupon_tx WHERE coupon_id = :id ORDER BY id")
					.param("id", id).query().listOfRows();
			assertThat(tx).extracting(m -> m.get("reason")).containsExactly("CHARGE", "USE", "REFUND");
			assertThat(tx.get(1)).containsEntry("delta", 0).containsEntry("free_delta", -1).containsEntry("order_id", (int) orderId);
			assertThat(tx.get(2)).containsEntry("delta", 0).containsEntry("free_delta", 1);
		}
	}

	@Nested
	@DisplayName("스태프 주문 처리")
	class StaffOps {

		private long pendingOrder() throws Exception {
			return ((Number) createOrder(cashOrder("박민수", line(AMERICANO_ICE, 1))).read("$.id")).longValue();
		}

		@Test
		@DisplayName("목록: PENDING 은 전부, DONE/CANCELED 는 오늘 것만, 순서는 주문순")
		void list() throws Exception {
			long a = pendingOrder();
			long b = pendingOrder();
			long c = pendingOrder();
			staffPost("/api/staff/orders/" + b + "/done", null);
			staffPost("/api/staff/orders/" + c + "/cancel", null);

			assertThat(staffGet("/api/staff/orders").<List<Integer>>read("$[*].id")).containsExactly((int) a);
			assertThat(staffGet("/api/staff/orders?status=PENDING").<List<Integer>>read("$[*].id")).containsExactly((int) a);
			assertThat(staffGet("/api/staff/orders?status=DONE").<List<Integer>>read("$[*].id")).containsExactly((int) b);
			assertThat(staffGet("/api/staff/orders?status=CANCELED").<List<Integer>>read("$[*].id")).containsExactly((int) c);
			assertThat(staffGet("/api/staff/orders?status=BOGUS").status()).isEqualTo(400);
		}

		@Test
		@DisplayName("지난 날짜의 PENDING 도 목록에 남는다")
		void stalePendingStaysVisible() throws Exception {
			long id = pendingOrder();
			jdbc.sql("UPDATE orders SET order_date = '2000-01-01' WHERE id = :id").param("id", id).update();
			Response r = staffGet("/api/staff/orders");
			assertThat(r.<List<String>>read("$[*].orderDate")).containsExactly("2000-01-01");
		}

		@Test
		@DisplayName("완료 → 되돌리기 → 완료, 이미 처리된 건 400")
		void doneAndReopen() throws Exception {
			long id = pendingOrder();
			assertThat(staffPost("/api/staff/orders/" + id + "/reopen", null).status()).as("PENDING 은 되돌릴 게 없음").isEqualTo(400);

			assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);
			assertThat(staffGet("/api/staff/orders?status=DONE").<String>read("$[0].completedAt")).isNotBlank();
			assertThat(staffPost("/api/staff/orders/" + id + "/done", null).message()).contains("이미");

			assertThat(staffPost("/api/staff/orders/" + id + "/reopen", null).status()).isEqualTo(200);
			assertThat(staffGet("/api/staff/orders").<List<Integer>>read("$[*].id")).containsExactly((int) id);

			assertThat(staffPost("/api/staff/orders/999/done", null).status()).isEqualTo(400);
		}

		@Test
		@DisplayName("취소: 두 번 눌러도 안전, 완료된 것도 취소 가능, 쿠폰(잔액+무료잔) 복원")
		void cancel() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Map<String, Object> body = couponOrder("이영희", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));
			body.put("useFreeDrink", true);
			long orderId = ((Number) createOrder(body).read("$.id")).longValue();
			assertThat(balanceOf("이영희")).isEqualTo(18000);

			staffPost("/api/staff/orders/" + orderId + "/done", null);
			assertThat(staffPost("/api/staff/orders/" + orderId + "/cancel", null).status()).isEqualTo(200);
			assertThat(balanceOf("이영희")).isEqualTo(20000);
			assertThat(freeDrinksOf("이영희")).isEqualTo(1);

			assertThat(staffPost("/api/staff/orders/" + orderId + "/cancel", null).status()).isEqualTo(200);
			assertThat(balanceOf("이영희")).as("두 번 취소해도 두 번 환불되지 않음").isEqualTo(20000);
			assertThat(staffGet("/api/staff/orders?status=CANCELED").<List<Integer>>read("$[*].id")).containsExactly((int) orderId);
		}

		@Test
		@DisplayName("수정: 항목/이름/장소/메모가 바뀌고 금액이 다시 계산된다")
		void update() throws Exception {
			long id = pendingOrder();
			Map<String, Object> body = Map.of(
					"customerName", "박민수2", "receiveType", "DELIVERY", "placeId", PLACE_DINING,
					"lines", List.of(line(VANILLA_ICE, 2)), "memo", "얼음 적게");
			Response r = staffPut("/api/staff/orders/" + id, body);
			assertThat(r.status()).as(r.body()).isEqualTo(200);
			assertThat(r.<String>read("$.customerName")).isEqualTo("박민수2");
			assertThat(r.<String>read("$.placeName")).isEqualTo("식당");
			assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(5000);
			assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(5000);
			assertThat(r.<String>read("$.memo")).isEqualTo("얼음 적게");
			assertThat(r.<List<String>>read("$.lines[*].menuName")).containsExactly("바닐라라떼");
			assertThat(r.<Integer>read("$.orderNo")).as("번호는 유지").isEqualTo(1);

			assertThat(staffPut("/api/staff/orders/" + id, Map.of("customerName", "x", "receiveType", "STORE", "lines", List.of())).status()).isEqualTo(400);
			assertThat(staffPut("/api/staff/orders/" + id, Map.of("customerName", "x", "receiveType", "DELIVERY", "lines", List.of(line(AMERICANO_ICE, 1)))).status()).isEqualTo(400);
		}

		@Test
		@DisplayName("수정: 쿠폰 주문은 환불 후 재차감, 무료 1잔 대상도 새 항목 기준으로")
		void updateRecalculatesCoupon() throws Exception {
			long id = registerCoupon("이영희", 20000);
			Map<String, Object> body = couponOrder("이영희", id, line(AMERICANO_ICE, 2), line(ICECREAM_CUP, 1));
			body.put("useFreeDrink", true);
			long orderId = ((Number) createOrder(body).read("$.id")).longValue();
			assertThat(balanceOf("이영희")).isEqualTo(18000);

			Response r = staffPut("/api/staff/orders/" + orderId, Map.of(
					"customerName", "이영희", "receiveType", "STORE", "lines", List.of(line(VANILLA_ICE, 1), line(AMERICANO_ICE, 1))));
			assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(2500);
			assertThat(r.<String>read("$.freeItemName")).isEqualTo("바닐라라떼 ICE");
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(1000);
			assertThat(balanceOf("이영희")).isEqualTo(19000);
			assertThat(freeDrinksOf("이영희")).isZero();
		}

		@Test
		@DisplayName("수정: 쿠폰 주문이 커져서 잔액을 넘으면 나머지는 현금으로 잡힌다")
		void updateBeyondBalanceFallsBackToCash() throws Exception {
			long id = registerCoupon("김철수", 3000);
			long orderId = ((Number) createOrder(couponOrder("김철수", id, line(AMERICANO_ICE, 1))).read("$.id")).longValue();
			Response r = staffPut("/api/staff/orders/" + orderId, Map.of(
					"customerName", "김철수", "receiveType", "STORE", "lines", List.of(line(ICECREAM_CUP, 2))));
			assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(3000);
			assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(3000);
			assertThat(r.<String>read("$.remainderMethod")).isEqualTo("CASH");
		}

		@Test
		@DisplayName("완료/취소된 주문은 수정할 수 없다")
		void updateOnlyPending() throws Exception {
			long id = pendingOrder();
			staffPost("/api/staff/orders/" + id + "/done", null);
			Response r = staffPut("/api/staff/orders/" + id, Map.of("customerName", "x", "receiveType", "STORE", "lines", List.of(line(AMERICANO_ICE, 1))));
			assertThat(r.status()).isEqualTo(400);
			assertThat(r.message()).contains("이미");
		}

		@Test
		@DisplayName("오늘 집계: 취소 제외, 수단별 합계")
		void summary() throws Exception {
			long coupon = registerCoupon("이영희", 20000);
			createOrder(cashOrder("a", line(AMERICANO_ICE, 2)));                                   // 현금 2000
			Map<String, Object> t = new HashMap<>(cashOrder("b", line(VANILLA_ICE, 1)));
			t.put("payMethod", "TRANSFER");
			createOrder(t);                                                                        // 이체 2500
			Map<String, Object> c = couponOrder("이영희", coupon, line(ICECREAM_CUP, 1), line(AMERICANO_ICE, 1));
			c.put("useFreeDrink", true);
			createOrder(c);                                                                        // 무료 3000 + 쿠폰 1000
			long canceled = ((Number) createOrder(cashOrder("d", line(ICECREAM_CUP, 1))).read("$.id")).longValue();
			staffPost("/api/staff/orders/" + canceled + "/cancel", null);

			Response s = staffGet("/api/staff/orders/summary");
			assertThat(s.<Integer>read("$.orderCount")).isEqualTo(3);
			assertThat(s.<Integer>read("$.totalAmount")).isEqualTo(2000 + 2500 + 4000);
			assertThat(s.<Integer>read("$.cashAmount")).isEqualTo(2000);
			assertThat(s.<Integer>read("$.transferAmount")).isEqualTo(2500);
			assertThat(s.<Integer>read("$.couponAmount")).isEqualTo(1000);
			assertThat(s.<Integer>read("$.freeAmount")).isEqualTo(3000);
		}
	}
}
