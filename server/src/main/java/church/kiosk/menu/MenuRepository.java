package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.ItemView;
import church.kiosk.menu.MenuDtos.VariantDetail;
import church.kiosk.menu.MenuDtos.VariantView;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class MenuRepository {

	private final JdbcClient jdbc;

	public MenuRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	private record FlatRow(long itemId, String itemName, String category,
						   long variantId, String label, int price) {}

	/** 판매중인 메뉴만. 카테고리 순서는 그 카테고리 첫 메뉴의 id 순 (커피 10번대 → 논커피 20번대 → 아이스크림 30번대). */
	public List<ItemView> findAvailableMenu() {
		List<FlatRow> rows = jdbc.sql("""
						SELECT i.id, i.name, i.category, v.id AS variant_id, v.label, v.price
						FROM menu_item i
						JOIN menu_variant v ON v.menu_item_id = i.id
						WHERE i.available = 1 AND v.available = 1
						ORDER BY MIN(i.id) OVER (PARTITION BY i.category), i.sort_order, v.sort_order
						""")
				.query((rs, n) -> new FlatRow(
						rs.getLong("id"), rs.getString("name"), rs.getString("category"),
						rs.getLong("variant_id"), rs.getString("label"), rs.getInt("price")))
				.list();

		Map<Long, ItemView> grouped = new LinkedHashMap<>();
		for (FlatRow row : rows) {
			ItemView item = grouped.computeIfAbsent(row.itemId(),
					id -> new ItemView(id, row.itemName(), row.category(), new ArrayList<>()));
			item.variants().add(new VariantView(row.variantId(), row.label(), row.price()));
		}
		return List.copyOf(grouped.values());
	}

	public Optional<VariantDetail> findVariantDetail(long variantId) {
		return jdbc.sql("""
						SELECT v.id, v.menu_item_id, i.name, v.label, v.price,
						       (v.available = 1 AND i.available = 1) AS available
						FROM menu_variant v
						JOIN menu_item i ON i.id = v.menu_item_id
						WHERE v.id = :id
						""")
				.param("id", variantId)
				.query((rs, n) -> new VariantDetail(
						rs.getLong("id"), rs.getLong("menu_item_id"), rs.getString("name"),
						rs.getString("label"), rs.getInt("price"), rs.getBoolean("available")))
				.optional();
	}
}
