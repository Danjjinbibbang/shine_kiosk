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
		return staffPut("/api/staff/orders/" + id, Map.of("customerName", "손님", "receiveType", "STORE", "lines", List.of(lines)));
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
		Response r = staffPut("/api/staff/orders/" + id, Map.of("customerName", "손님", "receiveType", "STORE",
				"lines", List.of(Map.of("variantId", AMERICANO_ICE, "quantity", 2, "staffFreeQty", 2))));
		assertThat(r.<Integer>read("$.cashAmount")).isZero();
		assertThat(r.<Integer>read("$.settledCash")).isEqualTo(2000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
	}

	@Test
	@DisplayName("쿠폰 주문은 잔액이 자동으로 맞춰지고 돌려줄 현금은 없다. 쿠폰+현금 주문은 현금 차액만")
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
	@DisplayName("취소한 주문도 받은 돈이 남아 있어 돌려줄 금액을 알 수 있다")
	void cancelKeepsSettled() throws Exception {
		long id = id(createOrder(cashOrder("손님", line(ICECREAM_CUP, 1))));
		staffPost("/api/staff/orders/" + id + "/cancel", null);
		Response r = staffGet("/api/staff/orders?status=CANCELED");
		assertThat(r.<List<Integer>>read("$[?(@.id==" + id + ")].settledCash")).containsExactly(3000);
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
