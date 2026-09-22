package church.kiosk.coupon;

import church.kiosk.support.BusinessException;
import church.kiosk.support.Validation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 선불 쿠폰. 주문 금액 전액이 잔액에서 빠지고, 충전 금액에 따라 무료 1잔이 적립된다 ({@link ChargePolicy}).
 * 무료 1잔은 고객이 원하는 주문에서 체크해 쓰며, 그 주문에서 가장 비싼 한 잔 값이 빠진다.
 */
@Service
public class CouponService {

	/** 이름으로 조회한 결과. 동명이인이면 후보를 돌려주고 고객이 자기 번호(뒤 4자리)를 고른다. */
	public enum LookupStatus { FOUND, NOT_FOUND, NEED_PHONE }

	public record LookupResult(LookupStatus status, Coupon coupon, int candidateCount, List<Coupon> candidates) {
		static LookupResult found(Coupon c) { return new LookupResult(LookupStatus.FOUND, c, 1, List.of()); }
		static LookupResult notFound() { return new LookupResult(LookupStatus.NOT_FOUND, null, 0, List.of()); }
		static LookupResult needPhone(List<Coupon> candidates) { return new LookupResult(LookupStatus.NEED_PHONE, null, candidates.size(), candidates); }
	}

	private final CouponRepository couponRepository;

	public CouponService(CouponRepository couponRepository) {
		this.couponRepository = couponRepository;
	}

	int freeDrinksFor(int amount) {
		return ChargePolicy.freeDrinksFor(amount);
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
			return LookupResult.needPhone(candidates);
		}
		Optional<Coupon> matched = candidates.stream()
				.filter(c -> phoneLast4.equals(c.phoneLast4()))
				.findFirst();
		return matched.map(LookupResult::found).orElseGet(LookupResult::notFound);
	}

	/** 전화번호는 필수. 동명이인 구분과 잔액 문자, 취소·차액 환불을 쿠폰에 넣을 때 혼동을 막기 위해서다. */
	@Transactional
	public Coupon register(String name, String phoneRaw, int amount) {
		String trimmed = Validation.name(name, "이름", Validation.NAME_MAX);
		Validation.chargeAmount(amount);
		String phone = Validation.mobile(phoneRaw);
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
		Validation.chargeAmount(amount);
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
		String phone = Validation.mobile(phoneRaw);
		String last4 = Coupon.last4Of(phone);
		boolean clash = couponRepository.findByName(coupon.name()).stream()
				.anyMatch(c -> c.id() != couponId && last4.equals(c.phoneLast4()));
		if (clash) {
			throw new BusinessException("같은 이름에 같은 뒤 4자리 번호가 이미 있습니다.");
		}
		couponRepository.updatePhone(couponId, phone);
		return couponRepository.findById(couponId).orElseThrow();
	}

	/** 이름 오타를 고친다. 바꾼 이름에 같은 뒤 4자리가 있으면 거부. */
	@Transactional
	public Coupon rename(long couponId, String nameRaw) {
		Coupon coupon = require(couponId);
		String name = Validation.name(nameRaw, "이름", Validation.NAME_MAX);
		if (!name.equals(coupon.name()) && coupon.phoneLast4() != null) {
			boolean clash = couponRepository.findByName(name).stream()
					.anyMatch(c -> coupon.phoneLast4().equals(c.phoneLast4()));
			if (clash) {
				throw new BusinessException("같은 이름에 같은 뒤 4자리 번호가 이미 있습니다.");
			}
		}
		couponRepository.rename(couponId, name);
		return couponRepository.findById(couponId).orElseThrow();
	}

	/** 충전을 잘못 넣었을 때 잔액과 무료잔 개수를 바로잡는다. 차액을 ADJUST 이력으로 남긴다. */
	@Transactional
	public Coupon adjust(long couponId, int newBalance, int newFreeDrinks) {
		Validation.balance(newBalance);
		Validation.freeDrinks(newFreeDrinks);
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
