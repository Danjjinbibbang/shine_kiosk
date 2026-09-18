package church.kiosk.place;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class PlaceRepository {

	public record Place(long id, int floor, String name) {}

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

	public Optional<Place> findById(long id) {
		return jdbc.sql("SELECT id, floor, name FROM delivery_place WHERE id = :id AND active = 1")
				.param("id", id)
				.query((rs, n) -> new Place(rs.getLong("id"), rs.getInt("floor"), rs.getString("name")))
				.optional();
	}
}
