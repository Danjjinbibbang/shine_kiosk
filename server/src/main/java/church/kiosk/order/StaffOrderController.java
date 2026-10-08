package church.kiosk.order;

import church.kiosk.coupon.Coupon;
import church.kiosk.order.OrderDtos.DailySummary;
import church.kiosk.order.OrderDtos.OrderView;
import church.kiosk.order.OrderDtos.Status;
import church.kiosk.order.OrderDtos.UpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 스태프 폰용. /api/staff/** 는 인터셉터가 PIN 토큰을 검사한다. */
@RestController
@RequestMapping("/api/staff/orders")
public class StaffOrderController {

	private final OrderService orderService;

	public StaffOrderController(OrderService orderService) {
		this.orderService = orderService;
	}

	/** PENDING 은 날짜 무관 전부, DONE/CANCELED 는 오늘 것만. */
	@GetMapping
	public List<OrderView> list(@RequestParam(defaultValue = "PENDING") Status status) {
		return status == Status.PENDING ? orderService.pending() : orderService.todayByStatus(status);
	}

	@GetMapping("/summary")
	public DailySummary summary() {
		return orderService.todaySummary();
	}

	@PutMapping("/{id}")
	public OrderView update(@PathVariable long id, @RequestBody @Valid UpdateRequest request) {
		return orderService.update(id, request);
	}

	@PostMapping("/{id}/done")
	public void done(@PathVariable long id) {
		orderService.complete(id);
	}

	/**
	 * 돌려줄 돈: method = CASH(기본) | TRANSFER | COUPON(couponId 필요). couponId 만 줘도 COUPON.
	 * 더 받을 돈: method = CASH | TRANSFER | COUPON (COUPON 이면 couponId 필요).
	 */
	public record SettleRequest(Long couponId, OrderDtos.PayMethod method) {}

	/** 수정으로 생긴 차액(돌려줄 돈/더 받을 돈)을 처리했다는 표시. */
	@PostMapping("/{id}/settle")
	public void settle(@PathVariable long id, @RequestBody(required = false) SettleRequest request) {
		orderService.settle(id, request == null ? null : request.couponId(), request == null ? null : request.method());
	}

	public record ChangeToCouponRequest(Long couponId) {}

	/** 거스름돈은 현금으로 주지 않고 손님 쿠폰 잔액에 넣는다 (무료 1잔 적립 없음). */
	@PostMapping("/{id}/change-to-coupon")
	public void changeToCoupon(@PathVariable long id, @RequestBody ChangeToCouponRequest request) {
		if (request == null || request.couponId() == null) {
			throw new church.kiosk.support.BusinessException("어느 쿠폰에 넣을지 골라 주세요.");
		}
		orderService.changeToCoupon(id, request.couponId());
	}

	public record ChangeToNewCouponRequest(String name, String phone) {}

	/** 쿠폰이 없는 손님: 쿠폰을 새로 만들고 거스름돈을 넣는다. 만든 쿠폰을 돌려준다. */
	@PostMapping("/{id}/change-to-new-coupon")
	public Coupon changeToNewCoupon(@PathVariable long id, @RequestBody ChangeToNewCouponRequest request) {
		if (request == null) {
			throw new church.kiosk.support.BusinessException("이름과 전화번호를 넣어 주세요.");
		}
		return orderService.changeToNewCoupon(id, request.name(), request.phone());
	}

	@PostMapping("/{id}/reopen")
	public void reopen(@PathVariable long id) {
		orderService.reopen(id);
	}

	/** 받은 돈을 어떻게 돌려줬는지: refundToCouponId 가 있으면 그 쿠폰 잔액으로, 아니면 method (CASH 기본 | TRANSFER). */
	public record CancelRequest(Long refundToCouponId, OrderDtos.PayMethod method) {}

	@PostMapping("/{id}/cancel")
	public void cancel(@PathVariable long id, @RequestBody(required = false) CancelRequest request) {
		orderService.cancel(id, request == null ? null : request.refundToCouponId(), request == null ? null : request.method());
	}
}
