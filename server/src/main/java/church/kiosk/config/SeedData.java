package church.kiosk.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 기본 메뉴/장소는 테이블이 비어 있을 때 한 번만 넣는다.
 * 그 뒤로는 스태프 화면(설정)에서 바꾼 값이 기준이고, 재시작해도 되돌아가지 않는다.
 */
@Component
@DependsOnDatabaseInitialization
public class SeedData {

	private static final Logger log = LoggerFactory.getLogger(SeedData.class);

	private record Seed(String table, String script) {}

	private static final List<Seed> SEEDS = List.of(
			new Seed("menu_item", "seed-menu.sql"),
			new Seed("delivery_place", "seed-places.sql")
	);

	private final JdbcClient jdbc;
	private final DataSource dataSource;

	/** SchemaMigration 이 먼저 컬럼을 맞춰 둔 뒤에 돈다. */
	public SeedData(JdbcClient jdbc, DataSource dataSource, SchemaMigration migration) {
		this.jdbc = jdbc;
		this.dataSource = dataSource;
	}

	@PostConstruct
	public void seedIfEmpty() throws SQLException {
		for (Seed seed : SEEDS) {
			int count = jdbc.sql("SELECT COUNT(*) FROM " + seed.table()).query(Integer.class).single();
			if (count > 0) {
				continue;
			}
			log.info("{} 이(가) 비어 있어 기본 데이터를 넣습니다: {}", seed.table(), seed.script());
			try (Connection conn = dataSource.getConnection()) {
				ScriptUtils.executeSqlScript(conn, new ClassPathResource(seed.script()));
			}
		}
	}
}
