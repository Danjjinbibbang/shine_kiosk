package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.ItemView;
import church.kiosk.menu.MenuDtos.OptionView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

	private final MenuRepository menuRepository;
	private final MenuOptionRepository optionRepository;

	public MenuController(MenuRepository menuRepository, MenuOptionRepository optionRepository) {
		this.menuRepository = menuRepository;
		this.optionRepository = optionRepository;
	}

	@GetMapping
	public List<ItemView> menu() {
		return menuRepository.findAvailableMenu();
	}

	/** 잔 단위 옵션(샷 추가, 연하게). 각 옵션의 category 와 같은 메뉴에만 붙는다. */
	@GetMapping("/options")
	public List<OptionView> options() {
		return optionRepository.findAvailable();
	}
}
