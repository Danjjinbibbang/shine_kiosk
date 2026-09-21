package church.kiosk;

import church.kiosk.config.KioskProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(KioskProperties.class)
@org.springframework.scheduling.annotation.EnableScheduling
public class KioskServerApplication {

	public static void main(String[] args) {
		// Termux 의 JVM 은 기기 시간대를 못 읽고 UTC 로 잡는 경우가 있어 주문/수정 시각이 9시간 어긋난다.
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(KioskServerApplication.class, args);
	}
}
