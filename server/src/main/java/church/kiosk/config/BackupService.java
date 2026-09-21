package church.kiosk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * SQLite 파일 백업. 기동할 때와 매일 새벽에 backup-dir 로 복사하고, 오래된 것은 지운다.
 * 태블릿에서는 backup-dir 을 다운로드 폴더로 두어 내 파일 앱에서 바로 옮길 수 있게 한다.
 * 기기가 고장 나면 이 폴더도 같이 사라지므로, 스태프 폰의 '백업 내려받기' 로 기기 밖에도 둔다.
 */
@Service
public class BackupService {

	private static final Logger log = LoggerFactory.getLogger(BackupService.class);
	private static final int KEEP = 10;

	private final JdbcClient jdbc;
	private final KioskProperties props;

	public BackupService(JdbcClient jdbc, KioskProperties props) {
		this.jdbc = jdbc;
		this.props = props;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onStart() {
		backupQuietly("기동");
	}

	/** 매일 04:00 (태블릿 시각). 주일 운영 중엔 건드리지 않는 시간. */
	@Scheduled(cron = "0 0 4 * * *")
	public void nightly() {
		backupQuietly("자동");
	}

	private void backupQuietly(String why) {
		try {
			Path file = backupNow();
			log.info("{} 백업: {}", why, file);
		}
		catch (Exception e) {
			log.warn("{} 백업 실패: {}", why, e.getMessage());
		}
	}

	/** 지금 백업 파일을 만들고 경로를 돌려준다. 같은 날 두 번이면 덮어쓴다. */
	public synchronized Path backupNow() throws IOException {
		Path dir = Paths.get(props.getBackupDir()).toAbsolutePath().normalize();
		Files.createDirectories(dir);
		Path file = dir.resolve("kiosk-backup-" + LocalDate.now() + ".db");
		Files.deleteIfExists(file);
		// VACUUM INTO 는 WAL 에 남은 변경까지 합쳐서 일관된 사본을 만든다.
		jdbc.sql("VACUUM INTO '" + file.toString().replace("'", "''") + "'").update();
		prune(dir);
		return file;
	}

	/** 스태프 폰으로 내려줄 사본. 임시 파일이라 호출자가 지운다. */
	public Path snapshotForDownload() throws IOException {
		Path tmp = Files.createTempFile("kiosk-", ".db");
		Files.deleteIfExists(tmp);
		jdbc.sql("VACUUM INTO '" + tmp.toString().replace("'", "''") + "'").update();
		return tmp;
	}

	private void prune(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			List<Path> backups = files.filter(p -> p.getFileName().toString().startsWith("kiosk-backup-"))
					.sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
					.toList();
			for (Path old : backups.stream().skip(KEEP).toList()) {
				Files.deleteIfExists(old);
			}
		}
	}
}
