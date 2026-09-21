package church.kiosk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 기록 화면: 날짜별 집계, 그날 주문, CSV. */
class ReportApiTests extends ApiTestSupport {

	private final String today = LocalDate.now().toString();

	private void seedDays() throws Exception {
		long coupon = registerCoupon("이영희", 20000);                                  // 오늘 충전 20,000
		createOrder(cashOrder("a", line(AMERICANO_ICE, 2)));                             // 현금 2,000
		Map<String, Object> t = new HashMap<>(cashOrder("b", line(VANILLA_ICE, 1)));
		t.put("payMethod", "TRANSFER");
		createOrder(t);                                                                  // 이체 2,500
		Map<String, Object> c = new HashMap<>(cashOrder("이영희", line(ICECREAM_CUP, 1), line(AMERICANO_ICE, 1)));
		c.put("payMethod", "COUPON");
		c.put("couponId", coupon);
		c.put("useFreeDrink", true);
		createOrder(c);                                                                  // 무료 3,000 + 쿠폰 1,000
		long canceled = ((Number) createOrder(cashOrder("d", line(ICECREAM_CUP, 1))).read("$.id")).longValue();
		staffPost("/api/staff/orders/" + canceled + "/cancel", null);                    // 취소 → 집계 제외
		// 지난 주일 주문 하나 (날짜만 바꿈)
		long old = ((Number) createOrder(cashOrder("옛날", line(PEACH_TEA, 1))).read("$.id")).longValue();
		jdbc.sql("UPDATE orders SET order_date = '2026-09-14', status = 'DONE' WHERE id = :id").param("id", old).update();
	}

	@Test
	@DisplayName("날짜별 집계: 최근 날짜부터, 취소 제외, 쿠폰 충전 입금 따로")
	void days() throws Exception {
		seedDays();
		Response r = staffGet("/api/staff/reports/days");
		assertThat(r.status()).as(r.body()).isEqualTo(200);
		assertThat(r.<List<String>>read("$[*].date")).containsExactly(today, "2026-09-14");
		assertThat(r.<Integer>read("$[0].orderCount")).isEqualTo(3);
		assertThat(r.<Integer>read("$[0].totalAmount")).as("주문 금액 합 (무료 1잔 포함, 사역자 무료 제외)").isEqualTo(2000 + 2500 + 4000);
		assertThat(r.<Integer>read("$[0].cashAmount")).isEqualTo(2000);
		assertThat(r.<Integer>read("$[0].transferAmount")).isEqualTo(2500);
		assertThat(r.<Integer>read("$[0].couponAmount")).isEqualTo(1000);
		assertThat(r.<Integer>read("$[0].freeAmount")).isEqualTo(3000);
		assertThat(r.<Integer>read("$[0].couponChargeAmount")).isEqualTo(20000);
		assertThat(r.<Integer>read("$[1].orderCount")).isEqualTo(1);
		assertThat(r.<Integer>read("$[1].totalAmount")).isEqualTo(2000);
		assertThat(r.<Integer>read("$[1].couponChargeAmount")).isZero();
	}

	@Test
	@DisplayName("충전만 있고 주문이 없는 날도 목록에 나온다")
	void chargeOnlyDay() throws Exception {
		registerCoupon("이영희", 20000);
		Response r = staffGet("/api/staff/reports/days");
		assertThat(r.<List<String>>read("$[*].date")).containsExactly(today);
		assertThat(r.<Integer>read("$[0].orderCount")).isZero();
		assertThat(r.<Integer>read("$[0].couponChargeAmount")).isEqualTo(20000);
	}

	@Test
	@DisplayName("그날 주문: 취소 포함 전부 번호순, 잘못된 날짜는 400")
	void ordersOfDay() throws Exception {
		seedDays();
		Response r = staffGet("/api/staff/reports/orders?date=" + today);
		assertThat(r.<List<Integer>>read("$[*].orderNo")).containsExactly(1, 2, 3, 4);
		assertThat(r.<List<String>>read("$[*].status")).containsExactly("PENDING", "PENDING", "PENDING", "CANCELED");
		assertThat(staffGet("/api/staff/reports/orders?date=2026-09-14").<List<String>>read("$[*].customerName")).containsExactly("옛날");
		assertThat(staffGet("/api/staff/reports/orders?date=어제").status()).isEqualTo(400);
	}

	@Test
	@DisplayName("CSV: BOM + 헤더 + 줄, 쉼표 있는 이름은 따옴표")
	void csv() throws Exception {
		seedDays();
		createOrder(cashOrder("김, 철수", line(AMERICANO_ICE, 1)));

		MvcResult days = mvc.perform(get("/api/staff/reports/days.csv").header("X-Staff-Token", staffToken)).andReturn();
		assertThat(days.getResponse().getStatus()).isEqualTo(200);
		assertThat(days.getResponse().getContentType()).startsWith("text/csv");
		byte[] bytes = days.getResponse().getContentAsByteArray();
		assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
		String text = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
		assertThat(text.lines().findFirst().orElse("")).startsWith("날짜,주문수,주문금액");
		assertThat(text).contains(today + ",4,");

		MvcResult orders = mvc.perform(get("/api/staff/reports/orders.csv").param("date", today).header("X-Staff-Token", staffToken)).andReturn();
		String body = new String(orders.getResponse().getContentAsByteArray(), 3, orders.getResponse().getContentAsByteArray().length - 3, StandardCharsets.UTF_8);
		assertThat(body).contains("\"김, 철수\"");
		assertThat(body).contains("아이스크림 컵 x1 / 아메리카노 ICE x1");
		assertThat(body).contains(",취소,");
		assertThat(orders.getResponse().getHeader("Content-Disposition")).contains("filename*=UTF-8''");

		assertThat(mvc.perform(get("/api/staff/reports/days.csv")).andReturn().getResponse().getStatus()).as("PIN 필요").isEqualTo(401);
	}
}
