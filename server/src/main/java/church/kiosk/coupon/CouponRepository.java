package church.kiosk.coupon;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class CouponRepository {

	private final JdbcClient jdbc;

	public CouponRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private static Coupon map(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
		return new Coupon(rs.getLong("id"), rs.getString("name"),
				rs.getString("phone_last4"), rs.getInt("balance"));
	}

	public List<Coupon> findByName(String name) {
		return jdbc.sql("SELECT id, name, phone_last4, balance FROM coupon WHERE name = :name ORDER BY id")
				.param("name", name)
				.query(CouponRepository::map)
				.list();
	}

	public Optional<Coupon> findById(long id) {
		return jdbc.sql("SELECT id, name, phone_last4, balance FROM coupon WHERE id = :id")
				.param("id", id)
				.query(CouponRepository::map)
				.optional();
	}

	public long insert(String name, String phoneLast4, int balance) {
		String now = LocalDateTime.now().toString();
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
						INSERT INTO coupon (name, phone_last4, balance, created_at, updated_at)
						VALUES (:name, :phone, :balance, :now, :now)
						""")
				.param("name", name).param("phone", phoneLast4)
				.param("balance", balance).param("now", now)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void updateBalance(long couponId, int newBalance) {
		jdbc.sql("UPDATE coupon SET balance = :balance, updated_at = :now WHERE id = :id")
				.param("balance", newBalance)
				.param("now", LocalDateTime.now().toString())
				.param("id", couponId)
				.update();
	}

	public void insertTx(long couponId, Long orderId, int delta, String reason, int balanceAfter) {
		jdbc.sql("""
						INSERT INTO coupon_tx (coupon_id, order_id, delta, reason, balance_after, created_at)
						VALUES (:couponId, :orderId, :delta, :reason, :balanceAfter, :now)
						""")
				.param("couponId", couponId).param("orderId", orderId)
				.param("delta", delta).param("reason", reason)
				.param("balanceAfter", balanceAfter)
				.param("now", LocalDateTime.now().toString())
				.update();
	}
}
