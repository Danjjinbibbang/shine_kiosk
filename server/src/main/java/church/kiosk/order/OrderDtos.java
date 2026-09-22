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

	/** NONE = 사역자 무료로 낼 금액이 0 이라 결제 없음 */
	public enum PayMethod { TRANSFER, COUPON, CASH, NONE }

	public enum Status { PENDING, DONE, CANCELED }

	public record LineRequest(@NotNull(message = "메뉴를 확인해 주세요.") Long variantId,
							  @Min(value = 1, message = "수량을 확인해 주세요.") int quantity,
							  /** 샷 추가/연하게 같은 옵션 id. 없으면 null 또는 빈 목록. */
							  List<Long> optionIds,
							  /** 이 줄에서 사역자 무료로 처리할 잔 수 (0 ~ quantity). 없으면 0. */
							  Integer staffFreeQty) {
		public List<Long> optionIdsOrEmpty() { return optionIds == null ? List.of() : optionIds; }
		public int staffFreeQtyOrZero() { return staffFreeQty == null ? 0 : staffFreeQty; }
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
			String memo,
			/** 손님이 낸 현금 (현금 결제일 때). 거스름돈은 서버가 지금 금액 기준으로 계산해 payNote 로 보여준다. */
			Integer cashGiven,
			/** 키오스크가 만든 요청 번호. 같은 번호로 다시 오면 새로 만들지 않고 처음 것을 돌려준다. */
			String clientRequestId) {
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
						   int unitPrice, int quantity, int staffFreeQty, List<LineOptionView> options) {}

	public record OrderView(long id, String orderDate, int orderNo, String customerName,
							ReceiveType receiveType, Long placeId, String placeName,
							int totalAmount, int staffFreeAmount,
							PayMethod payMethod, PayMethod remainderMethod,
							Long couponId, int couponAmount, int freeAmount, String freeItemName,
							int cashAmount, int transferAmount,
							Status status, String memo, String createdAt, String completedAt,
							/** 스태프가 고친 시각/내용. 안 고쳤으면 null */
							String editedAt, String editNote,
							/** 실제로 받은 현금/이체. cashAmount - settledCash 가 양수면 더 받을 돈, 음수면 돌려줄 돈 */
							int settledCash, int settledTransfer,
							/** 손님이 낸 현금. 없으면 null */
							Integer cashGiven,
							List<LineView> lines) {

		/**
		 * "현금 5,000원 받음 → 거스름돈 1,000원" — 지금 금액 기준이라 수정 뒤에도 맞는다. 현금을 안 냈으면 null.
		 * 쿠폰/이체와 섞인 주문이면 "현금 몫 6,000원 · 8,000원 받음 → 거스름돈 2,000원" 처럼 현금 몫을 앞에 써서
		 * 총액과 안 맞아 보이지 않게 한다.
		 */
		@com.fasterxml.jackson.annotation.JsonProperty
		public String payNote() {
			if (cashGiven == null) return null;
			boolean mixed = cashAmount != totalAmount;
			String head = mixed ? "현금 몫 " + won(cashAmount) + " · " : "현금 ";
			int change = cashGiven - cashAmount;
			if (change > 0) return head + won(cashGiven) + " 받음 → 거스름돈 " + won(change);
			if (change == 0) return head + won(cashGiven) + " 딱 맞게";
			// 낸 돈보다 금액이 커진 경우: 이미 거슬러 준 돈은 빼고, 실제로 아직 못 받은 만큼만
			int owed = cashAmount - settledCash;
			return owed > 0 ? head + won(cashGiven) + " 받음 → " + won(owed) + " 더 받아야" : head + won(cashGiven) + " 받음";
		}

		private static String won(int n) { return String.format("%,d원", n); }
	}

	/** 스태프 화면 상단의 오늘 집계. */
	public record DailySummary(String date, int orderCount, int totalAmount,
							   int cashAmount, int transferAmount, int couponAmount, int freeAmount, int staffFreeAmount) {}
}
