package church.kiosk.staff;

import church.kiosk.config.KioskProperties;
import church.kiosk.support.BusinessException;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/staff-auth")
public class StaffController {

	private final KioskProperties props;
	private final StaffTokenStore tokenStore;

	public StaffController(KioskProperties props, StaffTokenStore tokenStore) {
		this.props = props;
		this.tokenStore = tokenStore;
	}

	public record LoginRequest(@NotBlank(message = "PIN 을 입력해 주세요.") String pin) {}

	@PostMapping("/login")
	public Map<String, String> login(@RequestBody LoginRequest request) {
		if (!props.getStaffPin().equals(request.pin())) {
			throw new BusinessException("PIN 이 맞지 않습니다.");
		}
		return Map.of("token", tokenStore.issue());
	}

	@PostMapping("/logout")
	public void logout(@RequestHeader(value = StaffAuthInterceptor.HEADER, required = false) String token) {
		tokenStore.revoke(token);
	}

	/** 폰에 저장된 토큰이 아직 살아있는지 확인 (앱 진입 시 호출). */
	@GetMapping("/check")
	public Map<String, Boolean> check(@RequestHeader(value = StaffAuthInterceptor.HEADER, required = false) String token) {
		return Map.of("valid", tokenStore.isValid(token));
	}
}
