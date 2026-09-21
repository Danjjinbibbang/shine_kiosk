package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CouponApiTests extends ApiTestSupport {

	// ── 등록 / 충전 / 무료 1잔 적립 ──────────────────────────

	@Test
	@DisplayName("20,000원 등록 → 잔액 20,000 + 무료 1잔")
	void registerGrantsFreeDrink() throws Exception {
		Response r = staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", 20000, "phone", "010-1234-5678"));
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<Integer>read("$.balance")).isEqualTo(20000);
		assertThat(r.<String>read("$.phone")).isEqualTo("01012345678");
		assertThat(r.<Integer>read("$.freeDrinks")).isEqualTo(1);
		assertThat(r.<String>read("$.name")).isEqualTo("이영희");
	}

	@Test
	@DisplayName("충전 금액 / 20,000 만큼 무료잔: 10,000 → 0잔, 40,000 → 2잔, 누적")
	void chargeGrantsFreeDrinksPerPreset() throws Exception {
		long id = registerCoupon("이영희", 20000);

		Response r = staffPost("/api/staff/coupons/" + id + "/charge", Map.of("amount", 10000));
		assertThat(r.<Integer>read("$.balance")).isEqualTo(30000);
		assertThat(r.<Integer>read("$.freeDrinks")).isEqualTo(1);

		r = staffPost("/api/staff/coupons/" + id + "/charge", Map.of("amount", 40000));
		assertThat(r.<Integer>read("$.balance")).isEqualTo(70000);
		assertThat(r.<Integer>read("$.freeDrinks")).isEqualTo(3);

		r = staffPost("/api/staff/coupons/" + id + "/charge", Map.of("amount", 19999));
		assertThat(r.<Integer>read("$.freeDrinks")).isEqualTo(3);
	}

	@Test
	@DisplayName("이름 공백, 금액 0/음수, 전화번호 없음/짧음, 없는 쿠폰 충전은 400")
	void registerAndChargeValidation() throws Exception {
		assertThat(staffPost("/api/staff/coupons", Map.of("name", "  ", "amount", 20000, "phone", "01011112222")).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", 0, "phone", "01011112222")).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", -5, "phone", "01011112222")).status()).isEqualTo(400);
		Response noPhone = staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", 20000));
		assertThat(noPhone.status()).isEqualTo(400);
		assertThat(noPhone.message()).contains("전화번호");
		assertThat(staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", 20000, "phone", "123")).message()).contains("10~11자리");
		assertThat(staffPost("/api/staff/coupons/999/charge", Map.of("amount", 1000)).status()).isEqualTo(400);
		long id = registerCoupon("이영희", 20000);
		assertThat(staffPost("/api/staff/coupons/" + id + "/charge", Map.of("amount", 0)).status()).isEqualTo(400);
	}

	@Test
	@DisplayName("등록/충전은 PIN 없이 못 한다")
	void registerRequiresStaff() throws Exception {
		staffToken = null;
		assertThat(staffPost("/api/staff/coupons", Map.of("name", "이영희", "amount", 20000, "phone", "01011112222")).status()).isEqualTo(401);
	}

	// ── 동명이인 ────────────────────────────────────────────

	@Test
	@DisplayName("같은 이름은 전화 뒤 4자리로 구분되고, 뒤 4자리까지 같으면 거부")
	void duplicateNameNeedsPhone() throws Exception {
		registerCoupon("김철수", 20000, "01099990000");

		Response ok = staffPost("/api/staff/coupons", Map.of("name", "김철수", "phone", "010-1111-1234", "amount", 5000));
		assertThat(ok.status()).isEqualTo(200);
		assertThat(ok.<String>read("$.phoneLast4")).isEqualTo("1234");
	}

	@Test
	@DisplayName("조회: 한 명이면 FOUND, 둘이면 NEED_PHONE, 전화로 구분, 틀리면 NOT_FOUND")
	void lookupStatuses() throws Exception {
		assertThat(lookup("없는사람").<String>read("$.status")).isEqualTo("NOT_FOUND");

		registerCoupon("김철수", 20000, "01099990000");
		assertThat(lookup("김철수").<String>read("$.status")).isEqualTo("FOUND");
		assertThat(lookup(" 김철수 ").<String>read("$.status")).as("앞뒤 공백 무시").isEqualTo("FOUND");

		staffPost("/api/staff/coupons", Map.of("name", "김철수", "phone", "010-1111-1234", "amount", 5000));
		Response need = lookup("김철수");
		assertThat(need.<String>read("$.status")).isEqualTo("NEED_PHONE");
		assertThat(need.<Integer>read("$.candidateCount")).isEqualTo(2);

		Response found = postJson("/api/coupons/lookup", Map.of("name", "김철수", "phoneLast4", "1234"));
		assertThat(found.<String>read("$.status")).isEqualTo("FOUND");
		assertThat(found.<Integer>read("$.coupon.balance")).isEqualTo(5000);

		Response miss = postJson("/api/coupons/lookup", Map.of("name", "김철수", "phoneLast4", "9999"));
		assertThat(miss.<String>read("$.status")).isEqualTo("NOT_FOUND");

		assertThat(postJson("/api/coupons/lookup", Map.of("name", "")).status()).isEqualTo(400);
	}

	@Test
	@DisplayName("전화번호: 변경, 형식 검사, 비울 수 없음, 고객 조회엔 전체 번호가 안 나간다")
	void phone() throws Exception {
		long id = registerCoupon("이영희", 20000, "01000001111");
		assertThat(staffGet("/api/staff/coupons/" + id).<String>read("$.phone")).isEqualTo("01000001111");

		Response set = staffPut("/api/staff/coupons/" + id + "/phone", Map.of("phone", "010-2222-3333"));
		assertThat(set.status()).as(set.body()).isEqualTo(200);
		assertThat(set.<String>read("$.phone")).isEqualTo("01022223333");
		assertThat(set.<String>read("$.phoneLast4")).isEqualTo("3333");

		assertThat(staffPut("/api/staff/coupons/" + id + "/phone", Map.of("phone", "123")).message()).contains("10~11자리");

		// 고객 화면 조회에는 phoneLast4 만
		Response pub = lookup("이영희");
		assertThat(pub.<String>read("$.coupon.phoneLast4")).isEqualTo("3333");
		assertThat(pub.body()).doesNotContain("01022223333");
		// 스태프 조회에는 전체 번호
		Response staff = staffPost("/api/staff/coupons/lookup", Map.of("name", "이영희"));
		assertThat(staff.<String>read("$.coupon.phone")).isEqualTo("01022223333");

		// 번호는 비울 수 없다 (동명이인 구분·환불 혼동 방지)
		assertThat(staffPut("/api/staff/coupons/" + id + "/phone", Map.of("phone", "")).message()).contains("전화번호");

		// 동명이인과 뒤 4자리가 겹치면 안 된다
		long other = ((Number) staffPost("/api/staff/coupons", Map.of("name", "이영희", "phone", "01044445555", "amount", 1000)).read("$.id")).longValue();
		assertThat(staffPut("/api/staff/coupons/" + other + "/phone", Map.of("phone", "01000003333")).message()).contains("같은 뒤 4자리");
		assertThat(staffGet("/api/staff/coupons/999").status()).isEqualTo(400);
	}

	// ── 정정 / 삭제 ─────────────────────────────────────────

	@Test
	@DisplayName("정정: 잔액과 무료잔을 바꾸고 차액이 ADJUST 이력으로 남는다")
	void adjust() throws Exception {
		long id = registerCoupon("이영희", 200000); // 실수로 0 하나 더

		Response r = staffPost("/api/staff/coupons/" + id + "/adjust", Map.of("balance", 20000, "freeDrinks", 1));
		assertThat(r.<Integer>read("$.balance")).isEqualTo(20000);
		assertThat(r.<Integer>read("$.freeDrinks")).isEqualTo(1);

		List<Map<String, Object>> tx = jdbc.sql("SELECT delta, free_delta, reason FROM coupon_tx WHERE coupon_id = :id ORDER BY id")
				.param("id", id).query().listOfRows();
		assertThat(tx).hasSize(2);
		assertThat(tx.get(1)).containsEntry("delta", -180000).containsEntry("free_delta", -9).containsEntry("reason", "ADJUST");

		assertThat(staffPost("/api/staff/coupons/" + id + "/adjust", Map.of("balance", -1, "freeDrinks", 0)).status()).isEqualTo(400);
		assertThat(staffPost("/api/staff/coupons/" + id + "/adjust", Map.of("balance", 0, "freeDrinks", -1)).status()).isEqualTo(400);
	}

	@Test
	@DisplayName("삭제: 안 쓴 쿠폰은 이력까지 지워지고, 주문에 쓰인 쿠폰은 막힌다")
	void delete() throws Exception {
		long unused = registerCoupon("실수", 20000);
		assertThat(staffDelete("/api/staff/coupons/" + unused).status()).isEqualTo(200);
		assertThat(lookup("실수").<String>read("$.status")).isEqualTo("NOT_FOUND");
		assertThat(jdbc.sql("SELECT COUNT(*) FROM coupon_tx WHERE coupon_id = :id").param("id", unused)
				.query(Integer.class).single()).isZero();

		long used = registerCoupon("사용됨", 20000);
		Map<String, Object> order = new HashMap<>(cashOrder("사용됨", line(AMERICANO_ICE, 1)));
		order.put("payMethod", "COUPON");
		order.put("couponId", used);
		assertThat(createOrder(order).status()).isEqualTo(200);

		Response blocked = staffDelete("/api/staff/coupons/" + used);
		assertThat(blocked.status()).isEqualTo(400);
		assertThat(blocked.message()).contains("정정");

		assertThat(staffDelete("/api/staff/coupons/999").status()).isEqualTo(400);
	}
}
