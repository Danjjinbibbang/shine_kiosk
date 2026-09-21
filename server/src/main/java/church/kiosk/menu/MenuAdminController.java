package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.AdminItem;
import church.kiosk.menu.MenuDtos.AdminOption;
import church.kiosk.menu.MenuDtos.SaveOptionRequest;
import church.kiosk.menu.MenuDtos.SaveItemRequest;
import church.kiosk.menu.MenuDtos.SaveVariantRequest;
import church.kiosk.support.BusinessException;
import church.kiosk.support.Validation;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 스태프 화면 > 설정 > 메뉴. PIN 토큰 필요. */
@RestController
@RequestMapping("/api/staff/menu")
public class MenuAdminController {

	private final MenuRepository menuRepository;
	private final MenuOptionRepository optionRepository;
	private final MenuCategoryRepository categoryRepository;

	public MenuAdminController(MenuRepository menuRepository, MenuOptionRepository optionRepository,
							   MenuCategoryRepository categoryRepository) {
		this.menuRepository = menuRepository;
		this.optionRepository = optionRepository;
		this.categoryRepository = categoryRepository;
	}

	private String knownCategory(String raw) {
		String name = Validation.name(raw, "카테고리", Validation.NAME_MAX);
		if (!categoryRepository.existsByName(name)) {
			throw new BusinessException("없는 카테고리입니다. 설정 > 카테고리에서 먼저 만들어 주세요.");
		}
		return name;
	}

	// ── 옵션 (샷 추가 / 연하게) ─────────────────────────────

	@GetMapping("/options")
	public List<AdminOption> options() {
		return optionRepository.findAllForAdmin();
	}

	@PostMapping("/options")
	public AdminOption createOption(@RequestBody @Valid SaveOptionRequest req) {
		String name = Validation.name(req.name(), "옵션 이름", Validation.NAME_MAX);
		String category = knownCategory(req.category());
		rejectDuplicateOption(name, category, null);
		long id = optionRepository.insert(name, Validation.price(req.price()), category, req.groupOrNull(), req.available());
		return optionRepository.findById(id).orElseThrow();
	}

	@PutMapping("/options/{id}")
	public AdminOption updateOption(@PathVariable long id, @RequestBody @Valid SaveOptionRequest req) {
		optionRepository.findById(id).orElseThrow(() -> new BusinessException("옵션을 찾을 수 없습니다."));
		String name = Validation.name(req.name(), "옵션 이름", Validation.NAME_MAX);
		String category = knownCategory(req.category());
		rejectDuplicateOption(name, category, id);
		optionRepository.update(id, name, Validation.price(req.price()), category, req.groupOrNull(), req.available());
		return optionRepository.findById(id).orElseThrow();
	}

	/** 같은 카테고리에 같은 이름의 옵션은 하나만. */
	private void rejectDuplicateOption(String name, String category, Long exceptId) {
		boolean dup = optionRepository.findAllForAdmin().stream()
				.anyMatch(o -> (exceptId == null || o.id() != exceptId) && o.category().equals(category) && o.name().equalsIgnoreCase(name));
		if (dup) {
			throw new BusinessException("'" + category + "' 에 같은 이름의 옵션이 이미 있습니다.");
		}
	}

	/** 같은 이름의 메뉴는 하나만 (띄어쓰기·대소문자 무시). */
	private void rejectDuplicateItem(String name, Long exceptId) {
		String key = name.replaceAll("\\s+", "").toLowerCase();
		boolean dup = menuRepository.findAllForAdmin().stream()
				.anyMatch(i -> (exceptId == null || i.id() != exceptId) && i.name().replaceAll("\\s+", "").toLowerCase().equals(key));
		if (dup) {
			throw new BusinessException("같은 이름의 메뉴가 이미 있습니다: " + name);
		}
	}

	@DeleteMapping("/options/{id}")
	public void deleteOption(@PathVariable long id) {
		optionRepository.findById(id).orElseThrow(() -> new BusinessException("옵션을 찾을 수 없습니다."));
		optionRepository.delete(id);
	}

	// ── 메뉴 ────────────────────────────────────────────────

	@GetMapping
	public List<AdminItem> list() {
		return menuRepository.findAllForAdmin();
	}

	@PostMapping
	@Transactional
	public AdminItem create(@RequestBody @Valid SaveItemRequest req) {
		String name = Validation.name(req.name(), "메뉴 이름", Validation.MENU_NAME_MAX);
		rejectDuplicateItem(name, null);
		long id = menuRepository.insertItem(name, knownCategory(req.category()), req.available());
		saveVariants(id, req.variants());
		return menuRepository.findAdminItem(id).orElseThrow();
	}

	@PutMapping("/{id}")
	@Transactional
	public AdminItem update(@PathVariable long id, @RequestBody @Valid SaveItemRequest req) {
		require(id);
		String name = Validation.name(req.name(), "메뉴 이름", Validation.MENU_NAME_MAX);
		rejectDuplicateItem(name, id);
		menuRepository.updateItem(id, name, knownCategory(req.category()), req.available());
		saveVariants(id, req.variants());
		return menuRepository.findAdminItem(id).orElseThrow();
	}

	/** 품절/판매중 빠른 전환. */
	@PutMapping("/{id}/available")
	public AdminItem setAvailable(@PathVariable long id, @RequestBody Map<String, Boolean> body) {
		require(id);
		menuRepository.setItemAvailable(id, Boolean.TRUE.equals(body.get("available")));
		return menuRepository.findAdminItem(id).orElseThrow();
	}

	@DeleteMapping("/{id}")
	@Transactional
	public void delete(@PathVariable long id) {
		require(id);
		menuRepository.deleteItem(id);
	}

	/** 화면 순서 그대로 id 목록을 보내면 그 순서로 저장. */
	@PutMapping("/order")
	@Transactional
	public List<AdminItem> reorder(@RequestBody Map<String, List<Long>> body) {
		List<Long> ids = body.get("ids");
		if (ids == null || ids.isEmpty()) {
			throw new BusinessException("순서를 확인해 주세요.");
		}
		menuRepository.reorder(ids);
		return menuRepository.findAllForAdmin();
	}

	private void require(long id) {
		menuRepository.findAdminItem(id).orElseThrow(() -> new BusinessException("메뉴를 찾을 수 없습니다."));
	}

	/**
	 * 요청에 있는 변형: id 있으면 수정, 없으면 추가. 요청에 없는 기존 변형은 삭제.
	 * 선택지 없는 메뉴는 label 이 빈 변형 하나로 표현한다.
	 */
	private void saveVariants(long itemId, List<SaveVariantRequest> variants) {
		List<Long> keep = new ArrayList<>();
		java.util.Set<String> labels = new java.util.HashSet<>();
		int order = 1;
		for (SaveVariantRequest v : variants) {
			String label = (v.label() == null || v.label().isBlank()) ? null : v.label().trim();
			if (label != null && label.length() > Validation.LABEL_MAX) {
				throw new BusinessException("선택지 이름은 " + Validation.LABEL_MAX + "자까지입니다.");
			}
			if (!labels.add(label == null ? "" : label.toLowerCase())) {
				throw new BusinessException("선택지 이름이 겹칩니다: " + (label == null ? "(빈 이름)" : label));
			}
			Validation.price(v.price());
			if (v.id() != null) {
				menuRepository.updateVariant(v.id(), label, v.price(), order, v.available());
				keep.add(v.id());
			}
			else {
				keep.add(menuRepository.insertVariant(itemId, label, v.price(), order, v.available()));
			}
			order++;
		}
		menuRepository.deleteVariantsNotIn(itemId, keep);
	}
}
