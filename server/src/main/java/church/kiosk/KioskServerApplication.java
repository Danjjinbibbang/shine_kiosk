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
		SpringApplication.run(KioskServerApplication.class, args);
	}
}
