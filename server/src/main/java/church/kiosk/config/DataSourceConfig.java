package church.kiosk.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class DataSourceConfig {

	private static final Logger log = LoggerFactory.getLogger(DataSourceConfig.class);

	/**
	 * SQLite 는 쓰기가 DB 전체를 잠근다. 태블릿 1대 + 스태프 폰 3대 규모에서는
	 * 커넥션 풀을 1로 고정해 모든 접근을 직렬화하는 편이 SQLITE_BUSY 를 원천 차단하면서도
	 * 충분히 빠르다. 성능보다 주일 아침에 안 터지는 것이 우선이다.
	 */
	@Bean
	public DataSource dataSource(KioskProperties props) {
		Path dbFile = Paths.get(props.getDbPath()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(dbFile.getParent());
		}
		catch (IOException e) {
			throw new UncheckedIOException("DB 디렉터리를 만들 수 없습니다: " + dbFile.getParent(), e);
		}
		log.info("SQLite DB 파일: {}", dbFile);

		SQLiteConfig sqliteConfig = new SQLiteConfig();
		sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
		sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);
		sqliteConfig.enforceForeignKeys(true);
		sqliteConfig.setBusyTimeout(5000);

		SQLiteDataSource sqlite = new SQLiteDataSource(sqliteConfig);
		sqlite.setUrl("jdbc:sqlite:" + dbFile);

		HikariConfig hikari = new HikariConfig();
		hikari.setDataSource(sqlite);
		hikari.setMaximumPoolSize(1);
		hikari.setPoolName("sqlite-pool");

		return new HikariDataSource(hikari);
	}
}
