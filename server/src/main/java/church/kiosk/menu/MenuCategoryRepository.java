package church.kiosk.menu;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MenuCategoryRepository {

	public record Category(long id, String name, int sortOrder, int itemCount) {}

	private final JdbcClient jdbc;

	public MenuCategoryRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<Category> findAll() {
		return jdbc.sql("""
						SELECT c.id, c.name, c.sort_order,
						       (SELECT COUNT(*) FROM menu_item i WHERE i.category = c.name) AS item_count
						FROM menu_category c ORDER BY c.sort_order, c.id
						""")
				.query((rs, n) -> new Category(rs.getLong("id"), rs.getString("name"), rs.getInt("sort_order"), rs.getInt("item_count")))
				.list();
	}

	public Optional<Category> findById(long id) {
		return findAll().stream().filter(c -> c.id() == id).findFirst();
	}

	public boolean existsByName(String name) {
		return jdbc.sql("SELECT COUNT(*) FROM menu_category WHERE name = :name").param("name", name)
				.query(Integer.class).single() > 0;
	}

	public long insert(String name) {
		int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 10 FROM menu_category").query(Integer.class).single();
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO menu_category (name, sort_order) VALUES (:name, :sort)")
				.param("name", name).param("sort", next).update(keys);
		return keys.getKey().longValue();
	}

	/** 이름을 바꾸면 그 이름을 쓰던 메뉴/옵션도 같이 따라간다. */
	public void rename(long id, String oldName, String newName) {
		jdbc.sql("UPDATE menu_category SET name = :name WHERE id = :id").param("name", newName).param("id", id).update();
		jdbc.sql("UPDATE menu_item SET category = :n WHERE category = :o").param("n", newName).param("o", oldName).update();
		jdbc.sql("UPDATE menu_option SET category = :n WHERE category = :o").param("n", newName).param("o", oldName).update();
	}

	public void delete(long id) {
		jdbc.sql("DELETE FROM menu_category WHERE id = :id").param("id", id).update();
	}

	public void reorder(List<Long> idsInOrder) {
		int order = 10;
		for (Long id : idsInOrder) {
			jdbc.sql("UPDATE menu_category SET sort_order = :sort WHERE id = :id").param("sort", order).param("id", id).update();
			order += 10;
		}
	}
}
