package com.echoesofaegis.companions.data;

public final class CompanionPlayerStats {
    public int duelWins = 0;
    public int duelLosses = 0;

    public CompanionPlayerStats normalized() {
        duelWins = Math.max(0, duelWins);
        duelLosses = Math.max(0, duelLosses);
        return this;
    }
}
