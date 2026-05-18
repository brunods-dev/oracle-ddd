package com.copa.ticketing.ui.util;

import java.util.Map;

public final class FlagEmoji {

    private static final Map<String, String> FLAGS = Map.ofEntries(
        Map.entry("ALG", "🇩🇿"), Map.entry("ARG", "🇦🇷"), Map.entry("AUS", "🇦🇺"),
        Map.entry("AUT", "🇦🇹"), Map.entry("BEL", "🇧🇪"), Map.entry("BIH", "🇧🇦"),
        Map.entry("BRA", "🇧🇷"), Map.entry("CAN", "🇨🇦"), Map.entry("CIV", "🇨🇮"),
        Map.entry("COL", "🇨🇴"), Map.entry("COD", "🇨🇩"), Map.entry("CPV", "🇨🇻"),
        Map.entry("CRO", "🇭🇷"), Map.entry("CUW", "🇨🇼"), Map.entry("CZE", "🇨🇿"),
        Map.entry("ECU", "🇪🇨"), Map.entry("EGY", "🇪🇬"), Map.entry("ENG", "🏴󠁧󠁢󠁥󠁮󠁧󠁿"),
        Map.entry("ESP", "🇪🇸"), Map.entry("FRA", "🇫🇷"), Map.entry("GER", "🇩🇪"),
        Map.entry("GHA", "🇬🇭"), Map.entry("HAI", "🇭🇹"), Map.entry("IRQ", "🇮🇶"),
        Map.entry("IRN", "🇮🇷"), Map.entry("JOR", "🇯🇴"), Map.entry("JPN", "🇯🇵"),
        Map.entry("KOR", "🇰🇷"), Map.entry("KSA", "🇸🇦"), Map.entry("MAR", "🇲🇦"),
        Map.entry("MEX", "🇲🇽"), Map.entry("NED", "🇳🇱"), Map.entry("NOR", "🇳🇴"),
        Map.entry("NZL", "🇳🇿"), Map.entry("PAN", "🇵🇦"), Map.entry("PAR", "🇵🇾"),
        Map.entry("POR", "🇵🇹"), Map.entry("QAT", "🇶🇦"), Map.entry("RSA", "🇿🇦"),
        Map.entry("SCO", "🏴󠁧󠁢󠁳󠁣󠁴󠁿"), Map.entry("SEN", "🇸🇳"), Map.entry("SUI", "🇨🇭"),
        Map.entry("SWE", "🇸🇪"), Map.entry("TUN", "🇹🇳"), Map.entry("TUR", "🇹🇷"),
        Map.entry("URU", "🇺🇾"), Map.entry("USA", "🇺🇸"), Map.entry("UZB", "🇺🇿")
    );

    private FlagEmoji() {}

    public static String of(String teamCode) {
        if (teamCode == null || teamCode.isBlank()) return "🌍";
        return FLAGS.getOrDefault(teamCode.toUpperCase(), "🌍");
    }
}
