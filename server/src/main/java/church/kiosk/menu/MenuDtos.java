package church.kiosk.menu;

import java.util.List;

public final class MenuDtos {

	private MenuDtos() {}

	/** label 이 null 이면 선택지 없는 단일 메뉴. */
	public record VariantView(long id, String label, int price) {}

	public record ItemView(long id, String name, String category, List<VariantView> variants) {}

	/** 고객 화면용 옵션. category 가 같은 메뉴 줄에만 붙일 수 있다. */
	public record OptionView(long id, String name, int price, String category) {}

	/** 설정 화면용 옵션. */
	public record AdminOption(long id, String name, int price, String category, int sortOrder, boolean available) {}

	public record SaveOptionRequest(
			@jakarta.validation.constraints.NotBlank(message = "옵션 이름을 입력해 주세요.") String name,
			@jakarta.validation.constraints.Min(value = 0, message = "가격은 0원 이상이어야 합니다.") int price,
			@jakarta.validation.constraints.NotBlank(message = "카테고리를 입력해 주세요.") String category,
			boolean available) {}

	// ── 스태프 설정 화면용 ─────────────────────────────────

	/** 품절 포함 전체. 관리 화면에서 편집한다. */
	public record AdminVariant(Long id, String label, int price, boolean available) {}

	public record AdminItem(long id, String name, String category, int sortOrder, boolean available,
							List<AdminVariant> variants) {}

	/** 생성/수정 요청. variants 의 id 가 있으면 수정, 없으면 새로 추가, 목록에 없는 기존 id 는 삭제. */
	public record SaveItemRequest(
			@jakarta.validation.constraints.NotBlank(message = "메뉴 이름을 입력해 주세요.") String name,
			@jakarta.validation.constraints.NotBlank(message = "카테고리를 입력해 주세요.") String category,
			boolean available,
			@jakarta.validation.constraints.NotEmpty(message = "가격을 하나는 넣어 주세요.")
			@jakarta.validation.Valid List<SaveVariantRequest> variants) {}

	public record SaveVariantRequest(Long id, String label,
									 @jakarta.validation.constraints.Min(value = 0, message = "가격은 0원 이상이어야 합니다.") int price,
									 boolean available) {}

	/** 주문 생성 시 서버가 직접 조회하는 원본 정보. 가격은 절대 클라이언트를 믿지 않는다. */
	public record VariantDetail(long variantId, long menuItemId, String menuName, String category, String label,
								int price, boolean available) {}
}
