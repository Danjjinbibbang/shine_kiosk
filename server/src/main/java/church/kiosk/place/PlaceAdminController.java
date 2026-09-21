package church.kiosk.place;

import church.kiosk.place.PlaceRepository.AdminPlace;
import church.kiosk.support.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 스태프 화면 > 설정 > 배달 장소. PIN 토큰 필요. */
@RestController
@RequestMapping("/api/staff/places")
public class PlaceAdminController {

	private final PlaceRepository placeRepository;

	public PlaceAdminController(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	public record SavePlaceRequest(@Min(value = 1, message = "층을 확인해 주세요.") int floor,
								   @NotBlank(message = "장소 이름을 입력해 주세요.") String name,
								   boolean active) {}

	@GetMapping
	public List<AdminPlace> list() {
		return placeRepository.findAllForAdmin();
	}

	@PostMapping
	public AdminPlace create(@RequestBody @Valid SavePlaceRequest req) {
		long id = placeRepository.insert(req.floor(), req.name().trim());
		return placeRepository.findAdminById(id).orElseThrow();
	}

	@PutMapping("/{id}")
	public AdminPlace update(@PathVariable long id, @RequestBody @Valid SavePlaceRequest req) {
		require(id);
		placeRepository.update(id, req.floor(), req.name().trim(), req.active());
		return placeRepository.findAdminById(id).orElseThrow();
	}

	@DeleteMapping("/{id}")
	public void delete(@PathVariable long id) {
		require(id);
		placeRepository.deleteOrHide(id);
	}

	private void require(long id) {
		placeRepository.findAdminById(id).orElseThrow(() -> new BusinessException("장소를 찾을 수 없습니다."));
	}
}
