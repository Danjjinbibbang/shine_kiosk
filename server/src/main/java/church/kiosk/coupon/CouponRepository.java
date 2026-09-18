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
				rs.getString("phone_last4"), rs.getInt("balance"), rs.getInt("free_drinks"));
	}

	public List<Coupon> findByName(String name) {
		return jdbc.sql("SELECT id, name, phone_last4, balance, free_drinks FROM coupon WHERE name = :name ORDER BY id")
				.param("name", name)
				.query(CouponRepository::map)
				.list();
	}

	public Optional<Coupon> findById(long id) {
		return jdbc.sql("SELECT id, name, phone_last4, balance, free_drinks FROM coupon WHERE id = :id")
				.param("id", id)
				.query(CouponRepository::map)
				.optional();
	}

	public long insert(String name, String phoneLast4, int balance, int freeDrinks) {
		String now = LocalDateTime.now().toString();
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
						INSERT INTO coupon (name, phone_last4, balance, free_drinks, created_at, updated_at)
						VALUES (:name, :phone, :balance, :free, :now, :now)
						""")
				.param("name", name).param("phone", phoneLast4)
				.param("balance", balance).param("free", freeDrinks).param("now", now)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void update(long couponId, int newBalance, int newFreeDrinks) {
		jdbc.sql("UPDATE coupon SET balance = :balance, free_drinks = :free, updated_at = :now WHERE id = :id")
				.param("balance", newBalance)
				.param("free", newFreeDrinks)
				.param("now", LocalDateTime.now().toString())
				.param("id", couponId)
				.update();
	}

	/** 이 쿠폰으로 결제된 주문이 하나라도 있는지 (취소된 주문 포함 — 장부는 남아야 한다). */
	public boolean isUsedByAnyOrder(long couponId) {
		Integer count = jdbc.sql("SELECT COUNT(*) FROM orders WHERE coupon_id = :id")
				.param("id", couponId)
				.query(Integer.class)
				.single();
		return count > 0;
	}

	public void delete(long couponId) {
		jdbc.sql("DELETE FROM coupon_tx WHERE coupon_id = :id").param("id", couponId).update();
		jdbc.sql("DELETE FROM coupon WHERE id = :id").param("id", couponId).update();
	}

	public void insertTx(long couponId, Long orderId, int delta, int freeDelta, String reason, int balanceAfter) {
		jdbc.sql("""
						INSERT INTO coupon_tx (coupon_id, order_id, delta, free_delta, reason, balance_after, created_at)
						VALUES (:couponId, :orderId, :delta, :freeDelta, :reason, :balanceAfter, :now)
						""")
				.param("couponId", couponId).param("orderId", orderId)
				.param("delta", delta).param("freeDelta", freeDelta).param("reason", reason)
				.param("balanceAfter", balanceAfter)
				.param("now", LocalDateTime.now().toString())
				.update();
	}
}
