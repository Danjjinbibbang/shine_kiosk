package church.kiosk.menu;

import java.util.List;

public final class MenuDtos {

	private MenuDtos() {}

	/** label 이 null 이면 선택지 없는 단일 메뉴. */
	public record VariantView(long id, String label, int price) {}

	public record ItemView(long id, String name, String category, List<VariantView> variants) {}

	/** 주문 생성 시 서버가 직접 조회하는 원본 정보. 가격은 절대 클라이언트를 믿지 않는다. */
	public record VariantDetail(long variantId, long menuItemId, String menuName, String label,
								int price, boolean available) {}
}
