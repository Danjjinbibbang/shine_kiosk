package church.kiosk.coupon;

import church.kiosk.config.KioskProperties;
import church.kiosk.coupon.CouponService.LookupResult;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

	/** phone 은 전체 번호(선택). 잔액 문자용이자 동명이인 구분용. */
	public record RegisterRequest(@NotBlank(message = "이름을 입력해 주세요.") String name,
								  String phone,
								  @Min(value = 1, message = "충전 금액을 확인해 주세요.") int amount) {}

	public record PhoneRequest(String phone) {}

	public record NameRequest(String name) {}

	public record ChargeRequest(@Min(value = 1, message = "충전 금액을 확인해 주세요.") int amount) {}

	public record AdjustRequest(@Min(value = 0, message = "잔액은 0원 이상이어야 합니다.") int balance,
								 @Min(value = 0, message = "무료잔 개수는 0 이상이어야 합니다.") int freeDrinks) {}

	/** 고객 화면용 조회 결과. 전체 번호는 내려주지 않는다. */
	public record PublicLookup(CouponService.LookupStatus status, Coupon.PublicView coupon, int candidateCount) {}

	@PostMapping("/api/coupons/lookup")
	public PublicLookup lookup(@RequestBody @jakarta.validation.Valid LookupRequest request) {
		LookupResult r = couponService.lookup(request.name(), request.phoneLast4());
		return new PublicLookup(r.status(), r.coupon() == null ? null : r.coupon().toPublic(), r.candidateCount());
	}

	/** 스태프 화면용 조회. 전체 번호 포함. */
	@PostMapping("/api/staff/coupons/lookup")
	public LookupResult staffLookup(@RequestBody @jakarta.validation.Valid LookupRequest request) {
		return couponService.lookup(request.name(), request.phoneLast4());
	}

	/** 이름으로 후보 전부 (동명이인 포함). 현금 주문의 차액을 쿠폰에 넣을 때 누구 쿠폰인지 고르는 용도. */
	@GetMapping("/api/staff/coupons")
	public List<Coupon> byName(@RequestParam String name) {
		return couponService.findByName(name);
	}

	@GetMapping("/api/staff/coupons/{id}")
	public Coupon get(@PathVariable long id) {
		return couponService.require(id);
	}

	/** 최근 한 달 이력 (충전/사용/환불/정정). */
	@GetMapping("/api/staff/coupons/{id}/history")
	public List<CouponRepository.TxView> history(@PathVariable long id) {
		return couponService.history(id);
	}

	@PostMapping("/api/staff/coupons")
	public Coupon register(@RequestBody @jakarta.validation.Valid RegisterRequest request) {
		return couponService.register(request.name(), request.phone(), request.amount());
	}

	@PutMapping("/api/staff/coupons/{id}/name")
	public Coupon rename(@PathVariable long id, @RequestBody NameRequest request) {
		return couponService.rename(id, request.name());
	}

	@PutMapping("/api/staff/coupons/{id}/phone")
	public Coupon updatePhone(@PathVariable long id, @RequestBody PhoneRequest request) {
		return couponService.updatePhone(id, request.phone());
	}

	@PostMapping("/api/staff/coupons/{id}/charge")
	public Coupon charge(@PathVariable long id, @RequestBody @jakarta.validation.Valid ChargeRequest request) {
		return couponService.charge(id, request.amount());
	}

	@PostMapping("/api/staff/coupons/{id}/adjust")
	public Coupon adjust(@PathVariable long id, @RequestBody @jakarta.validation.Valid AdjustRequest request) {
		return couponService.adjust(id, request.balance(), request.freeDrinks());
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
