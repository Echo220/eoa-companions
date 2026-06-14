package com.echoesofaegis.companions.data;

import java.util.LinkedHashMap;
import java.util.Map;

public final class CompanionStorage {
    public Map<String, CompanionRecord> companions = new LinkedHashMap<>();
    public Map<String, CompanionPlayerStats> playerStats = new LinkedHashMap<>();
}
