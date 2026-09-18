package church.kiosk.customer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 단골 명단. 주문할 때마다 이름을 모아두었다가 이름 선택 버튼의 후보로 쓴다.
 * 별도 관리 화면 없이 자동으로 쌓이고, 오래 안 온 이름은 조회에서 빠진다.
 */
@Repository
public class CustomerRepository {

	private final JdbcClient jdbc;

	public CustomerRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public void recordOrder(String name) {
		jdbc.sql("""
						INSERT INTO customer (name, order_count, last_ordered_at)
						VALUES (:name, 1, :now)
						ON CONFLICT(name) DO UPDATE SET
						    order_count = order_count + 1,
						    last_ordered_at = excluded.last_ordered_at
						""")
				.param("name", name)
				.param("now", LocalDateTime.now().toString())
				.update();
	}

	/** 최근 N일 안에 주문한 이름을 가나다순으로. */
	public List<String> findRecentNames(int days) {
		String since = LocalDateTime.now().minusDays(days).toString();
		return jdbc.sql("SELECT name FROM customer WHERE last_ordered_at >= :since ORDER BY name")
				.param("since", since)
				.query(String.class)
				.list();
	}
}
