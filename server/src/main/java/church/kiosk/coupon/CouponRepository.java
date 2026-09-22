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
		return new Coupon(rs.getLong("id"), rs.getString("name"), rs.getString("phone"),
				rs.getInt("balance"), rs.getInt("free_drinks"));
	}

	public List<Coupon> findAll() {
		return jdbc.sql("SELECT id, name, phone, balance, free_drinks FROM coupon ORDER BY name, id")
				.query(CouponRepository::map)
				.list();
	}

	public List<Coupon> findByName(String name) {
		return jdbc.sql("SELECT id, name, phone, balance, free_drinks FROM coupon WHERE name = :name ORDER BY id")
				.param("name", name)
				.query(CouponRepository::map)
				.list();
	}

	public Optional<Coupon> findById(long id) {
		return jdbc.sql("SELECT id, name, phone, balance, free_drinks FROM coupon WHERE id = :id")
				.param("id", id)
				.query(CouponRepository::map)
				.optional();
	}

	public long insert(String name, String phone, int balance, int freeDrinks) {
		String now = LocalDateTime.now().toString();
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
						INSERT INTO coupon (name, phone, balance, free_drinks, created_at, updated_at)
						VALUES (:name, :phone, :balance, :free, :now, :now)
						""")
				.param("name", name).param("phone", phone)
				.param("balance", balance).param("free", freeDrinks).param("now", now)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void rename(long couponId, String name) {
		jdbc.sql("UPDATE coupon SET name = :name, updated_at = :now WHERE id = :id")
				.param("name", name).param("now", LocalDateTime.now().toString()).param("id", couponId)
				.update();
	}

	public void updatePhone(long couponId, String phone) {
		jdbc.sql("UPDATE coupon SET phone = :phone, updated_at = :now WHERE id = :id")
				.param("phone", phone)
				.param("now", LocalDateTime.now().toString()).param("id", couponId)
				.update();
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

	/** 이력 한 줄. orderNo/orderDate 는 주문과 관련된 이력에만. */
	public record TxView(long id, String createdAt, String reason, int delta, int freeDelta, int balanceAfter,
						 Long orderId, Integer orderNo, String orderDate) {}

	public List<TxView> findTxSince(long couponId, String sinceDate) {
		return jdbc.sql("""
						SELECT t.id, t.created_at, t.reason, t.delta, t.free_delta, t.balance_after,
						       t.order_id, o.order_no, o.order_date
						FROM coupon_tx t LEFT JOIN orders o ON o.id = t.order_id
						WHERE t.coupon_id = :id AND substr(t.created_at, 1, 10) >= :since
						ORDER BY t.id DESC
						""")
				.param("id", couponId).param("since", sinceDate)
				.query((rs, n) -> {
					long orderId = rs.getLong("order_id");
					Long orderIdOrNull = rs.wasNull() ? null : orderId;
					int orderNo = rs.getInt("order_no");
					Integer orderNoOrNull = rs.wasNull() ? null : orderNo;
					return new TxView(rs.getLong("id"), rs.getString("created_at"), rs.getString("reason"),
							rs.getInt("delta"), rs.getInt("free_delta"), rs.getInt("balance_after"),
							orderIdOrNull, orderNoOrNull, rs.getString("order_date"));
				})
				.list();
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
