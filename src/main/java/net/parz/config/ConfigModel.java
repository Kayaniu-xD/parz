package net.parz.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Data model for the menu: categories (with optional sub categories) and on/off toggles.
 */
public final class ConfigModel {
	private ConfigModel() {
	}

	public static final class Category {
		public final String name;
		public final String description;
		public final Category parent;
		public final List<Category> children = new ArrayList<>();
		public final List<Toggle> toggles = new ArrayList<>();
		public boolean expanded = false;

		public Category(String name, String description, Category parent) {
			this.name = name;
			this.description = description;
			this.parent = parent;
		}

		/** Adds a sub category and returns it. */
		public Category sub(String name, String description) {
			Category child = new Category(name, description, this);
			children.add(child);
			return child;
		}

		/** Adds an on/off toggle to this category and returns it. */
		public Toggle toggle(String key, String name, String description, boolean defaultValue) {
			Toggle toggle = new Toggle(key, name, description, defaultValue);
			toggles.add(toggle);
			return toggle;
		}

		public boolean matches(String query) {
			if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
				return true;
			}
			for (Toggle toggle : toggles) {
				if (toggle.matches(query)) {
					return true;
				}
			}
			for (Category child : children) {
				if (child.matches(query)) {
					return true;
				}
			}
			return false;
		}
	}

	public static final class Toggle {
		public final String key;
		public final String name;
		public final String description;
		public final boolean defaultValue;
		/** 0 = off, 1 = on; used for the sliding animation. */
		public float animation;

		public Toggle(String key, String name, String description, boolean defaultValue) {
			this.key = key;
			this.name = name;
			this.description = description;
			this.defaultValue = defaultValue;
		}

		public boolean isEnabled() {
			return ParzConfig.get(key, defaultValue);
		}

		public void flip() {
			ParzConfig.set(key, !isEnabled());
		}

		public boolean matches(String query) {
			return query.isEmpty()
					|| name.toLowerCase(Locale.ROOT).contains(query)
					|| description.toLowerCase(Locale.ROOT).contains(query);
		}
	}
}
