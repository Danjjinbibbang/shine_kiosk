package church.kiosk.place;

import church.kiosk.place.PlaceRepository.Place;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/places")
public class PlaceController {

	private final PlaceRepository placeRepository;

	public PlaceController(PlaceRepository placeRepository) {
		this.placeRepository = placeRepository;
	}

	/** 층 선택 -> 세부 장소 선택 2단계 UI 를 위해 층별로 묶어서 내려준다. */
	public record FloorGroup(int floor, List<Place> places) {}

	@GetMapping
	public List<FloorGroup> places() {
		return placeRepository.findActive().stream()
				.collect(java.util.stream.Collectors.groupingBy(Place::floor,
						java.util.TreeMap::new, java.util.stream.Collectors.toList()))
				.entrySet().stream()
				.map(e -> new FloorGroup(e.getKey(), e.getValue()))
				.toList();
	}
}
