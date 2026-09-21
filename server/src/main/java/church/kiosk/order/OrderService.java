package church.kiosk.order;

import church.kiosk.coupon.Coupon;
import church.kiosk.coupon.CouponService;
import church.kiosk.customer.CustomerRepository;
import church.kiosk.member.StaffMemberRepository;
import church.kiosk.member.StaffMemberRepository.StaffMember;
import church.kiosk.menu.MenuDtos.AdminOption;
import church.kiosk.menu.MenuDtos.VariantDetail;
import church.kiosk.menu.MenuOptionRepository;
import church.kiosk.menu.MenuRepository;
import church.kiosk.order.OrderDtos.LineOptionView;
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
	private final MenuOptionRepository optionRepository;
	private final PlaceRepository placeRepository;
	private final CouponService couponService;
	private final CustomerRepository customerRepository;
	private final StaffMemberRepository staffMemberRepository;
	private final KioskEventHandler events;

	public OrderService(OrderRepository orderRepository, MenuRepository menuRepository,
						MenuOptionRepository optionRepository, PlaceRepository placeRepository,
						CouponService couponService, CustomerRepository customerRepository,
						StaffMemberRepository staffMemberRepository, KioskEventHandler events) {
		this.orderRepository = orderRepository;
		this.menuRepository = menuRepository;
		this.optionRepository = optionRepository;
		this.placeRepository = placeRepository;
		this.couponService = couponService;
		this.customerRepository = customerRepository;
		this.staffMemberRepository = staffMemberRepository;
		this.events = events;
	}

	/**
	 * 가격이 확정된 주문 항목 묶음.
	 * total 은 사역자 무료 잔을 뺀 받을 금액, staffFreeAmount 는 사역자 무료로 뺀 금액.
	 * maxUnit* 은 돈 내는 잔 중 가장 비싼 한 잔 (쿠폰 무료 1잔 대상).
	 */
	private record PricedLines(List<LineRow> lines, int total, int staffFreeAmount, int maxUnitPrice, String maxUnitName) {}

	/** 쿠폰 적용 결과. */
	private record CouponUse(boolean useFree, int freeAmount, String freeItemName, int couponAmount, int remainder) {}

	/**
	 * 쿠폰 차감 규칙:
	 *  1) 무료 1잔을 쓰면 가장 비싼 한 잔 값(freeAmount)이 먼저 빠진다.
	 *  2) 남은 금액은 잔액에서 전액 차감. 잔액이 모자라면 잔액을 다 쓰고 나머지(remainder)는 현금/계좌이체.
	 */
	static CouponUse applyCoupon(Coupon coupon, PricedLines priced, boolean wantFree) {
		boolean useFree = wantFree && coupon.freeDrinks() > 0 && priced.maxUnitPrice() > 0;
		int freeAmount = useFree ? priced.maxUnitPrice() : 0;
		int payable = priced.total() - freeAmount;
		int couponAmount = Math.min(coupon.balance(), payable);
		return new CouponUse(useFree, freeAmount, useFree ? priced.maxUnitName() : null,
				couponAmount, payable - couponAmount);
	}

	public CouponPreview previewCoupon(long couponId, boolean useFreeDrink, List<LineRequest> lines) {
		PricedLines priced = price(lines);
		Coupon coupon = couponService.require(couponId);
		CouponUse use = applyCoupon(coupon, priced, useFreeDrink);
		return new CouponPreview(priced.total(), coupon.balance(), coupon.freeDrinks(),
				use.useFree(), use.freeAmount(), use.freeItemName(),
				use.couponAmount(), use.remainder(),
				coupon.balance() - use.couponAmount(), coupon.freeDrinks() - (use.useFree() ? 1 : 0));
	}

	@Transactional
	public OrderView create(CreateRequest req) {
		PricedLines priced = price(req.lines());
		Place place = resolvePlace(req.receiveType(), req.placeId());
		String name = req.customerName().trim();
		String staffName = resolveStaffMember(priced, req.staffMemberId());

		if (priced.total() == 0 && req.payMethod() != PayMethod.NONE) {
			throw new BusinessException("낼 금액이 없는 주문입니다. 결제 없이 접수해 주세요.");
		}
		if (priced.total() > 0 && req.payMethod() == PayMethod.NONE) {
			throw new BusinessException("결제 수단을 선택해 주세요.");
		}

		CouponUse use = new CouponUse(false, 0, null, 0, priced.total());
		Long couponId = null;
		PayMethod remainderMethod = null;
		if (req.payMethod() == PayMethod.COUPON) {
			if (req.couponId() == null) {
				throw new BusinessException("쿠폰을 먼저 조회해 주세요.");
			}
			Coupon coupon = couponService.require(req.couponId());
			if (req.wantsFreeDrink() && coupon.freeDrinks() < 1) {
				throw new BusinessException("남은 무료 1잔이 없습니다.");
			}
			couponId = coupon.id();
			use = applyCoupon(coupon, priced, req.wantsFreeDrink());
			if (use.remainder() > 0) {
				remainderMethod = requireRemainderMethod(req.remainderMethod());
			}
		}
		int remainder = use.remainder();
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
				priced.total(), staffName, priced.staffFreeAmount(), req.payMethod(), remainderMethod, couponId, use.couponAmount(),
				use.freeAmount(), use.freeItemName(), cash, transfer, blankToNull(req.memo()));
		long orderId = orderRepository.insert(row);
		orderRepository.insertLines(orderId, priced.lines());
		if (couponId != null) {
			couponService.useForOrder(couponId, use.couponAmount(), use.useFree(), orderId);
		}
		customerRepository.recordOrder(name);

		OrderView created = orderRepository.findById(orderId).orElseThrow();
		events.broadcastOrdersChanged();
		return created;
	}

	/**
	 * 스태프가 항목/이름/장소를 고친다. 쿠폰을 쓴 주문이면 먼저 차감(무료 1잔 포함)을 되돌리고
	 * 새 항목 기준으로 다시 차감해서, 잔액 이력이 항상 실제 주문과 맞아떨어지게 한다.
	 */
	@Transactional
	public OrderView update(long orderId, UpdateRequest req) {
		OrderView existing = requirePending(orderId);
		PricedLines priced = price(req.lines());
		Place place = resolvePlace(req.receiveType(), req.placeId());
		if (priced.staffFreeAmount() > 0 && existing.staffMemberName() == null) {
			throw new BusinessException("사역자 주문이 아니어서 사역자 무료를 넣을 수 없습니다.");
		}

		CouponUse use = new CouponUse(false, 0, null, 0, priced.total());
		PayMethod remainderMethod = existing.remainderMethod();
		if (existing.couponId() != null) {
			boolean usedFree = existing.freeAmount() > 0;
			couponService.refundForOrder(existing.couponId(), existing.couponAmount(), usedFree, orderId);
			Coupon coupon = couponService.require(existing.couponId());
			use = applyCoupon(coupon, priced, usedFree); // 무료 1잔 사용 여부는 원래 선택을 유지
			couponService.useForOrder(existing.couponId(), use.couponAmount(), use.useFree(), orderId);
			if (use.remainder() > 0 && remainderMethod == null) {
				remainderMethod = PayMethod.CASH; // 원래 쿠폰만으로 됐던 주문이 커지면 나머지는 현금으로 받는다
			}
		}
		int remainder = use.remainder();
		PayMethod remainderBy = existing.payMethod() == PayMethod.COUPON ? remainderMethod : existing.payMethod();
		if (remainderBy == PayMethod.NONE && remainder > 0) {
			remainderBy = PayMethod.CASH; // 전부 무료였던 주문에 돈 낼 잔이 생기면 현금으로 받는다
		}
		int cash = remainderBy == PayMethod.CASH ? remainder : 0;
		int transfer = remainderBy == PayMethod.TRANSFER ? remainder : 0;

		OrderRow row = new OrderRow(existing.orderDate(), existing.orderNo(), req.customerName().trim(),
				req.receiveType(), place == null ? null : place.id(), place == null ? null : place.name(),
				priced.total(), existing.staffMemberName(), priced.staffFreeAmount(),
				existing.payMethod(), remainderMethod, existing.couponId(), use.couponAmount(),
				use.freeAmount(), use.freeItemName(), cash, transfer, blankToNull(req.memo()));
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
			couponService.refundForOrder(order.couponId(), order.couponAmount(), order.freeAmount() > 0, orderId);
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

	/**
	 * 클라이언트가 보낸 variantId/optionIds 로 서버의 메뉴표를 읽어 가격을 확정한다.
	 * 한 잔 값 = 기본가 + 옵션 합. 무료 1잔 후보(가장 비싼 한 잔)도 이 값으로 고른다.
	 */
	private PricedLines price(List<LineRequest> requests) {
		List<LineRow> lines = new ArrayList<>();
		int total = 0;
		int staffFree = 0;
		int max = 0;
		String maxName = null;
		for (LineRequest r : requests) {
			int freeQty = r.staffFreeQtyOrZero();
			if (freeQty < 0 || freeQty > r.quantity()) {
				throw new BusinessException("사역자 잔 수는 0부터 수량까지만 가능합니다.");
			}
			int paidQty = r.quantity() - freeQty;
			VariantDetail v = menuRepository.findVariantDetail(r.variantId())
					.orElseThrow(() -> new BusinessException("없는 메뉴가 담겨 있습니다. 다시 담아 주세요."));
			if (!v.available()) {
				throw new BusinessException(v.menuName() + "은(는) 지금 주문할 수 없습니다.");
			}
			List<LineOptionView> options = new ArrayList<>();
			int unit = v.price();
			for (Long optionId : new java.util.LinkedHashSet<>(r.optionIdsOrEmpty())) {
				AdminOption opt = optionRepository.findById(optionId)
						.orElseThrow(() -> new BusinessException("없는 옵션이 담겨 있습니다. 다시 담아 주세요."));
				if (!opt.available()) {
					throw new BusinessException(opt.name() + " 옵션은 지금 고를 수 없습니다.");
				}
				if (!opt.category().equals(v.category())) {
					throw new BusinessException(v.menuName() + "에는 " + opt.name() + " 옵션을 넣을 수 없습니다.");
				}
				options.add(new LineOptionView(opt.id(), opt.name(), opt.price()));
				unit += opt.price();
			}
			lines.add(new LineRow(v.menuItemId(), v.variantId(), v.menuName(), v.label(), unit, r.quantity(), freeQty, options));
			total += unit * paidQty;
			staffFree += unit * freeQty;
			if (paidQty > 0 && unit > max) {
				max = unit;
				String base = v.label() == null ? v.menuName() : v.menuName() + " " + v.label();
				maxName = options.isEmpty() ? base
						: base + " (" + String.join(", ", options.stream().map(LineOptionView::name).toList()) + ")";
			}
		}
		return new PricedLines(lines, total, staffFree, max, maxName);
	}

	/** 사역자 무료 잔이 있으면 명단의 사역자여야 한다. 없으면 null. */
	private String resolveStaffMember(PricedLines priced, Long staffMemberId) {
		if (priced.staffFreeAmount() == 0 && priced.lines().stream().allMatch(l -> l.staffFreeQty() == 0)) {
			return null;
		}
		if (staffMemberId == null) {
			throw new BusinessException("사역자를 선택해 주세요.");
		}
		StaffMember member = staffMemberRepository.findById(staffMemberId)
				.filter(StaffMember::active)
				.orElseThrow(() -> new BusinessException("사역자 명단에 없는 이름입니다."));
		return member.name();
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
