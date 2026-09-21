package church.kiosk.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class OrderDtos {

	private OrderDtos() {}

	public enum ReceiveType { STORE, DELIVERY }

	public enum PayMethod { TRANSFER, COUPON, CASH }

	public enum Status { PENDING, DONE, CANCELED }

	public record LineRequest(@NotNull(message = "메뉴를 확인해 주세요.") Long variantId,
							  @Min(value = 1, message = "수량을 확인해 주세요.") int quantity,
							  /** 샷 추가/연하게 같은 옵션 id. 없으면 null 또는 빈 목록. */
							  List<Long> optionIds) {
		public List<Long> optionIdsOrEmpty() { return optionIds == null ? List.of() : optionIds; }
	}

	/** 고객 키오스크의 주문 생성. 가격은 보내지 않는다 — 서버가 메뉴표에서 직접 읽는다. */
	public record CreateRequest(
			@NotBlank(message = "이름을 선택해 주세요.") String customerName,
			@NotNull(message = "받는 방법을 선택해 주세요.") ReceiveType receiveType,
			Long placeId,
			@NotNull(message = "결제 수단을 선택해 주세요.") PayMethod payMethod,
			Long couponId,
			/** 이번 주문에서 무료 1잔을 쓸지. 가장 비싼 한 잔 값이 빠진다. 없으면 안 씀. */
			Boolean useFreeDrink,
			/** 쿠폰 잔액이 모자랄 때 나머지를 어떻게 낼지 (CASH | TRANSFER). */
			PayMethod remainderMethod,
			@NotEmpty(message = "메뉴를 담아 주세요.") @Valid List<LineRequest> lines,
			String memo) {
		public boolean wantsFreeDrink() { return Boolean.TRUE.equals(useFreeDrink); }
	}

	/** 쿠폰 선택 화면에서 얼마가 차감되고 얼마가 남는지 미리 보여주기 위한 요청. */
	public record CouponPreviewRequest(@NotNull Long couponId, Boolean useFreeDrink,
									   @NotEmpty @Valid List<LineRequest> lines) {
		public boolean wantsFreeDrink() { return Boolean.TRUE.equals(useFreeDrink); }
	}

	/**
	 * total 에서 freeAmount(무료 1잔) 를 빼고, 남은 금액을 잔액에서 couponAmount 만큼 빼고,
	 * 그래도 남는 remainder 는 현금/계좌이체.
	 */
	public record CouponPreview(int total, int balance, int freeDrinks,
								boolean useFreeDrink, int freeAmount, String freeItemName,
								int couponAmount, int remainder, int balanceAfter, int freeDrinksAfter) {}

	/** 스태프의 주문 수정. 결제 수단은 바꾸지 않고 항목/이름/장소만 고친다. */
	public record UpdateRequest(
			@NotBlank(message = "이름을 입력해 주세요.") String customerName,
			@NotNull(message = "받는 방법을 선택해 주세요.") ReceiveType receiveType,
			Long placeId,
			@NotEmpty(message = "메뉴가 하나는 있어야 합니다.") @Valid List<LineRequest> lines,
			String memo) {}

	/** 주문 시점에 붙인 옵션 스냅샷. */
	public record LineOptionView(Long optionId, String name, int price) {}

	/** unitPrice 는 옵션 가격까지 더한 한 잔 값. */
	public record LineView(long id, Long variantId, String menuName, String variantLabel,
						   int unitPrice, int quantity, List<LineOptionView> options) {}

	public record OrderView(long id, String orderDate, int orderNo, String customerName,
							ReceiveType receiveType, Long placeId, String placeName,
							int totalAmount, PayMethod payMethod, PayMethod remainderMethod,
							Long couponId, int couponAmount, int freeAmount, String freeItemName,
							int cashAmount, int transferAmount,
							Status status, String memo, String createdAt, String completedAt,
							List<LineView> lines) {}

	/** 스태프 화면 상단의 오늘 집계. */
	public record DailySummary(String date, int orderCount, int totalAmount,
							   int cashAmount, int transferAmount, int couponAmount, int freeAmount) {}
}
