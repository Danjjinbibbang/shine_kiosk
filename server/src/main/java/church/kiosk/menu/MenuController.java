package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.ItemView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/menu")
public class MenuController {

	private final MenuRepository menuRepository;

	public MenuController(MenuRepository menuRepository) {
		this.menuRepository = menuRepository;
	}

	@GetMapping
	public List<ItemView> menu() {
		return menuRepository.findAvailableMenu();
	}
}
