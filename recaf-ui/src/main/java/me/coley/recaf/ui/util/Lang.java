package me.coley.recaf.ui.util;

import javafx.beans.binding.StringBinding;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import me.coley.recaf.util.IOUtil;
import me.coley.recaf.util.InternalPath;
import me.coley.recaf.util.SelfReferenceUtil;
import me.coley.recaf.util.logging.Logging;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Simple translation utility, tracking a bundle instance in the future may be a better choice.
 *
 * @author Matt Coley
 */
public class Lang {
	private static final String DEFAULT_TRANSLATIONS = "en_US";
	private static String SYSTEM_LANGUAGE;
	private static final List<String> translationKeys = new ArrayList<>();
	private static final Logger logger = Logging.get(Lang.class);
	private static final Map<String, Map<String, String>> translations = new HashMap<>();
	private static final Map<String, StringBinding> translationBindings = new ConcurrentHashMap<>();
	private static Map<String, String> currentTranslationMap;
	private static final StringProperty currentTranslation = new SynchronizedSimpleStringProperty(DEFAULT_TRANSLATIONS);

	/**
	 * @return Provided translations, also keys for {@link #getTranslations()}.
	 */
	public static List<String> getTranslationKeys() {
		return translationKeys;
	}

	/**
	 * @return Default translations, English, also key for {@link #getTranslations()}.
	 */
	public static String getDefaultTranslations() {
		return DEFAULT_TRANSLATIONS;
	}

	/**
	 * @return Current translations, used as key in {@link #getTranslations()}.
	 */
	public static String getCurrentTranslations() {
		return currentTranslation.get();
	}

	/**
	 * Sets the current translations. Should be called before UI is shown for text components to use new values.
	 *
	 * @param translationsKey
	 * 		New translations, used as key in {@link #getTranslations()}.
	 */
	public static void setCurrentTranslations(String translationsKey) {
		if (translations.containsKey(translationsKey)) {
			currentTranslationMap = translations.get(translationsKey);
			currentTranslation.set(translationsKey);
		} else {
			logger.warn("Tried to set translations to '{}', but no entries for the translations were found!", translationsKey);
			// For case it fails to load, use default.
			setCurrentTranslations(DEFAULT_TRANSLATIONS);
		}
	}

	/**
	 * Sets the system language.
	 *
	 * @param translations
	 * 		System language.
	 */
	public static void setSystemLanguage(String translations) {
		SYSTEM_LANGUAGE = translations;
	}

	/**
	 * @return System language, or {@link #getDefaultTranslations()} if not set.
	 */
	public static String getSystemLanguage() {
		return SYSTEM_LANGUAGE == null ? getDefaultTranslations() : SYSTEM_LANGUAGE;
	}

	/**
	 * @return Map of supported translations and their key entries.
	 */
	public static Map<String, Map<String, String>> getTranslations() {
		return translations;
	}

	/**
	 * @param translationKey
	 * 		Key name.
	 *
	 * @return JavaFX string binding for specific translation key.
	 */
	public static synchronized StringBinding getBinding(String translationKey) {
		return translationBindings.computeIfAbsent(translationKey, k -> {
			StringProperty currentTranslation = Lang.currentTranslation;
			return new SynchronizedStringBinding() {
				{
					bind(currentTranslation);
				}

				@Override
				protected synchronized String computeValue() {
					String translated = Lang.get(currentTranslation.get(), translationKey);
					if (translated != null)
						translated = translated.replace("\\n", "\n");
					return translated;
				}
			};
		});
	}

	/**
	 * @param format
	 * 		String format.
	 * @param args
	 * 		Format arguments.
	 *
	 * @return JavaFX string binding for specific translation key with arguments.
	 */
	public static StringBinding formatBy(String format, ObservableValue<?>... args) {
		return new SynchronizedStringBinding() {
			{
				bind(args);
			}

			@Override
			protected synchronized String computeValue() {
				return String.format(format, Arrays.stream(args)
						.map(ObservableValue::getValue).toArray());
			}
		};
	}

	/**
	 * @param translationKey
	 * 		Key name.
	 * @param args
	 * 		Format arguments.
	 *
	 * @return JavaFX string binding for specific translation key with arguments.
	 */
	public static StringBinding format(String translationKey, ObservableValue<?>... args) {
		StringBinding root = getBinding(translationKey);
		return new SynchronizedStringBinding() {
			{
				bind(root);
				bind(args);
			}

			@Override
			protected synchronized String computeValue() {
				return String.format(root.getValue(), Arrays.stream(args)
						.map(ObservableValue::getValue).toArray());
			}
		};
	}

	/**
	 * @param translationKey
	 * 		Key name.
	 * @param args
	 * 		Format arguments.
	 *
	 * @return JavaFX string binding for specific translation key with arguments.
	 */
	public static StringBinding format(String translationKey, Object... args) {
		StringBinding root = getBinding(translationKey);
		return new SynchronizedStringBinding() {
			{
				bind(root);
			}

			@Override
			protected synchronized String computeValue() {
				return String.format(root.getValue(), args);
			}
		};
	}

	/**
	 * @param translation
	 * 		Translation value.
	 * @param args
	 * 		Format arguments.
	 *
	 * @return JavaFX string binding for specific translation key with arguments.
	 */
	public static StringBinding concat(ObservableValue<String> translation, String... args) {
		return new SynchronizedStringBinding() {
			{
				bind(translation);
			}

			@Override
			protected synchronized String computeValue() {
				return translation.getValue() + String.join(" ", args);
			}
		};
	}

	/**
	 * @param translationKey
	 * 		Key name.
	 * @param args
	 * 		Format arguments.
	 *
	 * @return JavaFX string binding for specific translation key with arguments.
	 */
	public static StringBinding concat(String translationKey, String... args) {
		StringBinding root = getBinding(translationKey);
		return new SynchronizedStringBinding() {
			{
				bind(root);
			}

			@Override
			protected synchronized String computeValue() {
				return root.getValue() + String.join(" ", args);
			}
		};
	}

	/**
	 * @return Translations property.
	 */
	public static StringProperty translationsProperty() {
		return currentTranslation;
	}

	/**
	 * @param translationKey
	 * 		Key name.
	 *
	 * @return Translated value, based on {@link #getCurrentTranslations() current loaded mappings}.
	 */
	public static String get(String translationKey) {
		return get(getCurrentTranslations(), translationKey);
	}

	/**
	 * @param translations
	 * 		Language translations group to load from.
	 * @param translationKey
	 * 		Key name.
	 *
	 * @return Translated value, based on {@link #getCurrentTranslations() current loaded mappings}.
	 */
	public static String get(String translations, String translationKey) {
		Map<String, String> map = Lang.translations.getOrDefault(translations, currentTranslationMap);
		String value = map.get(translationKey);
		if (value == null) {
			// Fallback to English if possible.
			if (translations.equals(DEFAULT_TRANSLATIONS)) {
				logger.error("Missing translation for '{}' in language '{}'", translationKey, currentTranslation);
				value = translationKey;
			} else {
				value = get(DEFAULT_TRANSLATIONS, translationKey);
			}
		}
		return value;
	}

	/**
	 * Load the translations and initialize the default one.
	 */
	public static void initialize() {
		// Get the actual locale for translations
		String userCountry = Locale.getDefault().getCountry();
		String userLanguage = Locale.getDefault().getLanguage();
		String userLanguageKey = userLanguage + "_" + userCountry;
		setSystemLanguage(userLanguageKey);
		// Then set the jvm to use to avoid the locale bug
		//  - https://mattryall.net/blog/the-infamous-turkish-locale-bug
		Locale.setDefault(Locale.US);
		// Load provided translations
		SelfReferenceUtil.initializeFromContext(Lang.class);
		SelfReferenceUtil selfReferenceUtil = SelfReferenceUtil.getInstance();
		for (InternalPath translationPath : selfReferenceUtil.getTranslations()) {
			String translationName = FilenameUtils.removeExtension(translationPath.getFileName());

			try {
				load(translationName, translationPath.getURL().openStream());
				translationKeys.add(translationName);
				logger.info("Loaded translations '{}'", translationName);
			} catch (IOException e) {
				logger.info("Failed to load translations '{}'", translationName, e);
			}
		}

		// Set default translations
		setCurrentTranslations(DEFAULT_TRANSLATIONS);
	}

	/**
	 * Load translations from {@link InputStream}.
	 *
	 * @param translations
	 * 		Target translations identifier. The key for {@link #getTranslations()}.
	 * @param in
	 *        {@link InputStream} to load translations from.
	 */
	public static void load(String translations, InputStream in) {
		try {
			Map<String, String> translationsMap = Lang.translations.computeIfAbsent(translations, l -> new HashMap<>());
			String string = IOUtil.toString(in, UTF_8);
			String[] lines = string.split("[\n\r]+");
			for (String line : lines) {
				// Skip comment lines
				if (line.startsWith("#")) {
					continue;
				}
				// Add each "key=value"
				if (line.contains("=")) {
					String[] parts = line.split("=", 2);
					String key = parts[0];
					String value = parts[1];
					translationsMap.put(key, value);
				}
			}
		} catch (Exception ex) {
			throw new IllegalStateException("Failed to fetch language from input stream", ex);
		}
	}
}

