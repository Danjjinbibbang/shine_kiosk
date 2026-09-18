package church.kiosk.customer;

import church.kiosk.config.KioskProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

	private final CustomerRepository customerRepository;
	private final KioskProperties props;

	public CustomerController(CustomerRepository customerRepository, KioskProperties props) {
		this.customerRepository = customerRepository;
		this.props = props;
	}

	/** 이름 선택 화면의 버튼 목록. */
	@GetMapping("/regulars")
	public List<String> regulars() {
		return customerRepository.findRecentNames(props.getRegularCustomerDays());
	}
}
