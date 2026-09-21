package church.kiosk;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * API 테스트 공통 바탕. 테스트마다 임시 SQLite 로 띄우고, 각 테스트 전에 데이터 테이블을 비운다.
 * 메뉴/장소는 시드 그대로 쓴다: 아메리카노 ICE 1001(1,000) · 바닐라라떼 ICE 1201(2,500) ·
 * 아이스크림 컵 3002(3,000) · 복숭아 아이스티 2101(2,000) · 배달장소 101 식당.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

	static final String PIN = "1234";
	static final long AMERICANO_ICE = 1001;
	static final long VANILLA_ICE = 1201;
	static final long ICECREAM_CUP = 3002;
	static final long PEACH_TEA = 2101;
	static final long PLACE_DINING = 101;

	@DynamicPropertySource
	static void tempDatabase(DynamicPropertyRegistry registry) throws Exception {
		Path dir = Files.createTempDirectory("kiosk-test");
		registry.add("kiosk.db-path", () -> dir.resolve("kiosk.db").toString());
		registry.add("kiosk.staff-pin", () -> PIN);
		registry.add("kiosk.bank-account", () -> "테스트은행 000-00");
		registry.add("kiosk.backup-dir", () -> dir.resolve("backups").toString());
	}

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcClient jdbc;

	@Autowired
	protected church.kiosk.config.SeedData seedData;

	protected String staffToken;

	@BeforeEach
	void cleanTables() throws Exception {
		for (String table : List.of("order_line", "orders", "coupon_tx", "coupon", "customer",
				"order_line_option", "menu_variant", "menu_item", "menu_option", "menu_category", "delivery_place")) {
			jdbc.sql("DELETE FROM " + table).update();
		}
		seedData.seedIfEmpty(); // 메뉴/장소는 매번 기본 시드로
		staffToken = JsonPath.read(postJson("/api/staff-auth/login", Map.of("pin", PIN)).body, "$.token");
	}

	// ── HTTP 도우미 ────────────────────────────────────────

	protected record Response(int status, String body) {
		DocumentContext json() { return JsonPath.parse(body); }
		<T> T read(String path) { return json().read(path); }
		String message() { return read("$.message"); }
	}

	protected Response postJson(String path, Object body) throws Exception {
		return exec(post(path).contentType(MediaType.APPLICATION_JSON).content(JsonPath.parse(body).jsonString()), false);
	}

	protected Response staffPost(String path, Object body) throws Exception {
		MockHttpServletRequestBuilder b = post(path).contentType(MediaType.APPLICATION_JSON);
		if (body != null) b = b.content(JsonPath.parse(body).jsonString());
		return exec(b, true);
	}

	protected Response staffPut(String path, Object body) throws Exception {
		return exec(put(path).contentType(MediaType.APPLICATION_JSON).content(JsonPath.parse(body).jsonString()), true);
	}

	protected Response staffGet(String path) throws Exception {
		return exec(get(path), true);
	}

	protected Response staffDelete(String path) throws Exception {
		return exec(delete(path), true);
	}

	protected Response getJson(String path) throws Exception {
		return exec(get(path), false);
	}

	private Response exec(MockHttpServletRequestBuilder builder, boolean asStaff) throws Exception {
		if (asStaff && staffToken != null) {
			builder = builder.header("X-Staff-Token", staffToken);
		}
		MvcResult result = mvc.perform(builder).andReturn();
		return new Response(result.getResponse().getStatus(),
				result.getResponse().getContentAsString(StandardCharsets.UTF_8));
	}

	// ── 시나리오 도우미 ─────────────────────────────────────

	private static int phoneSeq = 1000;

	/** 전화번호는 필수라 테스트마다 다른 번호를 만들어 넣는다. */
	protected long registerCoupon(String name, int amount) throws Exception {
		return registerCoupon(name, amount, "0100000" + (phoneSeq++));
	}

	protected long registerCoupon(String name, int amount, String phone) throws Exception {
		Response r = staffPost("/api/staff/coupons", Map.of("name", name, "amount", amount, "phone", phone));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		return ((Number) r.read("$.id")).longValue();
	}

	protected Map<String, Object> line(long variantId, int qty) {
		return Map.of("variantId", variantId, "quantity", qty);
	}

	protected Response createOrder(Map<String, Object> body) throws Exception {
		return postJson("/api/orders", body);
	}

	protected Map<String, Object> cashOrder(String name, Map<String, Object>... lines) {
		return Map.of("customerName", name, "receiveType", "STORE", "payMethod", "CASH", "lines", List.of(lines));
	}

	protected Response lookup(String name) throws Exception {
		return postJson("/api/coupons/lookup", Map.of("name", name));
	}

	protected int balanceOf(String name) throws Exception {
		return lookup(name).read("$.coupon.balance");
	}

	protected int freeDrinksOf(String name) throws Exception {
		return lookup(name).read("$.coupon.freeDrinks");
	}
}
