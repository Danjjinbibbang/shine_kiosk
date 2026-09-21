package church.kiosk.member;

import church.kiosk.member.StaffMemberRepository.StaffMember;
import church.kiosk.support.BusinessException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 사역자 명단. 조회는 키오스크가 쓰므로 공개, 편집은 스태프(PIN). */
@RestController
public class StaffMemberController {

	private final StaffMemberRepository repository;

	public StaffMemberController(StaffMemberRepository repository) {
		this.repository = repository;
	}

	public record SaveRequest(@NotBlank(message = "이름을 입력해 주세요.") String name, boolean active) {}

	/** 키오스크 장바구니의 "사역자 무료" 이름 버튼. */
	@GetMapping("/api/staff-members")
	public List<StaffMember> activeMembers() {
		return repository.findActive();
	}

	@GetMapping("/api/staff/members")
	public List<StaffMember> all() {
		return repository.findAll();
	}

	@PostMapping("/api/staff/members")
	public StaffMember create(@RequestBody @Valid SaveRequest req) {
		long id = repository.insert(req.name().trim());
		return repository.findById(id).orElseThrow();
	}

	@PutMapping("/api/staff/members/{id}")
	public StaffMember update(@PathVariable long id, @RequestBody @Valid SaveRequest req) {
		require(id);
		repository.update(id, req.name().trim(), req.active());
		return repository.findById(id).orElseThrow();
	}

	@DeleteMapping("/api/staff/members/{id}")
	public void delete(@PathVariable long id) {
		require(id);
		repository.delete(id);
	}

	private void require(long id) {
		repository.findById(id).orElseThrow(() -> new BusinessException("사역자를 찾을 수 없습니다."));
	}
}
