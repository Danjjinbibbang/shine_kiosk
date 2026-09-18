package church.kiosk.coupon;

import church.kiosk.config.KioskProperties;
import church.kiosk.coupon.CouponService.LookupResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 조회는 고객 키오스크에서 쓰므로 공개, 등록/충전은 스태프 화면(PIN)에서만.
 */
@RestController
public class CouponController {

	private final CouponService couponService;
	private final KioskProperties props;

	public CouponController(CouponService couponService, KioskProperties props) {
		this.couponService = couponService;
		this.props = props;
	}

	public record LookupRequest(@NotBlank(message = "이름을 입력해 주세요.") String name, String phoneLast4) {}

	public record RegisterRequest(@NotBlank(message = "이름을 입력해 주세요.") String name,
								  String phoneLast4,
								  @Min(value = 1, message = "충전 금액을 확인해 주세요.") int amount) {}

	public record ChargeRequest(@Min(value = 1, message = "충전 금액을 확인해 주세요.") int amount) {}

	public record AdjustRequest(@Min(value = 0, message = "잔액은 0원 이상이어야 합니다.") int balance) {}

	@PostMapping("/api/coupons/lookup")
	public LookupResult lookup(@RequestBody @jakarta.validation.Valid LookupRequest request) {
		return couponService.lookup(request.name(), request.phoneLast4());
	}

	@PostMapping("/api/staff/coupons")
	public Coupon register(@RequestBody @jakarta.validation.Valid RegisterRequest request) {
		return couponService.register(request.name(), request.phoneLast4(), request.amount());
	}

	@PostMapping("/api/staff/coupons/{id}/charge")
	public Coupon charge(@PathVariable long id, @RequestBody @jakarta.validation.Valid ChargeRequest request) {
		return couponService.charge(id, request.amount());
	}

	@PostMapping("/api/staff/coupons/{id}/adjust")
	public Coupon adjust(@PathVariable long id, @RequestBody @jakarta.validation.Valid AdjustRequest request) {
		return couponService.adjustBalance(id, request.balance());
	}

	@DeleteMapping("/api/staff/coupons/{id}")
	public void delete(@PathVariable long id) {
		couponService.delete(id);
	}

	@GetMapping("/api/staff/coupons/preset")
	public Map<String, Integer> preset() {
		return Map.of("amount", props.getCouponPresetAmount());
	}
}
