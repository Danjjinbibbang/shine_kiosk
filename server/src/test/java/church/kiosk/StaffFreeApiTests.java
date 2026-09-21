package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 사역자 무료: 명단의 사역자가 주문하면 잔 단위로 무료. */
class StaffFreeApiTests extends ApiTestSupport {

	private long addMember(String name) throws Exception {
		Response r = staffPost("/api/staff/members", Map.of("name", name, "active", true));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		return ((Number) r.read("$.id")).longValue();
	}

	private Map<String, Object> lineFree(long variantId, int qty, int staffFreeQty) {
		return Map.of("variantId", variantId, "quantity", qty, "staffFreeQty", staffFreeQty);
	}

	private Map<String, Object> order(String name, String payMethod, Long memberId, List<Map<String, Object>> lines) {
		Map<String, Object> m = new HashMap<>();
		m.put("customerName", name);
		m.put("receiveType", "STORE");
		m.put("payMethod", payMethod);
		m.put("staffMemberId", memberId);
		m.put("lines", lines);
		return m;
	}

	@Test
	@DisplayName("사역자 명단: 추가/수정/삭제, 공개 목록은 active 만 가나다순")
	void members() throws Exception {
		long a = addMember("박목사");
		long b = addMember("김전도사");
		assertThat(getJson("/api/staff-members").<List<String>>read("$[*].name")).containsExactly("김전도사", "박목사");

		staffPut("/api/staff/members/" + a, Map.of("name", "박목사", "active", false));
		assertThat(getJson("/api/staff-members").<List<String>>read("$[*].name")).containsExactly("김전도사");
		assertThat(staffGet("/api/staff/members").<List<String>>read("$[*].name")).containsExactly("김전도사", "박목사");

		staffDelete("/api/staff/members/" + b);
		assertThat(getJson("/api/staff-members").<List<Object>>read("$")).isEmpty();

		assertThat(staffPost("/api/staff/members", Map.of("name", " ", "active", true)).status()).isEqualTo(400);
		assertThat(staffDelete("/api/staff/members/999").status()).isEqualTo(400);
		staffToken = null;
		assertThat(staffPost("/api/staff/members", Map.of("name", "x", "active", true)).status()).isEqualTo(401);
	}

	@Test
	@DisplayName("3잔 중 2잔 사역자: 받을 금액은 1잔, 사역자 무료 금액 따로, 이름 스냅샷")
	void partialFree() throws Exception {
		long id = addMember("박목사");
		Response r = createOrder(order("박목사", "CASH", id, List.of(lineFree(AMERICANO_ICE, 3, 2))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
		assertThat(r.<String>read("$.staffMemberName")).isEqualTo("박목사");
		assertThat(r.<Integer>read("$.cashAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.lines[0].staffFreeQty")).isEqualTo(2);
	}

	@Test
	@DisplayName("전부 사역자면 결제 없음(NONE), 돈 낼 게 있는데 NONE 이면 400")
	void allFreeNeedsNone() throws Exception {
		long id = addMember("박목사");
		Response none = createOrder(order("박목사", "NONE", id, List.of(lineFree(AMERICANO_ICE, 2, 2), lineFree(ICECREAM_CUP, 1, 1))));
		assertThat(none.status()).as(none.body()).isEqualTo(200);
		assertThat(none.<Integer>read("$.totalAmount")).isZero();
		assertThat(none.<Integer>read("$.staffFreeAmount")).isEqualTo(5000);
		assertThat(none.<String>read("$.payMethod")).isEqualTo("NONE");

		assertThat(createOrder(order("박목사", "CASH", id, List.of(lineFree(AMERICANO_ICE, 1, 1)))).message()).contains("결제 없이");
		assertThat(createOrder(order("박목사", "NONE", id, List.of(lineFree(AMERICANO_ICE, 2, 1)))).message()).contains("결제 수단");
		assertThat(createOrder(order("박목사", "NONE", null, List.of(line(AMERICANO_ICE, 1)))).message()).contains("결제 수단");
	}

	@Test
	@DisplayName("사역자 잔이 있으면 명단의 사역자 id 가 필요하고, 비활성/없는 사역자는 거부")
	void requiresMember() throws Exception {
		assertThat(createOrder(order("아무개", "CASH", null, List.of(lineFree(AMERICANO_ICE, 2, 1)))).message()).contains("사역자를 선택");
		assertThat(createOrder(order("아무개", "CASH", 999L, List.of(lineFree(AMERICANO_ICE, 2, 1)))).message()).contains("명단에 없는");
		long id = addMember("박목사");
		staffPut("/api/staff/members/" + id, Map.of("name", "박목사", "active", false));
		assertThat(createOrder(order("박목사", "CASH", id, List.of(lineFree(AMERICANO_ICE, 2, 1)))).message()).contains("명단에 없는");
		assertThat(createOrder(order("박목사", "CASH", id, List.of(lineFree(AMERICANO_ICE, 2, 3)))).message()).contains("잔 수");
		// 사역자 잔이 없으면 id 를 보내도 그냥 일반 주문
		Response plain = createOrder(order("박목사", "CASH", id, List.of(line(AMERICANO_ICE, 1))));
		assertThat(plain.status()).isEqualTo(200);
		assertThat(plain.body()).doesNotContain("staffMemberName");
	}

	@Test
	@DisplayName("쿠폰 무료 1잔은 돈 내는 잔 중에서만 고른다")
	void couponFreeDrinkIgnoresStaffFreeCups() throws Exception {
		long member = addMember("박목사");
		long coupon = registerCoupon("박목사", 20000);
		Map<String, Object> body = order("박목사", "COUPON", member, List.of(lineFree(ICECREAM_CUP, 1, 1), line(AMERICANO_ICE, 1)));
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
	@DisplayName("수정: 사역자 잔 수를 바꾸면 금액이 다시 계산되고, 사역자 주문이 아니면 넣을 수 없다")
	void update() throws Exception {
		long id = addMember("박목사");
		long orderId = ((Number) createOrder(order("박목사", "NONE", id, List.of(lineFree(AMERICANO_ICE, 2, 2)))).read("$.id")).longValue();
		Response r = staffPut("/api/staff/orders/" + orderId, Map.of("customerName", "박목사", "receiveType", "STORE",
				"lines", List.of(lineFree(AMERICANO_ICE, 3, 1))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(2000);
		assertThat(r.<Integer>read("$.staffFreeAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$.cashAmount")).as("결제 없음이던 주문에 돈 낼 잔이 생기면 현금").isEqualTo(2000);
		assertThat(r.<String>read("$.staffMemberName")).isEqualTo("박목사");

		long plain = ((Number) createOrder(cashOrder("손님", line(AMERICANO_ICE, 1))).read("$.id")).longValue();
		assertThat(staffPut("/api/staff/orders/" + plain, Map.of("customerName", "손님", "receiveType", "STORE",
				"lines", List.of(lineFree(AMERICANO_ICE, 1, 1)))).message()).contains("사역자 주문이 아니");
	}

	@Test
	@DisplayName("오늘 집계에 사역자 무료 금액이 따로 잡힌다")
	void summary() throws Exception {
		long id = addMember("박목사");
		createOrder(order("박목사", "CASH", id, List.of(lineFree(AMERICANO_ICE, 3, 2))));
		Response s = staffGet("/api/staff/orders/summary");
		assertThat(s.<Integer>read("$.totalAmount")).isEqualTo(1000);
		assertThat(s.<Integer>read("$.cashAmount")).isEqualTo(1000);
		assertThat(s.<Integer>read("$.staffFreeAmount")).isEqualTo(2000);
	}
}
