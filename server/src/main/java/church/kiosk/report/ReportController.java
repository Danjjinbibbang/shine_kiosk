package church.kiosk.report;

import church.kiosk.coupon.Coupon;
import church.kiosk.coupon.CouponRepository;
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
	private final church.kiosk.config.BackupService backupService;
	private final CouponRepository couponRepository;

	public ReportController(ReportRepository reportRepository, OrderRepository orderRepository,
							church.kiosk.config.BackupService backupService, CouponRepository couponRepository) {
		this.reportRepository = reportRepository;
		this.orderRepository = orderRepository;
		this.backupService = backupService;
		this.couponRepository = couponRepository;
	}

	/**
	 * 폰에 저장하는 백업. DB 파일(.db)은 폰에서 열 수 없으니 zip 으로 묶어 복구용 DB 와
	 * 사람이 바로 볼 수 있는 CSV(쿠폰 잔액, 일별 매출, 전체 주문)를 같이 넣는다. 아이폰 파일 앱에서 바로 열린다.
	 */
	@GetMapping(value = "/backup.zip", produces = "application/zip")
	public ResponseEntity<byte[]> backupZip() throws java.io.IOException {
		java.nio.file.Path tmp = backupService.snapshotForDownload();
		try {
			java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
			try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
				zipEntry(zip, "kiosk.db", java.nio.file.Files.readAllBytes(tmp));
				zipEntry(zip, "쿠폰 잔액.csv", couponsCsv());
				zipEntry(zip, "매출-일별.csv", daysCsvText());
				zipEntry(zip, "주문-전체.csv", allOrdersCsvText());
				zipEntry(zip, "읽어주세요.txt", "kiosk.db 는 복구용 DB 파일입니다 (폰에서는 안 열립니다).\n"
						+ "태블릿이 고장 나면 새 태블릿의 ~/kiosk/data/kiosk.db 자리에 이 파일을 넣고 서버를 켜면 됩니다.\n"
						+ "나머지 CSV 는 그 시점의 쿠폰 잔액 / 일별 매출 / 전체 주문입니다.\n");
			}
			String name = "kiosk-backup-" + LocalDate.now() + ".zip";
			return ResponseEntity.ok()
					.contentType(MediaType.parseMediaType("application/zip"))
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
					.body(bytes.toByteArray());
		}
		finally {
			java.nio.file.Files.deleteIfExists(tmp);
		}
	}

	private static void zipEntry(java.util.zip.ZipOutputStream zip, String name, byte[] data) throws java.io.IOException {
		zip.putNextEntry(new java.util.zip.ZipEntry(name));
		zip.write(data);
		zip.closeEntry();
	}

	private static void zipEntry(java.util.zip.ZipOutputStream zip, String name, String text) throws java.io.IOException {
		zipEntry(zip, name, withBom(text));
	}

	/** 엑셀/아이폰이 한글을 제대로 읽도록 UTF-8 BOM 을 앞에 붙인다. */
	private static byte[] withBom(String text) {
		byte[] body = text.getBytes(StandardCharsets.UTF_8);
		byte[] out = new byte[body.length + 3];
		out[0] = (byte) 0xEF; out[1] = (byte) 0xBB; out[2] = (byte) 0xBF;
		System.arraycopy(body, 0, out, 3, body.length);
		return out;
	}

	private String couponsCsv() {
		StringBuilder sb = new StringBuilder("이름,전화번호,잔액,무료1잔\n");
		for (Coupon c : couponRepository.findAll()) {
			sb.append(String.join(",", q(c.name()), q(c.phone()), String.valueOf(c.balance()), String.valueOf(c.freeDrinks()))).append('\n');
		}
		return sb.toString();
	}

	private String allOrdersCsvText() {
		StringBuilder sb = new StringBuilder(ORDERS_HEADER);
		for (ReportRepository.DayReport d : reportRepository.findDays()) {
			appendOrders(sb, d.date());
		}
		return sb.toString();
	}

	/** DB 파일 통째로. 스태프 폰에 저장해 두면 태블릿이 고장 나도 복구할 수 있다. */
	@GetMapping(value = "/backup.db", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ResponseEntity<byte[]> backup() throws java.io.IOException {
		java.nio.file.Path tmp = backupService.snapshotForDownload();
		try {
			byte[] bytes = java.nio.file.Files.readAllBytes(tmp);
			String name = "kiosk-backup-" + LocalDate.now() + ".db";
			return ResponseEntity.ok()
					.contentType(MediaType.APPLICATION_OCTET_STREAM)
					.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
					.body(bytes);
		}
		finally {
			java.nio.file.Files.deleteIfExists(tmp);
		}
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
		return csv("매출-일별.csv", daysCsvText());
	}

	private String daysCsvText() {
		StringBuilder sb = new StringBuilder();
		sb.append("날짜,주문수,주문금액,현금,계좌이체,쿠폰사용,무료1잔,사역자무료,쿠폰충전입금\n");
		for (ReportRepository.DayReport d : reportRepository.findDays()) {
			sb.append(String.join(",", d.date(), String.valueOf(d.orderCount()), String.valueOf(d.totalAmount()),
					String.valueOf(d.cashAmount()), String.valueOf(d.transferAmount()), String.valueOf(d.couponAmount()),
					String.valueOf(d.freeAmount()), String.valueOf(d.staffFreeAmount()), String.valueOf(d.couponChargeAmount())))
					.append('\n');
		}
		return sb.toString();
	}

	private static final String ORDERS_HEADER = "날짜,번호,시각,이름,받는방법,장소,상태,메뉴,주문금액,현금,계좌이체,쿠폰,무료1잔,사역자무료,결제수단,메모\n";

	@GetMapping(value = "/orders.csv", produces = "text/csv")
	public ResponseEntity<byte[]> ordersCsv(@RequestParam String date) {
		String day = validDate(date);
		StringBuilder sb = new StringBuilder(ORDERS_HEADER);
		appendOrders(sb, day);
		return csv("주문-" + day + ".csv", sb.toString());
	}

	private void appendOrders(StringBuilder sb, String day) {
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
