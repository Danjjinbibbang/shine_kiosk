package church.kiosk.order;

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

	/** couponId 가 있으면 돌려줄 돈을 그 쿠폰 잔액에 넣는다. 없으면 현금으로 준 것으로. */
	public record SettleRequest(Long couponId) {}

	/** 수정으로 생긴 차액(돌려줄 돈/더 받을 돈)을 처리했다는 표시. */
	@PostMapping("/{id}/settle")
	public void settle(@PathVariable long id, @RequestBody(required = false) SettleRequest request) {
		orderService.settle(id, request == null ? null : request.couponId());
	}

	@PostMapping("/{id}/reopen")
	public void reopen(@PathVariable long id) {
		orderService.reopen(id);
	}

	/** refundToCouponId 가 있으면 받은 현금/이체를 그 쿠폰 잔액으로 돌려준다. 없으면 현금으로 돌려준 것으로. */
	public record CancelRequest(Long refundToCouponId) {}

	@PostMapping("/{id}/cancel")
	public void cancel(@PathVariable long id, @RequestBody(required = false) CancelRequest request) {
		orderService.cancel(id, request == null ? null : request.refundToCouponId());
	}
}
