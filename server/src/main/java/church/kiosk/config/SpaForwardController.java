package church.kiosk.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** React Router 경로를 새로고침해도 index.html 이 뜨도록 넘겨준다. */
@Controller
public class SpaForwardController {

	@RequestMapping({ "/", "/kiosk", "/kiosk/**", "/staff", "/staff/**" })
	public String forwardToIndex() {
		return "forward:/index.html";
	}
}
