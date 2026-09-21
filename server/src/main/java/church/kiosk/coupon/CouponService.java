package church.kiosk.coupon;

import church.kiosk.config.KioskProperties;
import church.kiosk.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 선불 쿠폰. 주문 금액 전액이 잔액에서 빠지고, 20,000원(설정값) 충전마다 무료 1잔이 적립된다.
 * 무료 1잔은 고객이 원하는 주문에서 체크해 쓰며, 그 주문에서 가장 비싼 한 잔 값이 빠진다.
 */
@Service
public class CouponService {

	/** 이름으로 조회한 결과. 동명이인이면 전화번호 뒤 4자리를 되묻는다. */
	public enum LookupStatus { FOUND, NOT_FOUND, NEED_PHONE }

	public record LookupResult(LookupStatus status, Coupon coupon, int candidateCount) {
		static LookupResult found(Coupon c) { return new LookupResult(LookupStatus.FOUND, c, 1); }
		static LookupResult notFound() { return new LookupResult(LookupStatus.NOT_FOUND, null, 0); }
		static LookupResult needPhone(int count) { return new LookupResult(LookupStatus.NEED_PHONE, null, count); }
	}

	private final CouponRepository couponRepository;
	private final KioskProperties props;

	public CouponService(CouponRepository couponRepository, KioskProperties props) {
		this.couponRepository = couponRepository;
		this.props = props;
	}

	/** 충전 금액으로 적립되는 무료 1잔 개수. 20,000원마다 1잔. */
	int freeDrinksFor(int amount) {
		int unit = props.getCouponPresetAmount();
		return unit > 0 ? amount / unit : 0;
	}

	public List<Coupon> findByName(String name) {
		return couponRepository.findByName(name.trim());
	}

	public LookupResult lookup(String name, String phoneLast4) {
		List<Coupon> candidates = couponRepository.findByName(name.trim());
		if (candidates.isEmpty()) {
			return LookupResult.notFound();
		}
		if (candidates.size() == 1 && (phoneLast4 == null || phoneLast4.isBlank())) {
			return LookupResult.found(candidates.get(0));
		}
		if (phoneLast4 == null || phoneLast4.isBlank()) {
			return LookupResult.needPhone(candidates.size());
		}
		Optional<Coupon> matched = candidates.stream()
				.filter(c -> phoneLast4.equals(c.phoneLast4()))
				.findFirst();
		return matched.map(LookupResult::found).orElseGet(LookupResult::notFound);
	}

	/** 전화번호는 필수. 동명이인 구분과 잔액 문자, 취소·차액 환불을 쿠폰에 넣을 때 혼동을 막기 위해서다. */
	@Transactional
	public Coupon register(String name, String phoneRaw, int amount) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException("이름을 입력해 주세요.");
		}
		if (amount <= 0) {
			throw new BusinessException("충전 금액을 확인해 주세요.");
		}
		String phone = validPhone(phoneRaw);
		if (phone == null) {
			throw new BusinessException("전화번호를 입력해 주세요.");
		}
		String last4 = Coupon.last4Of(phone);
		if (couponRepository.findByName(trimmed).stream().anyMatch(c -> last4.equals(c.phoneLast4()))) {
			throw new BusinessException("같은 이름에 같은 뒤 4자리 번호가 이미 있습니다.");
		}
		int free = freeDrinksFor(amount);
		long id = couponRepository.insert(trimmed, phone, amount, free);
		couponRepository.insertTx(id, null, amount, free, "CHARGE", amount);
		return couponRepository.findById(id).orElseThrow();
	}

	@Transactional
	public Coupon charge(long couponId, int amount) {
		if (amount <= 0) {
			throw new BusinessException("충전 금액을 확인해 주세요.");
		}
		Coupon coupon = require(couponId);
		if (coupon.phone() == null) {
			throw new BusinessException("전화번호가 없는 쿠폰입니다. 번호를 먼저 넣어 주세요.");
		}
		int free = freeDrinksFor(amount);
		int after = coupon.balance() + amount;
		couponRepository.update(couponId, after, coupon.freeDrinks() + free);
		couponRepository.insertTx(couponId, null, amount, free, "CHARGE", after);
		return couponRepository.findById(couponId).orElseThrow();
	}

	/** 잔액 문자를 보낼 전화번호를 넣거나 바꾼다. 동명이인이 있으면 뒤 4자리가 겹치면 안 된다. */
	@Transactional
	public Coupon updatePhone(long couponId, String phoneRaw) {
		Coupon coupon = require(couponId);
		String phone = validPhone(phoneRaw);
		if (phone == null) {
			throw new BusinessException("전화번호를 입력해 주세요.");
		}
		String last4 = Coupon.last4Of(phone);
		boolean clash = couponRepository.findByName(coupon.name()).stream()
				.anyMatch(c -> c.id() != couponId && last4.equals(c.phoneLast4()));
		if (clash) {
			throw new BusinessException("같은 이름에 같은 뒤 4자리 번호가 이미 있습니다.");
		}
		couponRepository.updatePhone(couponId, phone);
		return couponRepository.findById(couponId).orElseThrow();
	}

	private static String validPhone(String raw) {
		String phone = Coupon.normalizePhone(raw);
		if (phone != null && (phone.length() < 10 || phone.length() > 11)) {
			throw new BusinessException("전화번호는 숫자 10~11자리로 입력해 주세요.");
		}
		return phone;
	}

	/** 충전을 잘못 넣었을 때 잔액과 무료잔 개수를 바로잡는다. 차액을 ADJUST 이력으로 남긴다. */
	@Transactional
	public Coupon adjust(long couponId, int newBalance, int newFreeDrinks) {
		if (newBalance < 0 || newFreeDrinks < 0) {
			throw new BusinessException("잔액과 무료잔 개수는 0 이상이어야 합니다.");
		}
		Coupon coupon = require(couponId);
		int delta = newBalance - coupon.balance();
		int freeDelta = newFreeDrinks - coupon.freeDrinks();
		if (delta != 0 || freeDelta != 0) {
			couponRepository.update(couponId, newBalance, newFreeDrinks);
			couponRepository.insertTx(couponId, null, delta, freeDelta, "ADJUST", newBalance);
		}
		return couponRepository.findById(couponId).orElseThrow();
	}

	/** 잘못 등록한 쿠폰 삭제. 주문에 쓰인 적이 있으면 장부가 끊기므로 막고, 대신 정정을 안내한다. */
	@Transactional
	public void delete(long couponId) {
		require(couponId);
		if (couponRepository.isUsedByAnyOrder(couponId)) {
			throw new BusinessException("주문에 사용된 쿠폰은 지울 수 없습니다. 잔액을 0원으로 정정해 주세요.");
		}
		couponRepository.delete(couponId);
	}

	/**
	 * 주문 결제에 쿠폰을 사용한다. amount 는 잔액에서 뺄 금액(호출자가 잔액 범위로 계산),
	 * useFreeDrink 가 참이면 무료 1잔을 한 장 소모한다.
	 */
	@Transactional
	public void useForOrder(long couponId, int amount, boolean useFreeDrink, long orderId) {
		Coupon coupon = require(couponId);
		if (coupon.balance() < amount) {
			throw new BusinessException("쿠폰 잔액이 부족합니다.");
		}
		if (useFreeDrink && coupon.freeDrinks() < 1) {
			throw new BusinessException("남은 무료 1잔이 없습니다.");
		}
		if (amount <= 0 && !useFreeDrink) {
			return;
		}
		int after = coupon.balance() - amount;
		int freeDelta = useFreeDrink ? -1 : 0;
		couponRepository.update(couponId, after, coupon.freeDrinks() + freeDelta);
		couponRepository.insertTx(couponId, orderId, -amount, freeDelta, "USE", after);
	}

	/** 주문 취소/수정 시 차감한 금액과 소모한 무료 1잔을 되돌린다. */
	@Transactional
	public void refundForOrder(long couponId, int amount, boolean restoreFreeDrink, long orderId) {
		if (amount <= 0 && !restoreFreeDrink) {
			return;
		}
		Coupon coupon = require(couponId);
		int after = coupon.balance() + amount;
		int freeDelta = restoreFreeDrink ? 1 : 0;
		couponRepository.update(couponId, after, coupon.freeDrinks() + freeDelta);
		couponRepository.insertTx(couponId, orderId, amount, freeDelta, "REFUND", after);
	}

	/** 최근 한 달 이력 (오늘 포함). */
	public List<CouponRepository.TxView> history(long couponId) {
		require(couponId);
		String since = java.time.LocalDate.now().minusMonths(1).toString();
		return couponRepository.findTxSince(couponId, since);
	}

	public Coupon require(long couponId) {
		return couponRepository.findById(couponId)
				.orElseThrow(() -> new BusinessException("쿠폰을 찾을 수 없습니다."));
	}

}
