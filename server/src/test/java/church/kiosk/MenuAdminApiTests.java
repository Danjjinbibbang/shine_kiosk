package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MenuAdminApiTests extends ApiTestSupport {

	private static Map<String, Object> variant(Long id, String label, int price, boolean available) {
		Map<String, Object> m = new HashMap<>();
		m.put("id", id);
		m.put("label", label);
		m.put("price", price);
		m.put("available", available);
		return m;
	}

	private static Map<String, Object> item(String name, String category, List<Map<String, Object>> variants) {
		return Map.of("name", name, "category", category, "available", true, "variants", variants);
	}

	@Test
	@DisplayName("관리 목록은 품절 포함 전부, 품절 토글하면 고객 메뉴에서만 빠진다")
	void adminListIncludesUnavailable() throws Exception {
		Response off = staffPut("/api/staff/menu/10/available", Map.of("available", false));
		assertThat(off.status()).as(off.body()).isEqualTo(200);
		assertThat(off.<Boolean>read("$.available")).isFalse();

		assertThat(getJson("/api/menu").<List<String>>read("$[*].name")).doesNotContain("아메리카노");
		Response admin = staffGet("/api/staff/menu");
		assertThat(admin.<String>read("$[0].name")).isEqualTo("아메리카노");
		assertThat(admin.<Boolean>read("$[0].available")).isFalse();

		staffPut("/api/staff/menu/10/available", Map.of("available", true));
		assertThat(getJson("/api/menu").<List<String>>read("$[*].name")).contains("아메리카노");
	}

	@Test
	@DisplayName("새 메뉴 추가: 맨 뒤에 붙고, 선택지 없는 메뉴는 label 없이")
	void create() throws Exception {
		Response r = staffPost("/api/staff/menu", item("유자차", "논커피",
				List.of(variant(null, "HOT", 2500, true), variant(null, "ICE", 2500, true))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<List<String>>read("$.variants[*].label")).containsExactly("HOT", "ICE");
		assertThat(r.<Integer>read("$.sortOrder")).isEqualTo(110);

		List<String> names = getJson("/api/menu").read("$[*].name");
		assertThat(names).contains("유자차");
		List<String> cats = getJson("/api/menu").read("$[*].category");
		assertThat(cats.stream().distinct().toList()).as("논커피 묶음 안에 들어간다").containsExactly("커피", "논커피", "아이스크림");

		Response single = staffPost("/api/staff/menu", item("쿠키", "디저트", List.of(variant(null, "", 1500, true))));
		assertThat(single.body()).doesNotContain("\"label\"");
		assertThat(single.<Integer>read("$.variants[0].price")).isEqualTo(1500);
		assertThat(getJson("/api/menu").<List<String>>read("$[*].category").stream().distinct().toList())
				.containsExactly("커피", "논커피", "아이스크림", "디저트");
	}

	@Test
	@DisplayName("수정: 이름/가격 바꾸고, 변형 추가/삭제, 지난 주문은 스냅샷 유지")
	void update() throws Exception {
		long orderId = ((Number) createOrder(cashOrder("a", line(AMERICANO_ICE, 1))).read("$.id")).longValue();

		Response r = staffPut("/api/staff/menu/10", item("아메리카노 리뉴얼", "커피",
				List.of(variant(AMERICANO_ICE, "ICE", 1500, true), variant(null, "디카페인", 2000, true))));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<List<String>>read("$.variants[*].label")).as("HOT(1002) 은 목록에 없어 삭제").containsExactly("ICE", "디카페인");
		assertThat(r.<Integer>read("$.variants[0].price")).isEqualTo(1500);

		assertThat(createOrder(cashOrder("b", line(1002, 1))).message()).contains("없는 메뉴");
		assertThat(createOrder(cashOrder("c", line(AMERICANO_ICE, 1))).<Integer>read("$.totalAmount")).isEqualTo(1500);

		Response old = staffGet("/api/staff/orders");
		assertThat(old.<List<String>>read("$[?(@.id==" + orderId + ")].lines[0].menuName")).containsExactly("아메리카노");
		assertThat(old.<List<Integer>>read("$[?(@.id==" + orderId + ")].lines[0].unitPrice")).containsExactly(1000);
	}

	@Test
	@DisplayName("유효성: 이름/카테고리 공백, 변형 없음, 음수 가격, 없는 메뉴")
	void validation() throws Exception {
		List<Map<String, Object>> one = List.of(variant(null, null, 1000, true));
		assertThat(staffPost("/api/staff/menu", item(" ", "커피", one)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/menu", item("x", "", one)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/menu", item("x", "커피", List.of())).message()).contains("가격");
		assertThat(staffPost("/api/staff/menu", item("x", "커피", List.of(variant(null, null, -1, true)))).status()).isEqualTo(400);
		assertThat(staffPut("/api/staff/menu/999", item("x", "커피", one)).status()).isEqualTo(400);
		assertThat(staffDelete("/api/staff/menu/999").status()).isEqualTo(400);
	}

	@Test
	@DisplayName("삭제: 고객 메뉴에서 사라지고, 담긴 채 주문하면 거부, 지난 주문은 그대로")
	void delete() throws Exception {
		long orderId = ((Number) createOrder(cashOrder("a", line(ICECREAM_CUP, 1))).read("$.id")).longValue();
		assertThat(staffDelete("/api/staff/menu/30").status()).isEqualTo(200);
		assertThat(getJson("/api/menu").<List<String>>read("$[*].name")).doesNotContain("아이스크림");
		assertThat(createOrder(cashOrder("b", line(ICECREAM_CUP, 1))).status()).isEqualTo(400);
		assertThat(staffGet("/api/staff/orders").<List<String>>read("$[?(@.id==" + orderId + ")].lines[0].menuName"))
				.containsExactly("아이스크림");
	}

	@Test
	@DisplayName("순서 변경: 보낸 id 순서대로, 카테고리 순서도 따라간다")
	void reorder() throws Exception {
		List<Integer> ids = staffGet("/api/staff/menu").read("$[*].id");
		List<Long> newOrder = new ArrayList<>();
		newOrder.add(30L); // 아이스크림을 맨 앞으로
		ids.stream().filter(i -> i != 30).forEach(i -> newOrder.add(i.longValue()));

		Response r = staffPut("/api/staff/menu/order", Map.of("ids", newOrder));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<String>read("$[0].name")).isEqualTo("아이스크림");
		List<String> cats = getJson("/api/menu").read("$[*].category");
		assertThat(cats.stream().distinct().toList()).containsExactly("아이스크림", "커피", "논커피");

		assertThat(staffPut("/api/staff/menu/order", Map.of("ids", List.of())).status()).isEqualTo(400);
	}

	@Test
	@DisplayName("시드는 빈 DB 에만: 메뉴를 지우고 재시작해도 되살아나지 않는다")
	void seedOnlyWhenEmpty() throws Exception {
		staffDelete("/api/staff/menu/13");
		seedData.seedIfEmpty();
		assertThat(staffGet("/api/staff/menu").<List<String>>read("$[*].name")).doesNotContain("아샷추");
	}

	// ── 배달 장소 ──────────────────────────────────────────

	@Test
	@DisplayName("장소: 추가/수정/숨김/삭제, 고객 화면은 active 만")
	void places() throws Exception {
		Response created = staffPost("/api/staff/places", Map.of("floor", 2, "name", "1번방", "active", true));
		assertThat(created.status()).as(created.body()).isEqualTo(200);
		long id = ((Number) created.read("$.id")).longValue();
		assertThat(getJson("/api/places").<List<Integer>>read("$[*].floor")).containsExactly(1, 2);

		staffPut("/api/staff/places/" + id, Map.of("floor", 2, "name", "소망방", "active", false));
		assertThat(getJson("/api/places").<List<Integer>>read("$[*].floor")).containsExactly(1);
		assertThat(staffGet("/api/staff/places").<List<String>>read("$[?(@.id==" + id + ")].name")).containsExactly("소망방");

		Map<String, Object> order = new HashMap<>(cashOrder("a", line(AMERICANO_ICE, 1)));
		order.put("receiveType", "DELIVERY");
		order.put("placeId", PLACE_DINING);
		createOrder(order);
		staffDelete("/api/staff/places/" + PLACE_DINING);
		assertThat(staffGet("/api/staff/places").<List<Boolean>>read("$[?(@.id==101)].active"))
				.as("주문이 참조하는 장소는 숨김").containsExactly(false);

		staffDelete("/api/staff/places/" + id);
		assertThat(staffGet("/api/staff/places").<List<Integer>>read("$[*].id")).as("참조 없는 장소는 삭제").doesNotContain((int) id);

		assertThat(staffPost("/api/staff/places", Map.of("floor", 0, "name", "x", "active", true)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/places", Map.of("floor", 1, "name", " ", "active", true)).status()).isEqualTo(400);
		assertThat(staffDelete("/api/staff/places/999").status()).isEqualTo(400);
	}

	@Test
	@DisplayName("관리 API 는 PIN 없이 못 쓴다")
	void requiresStaff() throws Exception {
		staffToken = null;
		assertThat(staffGet("/api/staff/menu").status()).isEqualTo(401);
		assertThat(staffGet("/api/staff/places").status()).isEqualTo(401);
	}
}
