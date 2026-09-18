package church.kiosk.coupon;

import church.kiosk.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

	public CouponService(CouponRepository couponRepository) {
		this.couponRepository = couponRepository;
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

	@Transactional
	public Coupon register(String name, String phoneLast4, int amount) {
		String trimmed = name.trim();
		if (trimmed.isEmpty()) {
			throw new BusinessException("이름을 입력해 주세요.");
		}
		if (amount <= 0) {
			throw new BusinessException("충전 금액을 확인해 주세요.");
		}
		List<Coupon> sameName = couponRepository.findByName(trimmed);
		if (!sameName.isEmpty() && (phoneLast4 == null || phoneLast4.isBlank())) {
			throw new BusinessException("같은 이름이 이미 있습니다. 전화번호 뒤 4자리를 입력해 주세요.");
		}
		long id = couponRepository.insert(trimmed, blankToNull(phoneLast4), amount);
		couponRepository.insertTx(id, null, amount, "CHARGE", amount);
		return couponRepository.findById(id).orElseThrow();
	}

	@Transactional
	public Coupon charge(long couponId, int amount) {
		if (amount <= 0) {
			throw new BusinessException("충전 금액을 확인해 주세요.");
		}
		Coupon coupon = require(couponId);
		int after = coupon.balance() + amount;
		couponRepository.updateBalance(couponId, after);
		couponRepository.insertTx(couponId, null, amount, "CHARGE", after);
		return couponRepository.findById(couponId).orElseThrow();
	}

	/** 주문 결제에 쿠폰 잔액을 사용한다. 호출자가 이미 잔액 범위를 계산해 넘긴다. */
	@Transactional
	public void useForOrder(long couponId, int amount, long orderId) {
		if (amount <= 0) {
			return;
		}
		Coupon coupon = require(couponId);
		if (coupon.balance() < amount) {
			throw new BusinessException("쿠폰 잔액이 부족합니다.");
		}
		int after = coupon.balance() - amount;
		couponRepository.updateBalance(couponId, after);
		couponRepository.insertTx(couponId, orderId, -amount, "USE", after);
	}

	/** 주문 취소/수정 시 이미 차감된 금액을 되돌린다. */
	@Transactional
	public void refundForOrder(long couponId, int amount, long orderId) {
		if (amount <= 0) {
			return;
		}
		Coupon coupon = require(couponId);
		int after = coupon.balance() + amount;
		couponRepository.updateBalance(couponId, after);
		couponRepository.insertTx(couponId, orderId, amount, "REFUND", after);
	}

	public Coupon require(long couponId) {
		return couponRepository.findById(couponId)
				.orElseThrow(() -> new BusinessException("쿠폰을 찾을 수 없습니다."));
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
