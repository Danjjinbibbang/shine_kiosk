package church.kiosk.coupon;

import java.util.List;

/**
 * 쿠폰 충전 정책. 한 번에 30,000원까지 충전할 수 있고,
 * 20,000원이면 무료 1잔, 30,000원이면 무료 2잔이 적립된다 (그 사이 금액은 1잔, 20,000원 미만은 없음).
 * 잔액에 따라 쌓이는 게 아니라 "이번 충전 금액" 기준이다.
 */
public final class ChargePolicy {

	private ChargePolicy() {}

	public record Tier(int amount, int freeDrinks) {}

	/** 스태프 화면의 버튼으로 뜨는 금액 */
	public static final List<Tier> TIERS = List.of(new Tier(20_000, 1), new Tier(30_000, 2));

	public static final int MAX = 30_000;

	public static int freeDrinksFor(int amount) {
		int free = 0;
		for (Tier t : TIERS) {
			if (amount >= t.amount()) free = t.freeDrinks();
		}
		return free;
	}
}
