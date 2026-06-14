package com.echoesofaegis.companions.data;

import java.util.ArrayList;
import java.util.List;

public final class CompanionRecord {
    public String entityUuid = "";
    public boolean tamed = false;
    public String ownerUuid = "";
    public String ownerName = "";
    public String lastFeederUuid = "";
    public String lastFeederName = "";
    public int trust = 0;
    public CompanionMode mode = CompanionMode.FOLLOW;
    public String role = CompanionRole.DEFENDER.id();
    public String personality = "";
    public int level = 1;
    public long xp = 0L;
    public long survivalTicks = 0L;
    public double health = -1.0D;
    public boolean retired = false;
    public String lastDimension = "minecraft:overworld";
    public double lastX = 0.0D;
    public double lastY = 64.0D;
    public double lastZ = 0.0D;
    public ItemRecord hiddenHelmet = ItemRecord.empty();
    public ItemRecord chest = ItemRecord.empty();
    public ItemRecord legs = ItemRecord.empty();
    public ItemRecord boots = ItemRecord.empty();
    public ItemRecord mainHand = ItemRecord.empty();
    public ItemRecord offHand = ItemRecord.empty();
    public boolean dead = false;
    public long respawnReadyAt = 0L;
    public String respawnEggId = "";
    public boolean respawnEggIssued = false;
    public String bedDimension = "";
    public int bedX = 0;
    public int bedY = 0;
    public int bedZ = 0;
    public String bedFacing = "";
    public String bedColor = "red";
    public String bedFootUuid = "";
    public String bedHeadUuid = "";
    public String guardDimension = "";
    public double guardX = 0.0D;
    public double guardY = 64.0D;
    public double guardZ = 0.0D;
    public long createdAt = System.currentTimeMillis();
    public long updatedAt = System.currentTimeMillis();
    public List<ItemRecord> storage = new ArrayList<>();

    public CompanionRecord normalized() {
        if (mode == null) {
            mode = CompanionMode.FOLLOW;
        }
        if (role == null || role.isBlank()) {
            role = mode == CompanionMode.STAY ? CompanionRole.STAY.id() : CompanionRole.DEFENDER.id();
        }
        if (CompanionRole.parse(role) == CompanionRole.STAY) {
            mode = CompanionMode.STAY;
        } else if (mode == CompanionMode.STAY && CompanionRole.parse(role) == CompanionRole.DEFENDER) {
            role = CompanionRole.STAY.id();
        }
        if (personality == null) {
            personality = "";
        }
        level = Math.max(1, level);
        xp = Math.max(0L, xp);
        survivalTicks = Math.max(0L, survivalTicks);
        if (Double.isNaN(health) || Double.isInfinite(health)) {
            health = -1.0D;
        }
        if (storage == null) {
            storage = new ArrayList<>();
        }
        if (hiddenHelmet == null) {
            hiddenHelmet = ItemRecord.empty();
        }
        if (chest == null) {
            chest = ItemRecord.empty();
        }
        if (legs == null) {
            legs = ItemRecord.empty();
        }
        if (boots == null) {
            boots = ItemRecord.empty();
        }
        if (mainHand == null) {
            mainHand = ItemRecord.empty();
        }
        if (offHand == null) {
            offHand = ItemRecord.empty();
        }
        if (respawnEggId == null) {
            respawnEggId = "";
        }
        if (bedDimension == null) {
            bedDimension = "";
        }
        if (bedFacing == null) {
            bedFacing = "";
        }
        if (bedColor == null || bedColor.isBlank()) {
            bedColor = "red";
        }
        if (bedFootUuid == null) {
            bedFootUuid = "";
        }
        if (bedHeadUuid == null) {
            bedHeadUuid = "";
        }
        if (guardDimension == null) {
            guardDimension = "";
        }
        if (!dead) {
            respawnReadyAt = 0L;
            respawnEggId = "";
            respawnEggIssued = false;
        }
        while (storage.size() < 9) {
            storage.add(ItemRecord.empty());
        }
        if (storage.size() > 9) {
            storage = new ArrayList<>(storage.subList(0, 9));
        }
        for (int i = 0; i < storage.size(); i++) {
            if (storage.get(i) == null) {
                storage.set(i, ItemRecord.empty());
            }
        }
        return this;
    }
}
