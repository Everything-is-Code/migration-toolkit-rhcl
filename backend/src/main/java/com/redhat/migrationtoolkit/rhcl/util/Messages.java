package com.redhat.migrationtoolkit.rhcl.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

@ApplicationScoped
public class Messages {

    private static final String BASE_NAME = "messages";
    // Default is English, matching the frontend default language (i18n.ts).
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    /** Get a message using the default locale (English). */
    public String get(String key, Object... args) {
        return get(key, DEFAULT_LOCALE, args);
    }

    /** Get a message for the specified locale. Falls back to English for any locale other than Japanese. */
    public String get(String key, Locale locale, Object... args) {
        Locale resolved = (locale != null && "ja".equals(locale.getLanguage()))
                ? Locale.JAPANESE : DEFAULT_LOCALE;
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BASE_NAME, resolved);
            String pattern = bundle.getString(key);
            return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return key;
        }
    }

    /** Resolve the locale from the Accept-Language header value. */
    public static Locale resolveLocale(String acceptLanguageHeader) {
        if (acceptLanguageHeader != null && acceptLanguageHeader.toLowerCase().startsWith("ja")) {
            return Locale.JAPANESE;
        }
        return DEFAULT_LOCALE;
    }
}
