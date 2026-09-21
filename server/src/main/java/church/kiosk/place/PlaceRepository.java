package church.kiosk.place;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PlaceRepository {

	public record Place(long id, int floor, String name) {}

	/** 설정 화면용. 숨긴(active=0) 것도 포함. */
	public record AdminPlace(long id, int floor, String name, int sortOrder, boolean active) {}

	private final JdbcClient jdbc;

	public PlaceRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Place> findActive() {
		return jdbc.sql("""
						SELECT id, floor, name FROM delivery_place
						WHERE active = 1 ORDER BY floor, sort_order
						""")
				.query((rs, n) -> new Place(rs.getLong("id"), rs.getInt("floor"), rs.getString("name")))
				.list();
	}

	public List<AdminPlace> findAllForAdmin() {
		return jdbc.sql("SELECT id, floor, name, sort_order, active FROM delivery_place ORDER BY floor, sort_order, id")
				.query((rs, n) -> new AdminPlace(rs.getLong("id"), rs.getInt("floor"), rs.getString("name"),
						rs.getInt("sort_order"), rs.getBoolean("active")))
				.list();
	}

	public Optional<AdminPlace> findAdminById(long id) {
		return findAllForAdmin().stream().filter(p -> p.id() == id).findFirst();
	}

	public long insert(int floor, String name) {
		int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM delivery_place WHERE floor = :floor")
				.param("floor", floor).query(Integer.class).single();
		org.springframework.jdbc.support.KeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
		jdbc.sql("INSERT INTO delivery_place (floor, name, sort_order, active) VALUES (:floor, :name, :sort, 1)")
				.param("floor", floor).param("name", name).param("sort", next)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void update(long id, int floor, String name, boolean active) {
		jdbc.sql("UPDATE delivery_place SET floor = :floor, name = :name, active = :active WHERE id = :id")
				.param("floor", floor).param("name", name).param("active", active ? 1 : 0).param("id", id)
				.update();
	}

	/** 지난 주문이 참조하면 지우지 못하므로(FK) 숨기고, 아니면 지운다. */
	public void deleteOrHide(long id) {
		int referenced = jdbc.sql("SELECT COUNT(*) FROM orders WHERE place_id = :id").param("id", id)
				.query(Integer.class).single();
		if (referenced > 0) {
			jdbc.sql("UPDATE delivery_place SET active = 0 WHERE id = :id").param("id", id).update();
		}
		else {
			jdbc.sql("DELETE FROM delivery_place WHERE id = :id").param("id", id).update();
		}
	}

	public Optional<Place> findById(long id) {
		return jdbc.sql("SELECT id, floor, name FROM delivery_place WHERE id = :id AND active = 1")
				.param("id", id)
				.query((rs, n) -> new Place(rs.getLong("id"), rs.getInt("floor"), rs.getString("name")))
				.optional();
	}
}
