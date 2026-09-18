package church.kiosk.config;

import church.kiosk.staff.StaffAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final StaffAuthInterceptor staffAuthInterceptor;

	public WebConfig(StaffAuthInterceptor staffAuthInterceptor) {
		this.staffAuthInterceptor = staffAuthInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(staffAuthInterceptor).addPathPatterns("/api/staff/**");
	}
}
