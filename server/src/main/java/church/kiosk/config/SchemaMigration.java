package church.kiosk.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * schema.sql 은 CREATE TABLE IF NOT EXISTS 라서 이미 만들어진 DB 에는 새 컬럼이 안 붙는다.
 * SQLite 에는 ADD COLUMN IF NOT EXISTS 가 없으므로, 나중에 추가된 컬럼은 여기에 적어두고
 * 기동 때 없으면 붙인다. 태블릿의 운영 DB 를 지우지 않고 jar 만 바꿔 끼우기 위한 장치.
 */
@Component
@DependsOnDatabaseInitialization
public class SchemaMigration {

	private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);

	private record Column(String table, String name, String definition) {}

	private static final List<Column> COLUMNS = List.of(
			new Column("coupon", "free_drinks", "INTEGER NOT NULL DEFAULT 0"),
			new Column("coupon", "phone", "TEXT"),
			new Column("menu_option", "option_group", "TEXT"),
			new Column("orders", "edited_at", "TEXT"),
			new Column("orders", "edit_note", "TEXT"),
			new Column("orders", "settled_cash", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "settled_transfer", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "client_request_id", "TEXT"),
			new Column("coupon_tx", "free_delta", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_amount", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_item_name", "TEXT"),
			new Column("orders", "cash_given", "INTEGER"),
			new Column("orders", "change_credited", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "staff_free_amount", "INTEGER NOT NULL DEFAULT 0"),
			new Column("order_line", "staff_free_qty", "INTEGER NOT NULL DEFAULT 0")
	);

	private final JdbcClient jdbc;

	public SchemaMigration(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@PostConstruct
	public void migrate() {
		addMissingColumns();
		renumberMenuOrderIfNeeded();
		groupSeedOptionsIfNeeded();
		jdbc.sql("CREATE UNIQUE INDEX IF NOT EXISTS ux_orders_client_request ON orders(client_request_id)").update();
		dropColumnIfExists("coupon", "phone_last4"); // phone 에서 계산하므로 더 이상 저장하지 않는다
		registerMissingCategories();
		migrateCashMemos(); // 옛 키오스크 화면이 남아 있으면 그 뒤에도 옛 방식 메모가 들어올 수 있어 기동 때마다
	}

	/** 메뉴/옵션에 쓰인 카테고리가 카테고리 표에 없으면 (예전 DB) 메뉴 순서대로 등록한다. */
	private void registerMissingCategories() {
		List<String> used = jdbc.sql("""
						SELECT category FROM (
						    SELECT category, MIN(sort_order) AS o FROM menu_item GROUP BY category
						    UNION ALL
						    SELECT category, 1000000 + MIN(sort_order) FROM menu_option GROUP BY category
						) ORDER BY o
						""").query(String.class).list();
		Set<String> known = Set.copyOf(jdbc.sql("SELECT name FROM menu_category").query(String.class).list());
		int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 10 FROM menu_category").query(Integer.class).single();
		java.util.LinkedHashSet<String> missing = new java.util.LinkedHashSet<>(used);
		missing.removeAll(known);
		for (String name : missing) {
			jdbc.sql("INSERT INTO menu_category (name, sort_order) VALUES (:name, :sort)").param("name", name).param("sort", next).update();
			log.info("카테고리 등록: {}", name);
			next += 10;
		}
	}

	private void dropColumnIfExists(String table, String column) {
		Set<String> existing = Set.copyOf(jdbc.sql("SELECT name FROM pragma_table_info('" + table + "')")
				.query(String.class).list());
		if (existing.contains(column)) {
			log.info("컬럼 제거: {}.{}", table, column);
			jdbc.sql("ALTER TABLE " + table + " DROP COLUMN " + column).update();
		}
	}

	/** 예전 시드로 만들어진 '샷 추가'/'연하게' 에 그룹이 없으면 '농도' 로 묶어 한 잔에 하나만 고르게 한다. */
	private void groupSeedOptionsIfNeeded() {
		int updated = jdbc.sql("UPDATE menu_option SET option_group = '농도' WHERE option_group IS NULL AND name IN ('샷 추가', '연하게')")
				.update();
		if (updated > 0) {
			log.info("옵션 그룹 지정: 샷 추가/연하게 → 농도 ({}개)", updated);
		}
	}

	/**
	 * 예전 시드는 카테고리마다 10,20,30… 을 따로 매겼는데, 지금은 메뉴 전체가 한 순서다.
	 * 값이 겹치는 DB 를 만나면 (카테고리 첫 등장 순 → 카테고리 안 순서) 로 한 번 다시 매긴다.
	 */
	private void renumberMenuOrderIfNeeded() {
		int duplicates = jdbc.sql("SELECT COUNT(*) - COUNT(DISTINCT sort_order) FROM menu_item").query(Integer.class).single();
		if (duplicates == 0) {
			return;
		}
		List<Long> ids = jdbc.sql("""
						SELECT id FROM menu_item
						ORDER BY MIN(id) OVER (PARTITION BY category), sort_order, id
						""")
				.query(Long.class).list();
		int order = 10;
		for (Long id : ids) {
			jdbc.sql("UPDATE menu_item SET sort_order = :sort WHERE id = :id").param("sort", order).param("id", id).update();
			order += 10;
		}
		log.info("메뉴 순서를 하나의 순서로 다시 매겼습니다 ({}개)", ids.size());
	}

	/** 새 컬럼에 기존 행의 값을 채워야 하는 경우. 예전 주문은 지금 금액을 그대로 받은 것으로 본다. */
	private void backfill(Column c) {
		if (c.table().equals("orders") && c.name().equals("settled_cash")) {
			jdbc.sql("UPDATE orders SET settled_cash = cash_amount").update();
		}
		if (c.table().equals("orders") && c.name().equals("settled_transfer")) {
			jdbc.sql("UPDATE orders SET settled_transfer = transfer_amount").update();
		}
	}

	/**
	 * 예전엔 키오스크가 "현금 5,000원 받음 → 거스름돈 1,000원" 같은 글을 memo 에 넣었다.
	 * 그 글에서 낸 돈을 뽑아 cash_given 에 넣고, 메모는 자유 텍스트만 남긴다.
	 */
	private void migrateCashMemos() {
		List<java.util.Map<String, Object>> rows = jdbc.sql("SELECT id, memo, cash_amount FROM orders WHERE memo LIKE '현금 %' AND cash_given IS NULL").query().listOfRows();
		int n = 0;
		for (java.util.Map<String, Object> r : rows) {
			Integer amount = church.kiosk.order.OrderService.parseLegacyCashMemo(String.valueOf(r.get("memo")));
			if (amount == null) continue;
			// 낸 돈이 있으면 현금 몫 중 낸 돈 안의 금액은 이미 받은 것 (늘어난 몫이 거스름돈에서 흡수되도록)
			jdbc.sql("UPDATE orders SET cash_given = :given, memo = NULL, settled_cash = MIN(cash_amount, :given) WHERE id = :id AND status <> 'CANCELED'")
					.param("given", amount).param("id", r.get("id")).update();
			n++;
		}
		if (n > 0) log.info("옛 방식 현금 메모 {}건을 cash_given 으로 옮겼습니다", n);
	}

	private void addMissingColumns() {
		for (Column c : COLUMNS) {
			Set<String> existing = Set.copyOf(jdbc.sql("SELECT name FROM pragma_table_info('" + c.table() + "')")
					.query(String.class)
					.list());
			if (!existing.contains(c.name())) {
				log.info("컬럼 추가: {}.{}", c.table(), c.name());
				jdbc.sql("ALTER TABLE " + c.table() + " ADD COLUMN " + c.name() + " " + c.definition()).update();
				backfill(c);
			}
		}
	}
}
