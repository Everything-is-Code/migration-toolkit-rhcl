package com.redhat.migrationtoolkit.rhcl.util;

import jakarta.enterprise.context.ApplicationScoped;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

@ApplicationScoped
public class Messages {

    private static final String BASE_NAME = "messages";
    // フロントエンドのデフォルト言語 (i18n.ts) と合わせ、デフォルトは英語。
    private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

    /** デフォルトロケール（英語）でメッセージを取得する。 */
    public String get(String key, Object... args) {
        return get(key, DEFAULT_LOCALE, args);
    }

    /** 指定ロケールでメッセージを取得する。日本語以外は常に英語にフォールバックする。 */
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

    /** Accept-Language ヘッダーの値からロケールを解決する。 */
    public static Locale resolveLocale(String acceptLanguageHeader) {
        if (acceptLanguageHeader != null && acceptLanguageHeader.toLowerCase().startsWith("ja")) {
            return Locale.JAPANESE;
        }
        return DEFAULT_LOCALE;
    }
}
