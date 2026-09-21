package church.kiosk.support;

import java.util.regex.Pattern;

/**
 * 입력값 규칙 한곳에. 화면(클라이언트)도 같은 규칙으로 막지만, 최종 판단은 항상 서버가 한다.
 */
public final class Validation {

	private Validation() {}

	/** 한국 휴대폰: 010/011/016/017/018/019 + 7~8자리 (숫자만 남긴 상태) */
	private static final Pattern MOBILE = Pattern.compile("^01[016789][0-9]{7,8}$");

	public static final int NAME_MAX = 20;       // 손님/쿠폰/옵션/카테고리/장소 이름
	public static final int MENU_NAME_MAX = 30;
	public static final int LABEL_MAX = 10;      // ICE/HOT 같은 선택지 이름
	public static final int MEMO_MAX = 200;
	public static final int PRICE_MAX = 100_000;
	public static final int PRICE_UNIT = 100;
	public static final int CHARGE_MAX = 1_000_000;
	public static final int CHARGE_UNIT = 1_000;
	public static final int FREE_DRINKS_MAX = 100;
	public static final int QTY_MAX = 99;
	public static final int LINES_MAX = 50;
	public static final int FLOOR_MAX = 99;

	/** 앞뒤 공백을 지우고 비어 있거나 너무 길면 거부. 돌려주는 값은 정리된 문자열. */
	public static String name(String raw, String what, int max) {
		String s = raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
		if (s.isEmpty()) throw new BusinessException(what + "을(를) 입력해 주세요.");
		if (s.length() > max) throw new BusinessException(what + "은(는) " + max + "자까지입니다.");
		return s;
	}

	public static String memo(String raw) {
		if (raw == null || raw.isBlank()) return null;
		String s = raw.trim();
		if (s.length() > MEMO_MAX) throw new BusinessException("메모는 " + MEMO_MAX + "자까지입니다.");
		return s;
	}

	/** 숫자만 남긴 휴대폰 번호. 형식이 아니면 거부. */
	public static String mobile(String raw) {
		String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
		if (digits.isEmpty()) throw new BusinessException("전화번호를 입력해 주세요.");
		if (!MOBILE.matcher(digits).matches()) {
			throw new BusinessException("휴대폰 번호 형식이 아닙니다. 예: 010-1234-5678");
		}
		return digits;
	}

	public static int price(int price) {
		if (price < 0) throw new BusinessException("가격은 0원 이상이어야 합니다.");
		if (price > PRICE_MAX) throw new BusinessException("가격은 " + String.format("%,d", PRICE_MAX) + "원까지입니다.");
		if (price % PRICE_UNIT != 0) throw new BusinessException("가격은 " + PRICE_UNIT + "원 단위로 입력해 주세요.");
		return price;
	}

	public static int chargeAmount(int amount) {
		if (amount <= 0) throw new BusinessException("충전 금액을 확인해 주세요.");
		if (amount > CHARGE_MAX) throw new BusinessException("한 번에 " + String.format("%,d", CHARGE_MAX) + "원까지 충전할 수 있습니다.");
		if (amount % CHARGE_UNIT != 0) throw new BusinessException("충전 금액은 " + String.format("%,d", CHARGE_UNIT) + "원 단위로 입력해 주세요.");
		return amount;
	}

	public static int balance(int balance) {
		if (balance < 0) throw new BusinessException("잔액은 0원 이상이어야 합니다.");
		if (balance > CHARGE_MAX) throw new BusinessException("잔액은 " + String.format("%,d", CHARGE_MAX) + "원까지입니다.");
		return balance;
	}

	public static int freeDrinks(int n) {
		if (n < 0) throw new BusinessException("무료잔 개수는 0 이상이어야 합니다.");
		if (n > FREE_DRINKS_MAX) throw new BusinessException("무료잔은 " + FREE_DRINKS_MAX + "잔까지입니다.");
		return n;
	}

	public static int quantity(int qty) {
		if (qty < 1) throw new BusinessException("수량을 확인해 주세요.");
		if (qty > QTY_MAX) throw new BusinessException("한 줄에 " + QTY_MAX + "잔까지 담을 수 있습니다.");
		return qty;
	}

	public static int floor(int floor) {
		if (floor < 1 || floor > FLOOR_MAX) throw new BusinessException("층은 1~" + FLOOR_MAX + " 사이여야 합니다.");
		return floor;
	}
}
