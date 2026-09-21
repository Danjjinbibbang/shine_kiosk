package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 사역자 무료: 장바구니에서 잔마다 '사역자' 를 체크하면 그 잔은 0원. 누구인지는 묻지 않는다. */
class StaffFreeApiTests extends ApiTestSupport {

	private Map<String, Object> lineFree(long variantId, int qty, int staffFreeQty) {
		return Map.of("variantId", variantId, "quantity", qty, "staffFreeQty", staffFreeQty);
	}

	private Map<String, Object> order(String name, String payMethod, List<Map<String, Object>> lines) {
		Map<String, Object> m = new HashMap<>();
		m.put("customerName", name);
		m.put("receiveType", "STORE");
		m.put("payMethod", payMethod);
		m.put("lines", lines);
		return m;
	}

	@Test
	@DisplayName("3잔 중 2잔 사역자: 받을 금액은 1잔, 사역자 무료 금액 따로")
	void partialFree() throws Exception {
		Response r = createOrder(order("박목사", "CASH", List.of(lineFree(AMERICANO_ICE, 3, 2))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.lines[0].staffFreeQty")).isEqualTo(2);
	}

	@Test
	@DisplayName("전부 사역자면 결제 없음(NONE), 돈 낼 게 있는데 NONE 이면 400")
	void allFreeNeedsNone() throws Exception {
		Response none = createOrder(order("박목사", "NONE", List.of(lineFree(AMERICANO_ICE, 2, 2), lineFree(ICECREAM_CUP, 1, 1))));
		assertThat(none.status()).as(none.body()).isEqualTo(200);
		assertThat(none.<Integer>read("$.totalAmount")).isZero();
		assertThat(none.<Integer>read("$.staffFreeAmount")).isEqualTo(5000);
		assertThat(none.<String>read("$.payMethod")).isEqualTo("NONE");

		assertThat(createOrder(order("박목사", "CASH", List.of(lineFree(AMERICANO_ICE, 1, 1)))).message()).contains("결제 없이");
		assertThat(createOrder(order("박목사", "NONE", List.of(lineFree(AMERICANO_ICE, 2, 1)))).message()).contains("결제 수단");
		assertThat(createOrder(order("박목사", "NONE", List.of(line(AMERICANO_ICE, 1)))).message()).contains("결제 수단");
	}

	@Test
	@DisplayName("사역자 잔 수는 0 ~ 수량")
	void staffFreeRange() throws Exception {
		assertThat(createOrder(order("a", "CASH", List.of(lineFree(AMERICANO_ICE, 2, 3)))).message()).contains("잔 수");
		assertThat(createOrder(order("a", "CASH", List.of(lineFree(AMERICANO_ICE, 2, -1)))).message()).contains("잔 수");
		Response plain = createOrder(order("a", "CASH", List.of(lineFree(AMERICANO_ICE, 2, 0))));
		assertThat(plain.<Integer>read("$.staffFreeAmount")).isZero();
	}

	@Test
	@DisplayName("쿠폰 무료 1잔은 돈 내는 잔 중에서만 고른다")
	void couponFreeDrinkIgnoresStaffFreeCups() throws Exception {
		long coupon = registerCoupon("박목사", 20000);
		Map<String, Object> body = order("박목사", "COUPON", List.of(lineFree(ICECREAM_CUP, 1, 1), line(AMERICANO_ICE, 1)));
		body.put("couponId", coupon);
		body.put("useFreeDrink", true);
		Response r = createOrder(body);
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(3000);
		assertThat(r.<Integer>read("$.freeAmount")).as("아이스크림은 사역자 무료라 제외, 아메리카노가 무료 1잔").isEqualTo(1000);
		assertThat(r.<Integer>read("$.couponAmount")).isZero();
		assertThat(balanceOf("박목사")).isEqualTo(20000);
	}

	@Test
	@DisplayName("수정: 사역자 잔 수를 바꾸면 금액이 다시 계산되고, 일반 주문에도 넣을 수 있다")
	void update() throws Exception {
		long orderId = ((Number) createOrder(order("박목사", "NONE", List.of(lineFree(AMERICANO_ICE, 2, 2)))).read("$.id")).longValue();
		Response r = staffPut("/api/staff/orders/" + orderId, Map.of("customerName", "박목사", "receiveType", "STORE",
				"lines", List.of(lineFree(AMERICANO_ICE, 3, 1))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(2000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.cashAmount")).as("결제 없음이던 주문에 돈 낼 잔이 생기면 현금").isEqualTo(2000);

		long plain = ((Number) createOrder(cashOrder("손님", line(AMERICANO_ICE, 2))).read("$.id")).longValue();
		Response r2 = staffPut("/api/staff/orders/" + plain, Map.of("customerName", "손님", "receiveType", "STORE",
				"lines", List.of(lineFree(AMERICANO_ICE, 2, 1))));
		assertThat(r2.<Integer>read("$.staffFreeAmount")).isEqualTo(1000);
		assertThat(r2.<Integer>read("$.cashAmount")).isEqualTo(1000);
	}

	@Test
	@DisplayName("오늘 집계에 사역자 무료 금액이 따로 잡힌다")
	void summary() throws Exception {
		createOrder(order("박목사", "CASH", List.of(lineFree(AMERICANO_ICE, 3, 2))));
		Response s = staffGet("/api/staff/orders/summary");
		assertThat(s.<Integer>read("$.totalAmount")).isEqualTo(1000);
		assertThat(s.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
	}
}
