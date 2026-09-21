package church.kiosk.coupon;

/**
 * balance = 선불 잔액, freeDrinks = 남은 무료 1잔 개수.
 * phone 은 전체 번호(잔액 문자용) — 스태프 화면에만 보여주고 고객 화면엔 phoneLast4 만 내려간다.
 */
public record Coupon(long id, String name, String phone, String phoneLast4, int balance, int freeDrinks) {

	/** 고객 키오스크 조회용. 전체 번호는 뺀다. */
	public record PublicView(long id, String name, String phoneLast4, int balance, int freeDrinks) {}

	public PublicView toPublic() {
		return new PublicView(id, name, phoneLast4, balance, freeDrinks);
	}

	/** 숫자만 남긴다. 비어 있으면 null. */
	public static String normalizePhone(String raw) {
		if (raw == null) return null;
		String digits = raw.replaceAll("[^0-9]", "");
		return digits.isEmpty() ? null : digits;
	}

	public static String last4Of(String phone) {
		if (phone == null || phone.length() < 4) return null;
		return phone.substring(phone.length() - 4);
	}
}
