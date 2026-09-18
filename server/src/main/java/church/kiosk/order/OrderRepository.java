package church.kiosk.order;

import church.kiosk.order.OrderDtos.DailySummary;
import church.kiosk.order.OrderDtos.LineView;
import church.kiosk.order.OrderDtos.OrderView;
import church.kiosk.order.OrderDtos.PayMethod;
import church.kiosk.order.OrderDtos.ReceiveType;
import church.kiosk.order.OrderDtos.Status;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class OrderRepository {

	private static final String ORDER_COLUMNS = """
			id, order_date, order_no, customer_name, receive_type, place_id, place_name,
			total_amount, pay_method, remainder_method, coupon_id, coupon_amount,
			free_amount, free_item_name, cash_amount, transfer_amount, status, memo, created_at, completed_at
			""";

	/** 주문 저장에 필요한 값 묶음. 서비스가 계산을 끝낸 뒤 넘긴다. */
	public record OrderRow(String orderDate, int orderNo, String customerName, ReceiveType receiveType,
						   Long placeId, String placeName, int totalAmount, PayMethod payMethod,
						   PayMethod remainderMethod, Long couponId, int couponAmount,
						   int freeAmount, String freeItemName,
						   int cashAmount, int transferAmount, String memo) {}

	public record LineRow(Long menuItemId, Long variantId, String menuName, String variantLabel,
						  int unitPrice, int quantity) {}

	private final JdbcClient jdbc;

	public OrderRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public int nextOrderNo(String orderDate) {
		return jdbc.sql("SELECT COALESCE(MAX(order_no), 0) + 1 FROM orders WHERE order_date = :date")
				.param("date", orderDate)
				.query(Integer.class)
				.single();
	}

	public long insert(OrderRow row) {
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("""
						INSERT INTO orders (order_date, order_no, customer_name, receive_type, place_id, place_name,
						                    total_amount, pay_method, remainder_method, coupon_id, coupon_amount,
						                    free_amount, free_item_name, cash_amount, transfer_amount, status, memo, created_at)
						VALUES (:date, :no, :name, :receive, :placeId, :placeName,
						        :total, :pay, :remainder, :couponId, :couponAmount,
						        :freeAmount, :freeItemName, :cash, :transfer, 'PENDING', :memo, :now)
						""")
				.param("date", row.orderDate()).param("no", row.orderNo())
				.param("name", row.customerName()).param("receive", row.receiveType().name())
				.param("placeId", row.placeId()).param("placeName", row.placeName())
				.param("total", row.totalAmount()).param("pay", row.payMethod().name())
				.param("remainder", row.remainderMethod() == null ? null : row.remainderMethod().name())
				.param("couponId", row.couponId()).param("couponAmount", row.couponAmount())
				.param("freeAmount", row.freeAmount()).param("freeItemName", row.freeItemName())
				.param("cash", row.cashAmount()).param("transfer", row.transferAmount())
				.param("memo", row.memo()).param("now", LocalDateTime.now().toString())
				.update(keys);
		return keys.getKey().longValue();
	}

	/** 수정 시 결제 수단 자체는 유지하고 금액/항목 관련 컬럼만 바꾼다. */
	public void update(long orderId, OrderRow row) {
		jdbc.sql("""
						UPDATE orders SET customer_name = :name, receive_type = :receive,
						                  place_id = :placeId, place_name = :placeName,
						                  total_amount = :total, remainder_method = :remainder,
						                  coupon_amount = :couponAmount, free_amount = :freeAmount,
						                  free_item_name = :freeItemName, cash_amount = :cash,
						                  transfer_amount = :transfer, memo = :memo
						WHERE id = :id
						""")
				.param("name", row.customerName()).param("receive", row.receiveType().name())
				.param("placeId", row.placeId()).param("placeName", row.placeName())
				.param("total", row.totalAmount())
				.param("remainder", row.remainderMethod() == null ? null : row.remainderMethod().name())
				.param("couponAmount", row.couponAmount())
				.param("freeAmount", row.freeAmount()).param("freeItemName", row.freeItemName())
				.param("cash", row.cashAmount()).param("transfer", row.transferAmount())
				.param("memo", row.memo()).param("id", orderId)
				.update();
	}

	public void insertLines(long orderId, List<LineRow> lines) {
		for (LineRow line : lines) {
			jdbc.sql("""
							INSERT INTO order_line (order_id, menu_item_id, variant_id, menu_name, variant_label,
							                        unit_price, quantity)
							VALUES (:orderId, :itemId, :variantId, :menuName, :label, :price, :qty)
							""")
					.param("orderId", orderId).param("itemId", line.menuItemId())
					.param("variantId", line.variantId()).param("menuName", line.menuName())
					.param("label", line.variantLabel()).param("price", line.unitPrice())
					.param("qty", line.quantity())
					.update();
		}
	}

	public void deleteLines(long orderId) {
		jdbc.sql("DELETE FROM order_line WHERE order_id = :id").param("id", orderId).update();
	}

	public void markDone(long orderId) {
		jdbc.sql("UPDATE orders SET status = 'DONE', completed_at = :now WHERE id = :id")
				.param("now", LocalDateTime.now().toString()).param("id", orderId).update();
	}

	public void markPending(long orderId) {
		jdbc.sql("UPDATE orders SET status = 'PENDING', completed_at = NULL WHERE id = :id")
				.param("id", orderId).update();
	}

	public void markCanceled(long orderId) {
		jdbc.sql("UPDATE orders SET status = 'CANCELED', canceled_at = :now WHERE id = :id")
				.param("now", LocalDateTime.now().toString()).param("id", orderId).update();
	}

	public Optional<OrderView> findById(long orderId) {
		List<OrderView> found = attachLines(jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM orders WHERE id = :id")
				.param("id", orderId)
				.query(OrderRepository::mapOrder)
				.list());
		return found.stream().findFirst();
	}

	/** 만들어야 할 주문. 날짜와 무관하게 PENDING 전부 — 지난주 것이 남아있으면 눈에 보여야 한다. */
	public List<OrderView> findPending() {
		return attachLines(jdbc.sql("SELECT " + ORDER_COLUMNS + " FROM orders WHERE status = 'PENDING' ORDER BY id")
				.query(OrderRepository::mapOrder)
				.list());
	}

	public List<OrderView> findByDateAndStatus(String orderDate, Status status) {
		return attachLines(jdbc.sql("SELECT " + ORDER_COLUMNS
						+ " FROM orders WHERE order_date = :date AND status = :status ORDER BY id DESC")
				.param("date", orderDate).param("status", status.name())
				.query(OrderRepository::mapOrder)
				.list());
	}

	public DailySummary summarize(String orderDate) {
		return jdbc.sql("""
						SELECT COUNT(*) AS cnt,
						       COALESCE(SUM(total_amount), 0)    AS total,
						       COALESCE(SUM(cash_amount), 0)     AS cash,
						       COALESCE(SUM(transfer_amount), 0) AS transfer,
						       COALESCE(SUM(coupon_amount), 0)   AS coupon,
						       COALESCE(SUM(free_amount), 0)     AS free
						FROM orders WHERE order_date = :date AND status <> 'CANCELED'
						""")
				.param("date", orderDate)
				.query((rs, n) -> new DailySummary(orderDate, rs.getInt("cnt"), rs.getInt("total"),
						rs.getInt("cash"), rs.getInt("transfer"), rs.getInt("coupon"), rs.getInt("free")))
				.single();
	}

	private record LineWithOrder(long orderId, LineView line) {}

	private List<OrderView> attachLines(List<OrderView> orders) {
		if (orders.isEmpty()) {
			return orders;
		}
		Map<Long, List<LineView>> byOrder = new LinkedHashMap<>();
		for (OrderView o : orders) {
			byOrder.put(o.id(), new ArrayList<>());
		}
		List<LineWithOrder> rows = jdbc.sql("""
						SELECT id, order_id, variant_id, menu_name, variant_label, unit_price, quantity
						FROM order_line WHERE order_id IN (:ids) ORDER BY id
						""")
				.param("ids", new ArrayList<>(byOrder.keySet()))
				.query((rs, n) -> {
					long variantId = rs.getLong("variant_id");
					Long variantIdOrNull = rs.wasNull() ? null : variantId;
					return new LineWithOrder(rs.getLong("order_id"), new LineView(
							rs.getLong("id"), variantIdOrNull,
							rs.getString("menu_name"), rs.getString("variant_label"),
							rs.getInt("unit_price"), rs.getInt("quantity")));
				})
				.list();
		for (LineWithOrder row : rows) {
			byOrder.get(row.orderId()).add(row.line());
		}
		List<OrderView> result = new ArrayList<>(orders.size());
		for (OrderView o : orders) {
			result.add(withLines(o, byOrder.get(o.id())));
		}
		return result;
	}

	private static OrderView withLines(OrderView o, List<LineView> lines) {
		return new OrderView(o.id(), o.orderDate(), o.orderNo(), o.customerName(), o.receiveType(),
				o.placeId(), o.placeName(), o.totalAmount(), o.payMethod(), o.remainderMethod(),
				o.couponId(), o.couponAmount(), o.freeAmount(), o.freeItemName(),
				o.cashAmount(), o.transferAmount(), o.status(),
				o.memo(), o.createdAt(), o.completedAt(), lines);
	}

	private static OrderView mapOrder(ResultSet rs, int rowNum) throws SQLException {
		long placeId = rs.getLong("place_id");
		Long placeIdOrNull = rs.wasNull() ? null : placeId;
		long couponId = rs.getLong("coupon_id");
		Long couponIdOrNull = rs.wasNull() ? null : couponId;
		String remainder = rs.getString("remainder_method");
		return new OrderView(
				rs.getLong("id"), rs.getString("order_date"), rs.getInt("order_no"),
				rs.getString("customer_name"), ReceiveType.valueOf(rs.getString("receive_type")),
				placeIdOrNull, rs.getString("place_name"), rs.getInt("total_amount"),
				PayMethod.valueOf(rs.getString("pay_method")),
				remainder == null ? null : PayMethod.valueOf(remainder),
				couponIdOrNull, rs.getInt("coupon_amount"), rs.getInt("free_amount"), rs.getString("free_item_name"),
				rs.getInt("cash_amount"), rs.getInt("transfer_amount"), Status.valueOf(rs.getString("status")),
				rs.getString("memo"), rs.getString("created_at"), rs.getString("completed_at"),
				List.of());
	}
}
