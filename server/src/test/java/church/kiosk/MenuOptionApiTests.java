package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 샷 추가(+500) / 연하게(0원). 시드 id: 1 = 샷 추가, 2 = 연하게, 둘 다 커피 전용. */
class MenuOptionApiTests extends ApiTestSupport {

	static final long SHOT = 1;
	static final long MILD = 2;

	private Map<String, Object> lineOpt(long variantId, int qty, Long... optionIds) {
		return Map.of("variantId", variantId, "quantity", qty, "optionIds", List.of(optionIds));
	}

	@Test
	@DisplayName("고객용 옵션 목록: 시드 두 개, 커피 카테고리")
	void publicOptions() throws Exception {
		Response r = getJson("/api/menu/options");
		assertThat(r.<List<String>>read("$[*].name")).containsExactly("샷 추가", "연하게");
		assertThat(r.<List<Integer>>read("$[*].price")).containsExactly(500, 0);
		assertThat(r.<List<String>>read("$[*].category")).containsExactly("커피", "커피");
	}

	@Test
	@DisplayName("샷 추가하면 한 잔 값이 +500, 연하게는 0원, 옵션은 스냅샷으로 남는다")
	void optionPricing() throws Exception {
		Response r = createOrder(cashOrder("a", lineOpt(AMERICANO_ICE, 2, SHOT), lineOpt(AMERICANO_ICE, 1, MILD), line(AMERICANO_ICE, 1)));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(1500 * 2 + 1000 + 1000);
		assertThat(r.<List<Integer>>read("$.lines[*].unitPrice")).containsExactly(1500, 1000, 1000);
		assertThat(r.<List<String>>read("$.lines[0].options[*].name")).containsExactly("샷 추가");
		assertThat(r.<Integer>read("$.lines[0].options[0].price")).isEqualTo(500);
		assertThat(r.<List<String>>read("$.lines[1].options[*].name")).containsExactly("연하게");
		assertThat(r.<List<Object>>read("$.lines[2].options")).isEmpty();

		// 목록 조회에서도 옵션이 붙어 나온다
		Response list = staffGet("/api/staff/orders");
		assertThat(list.<List<String>>read("$[0].lines[0].options[*].name")).containsExactly("샷 추가");
	}

	@Test
	@DisplayName("같은 옵션 두 번 보내면 한 번만, 샷 추가 + 연하게 동시 가능")
	void dedupAndCombine() throws Exception {
		Response r = createOrder(cashOrder("a", lineOpt(AMERICANO_ICE, 1, SHOT, SHOT, MILD)));
		assertThat(r.<Integer>read("$.lines[0].unitPrice")).isEqualTo(1500);
		assertThat(r.<List<String>>read("$.lines[0].options[*].name")).containsExactly("샷 추가", "연하게");
	}

	@Test
	@DisplayName("커피가 아닌 메뉴에 옵션을 붙이면 400, 없는 옵션도 400")
	void optionValidation() throws Exception {
		Response wrong = createOrder(cashOrder("a", lineOpt(PEACH_TEA, 1, SHOT)));
		assertThat(wrong.status()).isEqualTo(400);
		assertThat(wrong.message()).contains("복숭아 아이스티").contains("샷 추가");
		assertThat(createOrder(cashOrder("a", lineOpt(AMERICANO_ICE, 1, 999L))).message()).contains("없는 옵션");
	}

	@Test
	@DisplayName("품절 옵션은 고객 목록에서 빠지고 주문에도 못 쓴다")
	void unavailableOption() throws Exception {
		staffPut("/api/staff/menu/options/" + SHOT, Map.of("name", "샷 추가", "price", 500, "category", "커피", "available", false));
		assertThat(getJson("/api/menu/options").<List<String>>read("$[*].name")).containsExactly("연하게");
		assertThat(createOrder(cashOrder("a", lineOpt(AMERICANO_ICE, 1, SHOT))).message()).contains("지금 고를 수 없습니다");
	}

	@Test
	@DisplayName("무료 1잔은 옵션 포함가로 가장 비싼 한 잔: 샷 추가 아메리카노(1,500) vs 아메리카노(1,000)")
	void freeDrinkUsesOptionPrice() throws Exception {
		long coupon = registerCoupon("이영희", 20000);
		Map<String, Object> body = new HashMap<>();
		body.put("customerName", "이영희");
		body.put("receiveType", "STORE");
		body.put("payMethod", "COUPON");
		body.put("couponId", coupon);
		body.put("useFreeDrink", true);
		body.put("lines", List.of(lineOpt(AMERICANO_ICE, 1, SHOT), line(AMERICANO_ICE, 1)));
		Response r = createOrder(body);
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.freeAmount")).isEqualTo(1500);
		assertThat(r.<String>read("$.freeItemName")).isEqualTo("아메리카노 ICE (샷 추가)");
		assertThat(r.<Integer>read("$.couponAmount")).isEqualTo(1000);

		// 미리보기도 같은 기준 (무료잔이 남은 새 쿠폰으로)
		long coupon2 = registerCoupon("김철수", 20000);
		Response p = postJson("/api/orders/coupon-preview", Map.of("couponId", coupon2, "useFreeDrink", true,
				"lines", List.of(lineOpt(AMERICANO_ICE, 1, SHOT), line(PEACH_TEA, 1))));
		assertThat(p.<Integer>read("$.freeAmount")).as("아이스티 2,000 이 샷추가 아메 1,500 보다 비쌈").isEqualTo(2000);
		assertThat(p.<String>read("$.freeItemName")).isEqualTo("복숭아 아이스티");
	}

	@Test
	@DisplayName("스태프 수정 때도 옵션이 반영되고 옛 옵션은 지워진다")
	void updateWithOptions() throws Exception {
		long id = ((Number) createOrder(cashOrder("a", lineOpt(AMERICANO_ICE, 1, SHOT))).read("$.id")).longValue();
		Response r = staffPut("/api/staff/orders/" + id, Map.of("customerName", "a", "receiveType", "STORE",
				"lines", List.of(lineOpt(AMERICANO_ICE, 2, MILD))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.totalAmount")).isEqualTo(2000);
		assertThat(r.<List<String>>read("$.lines[0].options[*].name")).containsExactly("연하게");
		assertThat(jdbc.sql("SELECT COUNT(*) FROM order_line_option").query(Integer.class).single()).isEqualTo(1);
	}

	@Test
	@DisplayName("옵션 관리: 추가/수정/삭제, 삭제해도 지난 주문 스냅샷은 남는다")
	void adminCrud() throws Exception {
		Response created = staffPost("/api/staff/menu/options", Map.of("name", "휘핑", "price", 300, "category", "논커피", "available", true));
		assertThat(created.status()).as(created.body()).isEqualTo(200);
		long id = ((Number) created.read("$.id")).longValue();
		assertThat(getJson("/api/menu/options").<List<String>>read("$[*].name")).containsExactly("샷 추가", "연하게", "휘핑");
		assertThat(createOrder(cashOrder("a", lineOpt(PEACH_TEA, 1, id))).<Integer>read("$.lines[0].unitPrice")).isEqualTo(2300);

		staffPut("/api/staff/menu/options/" + id, Map.of("name", "휘핑크림", "price", 400, "category", "논커피", "available", true));
		assertThat(getJson("/api/menu/options").<List<String>>read("$[?(@.id==" + id + ")].name")).containsExactly("휘핑크림");

		long orderId = ((Number) createOrder(cashOrder("b", lineOpt(PEACH_TEA, 1, id))).read("$.id")).longValue();
		assertThat(staffDelete("/api/staff/menu/options/" + id).status()).isEqualTo(200);
		assertThat(getJson("/api/menu/options").<List<String>>read("$[*].name")).doesNotContain("휘핑크림");
		assertThat(staffGet("/api/staff/orders").<List<String>>read("$[?(@.id==" + orderId + ")].lines[0].options[0].name"))
				.containsExactly("휘핑크림");

		assertThat(staffPost("/api/staff/menu/options", Map.of("name", " ", "price", 0, "category", "커피", "available", true)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/menu/options", Map.of("name", "x", "price", -1, "category", "커피", "available", true)).status()).isEqualTo(400);
		assertThat(staffDelete("/api/staff/menu/options/999").status()).isEqualTo(400);
	}
}
