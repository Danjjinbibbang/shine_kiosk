package church.kiosk.order;

import church.kiosk.config.KioskProperties;
import church.kiosk.order.OrderDtos.CouponPreview;
import church.kiosk.order.OrderDtos.CouponPreviewRequest;
import church.kiosk.order.OrderDtos.CreateRequest;
import church.kiosk.order.OrderDtos.OrderView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 고객 키오스크용. PIN 없이 열려 있다. */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

	private final OrderService orderService;
	private final KioskProperties props;

	public OrderController(OrderService orderService, KioskProperties props) {
		this.orderService = orderService;
		this.props = props;
	}

	@PostMapping
	public OrderView create(@RequestBody @Valid CreateRequest request) {
		return orderService.create(request);
	}

	@PostMapping("/coupon-preview")
	public CouponPreview couponPreview(@RequestBody @Valid CouponPreviewRequest request) {
		return orderService.previewCoupon(request.couponId(), request.wantsFreeDrink(), request.lines());
	}

	/** 계좌이체 화면에 띄울 계좌 안내. */
	@GetMapping("/payment-info")
	public Map<String, String> paymentInfo() {
		return Map.of("bankAccount", props.getBankAccount());
	}
}
