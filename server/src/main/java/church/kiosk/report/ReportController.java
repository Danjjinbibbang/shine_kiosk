package church.kiosk.report;

import church.kiosk.order.OrderDtos.OrderView;
import church.kiosk.order.OrderRepository;
import church.kiosk.support.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** 스태프 화면 > 기록. 주일별 매출과 그날 주문, CSV. PIN 토큰 필요. */
@RestController
@RequestMapping("/api/staff/reports")
public class ReportController {

	private final ReportRepository reportRepository;
	private final OrderRepository orderRepository;

	public ReportController(ReportRepository reportRepository, OrderRepository orderRepository) {
		this.reportRepository = reportRepository;
		this.orderRepository = orderRepository;
	}

	/** 주문이나 쿠폰 충전이 있었던 날짜별 집계. 최근 날짜부터. */
	@GetMapping("/days")
	public List<ReportRepository.DayReport> days() {
		return reportRepository.findDays();
	}

	/** 그날의 주문 전부 (취소 포함, status 로 구분). */
	@GetMapping("/orders")
	public List<OrderView> ordersOfDay(@RequestParam String date) {
		return orderRepository.findByDate(validDate(date));
	}

	@GetMapping(value = "/days.csv", produces = "text/csv")
	public ResponseEntity<byte[]> daysCsv() {
		StringBuilder sb = new StringBuilder();
		sb.append("날짜,주문수,주문금액,현금,계좌이체,쿠폰사용,무료1잔,사역자무료,쿠폰충전입금\n");
		for (ReportRepository.DayReport d : reportRepository.findDays()) {
			sb.append(String.join(",", d.date(), String.valueOf(d.orderCount()), String.valueOf(d.totalAmount()),
					String.valueOf(d.cashAmount()), String.valueOf(d.transferAmount()), String.valueOf(d.couponAmount()),
					String.valueOf(d.freeAmount()), String.valueOf(d.staffFreeAmount()), String.valueOf(d.couponChargeAmount())))
					.append('\n');
		}
		return csv("매출-일별.csv", sb.toString());
	}

	@GetMapping(value = "/orders.csv", produces = "text/csv")
	public ResponseEntity<byte[]> ordersCsv(@RequestParam String date) {
		String day = validDate(date);
		StringBuilder sb = new StringBuilder();
		sb.append("날짜,번호,시각,이름,받는방법,장소,상태,메뉴,주문금액,현금,계좌이체,쿠폰,무료1잔,사역자무료,결제수단,메모\n");
		for (OrderView o : orderRepository.findByDate(day)) {
			String menu = String.join(" / ", o.lines().stream().map(l -> {
				String name = l.variantLabel() == null ? l.menuName() : l.menuName() + " " + l.variantLabel();
				if (!l.options().isEmpty()) {
					name += "(" + String.join("+", l.options().stream().map(op -> op.name()).toList()) + ")";
				}
				String qty = "x" + l.quantity();
				if (l.staffFreeQty() > 0) qty += "(사역자" + l.staffFreeQty() + ")";
				return name + " " + qty;
			}).toList());
			sb.append(String.join(",",
					o.orderDate(), String.valueOf(o.orderNo()), o.createdAt().length() >= 16 ? o.createdAt().substring(11, 16) : "",
					q(o.customerName()), o.receiveType().name().equals("DELIVERY") ? "배달" : "카페", q(o.placeName()),
					statusLabel(o.status().name()), q(menu), String.valueOf(o.totalAmount()),
					String.valueOf(o.cashAmount()), String.valueOf(o.transferAmount()), String.valueOf(o.couponAmount()),
					String.valueOf(o.freeAmount()), String.valueOf(o.staffFreeAmount()), o.payMethod().name(), q(o.memo())))
					.append('\n');
		}
		return csv("주문-" + day + ".csv", sb.toString());
	}

	private static String validDate(String date) {
		try {
			return LocalDate.parse(date).toString();
		}
		catch (DateTimeParseException e) {
			throw new BusinessException("날짜 형식이 올바르지 않습니다.");
		}
	}

	private static String statusLabel(String status) {
		return switch (status) {
			case "DONE" -> "완료";
			case "CANCELED" -> "취소";
			default -> "대기";
		};
	}

	/** 쉼표/따옴표/줄바꿈이 들어갈 수 있는 칸은 따옴표로 감싼다. */
	private static String q(String s) {
		if (s == null) return "";
		return "\"" + s.replace("\"", "\"\"").replace("\n", " ") + "\"";
	}

	/** 엑셀이 한글을 제대로 읽도록 UTF-8 BOM 을 붙인다. */
	private static ResponseEntity<byte[]> csv(String filename, String body) {
		byte[] bom = { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF };
		byte[] text = body.getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[bom.length + text.length];
		System.arraycopy(bom, 0, out, 0, bom.length);
		System.arraycopy(text, 0, out, bom.length, text.length);
		String encoded = java.net.URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.ok()
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
				.body(out);
	}
}
