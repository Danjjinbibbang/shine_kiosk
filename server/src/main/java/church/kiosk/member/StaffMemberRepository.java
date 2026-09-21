package church.kiosk.member;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 사역자 명단. 사역자 잔은 무료. */
@Repository
public class StaffMemberRepository {

	public record StaffMember(long id, String name, boolean active) {}

	private final JdbcClient jdbc;

	public StaffMemberRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<StaffMember> findActive() {
		return jdbc.sql("SELECT id, name, active FROM staff_member WHERE active = 1 ORDER BY name")
				.query((rs, n) -> new StaffMember(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")))
				.list();
	}

	public List<StaffMember> findAll() {
		return jdbc.sql("SELECT id, name, active FROM staff_member ORDER BY active DESC, name")
				.query((rs, n) -> new StaffMember(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")))
				.list();
	}

	public Optional<StaffMember> findById(long id) {
		return jdbc.sql("SELECT id, name, active FROM staff_member WHERE id = :id").param("id", id)
				.query((rs, n) -> new StaffMember(rs.getLong("id"), rs.getString("name"), rs.getBoolean("active")))
				.optional();
	}

	public long insert(String name) {
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO staff_member (name, active) VALUES (:name, 1)").param("name", name).update(keys);
		return keys.getKey().longValue();
	}

	public void update(long id, String name, boolean active) {
		jdbc.sql("UPDATE staff_member SET name = :name, active = :active WHERE id = :id")
				.param("name", name).param("active", active ? 1 : 0).param("id", id).update();
	}

	/** 주문에는 이름이 스냅샷으로 남으므로 그냥 지워도 된다. */
	public void delete(long id) {
		jdbc.sql("DELETE FROM staff_member WHERE id = :id").param("id", id).update();
	}
}
