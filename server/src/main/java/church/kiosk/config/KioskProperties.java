package church.kiosk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiosk")
public class KioskProperties {

	private String dbPath = "./data/kiosk.db";
	private String staffPin = "2580";
	private String bankAccount = "";
	private int couponPresetAmount = 20000;
	private int regularCustomerDays = 21;

	public String getDbPath() { return dbPath; }
	public void setDbPath(String dbPath) { this.dbPath = dbPath; }

	public String getStaffPin() { return staffPin; }
	public void setStaffPin(String staffPin) { this.staffPin = staffPin; }

	public String getBankAccount() { return bankAccount; }
	public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

	public int getCouponPresetAmount() { return couponPresetAmount; }
	public void setCouponPresetAmount(int couponPresetAmount) { this.couponPresetAmount = couponPresetAmount; }

	public int getRegularCustomerDays() { return regularCustomerDays; }
	public void setRegularCustomerDays(int regularCustomerDays) { this.regularCustomerDays = regularCustomerDays; }
}
