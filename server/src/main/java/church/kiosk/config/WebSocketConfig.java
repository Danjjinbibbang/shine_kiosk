package church.kiosk.config;

import church.kiosk.realtime.KioskEventHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

	private final KioskEventHandler handler;

	public WebSocketConfig(KioskEventHandler handler) {
		this.handler = handler;
	}

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		// 교회 와이파이 내부 전용이므로 Origin 제한은 두지 않는다.
		registry.addHandler(handler, "/ws").setAllowedOriginPatterns("*");
	}
}
