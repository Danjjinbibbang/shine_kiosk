package church.kiosk.staff;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class StaffAuthInterceptor implements HandlerInterceptor {

	public static final String HEADER = "X-Staff-Token";

	private final StaffTokenStore tokenStore;

	public StaffAuthInterceptor(StaffTokenStore tokenStore) {
		this.tokenStore = tokenStore;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
			return true;
		}
		if (tokenStore.isValid(request.getHeader(HEADER))) {
			return true;
		}
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		return false;
	}
}
