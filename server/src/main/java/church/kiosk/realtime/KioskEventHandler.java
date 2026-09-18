package church.kiosk.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 주문 목록이 바뀌었다는 신호만 뿌린다. 변경 내용을 실어보내지 않고
 * 클라이언트가 목록을 다시 받아가게 하면 부분 동기화로 생기는 불일치가 없다.
 */
@Component
public class KioskEventHandler extends TextWebSocketHandler {

	private static final Logger log = LoggerFactory.getLogger(KioskEventHandler.class);
	private static final TextMessage ORDERS_CHANGED = new TextMessage("{\"type\":\"ORDERS_CHANGED\"}");

	private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.add(session);
		log.debug("WebSocket 연결: {} (총 {}대)", session.getId(), sessions.size());
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		sessions.remove(session);
		log.debug("WebSocket 종료: {} (총 {}대)", session.getId(), sessions.size());
	}

	public void broadcastOrdersChanged() {
		for (WebSocketSession session : sessions) {
			if (!session.isOpen()) {
				sessions.remove(session);
				continue;
			}
			try {
				synchronized (session) {
					session.sendMessage(ORDERS_CHANGED);
				}
			}
			catch (IOException e) {
				log.warn("알림 전송 실패, 세션 제거: {}", session.getId());
				sessions.remove(session);
			}
		}
	}
}
