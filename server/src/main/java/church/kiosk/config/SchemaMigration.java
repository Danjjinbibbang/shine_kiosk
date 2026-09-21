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
			new Column("coupon_tx", "free_delta", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_amount", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_item_name", "TEXT"),
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

	private void addMissingColumns() {
		for (Column c : COLUMNS) {
			Set<String> existing = Set.copyOf(jdbc.sql("SELECT name FROM pragma_table_info('" + c.table() + "')")
					.query(String.class)
					.list());
			if (!existing.contains(c.name())) {
				log.info("컬럼 추가: {}.{}", c.table(), c.name());
				jdbc.sql("ALTER TABLE " + c.table() + " ADD COLUMN " + c.name() + " " + c.definition()).update();
			}
		}
	}
}
