package church.kiosk.staff;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 스태프 PIN 로그인 후 발급되는 토큰 보관소.
 * 교회 와이파이 안에서 "고객 태블릿을 만지던 사람이 스태프 화면에 못 들어가게" 하는 수준의 장치다.
 * 서버가 재시작되면 전부 무효화되고, 그때는 폰에서 PIN 을 다시 넣으면 된다.
 */
@Component
public class StaffTokenStore {

	private static final long TTL_HOURS = 12;

	private final SecureRandom random = new SecureRandom();
	private final Map<String, Instant> tokens = new ConcurrentHashMap<>();

	public String issue() {
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		tokens.put(token, Instant.now().plusSeconds(TTL_HOURS * 3600));
		return token;
	}

	public boolean isValid(String token) {
		if (token == null || token.isBlank()) {
			return false;
		}
		Instant expiry = tokens.get(token);
		if (expiry == null) {
			return false;
		}
		if (expiry.isBefore(Instant.now())) {
			tokens.remove(token);
			return false;
		}
		return true;
	}

	public void revoke(String token) {
		if (token != null) {
			tokens.remove(token);
		}
	}
}
