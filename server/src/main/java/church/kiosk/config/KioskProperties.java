package church.kiosk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kiosk")
public class KioskProperties {

	private String dbPath = "./data/kiosk.db";
	private String staffPin = "1234";
	private String bankAccount = "";
	private int regularCustomerDays = 21;
	private String backupDir = "./backups";

	public String getDbPath() { return dbPath; }
	public void setDbPath(String dbPath) { this.dbPath = dbPath; }

	public String getStaffPin() { return staffPin; }
	public void setStaffPin(String staffPin) { this.staffPin = staffPin; }

	public String getBankAccount() { return bankAccount; }
	public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }

	public String getBackupDir() { return backupDir; }
	public void setBackupDir(String backupDir) { this.backupDir = backupDir; }

	public int getRegularCustomerDays() { return regularCustomerDays; }
	public void setRegularCustomerDays(int regularCustomerDays) { this.regularCustomerDays = regularCustomerDays; }
}
