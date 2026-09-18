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
			new Column("coupon_tx", "free_delta", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_amount", "INTEGER NOT NULL DEFAULT 0"),
			new Column("orders", "free_item_name", "TEXT")
	);

	private final JdbcClient jdbc;

	public SchemaMigration(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@PostConstruct
	public void migrate() {
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
