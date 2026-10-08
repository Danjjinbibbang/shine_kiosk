package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스태프가 주문을 고쳤을 때 돈이 어떻게 맞춰지는지.
 * settledCash/settledTransfer = 실제로 받은 돈. cashAmount - settledCash 가 양수면 더 받을 돈, 음수면 돌려줄 돈.
 */
class OrderEditSettlementTests extends ApiTestSupport {

	private Response edit(long id, Map<String, Object>... lines) throws Exception {
		return staffPut("/api/staff/orders/" + id, Map.of("customerName", "손님", "lines", List.of(lines)));
	}

	private long id(Response r) {
		return ((Number) r.read("$.id")).longValue();
	}

	@Test
	@DisplayName("현금 3,000원 받았는데 2,500원짜리로 바꾸면: 돌려줄 500원이 드러나고, 정산하면 사라진다")
	void cashRefundAfterEdit() throws Exception {
		long id = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		Response r = edit(id, line(VANILLA_ICE, 1));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(2500);
		assertThat(r.<Integer>read("$.settledCash")).as("받은 돈은 그대로").isEqualTo(3000);
		assertThat(r.<String>read("$.editedAt")).isNotBlank();
		assertThat(r.<String>read("$.editNote")).isEqualTo("아이스크림 컵 ×1 · 3,000원");

		assertThat(staffPost("/api/staff/orders/" + id + "/settle", null).status()).isEqualTo(200);
		Response after = staffGet("/api/staff/orders");
		assertThat(after.<List<Integer>>read("$[?(@.id==" + id + ")].settledCash")).containsExactly(2500);
		assertThat(after.<List<String>>read("$[?(@.id==" + id + ")].editedAt").get(0)).as("수정 표시는 남는다").isNotBlank();
	}

	@Test
	@DisplayName("차액이 남아 있으면 완료할 수 없고, 정산하면 완료된다")
	void doneBlockedUntilSettled() throws Exception {
		long id = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		edit(id, line(VANILLA_ICE, 1));                                        // 돌려줄 500
		Response blocked = staffPost("/api/staff/orders/" + id + "/done", null);
		assertThat(blocked.status()).isEqualTo(400);
		assertThat(blocked.message()).contains("정산");
		staffPost("/api/staff/orders/" + id + "/settle", null);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);

		long more = id(createOrder(cashOrder("손님", line(AMERICANO_ICE, 1))));
		edit(more, line(AMERICANO_ICE, 3));                                    // 더 받을 2,000
		assertThat(staffPost("/api/staff/orders/" + more + "/done", null).status()).isEqualTo(400);
		// 안 고친 주문은 그냥 완료
		long plain = id(createOrder(cashOrder("손님", line(AMERICANO_ICE, 1))));
		assertThat(staffPost("/api/staff/orders/" + plain + "/done", null).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("더 비싼 걸로 바꾸면 더 받을 돈, 계좌이체도 같은 방식")
	void extraPaymentAfterEdit() throws Exception {
		long cash = id(createOrder(cashOrder("손님", line(AMERICANO_ICE, 1))));
		Response r = edit(cash, line(AMERICANO_ICE, 3));
		assertThat(r.<Integer>read("$.cashAmount") - r.<Integer>read("$.settledCash")).isEqualTo(2000);

		Map<String, Object> t = new HashMap<>(cashOrder("손님", line(ICECREAM_CUP, 1)));
		t.put("payMethod", "TRANSFER");
		long transfer = id(createOrder(t));
		Response r2 = edit(transfer, line(AMERICANO_ICE, 1));
		assertThat(r2.<Integer>read("$.transferAmount")).isEqualTo(1000);
		assertThat(r2.<Integer>read("$.settledTransfer")).isEqualTo(3000);
		assertThat(r2.<Integer>read("$.cashAmount")).isZero();
	}

	@Test
	@DisplayName("두 번 고쳐도 차액은 실제 받은 돈 기준, 정산 뒤엔 새 기준")
	void twoEdits() throws Exception {
		long id = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));   // 3,000 받음
		edit(id, line(VANILLA_ICE, 1));                                        // 2,500 → 돌려줄 500
		Response r = edit(id, line(AMERICANO_ICE, 1));                         // 1,000 → 돌려줄 2,000
		assertThat(r.<Integer>read("$.settledCash")).isEqualTo(3000);
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(1000);
		assertThat(r.<String>read("$.editNote")).as("바로 직전 상태").isEqualTo("바닐라라떼 ICE ×1 · 2,500원");

		staffPost("/api/staff/orders/" + id + "/settle", null);
		Response r3 = edit(id, line(AMERICANO_ICE, 2));                        // 2,000 → 더 받을 1,000
		assertThat(r3.<Integer>read("$.cashAmount") - r3.<Integer>read("$.settledCash")).isEqualTo(1000);
	}

	@Test
	@DisplayName("사역자 잔으로 바꾸면 그만큼 돌려줄 돈, 전부 무료가 되면 현금 0")
	void staffFreeAfterEdit() throws Exception {
		long id = id(createOrder(cashOrder("손님", line(AMERICANO_ICE, 2))));  // 2,000 받음
		Response r = staffPut("/api/staff/orders/" + id, Map.of("customerName", "손님",
				"lines", List.of(Map.of("variantId", AMERICANO_ICE, "quantity", 2, "staffFreeQty", 2))));
		assertThat(r.<Integer>read("$.cashAmount")).isZero();
		assertThat(r.<Integer>read("$.settledCash")).isEqualTo(2000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
	}

	@Test
	@DisplayName("거스름돈 안내는 수정 뒤 지금 금액 기준으로 다시 계산된다. 현금으로 더 받으면 낸 돈도 늘어난다")
	void payNoteFollowsEdits() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("손님", line(ICECREAM_CUP, 1)));   // 3,000
		body.put("cashGiven", 5000);
		Response created = createOrder(body);
		long id = id(created);
		assertThat(created.<String>read("$.payNote")).isEqualTo("현금 5,000원 받음 → 거스름돈 2,000원 쿠폰에 넣기");

		Response r = edit(id, line(VANILLA_ICE, 1));                            // 2,500 → 돌려줄 500
		assertThat(r.<String>read("$.payNote")).isEqualTo("현금 5,000원 받음 → 거스름돈 2,500원 쿠폰에 넣기");
		assertThat(r.body()).as("메모는 비어 있음").doesNotContain("\"memo\"");
		staffPost("/api/staff/orders/" + id + "/settle", null);

		// 6,000 으로 늘면 낸 5,000 을 넘는 1,000 만 더 받을 돈 (거스름돈 2,500 은 흡수됨)
		Response r2 = edit(id, line(ICECREAM_CUP, 2));
		assertThat(r2.<String>read("$.payNote")).isEqualTo("현금 5,000원 받음 → 1,000원 더 받아야");
		assertThat(r2.<Integer>read("$.settledCash")).isEqualTo(5000);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).as("더 받기 전엔 완료 불가").isEqualTo(400);
		staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "CASH"));
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<String>>read("$[?(@.id==" + id + ")].payNote")).containsExactly("현금 6,000원 딱 맞게");

		// 이체로 더 받으면 낸 현금은 그대로
		Response r3 = edit(id, line(ICECREAM_CUP, 3));                          // 9,000 → 더 받을 3,000
		assertThat(r3.<String>read("$.payNote")).isEqualTo("현금 6,000원 받음 → 3,000원 더 받아야");
		staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "TRANSFER"));
		o = staffGet("/api/staff/orders");
		assertThat(o.<List<String>>read("$[?(@.id==" + id + ")].payNote")).as("섞인 결제면 현금 몫을 앞에")
				.containsExactly("현금 몫 6,000원 · 6,000원 딱 맞게");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].transferAmount")).containsExactly(3000);
	}

	@Test
	@DisplayName("거스름돈이 있으면 수정으로 늘어난 몫을 거기서 먼저 제한다. 남은 거스름돈은 쿠폰에 넣어야 완료된다")
	void changeAbsorbsGrowth() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("손님", line(PEACH_TEA, 3)));   // 6,000
		body.put("cashGiven", 10000);
		long id = id(createOrder(body));
		Response r = edit(id, line(PEACH_TEA, 3), line(AMERICANO_ICE, 1));          // 7,000
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(7000);
		assertThat(r.<Integer>read("$.settledCash")).as("낸 돈 안이라 이미 받은 것").isEqualTo(7000);
		assertThat(r.<String>read("$.payNote")).isEqualTo("현금 10,000원 받음 → 거스름돈 3,000원 쿠폰에 넣기");
		Response blocked = staffPost("/api/staff/orders/" + id + "/done", null);
		assertThat(blocked.status()).as("거스름돈은 현금으로 주지 않는다").isEqualTo(400);
		assertThat(blocked.message()).contains("3,000원").contains("쿠폰");
		long coupon = registerCoupon("손님", 20000);
		assertThat(staffPost("/api/staff/orders/" + id + "/change-to-coupon", Map.of("couponId", coupon)).status()).isEqualTo(200);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);

		// 완료 뒤 되돌려서 줄이면 새 거스름돈이 생기고, 늘리면 (쿠폰에 넣은 3,000 은 빼고) 손에 있는 7,000 을 넘는 만큼만 더 받는다
		staffPost("/api/staff/orders/" + id + "/reopen", null);
		Response r2 = edit(id, line(AMERICANO_ICE, 1));                              // 1,000
		assertThat(r2.<Integer>read("$.settledCash")).isEqualTo(1000);
		assertThat(r2.<String>read("$.payNote")).isEqualTo("현금 10,000원 받음 → 거스름돈 9,000원 (3,000원은 쿠폰 충전, 6,000원 쿠폰에 넣기)");
		assertThat(r2.<Integer>read("$.changeDue")).isEqualTo(6000);
		Response r3 = edit(id, line(PEACH_TEA, 4));                                  // 8,000
		assertThat(r3.<Integer>read("$.settledCash")).isEqualTo(7000);
		assertThat(r3.<String>read("$.payNote")).isEqualTo("현금 10,000원 받음 (3,000원은 쿠폰 충전) → 1,000원 더 받아야");
	}

	@Test
	@DisplayName("옛 주문: 거스름돈을 현금으로 줬던 기록(changePaid)도 안내에 그대로 보인다")
	void legacyChangePaid() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("손님", line(PEACH_TEA, 3)));   // 6,000
		body.put("cashGiven", 10000);
		long id = id(createOrder(body));
		jdbc.sql("UPDATE orders SET change_paid = 4000 WHERE id = :id").param("id", id).update();
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<String>>read("$[?(@.id==" + id + ")].payNote")).containsExactly("현금 10,000원 받음 → 거스름돈 4,000원 드림");
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("거스름돈을 쿠폰에 넣기: 잔액 +, 이력은 충전(주문 번호), 낸 돈 = 현금 몫이 되어 거스름돈 0. 두 번은 안 됨")
	void changeToCoupon() throws Exception {
		long coupon = registerCoupon("이영희", 20000);
		Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(VANILLA_ICE, 1)));   // 2,500
		body.put("cashGiven", 3000);
		long id = id(createOrder(body));
		assertThat(staffPost("/api/staff/orders/" + id + "/change-to-coupon", Map.of()).status()).isEqualTo(400);
		Response r = staffPost("/api/staff/orders/" + id + "/change-to-coupon", Map.of("couponId", coupon));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(balanceOf("이영희")).isEqualTo(20500);
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<String>>read("$[?(@.id==" + id + ")].payNote")).containsExactly("현금 3,000원 받음 → 거스름돈 500원은 쿠폰 충전");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].cashGiven")).as("낸 돈은 그대로").containsExactly(3000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].changeCredited")).containsExactly(500);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].changeDue")).containsExactly(0);
		Response h = staffGet("/api/staff/coupons/" + coupon + "/history");
		assertThat(h.<List<String>>read("$[?(@.orderId==" + id + ")].reason")).containsExactly("CHARGE");
		assertThat(staffPost("/api/staff/orders/" + id + "/change-to-coupon", Map.of("couponId", coupon)).message()).contains("거스름돈이 없");
		// 잔돈을 넣은 뒤 주문이 줄면 새 거스름돈이 또 생기고, 늘면 (쿠폰에 넣은 건 못 쓰니) 낸 돈 − 넣은 잔돈을 넘는 만큼 더 받는다
		Response smaller = edit(id, line(AMERICANO_ICE, 1));                                   // 1,000
		assertThat(smaller.<String>read("$.payNote")).isEqualTo("현금 3,000원 받음 → 거스름돈 2,000원 (500원은 쿠폰 충전, 1,500원 쿠폰에 넣기)");
		assertThat(smaller.<Integer>read("$.changeDue")).isEqualTo(1500);
		Response bigger = edit(id, line(ICECREAM_CUP, 1));                                     // 3,000
		assertThat(bigger.<Integer>read("$.settledCash")).as("손에 있는 현금은 2,500 뿐").isEqualTo(2500);
		assertThat(bigger.<String>read("$.payNote")).isEqualTo("현금 3,000원 받음 (500원은 쿠폰 충전) → 500원 더 받아야");
		edit(id, line(VANILLA_ICE, 1));                                                          // 2,500 으로 되돌림
		// 그날 충전 입금에 잡힌다
		Response days = staffGet("/api/staff/reports/days");
		assertThat(days.<Integer>read("$[0].couponChargeAmount")).isEqualTo(20500);

		// 쿠폰 주문의 나머지 현금 잔돈은 접수하자마자 그 쿠폰에 들어간다
		long other = registerCoupon("박민수", 20000);
		long poor = registerCoupon("김철수", 1000);
		Map<String, Object> mixed = new HashMap<>(cashOrder("김철수", line(ICECREAM_CUP, 1)));   // 3,000 = 쿠폰 1,000 + 현금 2,000
		mixed.put("payMethod", "COUPON"); mixed.put("couponId", poor); mixed.put("remainderMethod", "CASH"); mixed.put("cashGiven", 5000);
		Response created = createOrder(mixed);
		long mid = id(created);
		assertThat(created.<Integer>read("$.changeCredited")).isEqualTo(3000);
		assertThat(created.<Integer>read("$.changeDue")).isEqualTo(0);
		assertThat(balanceOf("김철수")).isEqualTo(3000);
		assertThat(staffPost("/api/staff/orders/" + mid + "/change-to-coupon", Map.of("couponId", other)).message()).contains("거스름돈이 없");
		// 줄이면 새로 생긴 거스름돈도 그 쿠폰에 바로 (쿠폰 1,000 만으로 낼 수 있게 되어 현금 2,000 이 통째로 거스름돈)
		edit(mid, line(AMERICANO_ICE, 1));                                                       // 1,000 = 쿠폰 1,000
		assertThat(balanceOf("김철수")).isEqualTo(5000);
		assertThat(staffPost("/api/staff/orders/" + mid + "/done", null).status()).isEqualTo(200);
		// 낸 현금 기록이 없는 주문은 불가
		long plain = id(createOrder(cashOrder("손님", line(AMERICANO_ICE, 1))));
		assertThat(staffPost("/api/staff/orders/" + plain + "/change-to-coupon", Map.of("couponId", coupon)).message()).contains("기록되지 않은");
	}

	@Test
	@DisplayName("거스름돈의 무료 1잔은 거스름돈 금액만 본다 — 잔액과 합쳐 2만원을 넘는 건 적립 아님, 거스름돈이 2만/3만 이상이면 1/2잔")
	void changeFreeDrinksByChangeAmountOnly() throws Exception {
		// 18,000 충전(무료 없음) + 거스름돈 2,500 → 20,500 이지만 무료 0
		long coupon = registerCoupon("이영희", 18000);
		Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(VANILLA_ICE, 1)));   // 2,500
		body.put("cashGiven", 5000);
		long id = id(createOrder(body));
		staffPost("/api/staff/orders/" + id + "/change-to-coupon", Map.of("couponId", coupon));
		assertThat(balanceOf("이영희")).isEqualTo(20500);
		assertThat(freeDrinksOf("이영희")).isZero();

		// 거스름돈 자체가 20,000 이상이면 1잔: 2,500 짜리를 25,000 내면 거스름돈 22,500
		Map<String, Object> big = new HashMap<>(cashOrder("이영희", line(VANILLA_ICE, 1)));
		big.put("cashGiven", 25000);
		long id2 = id(createOrder(big));
		staffPost("/api/staff/orders/" + id2 + "/change-to-coupon", Map.of("couponId", coupon));
		assertThat(balanceOf("이영희")).isEqualTo(43000);
		assertThat(freeDrinksOf("이영희")).isEqualTo(1);
		Response h = staffGet("/api/staff/coupons/" + coupon + "/history");
		assertThat(h.<List<Integer>>read("$[?(@.orderId==" + id2 + ")].freeDelta")).containsExactly(1);

		// 쿠폰 19,000 + 현금 2,000 을 50,000원으로 → 거스름돈 48,000 이 저절로 쿠폰에, 30,000 이상이라 2잔
		long poor = registerCoupon("김철수", 19000);
		Map<String, Object> mixed = new HashMap<>(cashOrder("김철수", line(ICECREAM_CUP, 7)));   // 21,000
		mixed.put("payMethod", "COUPON"); mixed.put("couponId", poor); mixed.put("remainderMethod", "CASH"); mixed.put("cashGiven", 50000);
		assertThat(createOrder(mixed).status()).isEqualTo(200);
		assertThat(balanceOf("김철수")).isEqualTo(48000);
		assertThat(freeDrinksOf("김철수")).isEqualTo(2);

		// 2만/3만원 충전은 그대로 무료 1·2잔
		long charged = registerCoupon("박민수", 20000);
		assertThat(staffPost("/api/staff/coupons/" + charged + "/charge", Map.of("amount", 30000)).status()).isEqualTo(200);
		assertThat(freeDrinksOf("박민수")).isEqualTo(3);
	}

	@Test
	@DisplayName("쿠폰이 없는 손님: 스태프가 쿠폰을 새로 만들며 거스름돈을 넣는다 (잔액 = 거스름돈, 무료잔은 거스름돈 금액 기준)")
	void changeToNewCoupon() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("새손님", line(AMERICANO_ICE, 1)));   // 1,000
		body.put("cashGiven", 10000);
		long id = id(createOrder(body));
		assertThat(staffPost("/api/staff/orders/" + id + "/change-to-new-coupon", Map.of("name", "새손님")).message()).as("전화번호 필수").contains("전화번호");
		Response r = staffPost("/api/staff/orders/" + id + "/change-to-new-coupon", Map.of("name", "새손님", "phone", "010-1234-5678"));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.balance")).isEqualTo(9000);
		assertThat(r.<Integer>read("$.freeDrinks")).isZero();
		assertThat(r.<String>read("$.phoneLast4")).isEqualTo("5678");
		Response h = staffGet("/api/staff/coupons/" + r.read("$.id") + "/history");
		assertThat(h.<List<String>>read("$[*].reason")).as("만들 때 충전 이력은 없고 거스름돈만").containsExactly("CHARGE");
		assertThat(h.<List<Integer>>read("$[*].freeDelta")).containsExactly(0);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);

		// 같은 이름 + 같은 뒤 4자리는 이미 있으니 거부, 아무것도 안 바뀐다
		Map<String, Object> again = new HashMap<>(cashOrder("새손님", line(AMERICANO_ICE, 1)));
		again.put("cashGiven", 5000);
		long id2 = id(createOrder(again));
		assertThat(staffPost("/api/staff/orders/" + id2 + "/change-to-new-coupon", Map.of("name", "새손님", "phone", "01099995678")).message()).contains("이미 있습니다");
		assertThat(balanceOf("새손님")).isEqualTo(9000);

		// 쿠폰 주문은 거스름돈이 이미 그 쿠폰에 들어가 있다
		long coupon = registerCoupon("이영희", 1000);
		Map<String, Object> mixed = new HashMap<>(cashOrder("이영희", line(ICECREAM_CUP, 1)));
		mixed.put("payMethod", "COUPON"); mixed.put("couponId", coupon); mixed.put("remainderMethod", "CASH"); mixed.put("cashGiven", 3000);
		long mid = id(createOrder(mixed));
		assertThat(staffPost("/api/staff/orders/" + mid + "/change-to-new-coupon", Map.of("name", "이영희", "phone", "01011112222")).message()).contains("거스름돈이 없");
	}

	@Test
	@DisplayName("돌려줄 돈은 현금/계좌이체/쿠폰 중 골라서 돌려주고, 현금/이체는 주문과 매출에 남는다")
	void refundMethodRecorded() throws Exception {
		Map<String, Object> t = new HashMap<>(cashOrder("손님", line(ICECREAM_CUP, 1)));   // 이체 3,000
		t.put("payMethod", "TRANSFER");
		long id = id(createOrder(t));
		edit(id, line(VANILLA_ICE, 1));                                                  // 2,500 → 돌려줄 500
		Response r = staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "TRANSFER"));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].refundTransfer")).containsExactly(500);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].refundCash")).containsExactly(0);

		long cash = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));           // 현금 3,000 (낸 돈 기록 없음)
		edit(cash, line(AMERICANO_ICE, 1));                                              // 1,000 → 돌려줄 2,000
		assertThat(staffPost("/api/staff/orders/" + cash + "/settle", null).status()).as("기본은 현금").isEqualTo(200);
		o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash + ")].refundCash")).containsExactly(2000);

		// 취소: 계좌이체로 돌려주고 취소
		long c = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		assertThat(staffPost("/api/staff/orders/" + c + "/cancel", Map.of("method", "TRANSFER")).status()).isEqualTo(200);
		Response canceled = staffGet("/api/staff/orders?status=CANCELED");
		assertThat(canceled.<List<Integer>>read("$[?(@.id==" + c + ")].refundTransfer")).containsExactly(3000);

		// 매출: 돌려준 돈은 취소 주문 것까지 합쳐서
		Response days = staffGet("/api/staff/reports/days");
		assertThat(days.<Integer>read("$[0].refundCash")).isEqualTo(2000);
		assertThat(days.<Integer>read("$[0].refundTransfer")).isEqualTo(3500);
		Response csv = staffGet("/api/staff/reports/days.csv");
		assertThat(csv.body()).contains("돌려준현금,돌려준이체").contains(",2000,3500");
	}

	@Test
	@DisplayName("더 받을 돈은 현금/계좌이체/쿠폰 중 골라서 받는다. 쿠폰은 잔액이 모자라면 거부")
	void extraByChosenMethod() throws Exception {
		// 이체 1,000 → 3,000: 더 받을 2,000 을 현금으로 → 이체 1,000 + 현금 2,000
		Map<String, Object> t = new HashMap<>(cashOrder("손님", line(AMERICANO_ICE, 1)));
		t.put("payMethod", "TRANSFER");
		long id = id(createOrder(t));
		edit(id, line(ICECREAM_CUP, 1));
		Response r = staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "CASH"));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].transferAmount")).containsExactly(1000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].cashAmount")).containsExactly(2000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].settledCash")).containsExactly(2000);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);

		// 현금 1,000 → 3,000: 더 받을 2,000 을 주문자 쿠폰에서 → 쿠폰 2,000 + 현금 1,000, 잔액 18,000
		long coupon = registerCoupon("이영희", 20000);
		long cash = id(createOrder(cashOrder("이영희", line(AMERICANO_ICE, 1))));
		edit(cash, line(ICECREAM_CUP, 1));
		assertThat(staffPost("/api/staff/orders/" + cash + "/settle", Map.of("method", "COUPON")).message()).contains("골라 주세요");
		Response c = staffPost("/api/staff/orders/" + cash + "/settle", Map.of("method", "COUPON", "couponId", coupon));
		assertThat(c.status()).as(c.body()).isEqualTo(200);
		assertThat(balanceOf("이영희")).isEqualTo(18000);
		o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash + ")].couponAmount")).containsExactly(2000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash + ")].cashAmount")).containsExactly(1000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash + ")].couponId")).containsExactly((int) coupon);
		// 취소하면 쿠폰에서 뺀 2,000 은 자동 복원
		staffPost("/api/staff/orders/" + cash + "/cancel", null);
		assertThat(balanceOf("이영희")).isEqualTo(20000);

		// 잔액 500 짜리 쿠폰으로 2,000 을 빼려 하면 거부
		long poor = registerCoupon("김철수", 500);
		long cash2 = id(createOrder(cashOrder("김철수", line(AMERICANO_ICE, 1))));
		edit(cash2, line(ICECREAM_CUP, 1));
		Response bad = staffPost("/api/staff/orders/" + cash2 + "/settle", Map.of("method", "COUPON", "couponId", poor));
		assertThat(bad.status()).isEqualTo(400);
		assertThat(bad.message()).contains("잔액이 500원");
		// 계좌이체로 받기
		assertThat(staffPost("/api/staff/orders/" + cash2 + "/settle", Map.of("method", "TRANSFER")).status()).isEqualTo(200);
		o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash2 + ")].transferAmount")).containsExactly(2000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + cash2 + ")].cashAmount")).containsExactly(1000);
	}

	@Test
	@DisplayName("쿠폰 주문이 커지면 자동으로 더 빼지 않고 더 받을 돈으로 남긴다 → 쿠폰에서 빼기 선택 가능")
	void couponOrderGrows() throws Exception {
		long coupon = registerCoupon("이영희", 20000);
		Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(AMERICANO_ICE, 1)));
		body.put("payMethod", "COUPON");
		body.put("couponId", coupon);
		long id = id(createOrder(body));
		assertThat(balanceOf("이영희")).isEqualTo(19000);
		Response r = edit(id, line(ICECREAM_CUP, 1));                          // 1,000 → 3,000
		assertThat(balanceOf("이영희")).as("자동으로 더 빼지 않는다").isEqualTo(19000);
		assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.cashAmount")).as("더 받을 2,000 이 현금 칸에 표시").isEqualTo(2000);
		assertThat(r.<Integer>read("$.settledCash")).isZero();
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).as("정산 전 완료 불가").isEqualTo(400);
		// 다른 쿠폰에서는 못 빼고, 그 쿠폰에서 빼면 잔액 17,000 / 쿠폰 3,000 / 현금 0
		long other = registerCoupon("박민수", 20000);
		assertThat(staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "COUPON", "couponId", other)).message()).contains("이 주문에 쓴 쿠폰");
		assertThat(staffPost("/api/staff/orders/" + id + "/settle", Map.of("method", "COUPON", "couponId", coupon)).status()).isEqualTo(200);
		assertThat(balanceOf("이영희")).isEqualTo(17000);
		Response o = staffGet("/api/staff/orders");
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].couponAmount")).containsExactly(3000);
		assertThat(o.<List<Integer>>read("$[?(@.id==" + id + ")].cashAmount")).containsExactly(0);
		assertThat(staffPost("/api/staff/orders/" + id + "/done", null).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("쿠폰 주문이 줄면 차액이 잔액으로 저절로 돌아가고 돌려줄 현금은 없다. 쿠폰+현금 주문은 현금 차액만")
	void couponAutoAdjusts() throws Exception {
		long coupon = registerCoupon("이영희", 20000);
		Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(ICECREAM_CUP, 1)));
		body.put("payMethod", "COUPON");
		body.put("couponId", coupon);
		long id = id(createOrder(body));
		assertThat(balanceOf("이영희")).isEqualTo(17000);
		Response r = edit(id, line(AMERICANO_ICE, 1));
		assertThat(balanceOf("이영희")).isEqualTo(19000);
		assertThat(r.<Integer>read("$.cashAmount")).isZero();
		assertThat(r.<Integer>read("$.settledCash")).isZero();

		// 잔액 1,500 + 현금 1,500 로 결제한 주문을 1,000원짜리로 → 쿠폰이 1,000 다 내고 현금은 0 → 현금 1,500 돌려줄 것
		long poor = registerCoupon("김철수", 1500);
		Map<String, Object> mixed = new HashMap<>(cashOrder("김철수", line(ICECREAM_CUP, 1)));
		mixed.put("payMethod", "COUPON");
		mixed.put("couponId", poor);
		mixed.put("remainderMethod", "CASH");
		long mixedId = id(createOrder(mixed));
		Response m = staffGet("/api/staff/orders");
		assertThat(m.<List<Integer>>read("$[?(@.id==" + mixedId + ")].settledCash")).containsExactly(1500);
		Response r2 = edit(mixedId, line(AMERICANO_ICE, 1));
		assertThat(r2.<Integer>read("$.couponAmount")).isEqualTo(1000);
		assertThat(r2.<Integer>read("$.cashAmount")).isZero();
		assertThat(r2.<Integer>read("$.settledCash")).isEqualTo(1500);
		assertThat(balanceOf("김철수")).isEqualTo(500);
	}

	@Test
	@DisplayName("돌려줄 돈을 쿠폰에 넣기: 쿠폰 주문은 그 쿠폰에, 현금 주문은 고른 쿠폰에")
	void refundToCoupon() throws Exception {
		// 잔액 1,500 + 현금 1,500 → 1,000원짜리로: 쿠폰이 1,000 내고 현금 1,500 돌려줄 것
		long coupon = registerCoupon("김철수", 1500);
		Map<String, Object> body = new HashMap<>(cashOrder("김철수", line(ICECREAM_CUP, 1)));
		body.put("payMethod", "COUPON");
		body.put("couponId", coupon);
		body.put("remainderMethod", "CASH");
		long id = id(createOrder(body));
		edit(id, line(AMERICANO_ICE, 1));
		assertThat(balanceOf("김철수")).isEqualTo(500);

		// 쿠폰 주문: 다른 쿠폰엔 못 넣고, 그 쿠폰엔 들어간다
		long other = registerCoupon("이영희", 20000);
		assertThat(staffPost("/api/staff/orders/" + id + "/settle", Map.of("couponId", other)).message()).contains("이 주문에 쓴 쿠폰");
		Response r = staffPost("/api/staff/orders/" + id + "/settle", Map.of("couponId", coupon));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(balanceOf("김철수")).as("돌려줄 1,500 이 잔액으로").isEqualTo(2000);
		Response after = staffGet("/api/staff/orders");
		assertThat(after.<List<Integer>>read("$[?(@.id==" + id + ")].settledCash")).containsExactly(0);
		// 두 번 누르면 돌려줄 게 없다
		assertThat(staffPost("/api/staff/orders/" + id + "/settle", Map.of("couponId", coupon)).message()).contains("돌려줄 금액이 없");

		// 현금 주문: 스태프가 고른 쿠폰(주문자 이름으로 찾은 것)에 넣는다
		long cash = id(createOrder(cashOrder("이영희", line(ICECREAM_CUP, 1))));
		edit(cash, line(AMERICANO_ICE, 1));                                   // 2,000 돌려줄 것
		assertThat(staffGet("/api/staff/coupons?name=이영희").<List<Integer>>read("$[*].id")).containsExactly((int) other);
		assertThat(staffPost("/api/staff/orders/" + cash + "/settle", Map.of("couponId", 999)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/orders/" + cash + "/settle", Map.of("couponId", other)).status()).isEqualTo(200);
		assertThat(balanceOf("이영희")).isEqualTo(22000);

		// 현금으로 돌려준 경우: 쿠폰 없이 정산
		long cash2 = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		edit(cash2, line(AMERICANO_ICE, 1));
		assertThat(staffPost("/api/staff/orders/" + cash2 + "/settle", null).status()).isEqualTo(200);
	}

	@Test
	@DisplayName("전부 사역자(결제 없음)였던 주문에 유료 잔이 생기면 현금으로 더 받을 돈")
	void noneToCash() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("손님", Map.of("variantId", AMERICANO_ICE, "quantity", 1, "staffFreeQty", 1)));
		body.put("payMethod", "NONE");
		long id = id(createOrder(body));
		Response r = edit(id, line(AMERICANO_ICE, 1), line(ICECREAM_CUP, 1));
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(4000);
		assertThat(r.<Integer>read("$.settledCash")).isZero();
	}

	@Test
	@DisplayName("취소: 받은 돈은 현금으로 돌려주거나(기본) 쿠폰 잔액에 넣을 수 있다")
	void cancelRefund() throws Exception {
		// 기본: 현금으로 돌려준 것으로 보고 받은 돈을 0 으로
		long id = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		staffPost("/api/staff/orders/" + id + "/cancel", null);
		assertThat(staffGet("/api/staff/orders?status=CANCELED").<List<Integer>>read("$[?(@.id==" + id + ")].settledCash")).containsExactly(0);

		// 현금 주문을 취소하면서 이름의 쿠폰에 넣기
		long coupon = registerCoupon("이영희", 20000);
		long cash = id(createOrder(cashOrder("이영희", line(ICECREAM_CUP, 1))));
		assertThat(staffPost("/api/staff/orders/" + cash + "/cancel", Map.of("refundToCouponId", 999)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/orders/" + cash + "/cancel", Map.of("refundToCouponId", coupon)).status()).isEqualTo(200);
		assertThat(balanceOf("이영희")).isEqualTo(23000);

		// 쿠폰+현금 주문 취소: 쿠폰 몫은 자동 복원, 현금 몫도 그 쿠폰에 (다른 쿠폰은 거부)
		long poor = registerCoupon("김철수", 1500);
		Map<String, Object> mixed = new HashMap<>(cashOrder("김철수", line(ICECREAM_CUP, 1)));
		mixed.put("payMethod", "COUPON");
		mixed.put("couponId", poor);
		mixed.put("remainderMethod", "CASH");
		long mixedId = id(createOrder(mixed));                                       // 쿠폰 1,500 + 현금 1,500
		assertThat(balanceOf("김철수")).isZero();
		assertThat(staffPost("/api/staff/orders/" + mixedId + "/cancel", Map.of("refundToCouponId", coupon)).message()).contains("이 주문에 쓴 쿠폰");
		staffPost("/api/staff/orders/" + mixedId + "/cancel", Map.of("refundToCouponId", poor));
		assertThat(balanceOf("김철수")).as("쿠폰 1,500 복원 + 현금 1,500 넣음").isEqualTo(3000);

		// 두 번 취소해도 두 번 환불되지 않는다
		staffPost("/api/staff/orders/" + mixedId + "/cancel", Map.of("refundToCouponId", poor));
		assertThat(balanceOf("김철수")).isEqualTo(3000);
	}

	@Test
	@DisplayName("안 고친 주문은 editedAt 이 없고 차액도 0")
	void untouched() throws Exception {
		Response r = createOrder(cashOrder("손님", line(AMERICANO_ICE, 1)));
		assertThat(r.body()).doesNotContain("editedAt");
		assertThat(r.<Integer>read("$.settledCash")).isEqualTo(1000);
		assertThat(staffPost("/api/staff/orders/999/settle", null).status()).isEqualTo(400);
	}
}
