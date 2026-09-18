package church.kiosk.support;

/** 화면에 그대로 보여줘도 되는, 사용자가 이해할 수 있는 오류. */
public class BusinessException extends RuntimeException {

	public BusinessException(String message) {
		super(message);
	}
}
