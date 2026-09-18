package church.kiosk.order;

import church.kiosk.coupon.Coupon;
import church.kiosk.coupon.CouponService;
import church.kiosk.customer.CustomerRepository;
import church.kiosk.menu.MenuDtos.VariantDetail;
import church.kiosk.menu.MenuRepository;
import church.kiosk.order.OrderDtos.CouponPreview;
import church.kiosk.order.OrderDtos.CreateRequest;
import church.kiosk.order.OrderDtos.LineRequest;
import church.kiosk.order.OrderDtos.OrderView;
import church.kiosk.order.OrderDtos.PayMethod;
import church.kiosk.order.OrderDtos.ReceiveType;
import church.kiosk.order.OrderDtos.Status;
import church.kiosk.order.OrderDtos.UpdateRequest;
import church.kiosk.order.OrderRepository.LineRow;
import church.kiosk.order.OrderRepository.OrderRow;
import church.kiosk.place.PlaceRepository;
import church.kiosk.place.PlaceRepository.Place;
import church.kiosk.realtime.KioskEventHandler;
import church.kiosk.support.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

	private final OrderRepository orderRepository;
	private final MenuRepository menuRepository;
	private final PlaceRepository placeRepository;
	private final CouponService couponService;
	private final CustomerRepository customerRepository;
	private final KioskEventHandler events;

	public OrderService(OrderRepository orderRepository, MenuRepository menuRepository,
						PlaceRepository placeRepository, CouponService couponService,
						CustomerRepository customerRepository, KioskEventHandler events) {
		this.orderRepository = orderRepository;
		this.menuRepository = menuRepository;
		this.placeRepository = placeRepository;
		this.couponService = couponService;
		this.customerRepository = customerRepository;
		this.events = events;
	}

	/** 가격이 확정된 주문 항목 묶음. */
	private record PricedLines(List<LineRow> lines, int total, int maxUnitPrice) {}

	/**
	 * 쿠폰 차감 규칙: 쿠폰은 "한 잔 무료" 이므로 주문한 것 중 가장 비싼 한 잔 값이 빠진다.
	 * 잔액이 그보다 적으면 잔액을 전부 쓰고, 총액에서 쿠폰으로 못 낸 나머지는 현금/계좌이체로 받는다.
	 */
	static int couponAmountFor(int balance, int maxUnitPrice) {
		return Math.min(balance, maxUnitPrice);
	}

	public CouponPreview previewCoupon(long couponId, List<LineRequest> lines) {
		PricedLines priced = price(lines);
		Coupon coupon = couponService.require(couponId);
		int couponAmount = couponAmountFor(coupon.balance(), priced.maxUnitPrice());
		return new CouponPreview(priced.total(), coupon.balance(), couponAmount,
				priced.total() - couponAmount, coupon.balance() - couponAmount);
	}

	@Transactional
	public OrderView create(CreateRequest req) {
		PricedLines priced = price(req.lines());
		Place place = resolvePlace(req.receiveType(), req.placeId());
		String name = req.customerName().trim();

		int couponAmount = 0;
		Long couponId = null;
		PayMethod remainderMethod = null;
		if (req.payMethod() == PayMethod.COUPON) {
			if (req.couponId() == null) {
				throw new BusinessException("쿠폰을 먼저 조회해 주세요.");
			}
			Coupon coupon = couponService.require(req.couponId());
			couponId = coupon.id();
			couponAmount = couponAmountFor(coupon.balance(), priced.maxUnitPrice());
			if (priced.total() - couponAmount > 0) {
				remainderMethod = requireRemainderMethod(req.remainderMethod());
			}
		}
		int remainder = priced.total() - couponAmount;
		int cash = 0;
		int transfer = 0;
		PayMethod remainderBy = req.payMethod() == PayMethod.COUPON ? remainderMethod : req.payMethod();
		if (remainderBy == PayMethod.CASH) {
			cash = remainder;
		}
		else if (remainderBy == PayMethod.TRANSFER) {
			transfer = remainder;
		}

		String today = LocalDate.now().toString();
		OrderRow row = new OrderRow(today, orderRepository.nextOrderNo(today), name, req.receiveType(),
				place == null ? null : place.id(), place == null ? null : place.name(),
				priced.total(), req.payMethod(), remainderMethod, couponId, couponAmount,
				cash, transfer, blankToNull(req.memo()));
		long orderId = orderRepository.insert(row);
		orderRepository.insertLines(orderId, priced.lines());
		if (couponId != null) {
			couponService.useForOrder(couponId, couponAmount, orderId);
		}
		customerRepository.recordOrder(name);

		OrderView created = orderRepository.findById(orderId).orElseThrow();
		events.broadcastOrdersChanged();
		return created;
	}

	/**
	 * 스태프가 항목/이름/장소를 고친다. 쿠폰을 쓴 주문이면 먼저 차감을 되돌리고
	 * 새 항목 기준으로 다시 차감해서, 잔액 이력이 항상 실제 주문과 맞아떨어지게 한다.
	 */
	@Transactional
	public OrderView update(long orderId, UpdateRequest req) {
		OrderView existing = requirePending(orderId);
		PricedLines priced = price(req.lines());
		Place place = resolvePlace(req.receiveType(), req.placeId());

		int couponAmount = 0;
		PayMethod remainderMethod = existing.remainderMethod();
		if (existing.couponId() != null) {
			couponService.refundForOrder(existing.couponId(), existing.couponAmount(), orderId);
			Coupon coupon = couponService.require(existing.couponId());
			couponAmount = couponAmountFor(coupon.balance(), priced.maxUnitPrice());
			couponService.useForOrder(existing.couponId(), couponAmount, orderId);
			if (priced.total() - couponAmount > 0 && remainderMethod == null) {
				remainderMethod = PayMethod.CASH; // 원래 쿠폰만으로 됐던 주문이 커지면 나머지는 현금으로 받는다
			}
		}
		int remainder = priced.total() - couponAmount;
		PayMethod remainderBy = existing.payMethod() == PayMethod.COUPON ? remainderMethod : existing.payMethod();
		int cash = remainderBy == PayMethod.CASH ? remainder : 0;
		int transfer = remainderBy == PayMethod.TRANSFER ? remainder : 0;

		OrderRow row = new OrderRow(existing.orderDate(), existing.orderNo(), req.customerName().trim(),
				req.receiveType(), place == null ? null : place.id(), place == null ? null : place.name(),
				priced.total(), existing.payMethod(), remainderMethod, existing.couponId(), couponAmount,
				cash, transfer, blankToNull(req.memo()));
		orderRepository.update(orderId, row);
		orderRepository.deleteLines(orderId);
		orderRepository.insertLines(orderId, priced.lines());

		OrderView updated = orderRepository.findById(orderId).orElseThrow();
		events.broadcastOrdersChanged();
		return updated;
	}

	@Transactional
	public void complete(long orderId) {
		requirePending(orderId);
		orderRepository.markDone(orderId);
		events.broadcastOrdersChanged();
	}

	/** 실수로 완료를 눌렀을 때 되돌린다. */
	@Transactional
	public void reopen(long orderId) {
		OrderView order = require(orderId);
		if (order.status() != Status.DONE) {
			throw new BusinessException("완료된 주문만 되돌릴 수 있습니다.");
		}
		orderRepository.markPending(orderId);
		events.broadcastOrdersChanged();
	}

	@Transactional
	public void cancel(long orderId) {
		OrderView order = require(orderId);
		if (order.status() == Status.CANCELED) {
			return;
		}
		if (order.couponId() != null) {
			couponService.refundForOrder(order.couponId(), order.couponAmount(), orderId);
		}
		orderRepository.markCanceled(orderId);
		events.broadcastOrdersChanged();
	}

	public List<OrderView> pending() {
		return orderRepository.findPending();
	}

	public List<OrderView> todayByStatus(Status status) {
		return orderRepository.findByDateAndStatus(LocalDate.now().toString(), status);
	}

	public OrderDtos.DailySummary todaySummary() {
		return orderRepository.summarize(LocalDate.now().toString());
	}

	// ── 내부 도우미 ──────────────────────────────────────────

	/** 클라이언트가 보낸 variantId 로 서버의 메뉴표를 읽어 가격을 확정한다. */
	private PricedLines price(List<LineRequest> requests) {
		List<LineRow> lines = new ArrayList<>();
		int total = 0;
		int max = 0;
		for (LineRequest r : requests) {
			VariantDetail v = menuRepository.findVariantDetail(r.variantId())
					.orElseThrow(() -> new BusinessException("없는 메뉴가 담겨 있습니다. 다시 담아 주세요."));
			if (!v.available()) {
				throw new BusinessException(v.menuName() + "은(는) 지금 주문할 수 없습니다.");
			}
			lines.add(new LineRow(v.menuItemId(), v.variantId(), v.menuName(), v.label(), v.price(), r.quantity()));
			total += v.price() * r.quantity();
			max = Math.max(max, v.price());
		}
		return new PricedLines(lines, total, max);
	}

	private Place resolvePlace(ReceiveType receiveType, Long placeId) {
		if (receiveType != ReceiveType.DELIVERY) {
			return null;
		}
		if (placeId == null) {
			throw new BusinessException("배달 장소를 선택해 주세요.");
		}
		return placeRepository.findById(placeId)
				.orElseThrow(() -> new BusinessException("배달 장소를 다시 선택해 주세요."));
	}

	private static PayMethod requireRemainderMethod(PayMethod method) {
		if (method != PayMethod.CASH && method != PayMethod.TRANSFER) {
			throw new BusinessException("쿠폰 잔액이 모자랍니다. 나머지를 현금 또는 계좌이체 중에서 골라 주세요.");
		}
		return method;
	}

	private OrderView require(long orderId) {
		return orderRepository.findById(orderId)
				.orElseThrow(() -> new BusinessException("주문을 찾을 수 없습니다."));
	}

	private OrderView requirePending(long orderId) {
		OrderView order = require(orderId);
		if (order.status() != Status.PENDING) {
			throw new BusinessException("이미 처리된 주문입니다.");
		}
		return order;
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s.trim();
	}
}
