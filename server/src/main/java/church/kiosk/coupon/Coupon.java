package church.kiosk.coupon;

/** balance = 선불 잔액, freeDrinks = 남은 무료 1잔 개수 */
public record Coupon(long id, String name, String phoneLast4, int balance, int freeDrinks) {}
