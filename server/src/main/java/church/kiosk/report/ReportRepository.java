package church.kiosk.report;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ReportRepository {

	/**
	 * 하루 집계. totalAmount 는 주문 금액 합(= 현금+이체+쿠폰 사용+무료 1잔, 사역자 무료는 제외),
	 * couponChargeAmount 는 그날 쿠폰 충전으로 들어온 돈.
	 * 쿠폰은 충전 때 돈이 들어오고 사용 때 매출로 잡히므로 둘 다 보여준다.
	 */
	public record DayReport(String date, int orderCount, int totalAmount, int cashAmount, int transferAmount,
							int couponAmount, int freeAmount, int staffFreeAmount, int couponChargeAmount) {}

	private final JdbcClient jdbc;

	public ReportRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<DayReport> findDays() {
		Map<String, DayReport> byDate = new LinkedHashMap<>();
		jdbc.sql("""
						SELECT order_date,
						       COUNT(*)                            AS cnt,
						       COALESCE(SUM(total_amount), 0)      AS total,
						       COALESCE(SUM(cash_amount), 0)       AS cash,
						       COALESCE(SUM(transfer_amount), 0)   AS transfer,
						       COALESCE(SUM(coupon_amount), 0)     AS coupon,
						       COALESCE(SUM(free_amount), 0)       AS free,
						       COALESCE(SUM(staff_free_amount), 0) AS staff_free
						FROM orders WHERE status <> 'CANCELED'
						GROUP BY order_date
						""")
				.query((rs, n) -> new DayReport(rs.getString("order_date"), rs.getInt("cnt"), rs.getInt("total"),
						rs.getInt("cash"), rs.getInt("transfer"), rs.getInt("coupon"), rs.getInt("free"),
						rs.getInt("staff_free"), 0))
				.list()
				.forEach(d -> byDate.put(d.date(), d));

		// 쿠폰 충전 입금 (충전/등록 이력의 날짜 기준)
		jdbc.sql("""
						SELECT substr(created_at, 1, 10) AS d, COALESCE(SUM(delta), 0) AS charged
						FROM coupon_tx WHERE reason = 'CHARGE'
						GROUP BY substr(created_at, 1, 10)
						""")
				.query((rs, n) -> Map.entry(rs.getString("d"), rs.getInt("charged")))
				.list()
				.forEach(e -> {
					DayReport d = byDate.getOrDefault(e.getKey(), new DayReport(e.getKey(), 0, 0, 0, 0, 0, 0, 0, 0));
					byDate.put(e.getKey(), new DayReport(d.date(), d.orderCount(), d.totalAmount(), d.cashAmount(),
							d.transferAmount(), d.couponAmount(), d.freeAmount(), d.staffFreeAmount(), e.getValue()));
				});

		List<DayReport> result = new ArrayList<>(byDate.values());
		result.sort(Comparator.comparing(DayReport::date).reversed());
		return result;
	}
}
