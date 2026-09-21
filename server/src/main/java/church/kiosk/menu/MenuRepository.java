package church.kiosk.menu;

import church.kiosk.menu.MenuDtos.AdminItem;
import church.kiosk.menu.MenuDtos.AdminVariant;
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

	/** 판매중인 메뉴만. 순서는 설정 화면에서 정한 sort_order 하나로 (카테고리 순서 = 그 카테고리 첫 메뉴의 순서). */
	public List<ItemView> findAvailableMenu() {
		List<FlatRow> rows = jdbc.sql("""
						SELECT i.id, i.name, i.category, v.id AS variant_id, v.label, v.price
						FROM menu_item i
						JOIN menu_variant v ON v.menu_item_id = i.id
						WHERE i.available = 1 AND v.available = 1
						ORDER BY i.sort_order, i.id, v.sort_order, v.id
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
		// 같은 카테고리가 흩어져 있어도 화면에서는 한 묶음으로 보이도록, 첫 등장 순서대로 카테고리를 모은다.
		Map<String, List<ItemView>> byCategory = new LinkedHashMap<>();
		for (ItemView item : grouped.values()) {
			byCategory.computeIfAbsent(item.category(), c -> new ArrayList<>()).add(item);
		}
		List<ItemView> ordered = new ArrayList<>();
		byCategory.values().forEach(ordered::addAll);
		return List.copyOf(ordered);
	}

	// ── 스태프 설정 화면 ────────────────────────────────────

	private record AdminFlatRow(long itemId, String itemName, String category, int sortOrder, boolean itemAvailable,
								Long variantId, String label, int price, boolean variantAvailable) {}

	/** 품절/선택지 없는 것까지 전부. */
	public List<AdminItem> findAllForAdmin() {
		List<AdminFlatRow> rows = jdbc.sql("""
						SELECT i.id, i.name, i.category, i.sort_order, i.available,
						       v.id AS variant_id, v.label, v.price, v.available AS v_available
						FROM menu_item i
						LEFT JOIN menu_variant v ON v.menu_item_id = i.id
						ORDER BY i.sort_order, i.id, v.sort_order, v.id
						""")
				.query((rs, n) -> {
					long vid = rs.getLong("variant_id");
					Long vidOrNull = rs.wasNull() ? null : vid;
					return new AdminFlatRow(rs.getLong("id"), rs.getString("name"), rs.getString("category"),
							rs.getInt("sort_order"), rs.getBoolean("available"),
							vidOrNull, rs.getString("label"), rs.getInt("price"), rs.getBoolean("v_available"));
				})
				.list();
		Map<Long, AdminItem> grouped = new LinkedHashMap<>();
		for (AdminFlatRow row : rows) {
			AdminItem item = grouped.computeIfAbsent(row.itemId(), id -> new AdminItem(id, row.itemName(),
					row.category(), row.sortOrder(), row.itemAvailable(), new ArrayList<>()));
			if (row.variantId() != null) {
				item.variants().add(new AdminVariant(row.variantId(), row.label(), row.price(), row.variantAvailable()));
			}
		}
		return List.copyOf(grouped.values());
	}

	public Optional<AdminItem> findAdminItem(long id) {
		return findAllForAdmin().stream().filter(i -> i.id() == id).findFirst();
	}

	public long insertItem(String name, String category, boolean available) {
		int next = jdbc.sql("SELECT COALESCE(MAX(sort_order), 0) + 10 FROM menu_item").query(Integer.class).single();
		org.springframework.jdbc.support.KeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
		jdbc.sql("INSERT INTO menu_item (name, category, sort_order, available) VALUES (:name, :category, :sort, :available)")
				.param("name", name).param("category", category).param("sort", next).param("available", available ? 1 : 0)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void updateItem(long id, String name, String category, boolean available) {
		jdbc.sql("UPDATE menu_item SET name = :name, category = :category, available = :available WHERE id = :id")
				.param("name", name).param("category", category).param("available", available ? 1 : 0).param("id", id)
				.update();
	}

	public void setItemAvailable(long id, boolean available) {
		jdbc.sql("UPDATE menu_item SET available = :available WHERE id = :id")
				.param("available", available ? 1 : 0).param("id", id).update();
	}

	/** 지난 주문은 이름/가격을 스냅샷으로 갖고 있어 메뉴를 지워도 기록은 남는다. */
	public void deleteItem(long id) {
		jdbc.sql("DELETE FROM menu_variant WHERE menu_item_id = :id").param("id", id).update();
		jdbc.sql("DELETE FROM menu_item WHERE id = :id").param("id", id).update();
	}

	public long insertVariant(long itemId, String label, int price, int sortOrder, boolean available) {
		org.springframework.jdbc.support.KeyHolder keys = new org.springframework.jdbc.support.GeneratedKeyHolder();
		jdbc.sql("""
						INSERT INTO menu_variant (menu_item_id, label, price, sort_order, available)
						VALUES (:itemId, :label, :price, :sort, :available)
						""")
				.param("itemId", itemId).param("label", label).param("price", price)
				.param("sort", sortOrder).param("available", available ? 1 : 0)
				.update(keys);
		return keys.getKey().longValue();
	}

	public void updateVariant(long variantId, String label, int price, int sortOrder, boolean available) {
		jdbc.sql("""
						UPDATE menu_variant SET label = :label, price = :price, sort_order = :sort, available = :available
						WHERE id = :id
						""")
				.param("label", label).param("price", price).param("sort", sortOrder)
				.param("available", available ? 1 : 0).param("id", variantId)
				.update();
	}

	public void deleteVariantsNotIn(long itemId, List<Long> keepIds) {
		if (keepIds.isEmpty()) {
			jdbc.sql("DELETE FROM menu_variant WHERE menu_item_id = :itemId").param("itemId", itemId).update();
			return;
		}
		jdbc.sql("DELETE FROM menu_variant WHERE menu_item_id = :itemId AND id NOT IN (:ids)")
				.param("itemId", itemId).param("ids", keepIds).update();
	}

	/** 화면에서 정한 순서대로 sort_order 를 10, 20, 30… 으로 다시 매긴다. */
	public void reorder(List<Long> itemIdsInOrder) {
		int order = 10;
		for (Long id : itemIdsInOrder) {
			jdbc.sql("UPDATE menu_item SET sort_order = :sort WHERE id = :id").param("sort", order).param("id", id).update();
			order += 10;
		}
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
