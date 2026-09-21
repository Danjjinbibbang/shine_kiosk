package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.AdminOption;
import church.kiosk.menu.MenuDtos.OptionView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MenuOptionRepository {

	private final JdbcClient jdbc;

	public MenuOptionRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<OptionView> findAvailable() {
		return jdbc.sql("SELECT id, name, price, category, option_group FROM menu_option WHERE available = 1 ORDER BY sort_order, id")
				.query((rs, n) -> new OptionView(rs.getLong("id"), rs.getString("name"), rs.getInt("price"),
						rs.getString("category"), rs.getString("option_group")))
				.list();
	}

	/** 주문 시 검증용: 있는 옵션이면 (품절 여부 포함) 돌려준다. */
	public Optional<AdminOption> findById(long id) {
		return findAllForAdmin().stream().filter(o -> o.id() == id).findFirst();
	}

	public List<AdminOption> findAllForAdmin() {
		return jdbc.sql("SELECT id, name, price, category, option_group, sort_order, available FROM menu_option ORDER BY sort_order, id")
				.query((rs, n) -> new AdminOption(rs.getLong("id"), rs.getString("name"), rs.getInt("price"),
						rs.getString("category"), rs.getString("option_group"), rs.getInt("sort_order"), rs.getBoolean("available")))
				.list();
	}

	public long insert(String name, int price, String category, String group, boolean available) {
		int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM menu_option").query(Integer.class).single();
		KeyHolder keys = new GeneratedKeyHolder();
		jdbc.sql("INSERT INTO menu_option (name, price, category, option_group, sort_order, available) VALUES (:name, :price, :category, :grp, :sort, :available)")
				.param("name", name).param("price", price).param("category", category).param("grp", group)
				.param("sort", next).param("available", available ? 1 : 0)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void update(long id, String name, int price, String category, String group, boolean available) {
		jdbc.sql("UPDATE menu_option SET name = :name, price = :price, category = :category, option_group = :grp, available = :available WHERE id = :id")
				.param("name", name).param("price", price).param("category", category).param("grp", group)
				.param("available", available ? 1 : 0).param("id", id)
				.update();
	}

	/** 지난 주문은 옵션 이름/가격을 스냅샷으로 갖고 있어 지워도 된다. */
	public void delete(long id) {
		jdbc.sql("DELETE FROM menu_option WHERE id = :id").param("id", id).update();
	}
}
