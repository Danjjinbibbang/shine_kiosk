package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.AdminItem;
import church.kiosk.menu.MenuDtos.AdminOption;
import church.kiosk.menu.MenuDtos.SaveOptionRequest;
import church.kiosk.menu.MenuDtos.SaveItemRequest;
import church.kiosk.menu.MenuDtos.SaveVariantRequest;
import church.kiosk.support.BusinessException;
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

	public MenuAdminController(MenuRepository menuRepository, MenuOptionRepository optionRepository) {
		this.menuRepository = menuRepository;
		this.optionRepository = optionRepository;
	}

	// ── 옵션 (샷 추가 / 연하게) ─────────────────────────────

	@GetMapping("/options")
	public List<AdminOption> options() {
		return optionRepository.findAllForAdmin();
	}

	@PostMapping("/options")
	public AdminOption createOption(@RequestBody @Valid SaveOptionRequest req) {
		long id = optionRepository.insert(req.name().trim(), req.price(), req.category().trim(), req.available());
		return optionRepository.findById(id).orElseThrow();
	}

	@PutMapping("/options/{id}")
	public AdminOption updateOption(@PathVariable long id, @RequestBody @Valid SaveOptionRequest req) {
		optionRepository.findById(id).orElseThrow(() -> new BusinessException("옵션을 찾을 수 없습니다."));
		optionRepository.update(id, req.name().trim(), req.price(), req.category().trim(), req.available());
		return optionRepository.findById(id).orElseThrow();
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
		long id = menuRepository.insertItem(req.name().trim(), req.category().trim(), req.available());
		saveVariants(id, req.variants());
		return menuRepository.findAdminItem(id).orElseThrow();
	}

	@PutMapping("/{id}")
	@Transactional
	public AdminItem update(@PathVariable long id, @RequestBody @Valid SaveItemRequest req) {
		require(id);
		menuRepository.updateItem(id, req.name().trim(), req.category().trim(), req.available());
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
		int order = 1;
		for (SaveVariantRequest v : variants) {
			String label = (v.label() == null || v.label().isBlank()) ? null : v.label().trim();
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
