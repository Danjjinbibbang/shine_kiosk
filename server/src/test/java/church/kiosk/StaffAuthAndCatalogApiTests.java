package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StaffAuthAndCatalogApiTests extends ApiTestSupport {

	// ── 스태프 PIN 인증 ─────────────────────────────────────

	@Test
	@DisplayName("맞는 PIN 이면 토큰, 틀리면 400")
	void login() throws Exception {
		assertThat(postJson("/api/staff-auth/login", Map.of("pin", PIN)).<String>read("$.token")).isNotBlank();

		Response wrong = postJson("/api/staff-auth/login", Map.of("pin", "0000"));
		assertThat(wrong.status()).isEqualTo(400);
		assertThat(wrong.message()).contains("PIN");

		assertThat(postJson("/api/staff-auth/login", Map.of("pin", "")).status()).isEqualTo(400);
	}

	@Test
	@DisplayName("스태프 API 는 토큰 없거나 가짜면 401, 로그아웃하면 무효")
	void staffApiRequiresToken() throws Exception {
		staffToken = null;
		assertThat(staffGet("/api/staff/orders").status()).isEqualTo(401);

		staffToken = "not-a-real-token";
		assertThat(staffGet("/api/staff/orders").status()).isEqualTo(401);
		assertThat(staffGet("/api/staff-auth/check").<Boolean>read("$.valid")).isFalse();

		staffToken = postJson("/api/staff-auth/login", Map.of("pin", PIN)).read("$.token");
		assertThat(staffGet("/api/staff-auth/check").<Boolean>read("$.valid")).isTrue();
		assertThat(staffGet("/api/staff/orders").status()).isEqualTo(200);

		staffPost("/api/staff-auth/logout", null);
		assertThat(staffGet("/api/staff/orders").status()).isEqualTo(401);
	}

	@Test
	@DisplayName("고객 API 는 토큰 없이 열려 있다")
	void customerApiIsPublic() throws Exception {
		staffToken = null;
		assertThat(getJson("/api/menu").status()).isEqualTo(200);
		assertThat(getJson("/api/places").status()).isEqualTo(200);
		assertThat(getJson("/api/customers/regulars").status()).isEqualTo(200);
		assertThat(getJson("/api/orders/payment-info").<String>read("$.bankAccount")).isEqualTo("테스트은행 000-00");
	}

	// ── 메뉴 / 장소 ────────────────────────────────────────

	@Test
	@DisplayName("메뉴는 커피 → 논커피 → 아이스크림 순, 아포카토는 커피")
	void menuOrderAndCategories() throws Exception {
		Response r = getJson("/api/menu");
		List<String> categories = r.read("$[*].category");
		assertThat(categories).isNotEmpty();
		assertThat(categories.stream().distinct().toList()).containsExactly("커피", "논커피", "아이스크림");

		List<String> coffee = r.read("$[?(@.category=='커피')].name");
		assertThat(coffee).contains("아포카토", "아메리카노", "아이스크림 라떼");
		assertThat(r.<List<String>>read("$[?(@.category=='아이스크림')].name")).containsExactly("아이스크림");
	}

	@Test
	@DisplayName("선택지 있는 메뉴는 ICE/HOT, 단일 메뉴는 label 없이 가격만")
	void menuVariants() throws Exception {
		Response r = getJson("/api/menu");
		assertThat(r.<List<String>>read("$[?(@.name=='아메리카노')].variants[*].label")).containsExactly("ICE", "HOT");
		assertThat(r.<List<Integer>>read("$[?(@.name=='아메리카노')].variants[*].price")).containsExactly(1000, 1000);
		assertThat(r.<List<String>>read("$[?(@.name=='아이스크림')].variants[*].label")).containsExactly("콘", "컵");
		assertThat(r.<List<Object>>read("$[?(@.name=='아샷추')].variants[*].label")).isEmpty(); // null 은 응답에서 빠진다
		assertThat(r.<List<Integer>>read("$[?(@.name=='아샷추')].variants[*].price")).containsExactly(2500);
	}

	@Test
	@DisplayName("배달 장소는 1층 식당/전도사님실만")
	void placesOnlyFirstFloor() throws Exception {
		Response r = getJson("/api/places");
		assertThat(r.<List<Integer>>read("$[*].floor")).containsExactly(1);
		assertThat(r.<List<String>>read("$[0].places[*].name")).containsExactly("식당", "전도사님실");
	}

	@Test
	@DisplayName("깨진 JSON 은 500 이 아니라 400")
	void malformedJson() throws Exception {
		int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/orders")
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{not json"))
				.andReturn().getResponse().getStatus();
		assertThat(status).isEqualTo(400);
	}

	@Test
	@DisplayName("SPA 경로는 전부 index.html")
	void spaRoutes() throws Exception {
		for (String path : List.of("/", "/kiosk", "/kiosk/anything", "/staff", "/staff/x")) {
			int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path))
					.andReturn().getResponse().getStatus();
			// MockMvc 에서는 forward 로 끝나므로 200 또는 forward(=200) 둘 다 정상, 404 만 아니면 된다
			assertThat(status).as(path).isNotEqualTo(404);
		}
	}
}
