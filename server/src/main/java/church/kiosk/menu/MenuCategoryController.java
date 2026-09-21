package church.kiosk.menu;

import church.kiosk.menu.MenuCategoryRepository.Category;
import church.kiosk.support.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 스태프 화면 > 설정 > 카테고리. 메뉴/옵션은 여기 있는 이름만 쓸 수 있다. */
@RestController
@RequestMapping("/api/staff/categories")
public class MenuCategoryController {

	private final MenuCategoryRepository repository;

	public MenuCategoryController(MenuCategoryRepository repository) {
		this.repository = repository;
	}

	public record SaveRequest(@NotBlank(message = "카테고리 이름을 입력해 주세요.") String name) {}

	@GetMapping
	public List<Category> list() {
		return repository.findAll();
	}

	@PostMapping
	public Category create(@RequestBody @Valid SaveRequest req) {
		String name = req.name().trim();
		if (repository.existsByName(name)) {
			throw new BusinessException("이미 있는 카테고리입니다.");
		}
		return repository.findById(repository.insert(name)).orElseThrow();
	}

	@PutMapping("/{id}")
	@Transactional
	public Category rename(@PathVariable long id, @RequestBody @Valid SaveRequest req) {
		Category c = require(id);
		String name = req.name().trim();
		if (!name.equals(c.name()) && repository.existsByName(name)) {
			throw new BusinessException("이미 있는 카테고리입니다.");
		}
		repository.rename(id, c.name(), name);
		return repository.findById(id).orElseThrow();
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable long id) {
		Category c = require(id);
		if (c.itemCount() > 0) {
			throw new BusinessException("이 카테고리에 메뉴가 " + c.itemCount() + "개 있어 지울 수 없습니다. 메뉴를 먼저 옮기거나 지워 주세요.");
		}
		repository.delete(id);
	}

	@PutMapping("/order")
	public List<Category> reorder(@RequestBody Map<String, List<Long>> body) {
		List<Long> ids = body.get("ids");
		if (ids == null || ids.isEmpty()) {
			throw new BusinessException("순서를 확인해 주세요.");
		}
		repository.reorder(ids);
		return repository.findAll();
	}

	private Category require(long id) {
		return repository.findById(id).orElseThrow(() -> new BusinessException("카테고리를 찾을 수 없습니다."));
	}
}
