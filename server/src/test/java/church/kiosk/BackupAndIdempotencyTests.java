package church.kiosk;

import church.kiosk.config.BackupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

class BackupAndIdempotencyTests extends ApiTestSupport {

	@Autowired
	BackupService backupService;

	@Test
	@DisplayName("같은 요청 번호로 두 번 보내면 주문은 하나, 두 번째 응답은 처음 것")
	void duplicateRequestIsIgnored() throws Exception {
		Map<String, Object> body = new HashMap<>(cashOrder("손님", line(AMERICANO_ICE, 1)));
		body.put("clientRequestId", "abc-123");
		Response first = createOrder(body);
		Response second = createOrder(body);
		assertThat(second.status()).isEqualTo(200);
		assertThat(second.<Integer>read("$.id")).isEqualTo(first.<Integer>read("$.id"));
		assertThat(staffGet("/api/staff/orders").<List<Object>>read("$")).hasSize(1);
		assertThat(getJson("/api/customers/regulars").<List<String>>read("$")).containsExactly("손님");

		// 다른 번호면 새 주문, 번호 없으면 매번 새 주문
		body.put("clientRequestId", "abc-124");
		assertThat(createOrder(body).<Integer>read("$.orderNo")).isEqualTo(2);
		body.remove("clientRequestId");
		createOrder(body);
		createOrder(body);
		assertThat(staffGet("/api/staff/orders").<List<Object>>read("$")).hasSize(4);
	}

	@Test
	@DisplayName("중복 요청은 쿠폰도 한 번만 차감된다")
	void duplicateDoesNotDeductTwice() throws Exception {
		long coupon = registerCoupon("이영희", 20000);
		Map<String, Object> body = new HashMap<>(cashOrder("이영희", line(ICECREAM_CUP, 1)));
		body.put("payMethod", "COUPON");
		body.put("couponId", coupon);
		body.put("clientRequestId", "coupon-1");
		createOrder(body);
		createOrder(body);
		assertThat(balanceOf("이영희")).isEqualTo(17000);
	}

	@Test
	@DisplayName("백업 파일은 열리는 SQLite 이고 주문이 들어 있다, 같은 날 두 번이면 덮어쓴다")
	void backupFile() throws Exception {
		createOrder(cashOrder("백업", line(AMERICANO_ICE, 1)));
		Path file = backupService.backupNow();
		assertThat(Files.exists(file)).isTrue();
		assertThat(file.getFileName().toString()).startsWith("kiosk-backup-").endsWith(".db");
		try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + file);
			 Statement st = c.createStatement();
			 ResultSet rs = st.executeQuery("SELECT customer_name FROM orders")) {
			assertThat(rs.next()).isTrue();
			assertThat(rs.getString(1)).isEqualTo("백업");
		}
		Path again = backupService.backupNow();
		assertThat(again).isEqualTo(file);
	}

	@Test
	@DisplayName("스태프 폰으로 백업 내려받기: PIN 필요, SQLite 헤더로 시작")
	void downloadBackup() throws Exception {
		createOrder(cashOrder("손님", line(AMERICANO_ICE, 1)));
		MvcResult r = mvc.perform(get("/api/staff/reports/backup.db").header("X-Staff-Token", staffToken)).andReturn();
		assertThat(r.getResponse().getStatus()).isEqualTo(200);
		byte[] bytes = r.getResponse().getContentAsByteArray();
		assertThat(new String(bytes, 0, 15, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("SQLite format 3");
		assertThat(r.getResponse().getHeader("Content-Disposition")).contains("kiosk-backup-");
		assertThat(mvc.perform(get("/api/staff/reports/backup.db")).andReturn().getResponse().getStatus()).isEqualTo(401);
	}
}
