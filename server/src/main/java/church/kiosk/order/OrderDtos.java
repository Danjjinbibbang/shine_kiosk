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
							  @Min(value = 1, message = "수량을 확인해 주세요.") int quantity) {}

	/** 고객 키오스크의 주문 생성. 가격은 보내지 않는다 — 서버가 메뉴표에서 직접 읽는다. */
	public record CreateRequest(
			@NotBlank(message = "이름을 선택해 주세요.") String customerName,
			@NotNull(message = "받는 방법을 선택해 주세요.") ReceiveType receiveType,
			Long placeId,
			@NotNull(message = "결제 수단을 선택해 주세요.") PayMethod payMethod,
			Long couponId,
			/** 쿠폰 잔액이 모자랄 때 나머지를 어떻게 낼지 (CASH | TRANSFER). */
			PayMethod remainderMethod,
			@NotEmpty(message = "메뉴를 담아 주세요.") @Valid List<LineRequest> lines,
			String memo) {}

	/** 쿠폰 선택 화면에서 얼마가 차감되고 얼마가 남는지 미리 보여주기 위한 요청. */
	public record CouponPreviewRequest(@NotNull Long couponId,
									   @NotEmpty @Valid List<LineRequest> lines) {}

	public record CouponPreview(int total, int balance, int couponAmount, int remainder, int balanceAfter) {}

	/** 스태프의 주문 수정. 결제 수단은 바꾸지 않고 항목/이름/장소만 고친다. */
	public record UpdateRequest(
			@NotBlank(message = "이름을 입력해 주세요.") String customerName,
			@NotNull(message = "받는 방법을 선택해 주세요.") ReceiveType receiveType,
			Long placeId,
			@NotEmpty(message = "메뉴가 하나는 있어야 합니다.") @Valid List<LineRequest> lines,
			String memo) {}

	public record LineView(long id, Long variantId, String menuName, String variantLabel,
						   int unitPrice, int quantity) {}

	public record OrderView(long id, String orderDate, int orderNo, String customerName,
							ReceiveType receiveType, Long placeId, String placeName,
							int totalAmount, PayMethod payMethod, PayMethod remainderMethod,
							Long couponId, int couponAmount, int cashAmount, int transferAmount,
							Status status, String memo, String createdAt, String completedAt,
							List<LineView> lines) {}

	/** 스태프 화면 상단의 오늘 집계. */
	public record DailySummary(String date, int orderCount, int totalAmount,
							   int cashAmount, int transferAmount, int couponAmount) {}
}
