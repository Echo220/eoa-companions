package com.echoesofaegis.companions.companion;

import com.echoesofaegis.companions.config.CompanionsConfig;
import com.echoesofaegis.companions.config.CompanionsConfigManager;
import com.echoesofaegis.companions.data.CompanionDataStore;
import com.echoesofaegis.companions.data.CompanionMode;
import com.echoesofaegis.companions.data.CompanionRecord;
import com.echoesofaegis.companions.data.CompanionRole;
import com.echoesofaegis.companions.data.ItemRecord;
import com.echoesofaegis.companions.data.PersonalityTrait;
import com.mojang.math.Transformation;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class CompanionManager {
    public static final String TAG_COMPANION = "echoes_companion";
    public static final String TAG_WILD = "echoes_companion_wild";
    public static final String TAG_TAMED = "echoes_companion_tamed";
    public static final String TAG_BED_DISPLAY = "echoes_companion_bed";
    private static final String RESPAWN_EGG_MARKER = "echoes_companion_respawn_egg";
    private static final String RESPAWN_EGG_TYPE = "echoes_companion_item";
    private static final String RESPAWN_EGG_RECORD_UUID = "record_uuid";
    private static final String RESPAWN_EGG_OWNER_UUID = "owner_uuid";
    private static final String RESPAWN_EGG_ID = "egg_id";
    private static final String RESPAWN_EGG_READY_AT = "ready_at";
    private static final String COMPANION_BED_MARKER = "echoes_companion_bed";
    private static final String COMPANION_BED_TYPE = "echoes_companion_item";
    private static final String COMPANION_BED_COLOR = "bed_color";
    private static final String COMPANION_WHISTLE_MARKER = "echoes_companion_whistle";
    private static final String COMPANION_WHISTLE_TYPE = "echoes_companion_item";
    private static final int OWNER_DISTANCE_CATCHUP_CHECK_TICKS = 40;
    private static final int WILD_IDLE_WANDER_RADIUS = 7;
    private static final int WILD_IDLE_WANDER_MIN_COOLDOWN = 55;
    private static final int WILD_IDLE_WANDER_MAX_COOLDOWN = 140;

    private final CompanionsConfigManager configManager;
    private final Supplier<CompanionDataStore> dataStore;
    private final Random random = new Random();
    private final Map<UUID, Integer> attackCooldowns = new HashMap<>();
    private final Map<UUID, Integer> stuckTicks = new HashMap<>();
    private final Map<UUID, Integer> ambientSoundCooldowns = new HashMap<>();
    private final Map<UUID, Integer> wildWanderCooldowns = new HashMap<>();
    private final Map<UUID, BlockPos> guardPatrolTargets = new HashMap<>();
    private final Map<UUID, Integer> guardPatrolCooldowns = new HashMap<>();
    private final Map<UUID, UUID> duelOpponents = new HashMap<>();
    private final Map<UUID, OwnerFollowRequest> ownerFollowRequests = new HashMap<>();
    private final Map<UUID, OwnerSnapshot> ownerSnapshots = new HashMap<>();
    private BiConsumer<ServerPlayer, Mob> storageOpener = (player, companion) ->
            player.sendSystemMessage(Component.literal("Companion storage is not ready yet.").withStyle(ChatFormatting.RED));
    private int spawnTickCounter;
    private int dataSaveTickCounter;
    private int companionLimitCleanupTickCounter;
    private int respawnEggTickCounter;
    private int ownerDistanceCatchupTickCounter;

    public CompanionManager(CompanionsConfigManager configManager, Supplier<CompanionDataStore> dataStore) {
        this.configManager = configManager;
        this.dataStore = dataStore;
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        UseBlockCallback.EVENT.register(this::useBlock);
        UseEntityCallback.EVENT.register(this::interact);
        UseItemCallback.EVENT.register(this::useItem);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(this::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(this::allowDeath);
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register(this::afterPlayerLevelChange);
    }

    public void setStorageOpener(BiConsumer<ServerPlayer, Mob> storageOpener) {
        this.storageOpener = storageOpener == null ? this.storageOpener : storageOpener;
    }

    public CompanionsConfig config() {
        return configManager.config();
    }

    public static boolean hasCompanionTag(Entity entity) {
        return entity != null && entity.entityTags().contains(TAG_COMPANION);
    }

    public boolean isCompanion(Entity entity) {
        return entity instanceof Mob && hasCompanionTag(entity);
    }

    public Optional<Mob> findNearestOwned(ServerPlayer player, double radius) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        AABB area = player.getBoundingBox().inflate(radius);
        return player.level().getEntitiesOfClass(Mob.class, area, this::isCompanion).stream()
                .filter(companion -> {
                    CompanionRecord record = store.get(companion.getUUID());
                    return record != null && record.tamed && !record.retired && player.getUUID().toString().equals(record.ownerUuid);
                })
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    public Optional<Mob> findOwnedAnywhere(ServerPlayer player) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        MinecraftServer server = player.level().getServer();
        Mob best = null;
        double bestDistance = Double.MAX_VALUE;
        for (CompanionRecord record : store.all()) {
            if (!record.tamed || record.retired || !player.getUUID().toString().equals(record.ownerUuid)) {
                continue;
            }
            Optional<Mob> live = liveEntity(server, record);
            if (live.isEmpty()) {
                continue;
            }
            Mob companion = live.get();
            setManagedEquipmentDropChances(companion);
            double distance = companion.level() == player.level() ? companion.distanceToSqr(player) : Double.MAX_VALUE - record.updatedAt;
            if (best == null || distance < bestDistance) {
                best = companion;
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public Optional<Mob> recallOwned(ServerPlayer player) {
        Optional<Mob> live = findOwnedAnywhere(player);
        if (live.isPresent()) {
            return Optional.of(recall(player, live.get()));
        }
        CompanionRecord record = latestOwnedRecord(player).orElse(null);
        if (record == null) {
            CompanionRecord deadRecord = latestDeadOwnedRecord(player).orElse(null);
            if (deadRecord != null) {
                issueRespawnEggIfNeeded(player, deadRecord);
                player.sendSystemMessage(Component.literal("Your companion is recovering. Keep its Little Companion Egg in your inventory. " + respawnStatus(deadRecord)).withStyle(ChatFormatting.YELLOW));
                return Optional.empty();
            }
            player.sendSystemMessage(Component.literal("No bonded companion found.").withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        Mob companion = respawnOwned(player, record);
        if (companion == null) {
            player.sendSystemMessage(Component.literal("Could not recall your companion here.").withStyle(ChatFormatting.RED));
            return Optional.empty();
        }
        player.sendSystemMessage(Component.literal("Your companion finds a way back to you.").withStyle(ChatFormatting.AQUA));
        return Optional.of(companion);
    }

    public boolean startDuel(ServerPlayer firstOwner, ServerPlayer secondOwner) {
        if (!config().companion_duels_enabled) {
            firstOwner.sendSystemMessage(Component.literal("Companion duels are disabled on this server.").withStyle(ChatFormatting.RED));
            return false;
        }
        if (firstOwner.level() != secondOwner.level() || firstOwner.distanceToSqr(secondOwner) > config().duel_start_radius * config().duel_start_radius) {
            firstOwner.sendSystemMessage(Component.literal("Stand near the other player to start a companion duel.").withStyle(ChatFormatting.YELLOW));
            return false;
        }
        Mob first = recallOwned(firstOwner).orElse(null);
        Mob second = recallOwned(secondOwner).orElse(null);
        if (first == null || second == null || first == second) {
            firstOwner.sendSystemMessage(Component.literal("Both players need bonded companions.").withStyle(ChatFormatting.RED));
            return false;
        }
        first.teleportTo(firstOwner.getX(), firstOwner.getY(), firstOwner.getZ());
        second.teleportTo(secondOwner.getX(), secondOwner.getY(), secondOwner.getZ());
        first.setHealth(first.getMaxHealth());
        second.setHealth(second.getMaxHealth());
        duelOpponents.put(first.getUUID(), second.getUUID());
        duelOpponents.put(second.getUUID(), first.getUUID());
        first.setTarget(second);
        second.setTarget(first);
        firstOwner.sendSystemMessage(Component.literal("Companion duel started against " + secondOwner.getGameProfile().name() + ".").withStyle(ChatFormatting.GOLD));
        secondOwner.sendSystemMessage(Component.literal("Companion duel started against " + firstOwner.getGameProfile().name() + ".").withStyle(ChatFormatting.GOLD));
        save();
        return true;
    }

    public CompanionRecord record(Mob companion) {
        CompanionDataStore store = dataStore.get();
        return store == null ? null : store.record(companion.getUUID());
    }

    public Mob summonWild(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos spawn = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.blockPosition().offset(2, 0, 2));
        return spawnWild(level, spawn);
    }

    public Mob recall(ServerPlayer player, Mob companion) {
        CompanionRecord record = record(companion);
        Mob moved = moveCompanionToOwner(player, companion, record, true);
        discardNearbyOwnedDuplicates(player, moved);
        player.sendSystemMessage(Component.literal("Your companion snaps back to your side.").withStyle(ChatFormatting.AQUA));
        return moved;
    }

    private Mob moveCompanionToOwner(ServerPlayer player, Mob companion, CompanionRecord record, boolean forceFollow) {
        float healthBefore = companion.getHealth();
        if (companion.level() != player.level()) {
            Entity moved = companion.teleport(new TeleportTransition(player.level(), player.position(), Vec3.ZERO, player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
            if (moved instanceof Mob movedCompanion) {
                companion = movedCompanion;
            }
        } else {
            teleportCompanionTo(companion, player.getX(), player.getY(), player.getZ());
        }
        companion.setTarget(null);
        companion.setAggressive(false);
        companion.setDeltaMovement(Vec3.ZERO);
        companion.resetFallDistance();
        companion.getNavigation().stop();
        if (record == null) {
            record = record(companion);
        }
        if (record != null) {
            record = ensureRecordMatchesEntity(player, companion, record);
            normalizeCompanion(companion, record);
            restoreHealth(companion, record, healthBefore, false);
            if (forceFollow) {
                record.mode = CompanionMode.FOLLOW;
            }
            updateLocation(companion, record);
            record.updatedAt = System.currentTimeMillis();
            save();
        }
        return companion;
    }

    private void teleportCompanionTo(Mob companion, double x, double y, double z) {
        companion.teleportTo(x, y, z);
        companion.setDeltaMovement(Vec3.ZERO);
        companion.resetFallDistance();
    }

    public void setMode(ServerPlayer player, Mob companion, CompanionMode mode) {
        setRole(player, companion, mode == CompanionMode.STAY ? CompanionRole.STAY : CompanionRole.DEFENDER);
    }

    public void setRole(ServerPlayer player, Mob companion, CompanionRole role) {
        CompanionRecord record = record(companion);
        if (record == null || !player.getUUID().toString().equals(record.ownerUuid)) {
            player.sendSystemMessage(Component.literal("That companion is not bonded to you.").withStyle(ChatFormatting.RED));
            return;
        }
        role = role == null ? CompanionRole.DEFENDER : role;
        record.role = role.id();
        record.mode = role == CompanionRole.STAY ? CompanionMode.STAY : CompanionMode.FOLLOW;
        if (role == CompanionRole.GUARD_CLAIM) {
            setGuardPost(record, companion);
        } else {
            guardPatrolTargets.remove(companion.getUUID());
            guardPatrolCooldowns.remove(companion.getUUID());
        }
        record.updatedAt = System.currentTimeMillis();
        save();
        String detail = role == CompanionRole.STAY && hasCompanionBed(record)
                ? " and will rest at its little bed."
                : role == CompanionRole.GUARD_CLAIM
                ? " and will guard this area."
                : ".";
        player.sendSystemMessage(Component.literal(record.ownerName + "'s companion role is now " + role.label() + detail).withStyle(ChatFormatting.GREEN));
    }

    public ItemStack companionWhistleItem() {
        ItemStack stack = new ItemStack(Items.GOAT_HORN);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Companion Whistle").withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Right-click to recall your companion.").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Sneak-right-click to recall and open its menu.").withStyle(ChatFormatting.YELLOW));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        CompoundTag tag = new CompoundTag();
        tag.putString(COMPANION_WHISTLE_TYPE, COMPANION_WHISTLE_MARKER);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    public int giveCompanionWhistle(ServerPlayer player) {
        giveOrDrop(player, companionWhistleItem());
        player.sendSystemMessage(Component.literal("You received a Companion Whistle. Right-click it to call your companion.").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    public ItemStack companionBedItem() {
        return companionBedItem("red");
    }

    public ItemStack companionBedItem(String color) {
        color = normalizedBedColor(color);
        ItemStack stack = new ItemStack(bedItem(color));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Little Companion Bed").withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("A normal bed scaled for your companion.").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Place it on the ground to set a rest spot.").withStyle(ChatFormatting.YELLOW));
        lore.add(Component.literal("Sneak-right-click it to pick it back up.").withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        CompoundTag tag = new CompoundTag();
        tag.putString(COMPANION_BED_TYPE, COMPANION_BED_MARKER);
        tag.putString(COMPANION_BED_COLOR, color);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    public int giveCompanionBed(ServerPlayer player) {
        return giveCompanionBed(player, "red");
    }

    public int giveCompanionBed(ServerPlayer player, String color) {
        if (!config().companion_beds_enabled) {
            player.sendSystemMessage(Component.literal("Companion beds are disabled on this server.").withStyle(ChatFormatting.RED));
            return 0;
        }
        giveOrDrop(player, companionBedItem(color));
        player.sendSystemMessage(Component.literal("You received a Little Companion Bed. Place it on the ground near your base.").withStyle(ChatFormatting.AQUA));
        return 1;
    }

    public List<String> bedColors() {
        return List.of("white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");
    }

    public int clearOwnedBed(ServerPlayer player) {
        CompanionRecord record = latestAnyOwnedRecord(player).orElse(null);
        if (record == null || !hasCompanionBed(record)) {
            player.sendSystemMessage(Component.literal("No companion bed is set.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        pickUpCompanionBed(player, record, true);
        return 1;
    }

    public CompanionRole role(CompanionRecord record) {
        return CompanionRole.parse(record == null ? null : record.role);
    }

    public PersonalityTrait personality(CompanionRecord record) {
        return PersonalityTrait.parse(record == null ? null : record.personality);
    }

    public long xpForNextLevel(CompanionRecord record) {
        int level = record == null ? 1 : Math.max(1, record.level);
        return config().companion_xp_per_level_base + (long) (level - 1) * config().companion_xp_per_level_growth;
    }

    public double estimatedDamage(CompanionRecord record, ItemStack weapon) {
        return companionAttackDamage(weapon, record);
    }

    public double armorScore(Mob companion) {
        double armor = 0.0D;
        armor += armorScore(companion.getItemBySlot(EquipmentSlot.HEAD), EquipmentSlot.HEAD);
        armor += armorScore(companion.getItemBySlot(EquipmentSlot.CHEST), EquipmentSlot.CHEST);
        armor += armorScore(companion.getItemBySlot(EquipmentSlot.LEGS), EquipmentSlot.LEGS);
        armor += armorScore(companion.getItemBySlot(EquipmentSlot.FEET), EquipmentSlot.FEET);
        return armor;
    }

    public double shieldBlockChance(CompanionRecord record) {
        double chance = config().companion_shield_block_chance;
        chance += personality(record) == PersonalityTrait.CAREFUL ? 0.15D : 0.0D;
        chance += personality(record) == PersonalityTrait.LOYAL ? 0.05D : 0.0D;
        chance += role(record) == CompanionRole.DEFENDER || role(record) == CompanionRole.GUARD_CLAIM ? 0.05D : 0.0D;
        return Mth.clamp(chance, 0.0D, 0.95D);
    }

    public String respawnStatusText(CompanionRecord record) {
        return record == null || !record.dead ? "Ready" : respawnStatus(record);
    }

    private void setGuardPost(CompanionRecord record, Entity entity) {
        if (record == null || entity == null) {
            return;
        }
        record.guardDimension = entity.level().dimension().identifier().toString();
        record.guardX = entity.getX();
        record.guardY = entity.getY();
        record.guardZ = entity.getZ();
        guardPatrolTargets.remove(entity.getUUID());
        guardPatrolCooldowns.remove(entity.getUUID());
    }

    public int reissueRespawnEgg(ServerPlayer player) {
        CompanionRecord record = latestDeadOwnedRecord(player).orElse(null);
        if (record == null) {
            player.sendSystemMessage(Component.literal("You do not have a defeated companion waiting to revive.").withStyle(ChatFormatting.YELLOW));
            return 0;
        }
        if (hasMatchingRespawnEgg(player, record)) {
            player.sendSystemMessage(Component.literal("You already have this companion's Little Companion Egg. Keep it in your inventory. " + respawnStatus(record)).withStyle(ChatFormatting.YELLOW));
            return 1;
        }
        record.respawnEggIssued = false;
        issueRespawnEggIfNeeded(player, record);
        player.sendSystemMessage(Component.literal("A Little Companion Egg has been issued. Keep it in your inventory. " + respawnStatus(record)).withStyle(ChatFormatting.AQUA));
        return 1;
    }

    private void tick(MinecraftServer server) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return;
        }

        detectOwnerTeleports(server);
        detectOwnerDistanceGaps(server);
        tickOwnerFollows(server);

        for (ServerLevel level : server.getAllLevels()) {
            Set<UUID> ticked = new HashSet<>();
            for (ServerPlayer player : level.players()) {
                AABB area = player.getBoundingBox().inflate(Math.max(config().nearby_companion_limit_radius, config().teleport_distance + 16.0D));
                for (Mob companion : level.getEntitiesOfClass(Mob.class, area, this::isCompanion)) {
                    if (ticked.add(companion.getUUID())) {
                        tickCompanion(server, level, companion, store.record(companion.getUUID()));
                    }
                }
            }
        }

        if (++spawnTickCounter >= config().spawn_check_interval_ticks) {
            spawnTickCounter = 0;
            tickNaturalSpawns(server);
        }
        if (++companionLimitCleanupTickCounter >= 1200) {
            companionLimitCleanupTickCounter = 0;
            enforceCompanionLimit(server, store);
        }
        if (++respawnEggTickCounter >= 20) {
            respawnEggTickCounter = 0;
            tickRespawnEggs(server, store);
        }
        if (++dataSaveTickCounter >= 1200) {
            dataSaveTickCounter = 0;
            store.save();
        }
    }

    private void afterPlayerLevelChange(ServerPlayer player, ServerLevel origin, ServerLevel destination) {
        if (!config().follow_owner_through_portals || dataStore.get() == null || origin == destination) {
            return;
        }
        CompanionRecord record = latestOwnedRecord(player).orElse(null);
        queueOwnerFollow(player, record, Math.max(0, config().portal_follow_delay_ticks), false,
                "Your companion follows you through the portal.",
                "Your companion finds a safe way through the portal.");
    }

    private void detectOwnerTeleports(MinecraftServer server) {
        if (!config().follow_owner_after_teleports) {
            return;
        }
        Set<UUID> online = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID ownerUuid = player.getUUID();
            online.add(ownerUuid);
            OwnerSnapshot current = new OwnerSnapshot(
                    player.level().dimension().identifier().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ()
            );
            OwnerSnapshot previous = ownerSnapshots.put(ownerUuid, current);
            if (previous == null || !previous.dimension.equals(current.dimension)) {
                continue;
            }
            double threshold = config().owner_teleport_follow_distance;
            if (previous.distanceToSqr(current) > threshold * threshold) {
                CompanionRecord record = latestOwnedRecord(player).orElse(null);
                queueOwnerFollow(player, record, Math.max(0, config().owner_teleport_follow_delay_ticks), true,
                        "Your companion catches up after your teleport.",
                        "Your companion finds a safe way back after your teleport.");
            }
        }
        ownerSnapshots.keySet().removeIf(uuid -> !online.contains(uuid));
    }

    private void detectOwnerDistanceGaps(MinecraftServer server) {
        if (!config().follow_owner_after_teleports) {
            return;
        }
        if (++ownerDistanceCatchupTickCounter < OWNER_DISTANCE_CATCHUP_CHECK_TICKS) {
            return;
        }
        ownerDistanceCatchupTickCounter = 0;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isFallFlying() || ownerFollowRequests.containsKey(player.getUUID())) {
                continue;
            }
            CompanionRecord record = latestOwnedRecord(player).orElse(null);
            if (!shouldFollowOwnerMove(record) || isInDuel(record)) {
                continue;
            }
            record.normalized();
            double threshold = Math.max(config().teleport_distance, config().catch_up_distance + 4.0D);
            boolean sameDimension = record.lastDimension != null
                    && record.lastDimension.equals(player.level().dimension().identifier().toString());
            boolean farFromSavedLocation = !sameDimension || distanceToSavedLocationSqr(player, record) > threshold * threshold;
            if (!farFromSavedLocation) {
                continue;
            }
            Optional<Mob> live = liveEntity(server, record);
            if (live.isPresent()) {
                Mob companion = live.get();
                if (companion.level() == player.level() && companion.distanceToSqr(player) <= threshold * threshold) {
                    continue;
                }
                if (config().portal_follow_requires_not_in_combat && companion.getTarget() != null) {
                    continue;
                }
            }
            ownerFollowRequests.put(player.getUUID(), new OwnerFollowRequest(0, true,
                    "Your companion catches up after your travel.",
                    "Your companion finds a safe way back after your travel."));
        }
    }

    private double distanceToSavedLocationSqr(ServerPlayer player, CompanionRecord record) {
        double dx = player.getX() - record.lastX;
        double dy = player.getY() - record.lastY;
        double dz = player.getZ() - record.lastZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private void queueOwnerFollow(ServerPlayer owner, CompanionRecord record, int delayTicks, boolean allowSameLevelMove, String liveMessage, String rebuildMessage) {
        if (!shouldFollowOwnerMove(record) || isInDuel(record)) {
            return;
        }
        Optional<Mob> live = liveEntity(owner.level().getServer(), record);
        if (config().portal_follow_requires_not_in_combat && live.isPresent() && live.get().getTarget() != null) {
            return;
        }
        ownerFollowRequests.put(owner.getUUID(), new OwnerFollowRequest(delayTicks, allowSameLevelMove, liveMessage, rebuildMessage));
    }

    private void tickOwnerFollows(MinecraftServer server) {
        if (ownerFollowRequests.isEmpty()) {
            return;
        }
        var iterator = ownerFollowRequests.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, OwnerFollowRequest> entry = iterator.next();
            OwnerFollowRequest request = entry.getValue();
            if (request.remainingTicks-- > 0) {
                continue;
            }
            iterator.remove();
            ServerPlayer owner = server.getPlayerList().getPlayer(entry.getKey());
            if (owner != null) {
                completeOwnerFollow(owner, request);
            }
        }
    }

    private void completeOwnerFollow(ServerPlayer owner, OwnerFollowRequest request) {
        CompanionRecord record = latestOwnedRecord(owner).orElse(null);
        if (!shouldFollowOwnerMove(record) || isInDuel(record)) {
            return;
        }
        Optional<Mob> live = liveEntity(owner.level().getServer(), record);
        if (live.isPresent()) {
            Mob companion = live.get();
            if (companion.level() == owner.level() && !request.allowSameLevelMove) {
                return;
            }
            if (companion.level() == owner.level() && companion.distanceToSqr(owner) <= config().follow_start_distance * config().follow_start_distance) {
                return;
            }
            if (config().portal_follow_requires_not_in_combat && companion.getTarget() != null) {
                return;
            }
            Mob moved = moveCompanionToOwner(owner, companion, record, false);
            discardNearbyOwnedDuplicates(owner, moved);
            owner.sendSystemMessage(Component.literal(request.liveMessage).withStyle(ChatFormatting.AQUA));
            return;
        }
        Mob companion = respawnOwned(owner, record);
        if (companion != null) {
            owner.sendSystemMessage(Component.literal(request.rebuildMessage).withStyle(ChatFormatting.AQUA));
        }
    }

    private boolean shouldFollowOwnerMove(CompanionRecord record) {
        if (record == null || !record.tamed || record.retired || record.dead) {
            return false;
        }
        record.normalized();
        CompanionRole role = role(record);
        return record.mode != CompanionMode.STAY && role != CompanionRole.STAY && role != CompanionRole.GUARD_CLAIM;
    }

    private boolean handleOwnerVehicleTravel(ServerPlayer owner, Mob companion, CompanionRecord record, CompanionRole role) {
        Entity ownerVehicle = owner.getVehicle();
        boolean followRole = shouldFollowOwnerMove(record)
                && role != CompanionRole.STAY
                && role != CompanionRole.GUARD_CLAIM
                && !isInDuel(record);

        if (!followRole) {
            if (isManagedOwnerVehicle(companion.getVehicle())) {
                companion.stopRiding();
            }
            return false;
        }

        if (ownerVehicle != null && canShareOwnerVehicle(ownerVehicle, companion)) {
            companion.setTarget(null);
            companion.setAggressive(false);
            companion.getNavigation().stop();
            companion.resetFallDistance();
            if (companion.getVehicle() == ownerVehicle) {
                return true;
            }
            if (companion.isPassenger()) {
                companion.stopRiding();
            }
            teleportCompanionTo(companion, ownerVehicle.getX(), ownerVehicle.getY(), ownerVehicle.getZ());
            return companion.startRiding(ownerVehicle);
        }

        if (isManagedOwnerVehicle(companion.getVehicle())) {
            companion.stopRiding();
            companion.resetFallDistance();
        }
        return false;
    }

    private boolean canShareOwnerVehicle(Entity ownerVehicle, Mob companion) {
        if (ownerVehicle == null || ownerVehicle == companion) {
            return false;
        }
        if (ownerVehicle instanceof AbstractBoat) {
            return config().follow_owner_into_boats
                    && (ownerVehicle.hasPassenger(companion) || hasKnownPassengerRoom(ownerVehicle, companion));
        }
        if (!config().follow_owner_into_multi_seat_mounts || !isConfiguredMultiSeatMount(ownerVehicle)) {
            return false;
        }
        return ownerVehicle.hasPassenger(companion) || hasKnownPassengerRoom(ownerVehicle, companion);
    }

    private boolean isManagedOwnerVehicle(Entity vehicle) {
        return vehicle instanceof AbstractBoat || isConfiguredMultiSeatMount(vehicle);
    }

    private boolean isConfiguredMultiSeatMount(Entity vehicle) {
        if (vehicle == null || config().companion_multi_seat_mounts == null) {
            return false;
        }
        String id = BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString();
        return config().companion_multi_seat_mounts.stream()
                .map(value -> value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT))
                .anyMatch(id::equals);
    }

    private boolean hasKnownPassengerRoom(Entity vehicle, Mob companion) {
        if (vehicle.hasPassenger(companion)) {
            return true;
        }
        int capacity = knownPassengerCapacity(vehicle);
        return capacity <= 0 || vehicle.getPassengers().size() < capacity;
    }

    private int knownPassengerCapacity(Entity vehicle) {
        if (vehicle instanceof AbstractBoat) {
            return 2;
        }
        EntityType<?> type = vehicle.getType();
        if (type == EntityType.CAMEL || type == EntityType.CAMEL_HUSK) {
            return 2;
        }
        if (type == EntityType.HAPPY_GHAST) {
            return 4;
        }
        return 0;
    }

    private boolean isInDuel(CompanionRecord record) {
        if (record == null || record.entityUuid == null || record.entityUuid.isBlank()) {
            return false;
        }
        try {
            return duelOpponents.containsKey(UUID.fromString(record.entityUuid));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private void tickCompanion(MinecraftServer server, ServerLevel level, Mob companion, CompanionRecord record) {
        if (record.retired) {
            discardManagedCompanion(companion);
            return;
        }
        if (companion.getType() != EntityType.ZOMBIE) {
            upgradeLegacyCompanion(level, companion, record);
            return;
        }
        normalizeCompanion(companion, record);
        updateLocation(companion, record);
        syncVisibleEquipmentToRecord(server, companion, record);
        if (!companion.isAlive()) {
            return;
        }
        companion.clearFire();
        clearPlayerTarget(companion);
        calmNearbyNonHostileTargets(level, companion);
        tickAmbientSound(level, companion, record);

        if (!record.tamed) {
            companion.setTarget(null);
            companion.setAggressive(false);
            companion.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 6, true, false));
            if (!tickWildFood(level, companion, record)) {
                tickWildIdleWander(level, companion);
            }
            return;
        }
        ensureTamedProfile(record);

        ServerPlayer owner = resolveOwner(server, record);
        if (owner == null || owner.level() != level) {
            companion.setTarget(null);
            companion.setAggressive(false);
            companion.getNavigation().stop();
            return;
        }
        tickSurvivalProgress(level, companion, record, owner);
        if (duelOpponents.containsKey(companion.getUUID())) {
            if (tickDuel(server, level, companion, record, owner)) {
                return;
            }
        }
        CompanionRole role = role(record);
        if (handleOwnerVehicleTravel(owner, companion, record, role)) {
            return;
        }
        if (role == CompanionRole.STAY || record.mode == CompanionMode.STAY) {
            companion.setTarget(null);
            companion.setAggressive(false);
            if (restAtBed(level, companion, record)) {
                return;
            }
            companion.getNavigation().stop();
            return;
        }
        if (role == CompanionRole.GUARD_CLAIM) {
            ensureGuardPost(record, companion);
            LivingEntity target = chooseDefensiveTarget(level, owner, companion, record, role);
            if (target != null) {
                fightTarget(level, companion, target, record);
                return;
            }
            companion.setTarget(null);
            companion.setAggressive(false);
            patrolGuardArea(level, companion, record);
            return;
        }
        if (role == CompanionRole.PASSIVE) {
            companion.setTarget(null);
            companion.setAggressive(false);
            followOwner(owner, companion, role);
            return;
        }

        LivingEntity target = chooseDefensiveTarget(level, owner, companion, record, role);
        if (target != null) {
            fightTarget(level, companion, target, record);
            return;
        }

        companion.setTarget(null);
        companion.setAggressive(false);
        followOwner(owner, companion, role);
    }

    private boolean allowDamage(LivingEntity target, DamageSource source, float amount) {
        if (target instanceof Player && (isCompanion(source.getEntity()) || isCompanion(source.getDirectEntity()))) {
            clearCompanionAttacker(source.getEntity());
            clearCompanionAttacker(source.getDirectEntity());
            return false;
        }
        if (target instanceof Mob companion && isCompanion(companion) && source.getEntity() instanceof Player) {
            clearPlayerTarget(companion);
            CompanionRecord record = record(companion);
            return record == null || !record.tamed;
        }
        if (target instanceof Mob companion && isCompanion(companion) && companion.level() instanceof ServerLevel level) {
            CompanionRecord record = record(companion);
            if (record != null && record.tamed && config().companion_fall_damage_immunity && companionTravelDamage(source)) {
                companion.resetFallDistance();
                return false;
            }
            if (record != null && record.tamed && nonHostileMobAttack(source)) {
                clearNonHostileAttacker(source.getEntity(), companion);
                clearNonHostileAttacker(source.getDirectEntity(), companion);
                return false;
            }
            if (record != null && record.tamed && tryShieldBlock(level, companion, source, amount)) {
                return false;
            }
        }
        return true;
    }

    private boolean companionTravelDamage(DamageSource source) {
        return source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL);
    }

    private boolean nonHostileMobAttack(DamageSource source) {
        return isNonHostileMob(source.getEntity()) || isNonHostileMob(source.getDirectEntity());
    }

    private boolean isNonHostileMob(Entity entity) {
        return entity instanceof Mob mob && !(mob instanceof Monster);
    }

    private void clearNonHostileAttacker(Entity entity, Mob companion) {
        if (entity instanceof Mob mob && !(mob instanceof Monster)) {
            clearTargetingCompanion(mob, companion);
        }
    }

    private void calmNearbyNonHostileTargets(ServerLevel level, Mob companion) {
        AABB area = companion.getBoundingBox().inflate(24.0D);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, area, mob -> !(mob instanceof Monster) && mob.getTarget() == companion)) {
            clearTargetingCompanion(mob, companion);
        }
    }

    private void clearTargetingCompanion(Mob mob, Mob companion) {
        if (mob.getTarget() == companion) {
            mob.setTarget(null);
            mob.setAggressive(false);
            mob.getNavigation().stop();
        }
        if (mob instanceof NeutralMob neutral) {
            neutral.stopBeingAngry();
            if (neutral.getLastHurtByMob() == companion) {
                neutral.setLastHurtByMob(null);
            }
        }
    }

    private boolean tryShieldBlock(ServerLevel level, Mob companion, DamageSource source, float amount) {
        if (amount <= 0.0F || source.getEntity() == null || isCompanion(source.getEntity())) {
            return false;
        }
        ItemStack shield = companion.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!shield.is(Items.SHIELD) || random.nextDouble() > config().companion_shield_block_chance) {
            return false;
        }
        companion.swing(InteractionHand.OFF_HAND);
        shield.hurtAndBreak(Math.max(1, Mth.ceil(amount / 6.0F)), companion, EquipmentSlot.OFFHAND);
        level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.NEUTRAL, 0.75F, 1.35F);
        CompanionRecord record = record(companion);
        if (record != null) {
            syncVisibleEquipmentToRecord(level.getServer(), companion, record);
        }
        return true;
    }

    private void clearCompanionAttacker(Entity entity) {
        if (entity instanceof Mob companion && isCompanion(companion)) {
            clearPlayerTarget(companion);
        }
    }

    private boolean clearPlayerTarget(Mob companion) {
        if (companion.getTarget() instanceof Player) {
            companion.setTarget(null);
            companion.setAggressive(false);
            companion.getNavigation().stop();
            return true;
        }
        return false;
    }

    private boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (entity instanceof Mob companion && isCompanion(companion) && duelOpponents.containsKey(companion.getUUID()) && companion.level() instanceof ServerLevel level) {
            companion.setHealth((float) Math.max(1.0D, config().duel_stop_health));
            recordDuelDeathResult(level.getServer(), companion);
            finishDuel(level.getServer(), companion, null);
            return false;
        }
        if (entity instanceof Mob companion && isCompanion(companion) && companion.level() instanceof ServerLevel level) {
            setManagedEquipmentDropChances(companion);
            CompanionRecord record = record(companion);
            if (record != null && record.retired) {
                discardManagedCompanion(companion);
                return false;
            }
            if (record != null && record.tamed && !record.dead) {
                queueRespawnEgg(level, companion, record);
                return false;
            }
        }
        return true;
    }

    private void recordDuelDeathResult(MinecraftServer server, Mob loserCompanion) {
        UUID opponentUuid = duelOpponents.get(loserCompanion.getUUID());
        if (opponentUuid == null || !(loserCompanion.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntityInAnyDimension(opponentUuid);
        if (!(entity instanceof Mob winnerCompanion)) {
            return;
        }
        CompanionRecord loserRecord = record(loserCompanion);
        CompanionRecord winnerRecord = record(winnerCompanion);
        ServerPlayer loser = loserRecord == null ? null : resolveOwner(server, loserRecord);
        ServerPlayer winner = winnerRecord == null ? null : resolveOwner(server, winnerRecord);
        if (winner != null && loser != null) {
            winner.sendSystemMessage(Component.literal("Your companion won the duel.").withStyle(ChatFormatting.GOLD));
            loser.sendSystemMessage(Component.literal("Your companion yields the duel.").withStyle(ChatFormatting.YELLOW));
            recordDuelResult(winner, loser);
            awardXp(winnerCompanion, winnerRecord, config().companion_xp_duel_win, "duel win");
            awardXp(loserCompanion, loserRecord, config().companion_xp_duel_loss, "duel practice");
        }
    }

    private boolean tickDuel(MinecraftServer server, ServerLevel level, Mob companion, CompanionRecord record, ServerPlayer owner) {
        UUID opponentUuid = duelOpponents.get(companion.getUUID());
        Entity entity = opponentUuid == null ? null : level.getEntityInAnyDimension(opponentUuid);
        if (!(entity instanceof Mob opponent) || !isCompanion(opponent) || !opponent.isAlive()) {
            finishDuel(server, companion, null);
            return false;
        }
        CompanionRecord opponentRecord = record(opponent);
        ServerPlayer opponentOwner = opponentRecord == null ? null : resolveOwner(server, opponentRecord);
        if (opponentOwner == null || opponentOwner.level() != level) {
            finishDuel(server, companion, opponent);
            return false;
        }
        if (companion.getHealth() <= config().duel_stop_health || opponent.getHealth() <= config().duel_stop_health) {
            boolean companionWon = companion.getHealth() > opponent.getHealth();
            ServerPlayer winner = companionWon ? owner : opponentOwner;
            ServerPlayer loser = companionWon ? opponentOwner : owner;
            winner.sendSystemMessage(Component.literal("Your companion won the duel.").withStyle(ChatFormatting.GOLD));
            loser.sendSystemMessage(Component.literal("Your companion yields the duel.").withStyle(ChatFormatting.YELLOW));
            recordDuelResult(winner, loser);
            awardXp(companionWon ? companion : opponent, companionWon ? record : opponentRecord, config().companion_xp_duel_win, "duel win");
            awardXp(companionWon ? opponent : companion, companionWon ? opponentRecord : record, config().companion_xp_duel_loss, "duel practice");
            finishDuel(server, companion, opponent);
            return false;
        }
        fightTarget(level, companion, opponent, record);
        return true;
    }

    private void finishDuel(MinecraftServer server, Mob first, Mob second) {
        Mob a = first;
        Mob b = second;
        if (b == null) {
            UUID other = duelOpponents.get(a.getUUID());
            Entity entity = other == null ? null : ((ServerLevel) a.level()).getEntityInAnyDimension(other);
            if (entity instanceof Mob found) {
                b = found;
            }
        }
        clearDuelEntity(server, a);
        if (b != null) {
            clearDuelEntity(server, b);
        }
        save();
    }

    private void clearDuelEntity(MinecraftServer server, Mob companion) {
        duelOpponents.remove(companion.getUUID());
        companion.setTarget(null);
        companion.setAggressive(false);
        companion.getNavigation().stop();
        companion.setHealth((float) Math.max(config().duel_stop_health + 1.0D, companion.getHealth()));
        CompanionRecord record = record(companion);
        ServerPlayer owner = record == null ? null : resolveOwner(server, record);
        if (owner != null) {
            recall(owner, companion);
        }
    }

    private void tickNaturalSpawns(MinecraftServer server) {
        if (!config().natural_spawns_enabled) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension() != Level.OVERWORLD || !(player.level() instanceof ServerLevel level)) {
                continue;
            }
            if (random.nextDouble() > config().spawn_chance_per_player) {
                continue;
            }
            if (nearbyWildCount(level, player.blockPosition(), config().nearby_companion_limit_radius) >= config().max_wild_companions_near_player) {
                continue;
            }
            BlockPos spawn = findSpawnNear(level, player);
            if (spawn != null) {
                spawnWild(level, spawn);
            }
        }
    }

    private Mob spawnWild(ServerLevel level, BlockPos spawn) {
        Mob companion = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (companion == null) {
            return null;
        }
        companion.snapTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, random.nextFloat() * 360.0F, 0.0F);
        companion.addTag(TAG_COMPANION);
        companion.addTag(TAG_WILD);
        companion.setCustomName(Component.literal("Little Wanderer").withStyle(ChatFormatting.GREEN));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();
        companion.setSilent(true);
        companion.setCanPickUpLoot(false);
        setTinyNpcShape(companion);
        companion.setItemSlot(EquipmentSlot.HEAD, steveHead());
        companion.setDropChance(EquipmentSlot.HEAD, 0.0F);
        CompanionRecord record = dataStore.get().record(companion.getUUID());
        record.tamed = false;
        record.trust = 0;
        record.updatedAt = System.currentTimeMillis();
        normalizeCompanion(companion, record);
        level.addFreshEntity(companion);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, companion.getX(), companion.getY() + 0.5D, companion.getZ(), 10, 0.25D, 0.25D, 0.25D, 0.02D);
        return companion;
    }

    private boolean tickWildFood(ServerLevel level, Mob companion, CompanionRecord record) {
        AABB area = companion.getBoundingBox().inflate(config().food_detection_radius);
        ItemEntity food = level.getEntitiesOfClass(ItemEntity.class, area, item -> item.isAlive() && isFood(item.getItem())).stream()
                .min(Comparator.comparingDouble(companion::distanceToSqr))
                .orElse(null);
        if (food == null) {
            return false;
        }
        companion.getNavigation().moveTo(food, config().wild_speed);
        if (companion.distanceToSqr(food) > config().food_pickup_distance * config().food_pickup_distance) {
            return true;
        }
        ServerPlayer feeder = resolveFeeder(level, food);
        ItemStack stack = food.getItem();
        ItemStack eaten = stack.copyWithCount(1);
        int nutrition = foodNutrition(stack);
        stack.shrink(1);
        if (stack.isEmpty()) {
            food.discard();
        } else {
            food.setItem(stack);
        }
        record.trust += Math.max(1, (nutrition + 1) / 2);
        if (feeder != null) {
            record.lastFeederUuid = feeder.getUUID().toString();
            record.lastFeederName = feeder.getGameProfile().name();
        }
        record.updatedAt = System.currentTimeMillis();

        if (feeder != null && shouldTame(record, nutrition, eaten)) {
            tame(level, companion, record, feeder);
        } else {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, companion.getX(), companion.getY() + 0.7D, companion.getZ(), 6, 0.25D, 0.25D, 0.25D, 0.02D);
            level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.45F, 1.55F);
            save();
        }
        return true;
    }

    private void tickWildIdleWander(ServerLevel level, Mob companion) {
        UUID id = companion.getUUID();
        int cooldown = wildWanderCooldowns.getOrDefault(id, randomInt(15, WILD_IDLE_WANDER_MIN_COOLDOWN));
        if (cooldown > 0) {
            wildWanderCooldowns.put(id, cooldown - 1);
            return;
        }
        BlockPos target = chooseWildWanderTarget(level, companion.blockPosition());
        if (target != null) {
            boolean moving = companion.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, config().wild_speed);
            if (!moving) {
                wildWanderCooldowns.put(id, randomInt(20, WILD_IDLE_WANDER_MIN_COOLDOWN));
                return;
            }
        }
        wildWanderCooldowns.put(id, randomInt(WILD_IDLE_WANDER_MIN_COOLDOWN, WILD_IDLE_WANDER_MAX_COOLDOWN));
    }

    private BlockPos chooseWildWanderTarget(ServerLevel level, BlockPos origin) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int x = origin.getX() + randomInt(-WILD_IDLE_WANDER_RADIUS, WILD_IDLE_WANDER_RADIUS);
            int z = origin.getZ() + randomInt(-WILD_IDLE_WANDER_RADIUS, WILD_IDLE_WANDER_RADIUS);
            BlockPos target = surfacePatrolPos(level, x, z);
            if (validWildWanderTarget(level, target)) {
                return target;
            }
        }
        return null;
    }

    private boolean validWildWanderTarget(ServerLevel level, BlockPos target) {
        if (target == null || !level.hasChunkAt(target) || !level.getWorldBorder().isWithinBounds(target)) {
            return false;
        }
        if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
            return false;
        }
        BlockState ground = level.getBlockState(target.below());
        return !ground.isAir() && !ground.is(BlockTags.LEAVES);
    }

    private boolean shouldTame(CompanionRecord record, int nutrition, ItemStack eatenStack) {
        if (record.trust < config().trust_required) {
            return false;
        }
        double chance = config().base_tame_chance
                + record.trust * config().tame_chance_per_trust
                + nutrition * config().tame_chance_per_food_nutrition;
        Identifier id = BuiltInRegistries.ITEM.getKey(eatenStack.getItem());
        if (id != null && config().bonus_taming_foods.contains(id.toString())) {
            chance += 0.12D;
        }
        return random.nextDouble() < Math.min(0.95D, chance);
    }

    private void tame(ServerLevel level, Mob companion, CompanionRecord record, ServerPlayer owner) {
        if (activeCompanionCount(owner) >= config().max_tamed_companions_per_player) {
            owner.sendSystemMessage(Component.literal("You already have a bonded companion. Use /companions recall to bring it back.").withStyle(ChatFormatting.YELLOW));
            return;
        }
        companion.removeTag(TAG_WILD);
        companion.addTag(TAG_TAMED);
        wildWanderCooldowns.remove(companion.getUUID());
        record.tamed = true;
        record.retired = false;
        record.ownerUuid = owner.getUUID().toString();
        record.ownerName = owner.getGameProfile().name();
        record.mode = CompanionMode.FOLLOW;
        record.role = CompanionRole.DEFENDER.id();
        record.personality = PersonalityTrait.random(random).id();
        record.level = 1;
        record.xp = 0L;
        record.survivalTicks = 0L;
        record.updatedAt = System.currentTimeMillis();
        companion.setCustomName(Component.literal("Little " + record.ownerName).withStyle(ChatFormatting.AQUA));
        companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(owner));
        normalizeCompanion(companion, record);
        restoreHealth(companion, record, companion.getMaxHealth(), false);
        level.sendParticles(ParticleTypes.HEART, companion.getX(), companion.getY() + 0.8D, companion.getZ(), 14, 0.35D, 0.35D, 0.35D, 0.02D);
        level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.75F, 1.75F);
        owner.sendSystemMessage(Component.literal("Little " + record.ownerName + " bonded with you. Sneak-right-click to open companion storage.").withStyle(ChatFormatting.AQUA));
        save();
    }

    private int activeCompanionCount(ServerPlayer owner) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return 0;
        }
        String ownerUuid = owner.getUUID().toString();
        int count = 0;
        for (CompanionRecord record : store.all()) {
            if (record.tamed && !record.retired && ownerUuid.equals(record.ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    private void enforceCompanionLimit(MinecraftServer server, CompanionDataStore store) {
        if (config().max_tamed_companions_per_player > 1) {
            return;
        }
        Map<String, CompanionRecord> newestByOwner = new HashMap<>();
        for (CompanionRecord record : store.all()) {
            if (!record.tamed || record.retired || record.ownerUuid == null || record.ownerUuid.isBlank()) {
                continue;
            }
            CompanionRecord current = newestByOwner.get(record.ownerUuid);
            if (current == null || record.updatedAt > current.updatedAt) {
                newestByOwner.put(record.ownerUuid, record);
            }
        }
        for (CompanionRecord record : store.all()) {
            if (!record.tamed || record.retired || record.ownerUuid == null || record.ownerUuid.isBlank()) {
                continue;
            }
            if (newestByOwner.get(record.ownerUuid) == record) {
                continue;
            }
            record.retired = true;
            record.updatedAt = System.currentTimeMillis();
            liveEntity(server, record).ifPresent(this::discardManagedCompanion);
        }
    }

    private void recordDuelResult(ServerPlayer winner, ServerPlayer loser) {
        CompanionDataStore store = dataStore.get();
        if (store == null || winner == null || loser == null) {
            return;
        }
        store.recordDuelWin(winner.getUUID());
        store.recordDuelLoss(loser.getUUID());
    }

    private void ensureTamedProfile(CompanionRecord record) {
        if (record == null || !record.tamed) {
            return;
        }
        record.role = role(record).id();
        if (record.personality == null || record.personality.isBlank()) {
            record.personality = PersonalityTrait.random(random).id();
            record.updatedAt = System.currentTimeMillis();
        }
        record.level = Math.max(1, Math.min(config().companion_max_level, record.level));
        record.xp = Math.max(0L, record.xp);
    }

    private void tickSurvivalProgress(ServerLevel level, Mob companion, CompanionRecord record, ServerPlayer owner) {
        if (config().companion_xp_survival_amount <= 0) {
            return;
        }
        record.survivalTicks++;
        if (record.survivalTicks < config().companion_xp_survival_interval_ticks) {
            return;
        }
        record.survivalTicks = 0L;
        awardXp(companion, record, config().companion_xp_survival_amount, "survival");
    }

    private void awardXp(Mob companion, CompanionRecord record, int rawAmount, String reason) {
        if (record == null || !record.tamed || record.retired || rawAmount <= 0 || record.level >= config().companion_max_level) {
            return;
        }
        int amount = rawAmount;
        if (personality(record) == PersonalityTrait.LUCKY) {
            amount = Math.max(1, Mth.ceil(rawAmount * 1.15D));
        }
        record.xp += amount;
        boolean leveled = false;
        while (record.level < config().companion_max_level && record.xp >= xpForNextLevel(record)) {
            record.xp -= xpForNextLevel(record);
            record.level++;
            leveled = true;
        }
        record.updatedAt = System.currentTimeMillis();
        if (leveled) {
            normalizeCompanion(companion, record);
            companion.heal(2.0F);
            if (companion.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, companion.getX(), companion.getY() + 0.7D, companion.getZ(), 18, 0.35D, 0.35D, 0.35D, 0.03D);
                level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.55F, 1.9F);
                ServerPlayer owner = resolveOwner(level.getServer(), record);
                if (owner != null) {
                    owner.sendSystemMessage(Component.literal("Little " + record.ownerName + " reached level " + record.level + ".").withStyle(ChatFormatting.AQUA));
                }
            }
        }
    }

    private double companionHealthWithProgression(double base, CompanionRecord record) {
        if (record == null || !record.tamed) {
            return base;
        }
        double value = base + Math.max(0, record.level - 1) * 0.75D;
        return switch (personality(record)) {
            case BRAVE -> value + 2.0D;
            case LOYAL -> value + 1.0D;
            default -> value;
        };
    }

    private double companionDamageWithProgression(double base, CompanionRecord record) {
        if (record == null || !record.tamed) {
            return base;
        }
        double value = base + Math.max(0, record.level - 1) * 0.16D;
        value += switch (role(record)) {
            case AGGRESSIVE -> 0.9D;
            case SCOUT -> -0.35D;
            case PASSIVE -> -1.0D;
            case GUARD_CLAIM, DEFENDER, STAY -> 0.0D;
        };
        value += switch (personality(record)) {
            case BRAVE -> 0.45D;
            case SCRAPPY -> 0.55D;
            case CAREFUL -> -0.2D;
            default -> 0.0D;
        };
        return Math.max(0.5D, value);
    }

    private double roleSpeed(double base, CompanionRecord record, CompanionRole role) {
        double value = base;
        value += switch (role == null ? CompanionRole.DEFENDER : role) {
            case SCOUT -> 0.10D;
            case AGGRESSIVE -> 0.04D;
            case PASSIVE -> 0.02D;
            case GUARD_CLAIM, DEFENDER, STAY -> 0.0D;
        };
        if (personality(record) == PersonalityTrait.LOYAL) {
            value += 0.03D;
        }
        return Mth.clamp(value, 0.05D, 0.9D);
    }

    private double roleDefendRadius(CompanionRole role) {
        return switch (role == null ? CompanionRole.DEFENDER : role) {
            case AGGRESSIVE -> config().defend_radius + 8.0D;
            case SCOUT -> Math.max(4.0D, config().defend_radius - 5.0D);
            case GUARD_CLAIM -> config().defend_radius + 4.0D;
            case PASSIVE -> 0.0D;
            case DEFENDER, STAY -> config().defend_radius;
        };
    }

    private double armorScore(ItemStack stack, EquipmentSlot slot) {
        if (stack == null || stack.isEmpty()) {
            return 0.0D;
        }
        ItemAttributeModifiers modifiers = stack.get(DataComponents.ATTRIBUTE_MODIFIERS);
        return modifiers == null ? 0.0D : Math.max(0.0D, modifiers.compute(Attributes.ARMOR, 0.0D, slot));
    }

    private Optional<CompanionRecord> latestOwnedRecord(ServerPlayer player) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        return store.all().stream()
                .filter(record -> record.tamed && !record.retired && !record.dead && player.getUUID().toString().equals(record.ownerUuid))
                .max(Comparator.comparingLong(record -> record.updatedAt));
    }

    private Optional<CompanionRecord> latestDeadOwnedRecord(ServerPlayer player) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        return store.all().stream()
                .filter(record -> record.tamed && !record.retired && record.dead && player.getUUID().toString().equals(record.ownerUuid))
                .max(Comparator.comparingLong(record -> record.updatedAt));
    }

    private Optional<CompanionRecord> latestAnyOwnedRecord(ServerPlayer player) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        return store.all().stream()
                .filter(record -> record.tamed && !record.retired && player.getUUID().toString().equals(record.ownerUuid))
                .max(Comparator.comparingLong(record -> record.updatedAt));
    }

    private void queueRespawnEgg(ServerLevel level, Mob companion, CompanionRecord record) {
        syncVisibleEquipmentToRecord(level.getServer(), companion, record);
        updateLocation(companion, record);
        long now = System.currentTimeMillis();
        record.dead = true;
        record.respawnReadyAt = now + config().companion_respawn_delay_seconds * 1000L;
        record.respawnEggId = UUID.randomUUID().toString();
        record.respawnEggIssued = false;
        record.updatedAt = now;

        ServerPlayer owner = resolveOwner(level.getServer(), record);
        if (owner != null) {
            issueRespawnEggIfNeeded(owner, record);
            owner.sendSystemMessage(Component.literal("Your companion was defeated. Keep its Little Companion Egg in your inventory to revive it. " + respawnStatus(record)).withStyle(ChatFormatting.YELLOW));
        }
        level.sendParticles(ParticleTypes.SMOKE, companion.getX(), companion.getY() + 0.5D, companion.getZ(), 14, 0.35D, 0.25D, 0.35D, 0.03D);
        level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.55F, 1.55F);
        discardManagedCompanion(companion);
        save();
    }

    private void tickRespawnEggs(MinecraftServer server, CompanionDataStore store) {
        long now = System.currentTimeMillis();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (CompanionRecord record : store.all()) {
                if (record.dead && !record.retired && !record.respawnEggIssued && player.getUUID().toString().equals(record.ownerUuid)) {
                    issueRespawnEggIfNeeded(player, record);
                }
            }
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                ItemStack stack = player.getInventory().getItem(slot);
                RespawnEgg egg = readRespawnEgg(stack).orElse(null);
                if (egg == null || !player.getUUID().toString().equals(egg.ownerUuid)) {
                    continue;
                }
                CompanionRecord record = store.get(egg.recordUuid);
                if (record == null || !record.dead || record.retired || !egg.eggId.equals(record.respawnEggId)) {
                    continue;
                }
                updateRespawnEggVisual(stack, record);
                if (now < record.respawnReadyAt) {
                    continue;
                }
                Mob revived = respawnOwned(player, record);
                if (revived == null) {
                    player.sendSystemMessage(Component.literal("Your Little Companion Egg is ready, but there is no safe place to revive it here.").withStyle(ChatFormatting.RED));
                    continue;
                }
                stack.shrink(1);
                if (stack.isEmpty()) {
                    player.getInventory().setItem(slot, ItemStack.EMPTY);
                }
                player.sendSystemMessage(Component.literal("Your Little Wanderer has revived by your side.").withStyle(ChatFormatting.AQUA));
                if (player.level() instanceof ServerLevel level) {
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, revived.getX(), revived.getY() + 0.6D, revived.getZ(), 18, 0.35D, 0.35D, 0.35D, 0.03D);
                    level.playSound(null, revived.getX(), revived.getY(), revived.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.55F, 1.85F);
                }
                store.save();
                break;
            }
        }
    }

    private void issueRespawnEggIfNeeded(ServerPlayer owner, CompanionRecord record) {
        if (!record.dead || record.respawnEggIssued) {
            return;
        }
        record.respawnEggIssued = true;
        record.updatedAt = System.currentTimeMillis();
        giveOrDrop(owner, createRespawnEgg(record));
        save();
    }

    private ItemStack createRespawnEgg(CompanionRecord record) {
        ItemStack stack = new ItemStack(Items.EGG);
        updateRespawnEggVisual(stack, record);
        CompoundTag tag = new CompoundTag();
        tag.putString(RESPAWN_EGG_TYPE, RESPAWN_EGG_MARKER);
        tag.putString(RESPAWN_EGG_RECORD_UUID, record.entityUuid);
        tag.putString(RESPAWN_EGG_OWNER_UUID, record.ownerUuid);
        tag.putString(RESPAWN_EGG_ID, record.respawnEggId);
        tag.putLong(RESPAWN_EGG_READY_AT, record.respawnReadyAt);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    private void updateRespawnEggVisual(ItemStack stack, CompanionRecord record) {
        String ownerName = record.ownerName == null || record.ownerName.isBlank() ? "Companion" : record.ownerName;
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Little Companion Egg").withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Bound to Little " + ownerName).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Keep this in your inventory.").withStyle(ChatFormatting.YELLOW));
        lore.add(Component.literal(respawnStatus(record)).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, System.currentTimeMillis() >= record.respawnReadyAt);
    }

    private Optional<RespawnEgg> readRespawnEgg(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = customData.copyTag();
        if (!RESPAWN_EGG_MARKER.equals(tag.getStringOr(RESPAWN_EGG_TYPE, ""))) {
            return Optional.empty();
        }
        try {
            UUID recordUuid = UUID.fromString(tag.getStringOr(RESPAWN_EGG_RECORD_UUID, ""));
            return Optional.of(new RespawnEgg(
                    recordUuid,
                    tag.getStringOr(RESPAWN_EGG_OWNER_UUID, ""),
                    tag.getStringOr(RESPAWN_EGG_ID, ""),
                    tag.getLongOr(RESPAWN_EGG_READY_AT, 0L)));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private boolean hasMatchingRespawnEgg(ServerPlayer player, CompanionRecord record) {
        try {
            UUID recordUuid = UUID.fromString(record.entityUuid);
            for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
                RespawnEgg egg = readRespawnEgg(player.getInventory().getItem(slot)).orElse(null);
                if (egg != null
                        && recordUuid.equals(egg.recordUuid)
                        && player.getUUID().toString().equals(egg.ownerUuid)
                        && record.respawnEggId.equals(egg.eggId)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String respawnStatus(CompanionRecord record) {
        long seconds = Math.max(0L, (record.respawnReadyAt - System.currentTimeMillis() + 999L) / 1000L);
        if (seconds <= 0L) {
            return "Ready to revive.";
        }
        long minutes = seconds / 60L;
        long remainder = seconds % 60L;
        return "Revives in " + minutes + "m " + remainder + "s.";
    }

    private void placeCompanionBed(ServerLevel level, ServerPlayer owner, CompanionRecord record, BlockPos base, Direction facing, String color) {
        color = normalizedBedColor(color);
        clearCompanionBed(level.getServer(), record);
        Display.BlockDisplay foot = spawnBedDisplay(level, base, facing, BedPart.FOOT, color);
        Display.BlockDisplay head = spawnBedDisplay(level, base, facing, BedPart.HEAD, color);
        record.bedDimension = level.dimension().identifier().toString();
        record.bedX = base.getX();
        record.bedY = base.getY();
        record.bedZ = base.getZ();
        record.bedFacing = facing.getSerializedName();
        record.bedColor = color;
        record.bedFootUuid = foot == null ? "" : foot.getUUID().toString();
        record.bedHeadUuid = head == null ? "" : head.getUUID().toString();
        record.updatedAt = System.currentTimeMillis();
        save();
        if (record.mode == CompanionMode.STAY) {
            findOwnedAnywhere(owner).ifPresent(companion -> restAtBed(level, companion, record));
        }
    }

    private Display.BlockDisplay spawnBedDisplay(ServerLevel level, BlockPos base, Direction facing, BedPart part, String color) {
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        double scale = config().companion_bed_scale;
        Vec3 pos = bedPartPosition(base, facing, part, scale);
        display.snapTo(pos.x, pos.y, pos.z, facing.toYRot(), 0.0F);
        display.setBlockState(bedState(facing, part, color));
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f((float) scale), new Quaternionf()));
        display.setViewRange(32.0F);
        display.setWidth((float) scale);
        display.setHeight((float) scale);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.addTag(TAG_BED_DISPLAY);
        level.addFreshEntity(display);
        return display;
    }

    private Vec3 bedPartPosition(BlockPos base, Direction facing, BedPart part, double scale) {
        double centered = (1.0D - scale) * 0.5D;
        double partOffset = part == BedPart.HEAD ? scale : 0.0D;
        return new Vec3(
                base.getX() + centered + facing.getStepX() * partOffset,
                base.getY() + 0.03D,
                base.getZ() + centered + facing.getStepZ() * partOffset);
    }

    private BlockState bedState(Direction facing, BedPart part, String color) {
        return bedBlock(color).defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, facing)
                .setValue(BedBlock.PART, part);
    }

    private net.minecraft.world.level.block.Block bedBlock(String color) {
        return switch (normalizedBedColor(color)) {
            case "white" -> Blocks.WHITE_BED;
            case "orange" -> Blocks.ORANGE_BED;
            case "magenta" -> Blocks.MAGENTA_BED;
            case "light_blue" -> Blocks.LIGHT_BLUE_BED;
            case "yellow" -> Blocks.YELLOW_BED;
            case "lime" -> Blocks.LIME_BED;
            case "pink" -> Blocks.PINK_BED;
            case "gray" -> Blocks.GRAY_BED;
            case "light_gray" -> Blocks.LIGHT_GRAY_BED;
            case "cyan" -> Blocks.CYAN_BED;
            case "purple" -> Blocks.PURPLE_BED;
            case "blue" -> Blocks.BLUE_BED;
            case "brown" -> Blocks.BROWN_BED;
            case "green" -> Blocks.GREEN_BED;
            case "black" -> Blocks.BLACK_BED;
            default -> Blocks.RED_BED;
        };
    }

    private boolean restAtBed(ServerLevel level, Mob companion, CompanionRecord record) {
        if (!hasCompanionBed(record) || !level.dimension().identifier().toString().equals(record.bedDimension)) {
            companion.getNavigation().stop();
            return false;
        }
        Direction facing = Direction.byName(record.bedFacing);
        if (facing == null) {
            facing = Direction.SOUTH;
        }
        Vec3 rest = bedPartPosition(new BlockPos(record.bedX, record.bedY, record.bedZ), facing, BedPart.FOOT, config().companion_bed_scale)
                .add(0.0D, 0.05D, 0.0D);
        double distance = companion.distanceToSqr(rest);
        double returnDistance = config().companion_bed_return_distance * config().companion_bed_return_distance;
        if (distance > config().teleport_distance * config().teleport_distance) {
            companion.teleportTo(rest.x, rest.y, rest.z);
            companion.getNavigation().stop();
            return true;
        }
        if (distance > returnDistance) {
            companion.getNavigation().moveTo(rest.x, rest.y, rest.z, config().tamed_speed);
            return true;
        }
        companion.getNavigation().stop();
        companion.getLookControl().setLookAt(rest.x + facing.getStepX(), rest.y + 0.4D, rest.z + facing.getStepZ(), 20.0F, 20.0F);
        return false;
    }

    private boolean hasCompanionBed(CompanionRecord record) {
        return record != null && record.bedDimension != null && !record.bedDimension.isBlank();
    }

    private boolean pickUpCompanionBed(ServerPlayer player, CompanionRecord record, boolean fromCommand) {
        if (record == null || !hasCompanionBed(record)) {
            return false;
        }
        String color = normalizedBedColor(record.bedColor);
        clearCompanionBed(player.level().getServer(), record);
        record.updatedAt = System.currentTimeMillis();
        save();
        if (!player.getAbilities().instabuild || fromCommand) {
            giveOrDrop(player, companionBedItem(color));
        }
        player.sendSystemMessage(Component.literal("Picked up your Little Companion Bed.").withStyle(ChatFormatting.GREEN));
        return true;
    }

    private Optional<CompanionRecord> ownedBedRecordForDisplay(ServerPlayer player, Entity entity) {
        if (entity == null || !entity.entityTags().contains(TAG_BED_DISPLAY)) {
            return Optional.empty();
        }
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        String playerUuid = player.getUUID().toString();
        String entityUuid = entity.getUUID().toString();
        return store.all().stream()
                .filter(this::hasCompanionBed)
                .filter(record -> playerUuid.equals(record.ownerUuid))
                .filter(record -> entityUuid.equals(record.bedFootUuid) || entityUuid.equals(record.bedHeadUuid))
                .findFirst();
    }

    private Optional<CompanionRecord> ownedBedRecordNear(ServerPlayer player, Vec3 position) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return Optional.empty();
        }
        String playerUuid = player.getUUID().toString();
        String dimension = player.level().dimension().identifier().toString();
        return store.all().stream()
                .filter(this::hasCompanionBed)
                .filter(record -> playerUuid.equals(record.ownerUuid))
                .filter(record -> dimension.equals(record.bedDimension))
                .filter(record -> distanceToBedSqr(record, position) <= 1.25D)
                .min(Comparator.comparingDouble(record -> distanceToBedSqr(record, position)));
    }

    private double distanceToBedSqr(CompanionRecord record, Vec3 position) {
        Direction facing = Direction.byName(record.bedFacing);
        if (facing == null) {
            facing = Direction.SOUTH;
        }
        BlockPos base = new BlockPos(record.bedX, record.bedY, record.bedZ);
        double scale = config().companion_bed_scale;
        double foot = position.distanceToSqr(bedPartPosition(base, facing, BedPart.FOOT, scale));
        double head = position.distanceToSqr(bedPartPosition(base, facing, BedPart.HEAD, scale));
        return Math.min(foot, head);
    }

    private void clearCompanionBed(MinecraftServer server, CompanionRecord record) {
        discardSavedEntity(server, record.bedFootUuid);
        discardSavedEntity(server, record.bedHeadUuid);
        record.bedDimension = "";
        record.bedX = 0;
        record.bedY = 0;
        record.bedZ = 0;
        record.bedFacing = "";
        record.bedColor = "red";
        record.bedFootUuid = "";
        record.bedHeadUuid = "";
    }

    private void discardSavedEntity(MinecraftServer server, String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return;
        }
        try {
            UUID parsed = UUID.fromString(uuid);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntityInAnyDimension(parsed);
                if (entity != null && entity.entityTags().contains(TAG_BED_DISPLAY)) {
                    entity.discard();
                    return;
                }
            }
        } catch (Exception ignored) {
        }
    }

    private Optional<Mob> liveEntity(MinecraftServer server, CompanionRecord record) {
        try {
            UUID uuid = UUID.fromString(record.entityUuid);
            for (ServerLevel level : server.getAllLevels()) {
                Entity entity = level.getEntityInAnyDimension(uuid);
                if (entity instanceof Mob companion && isCompanion(companion) && companion.isAlive()) {
                    return Optional.of(companion);
                }
            }
        } catch (Exception ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private CompanionRecord ensureRecordMatchesEntity(ServerPlayer owner, Mob companion, CompanionRecord record) {
        if (record == null || companion == null || companion.getUUID().toString().equals(record.entityUuid)) {
            return record;
        }
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return record;
        }
        CompanionRecord replacement = store.record(companion.getUUID());
        copyRecordToReplacement(record, replacement, companion.getUUID());
        replacement.ownerUuid = owner.getUUID().toString();
        replacement.ownerName = owner.getGameProfile().name();
        record.retired = true;
        record.updatedAt = System.currentTimeMillis();
        return replacement;
    }

    private void discardNearbyOwnedDuplicates(ServerPlayer owner, Mob keep) {
        if (owner == null || keep == null || config().max_tamed_companions_per_player > 1) {
            return;
        }
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return;
        }
        String ownerUuid = owner.getUUID().toString();
        AABB area = keep.getBoundingBox().inflate(Math.max(16.0D, config().teleport_distance + 8.0D));
        for (Mob companion : keep.level().getEntitiesOfClass(Mob.class, area, this::isCompanion)) {
            if (companion.getUUID().equals(keep.getUUID())) {
                continue;
            }
            CompanionRecord record = store.get(companion.getUUID());
            if (record == null || !ownerUuid.equals(record.ownerUuid)) {
                continue;
            }
            record.retired = true;
            record.updatedAt = System.currentTimeMillis();
            discardManagedCompanion(companion);
        }
    }

    private void setManagedEquipmentDropChances(Mob companion) {
        if (companion == null) {
            return;
        }
        companion.setDropChance(EquipmentSlot.HEAD, 0.0F);
        companion.setDropChance(EquipmentSlot.CHEST, 0.0F);
        companion.setDropChance(EquipmentSlot.LEGS, 0.0F);
        companion.setDropChance(EquipmentSlot.FEET, 0.0F);
        companion.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        companion.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
    }

    private void discardManagedCompanion(Mob companion) {
        if (companion == null) {
            return;
        }
        wildWanderCooldowns.remove(companion.getUUID());
        guardPatrolTargets.remove(companion.getUUID());
        guardPatrolCooldowns.remove(companion.getUUID());
        stuckTicks.remove(companion.getUUID());
        ambientSoundCooldowns.remove(companion.getUUID());
        attackCooldowns.remove(companion.getUUID());
        setManagedEquipmentDropChances(companion);
        companion.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        companion.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        companion.discard();
    }

    private Mob respawnOwned(ServerPlayer owner, CompanionRecord oldRecord) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            return null;
        }

        ServerLevel level = owner.level();
        Mob companion = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (companion == null) {
            return null;
        }
        companion.snapTo(owner.getX(), owner.getY(), owner.getZ(), owner.getYRot(), 0.0F);
        companion.addTag(TAG_COMPANION);
        companion.addTag(TAG_TAMED);
        companion.setCustomName(Component.literal("Little " + owner.getGameProfile().name()).withStyle(ChatFormatting.AQUA));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();
        companion.setSilent(true);
        companion.setCanPickUpLoot(false);
        setTinyNpcShape(companion);
        companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(owner));
        companion.setDropChance(EquipmentSlot.HEAD, 0.0F);

        CompanionRecord record = store.record(companion.getUUID());
        copyOwnedRecord(oldRecord, record, companion.getUUID(), owner);
        applyRecordEquipment(companion, record, level.getServer().registryAccess());
        normalizeCompanion(companion, record);
        restoreHealth(companion, record, (float) oldRecord.health, oldRecord.dead);
        updateLocation(companion, record);
        if (!level.addFreshEntity(companion)) {
            store.remove(companion.getUUID());
            discardManagedCompanion(companion);
            return null;
        }
        oldRecord.retired = true;
        oldRecord.updatedAt = System.currentTimeMillis();
        discardNearbyOwnedDuplicates(owner, companion);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, companion.getX(), companion.getY() + 0.5D, companion.getZ(), 12, 0.25D, 0.25D, 0.25D, 0.02D);
        save();
        return companion;
    }

    private void copyOwnedRecord(CompanionRecord from, CompanionRecord to, UUID newUuid, ServerPlayer owner) {
        to.entityUuid = newUuid.toString();
        to.tamed = true;
        to.retired = false;
        to.ownerUuid = owner.getUUID().toString();
        to.ownerName = owner.getGameProfile().name();
        to.lastFeederUuid = from.lastFeederUuid;
        to.lastFeederName = from.lastFeederName;
        to.trust = from.trust;
        to.mode = CompanionMode.FOLLOW;
        to.role = from.role;
        to.personality = from.personality;
        to.level = from.level;
        to.xp = from.xp;
        to.survivalTicks = from.survivalTicks;
        to.health = from.dead ? -1.0D : from.health;
        to.hiddenHelmet = from.hiddenHelmet;
        to.chest = from.chest;
        to.legs = from.legs;
        to.boots = from.boots;
        to.mainHand = from.mainHand;
        to.offHand = from.offHand;
        to.dead = false;
        to.respawnReadyAt = 0L;
        to.respawnEggId = "";
        to.respawnEggIssued = false;
        to.bedDimension = from.bedDimension;
        to.bedX = from.bedX;
        to.bedY = from.bedY;
        to.bedZ = from.bedZ;
        to.bedFacing = from.bedFacing;
        to.bedColor = from.bedColor;
        to.bedFootUuid = from.bedFootUuid;
        to.bedHeadUuid = from.bedHeadUuid;
        to.guardDimension = from.guardDimension;
        to.guardX = from.guardX;
        to.guardY = from.guardY;
        to.guardZ = from.guardZ;
        to.storage = new java.util.ArrayList<>(from.storage);
        to.createdAt = from.createdAt;
        to.updatedAt = System.currentTimeMillis();
        to.normalized();
    }

    private void upgradeLegacyCompanion(ServerLevel level, Mob oldCompanion, CompanionRecord oldRecord) {
        CompanionDataStore store = dataStore.get();
        if (store == null) {
            discardManagedCompanion(oldCompanion);
            return;
        }
        float healthBefore = oldCompanion.getHealth();

        Mob companion = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
        if (companion == null) {
            setManagedEquipmentDropChances(oldCompanion);
            save();
            return;
        }
        companion.snapTo(oldCompanion.getX(), oldCompanion.getY(), oldCompanion.getZ(), oldCompanion.getYRot(), oldCompanion.getXRot());
        companion.addTag(TAG_COMPANION);
        companion.addTag(oldRecord.tamed ? TAG_TAMED : TAG_WILD);
        companion.setCustomName(Component.literal(oldRecord.tamed
                ? "Little " + (oldRecord.ownerName == null || oldRecord.ownerName.isBlank() ? "Companion" : oldRecord.ownerName)
                : "Little Wanderer").withStyle(oldRecord.tamed ? ChatFormatting.AQUA : ChatFormatting.GREEN));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();
        companion.setSilent(true);
        companion.setCanPickUpLoot(false);
        setTinyNpcShape(companion);

        CompanionRecord record = store.record(companion.getUUID());
        copyRecordToReplacement(oldRecord, record, companion.getUUID());
        applyRecordEquipment(companion, record, level.getServer().registryAccess());
        normalizeCompanion(companion, record);
        restoreHealth(companion, record, healthBefore, false);
        updateLocation(companion, record);
        if (!level.addFreshEntity(companion)) {
            store.remove(companion.getUUID());
            discardManagedCompanion(companion);
            setManagedEquipmentDropChances(oldCompanion);
            save();
            return;
        }
        oldRecord.retired = true;
        oldRecord.updatedAt = System.currentTimeMillis();
        discardManagedCompanion(oldCompanion);
        save();
    }

    private void copyRecordToReplacement(CompanionRecord from, CompanionRecord to, UUID newUuid) {
        to.entityUuid = newUuid.toString();
        to.tamed = from.tamed;
        to.retired = false;
        to.ownerUuid = from.ownerUuid;
        to.ownerName = from.ownerName;
        to.lastFeederUuid = from.lastFeederUuid;
        to.lastFeederName = from.lastFeederName;
        to.trust = from.trust;
        to.mode = from.mode == null ? CompanionMode.FOLLOW : from.mode;
        to.role = from.role;
        to.personality = from.personality;
        to.level = from.level;
        to.xp = from.xp;
        to.survivalTicks = from.survivalTicks;
        to.health = from.health;
        to.hiddenHelmet = from.hiddenHelmet;
        to.chest = from.chest;
        to.legs = from.legs;
        to.boots = from.boots;
        to.mainHand = from.mainHand;
        to.offHand = from.offHand;
        to.dead = false;
        to.respawnReadyAt = 0L;
        to.respawnEggId = "";
        to.respawnEggIssued = false;
        to.bedDimension = from.bedDimension;
        to.bedX = from.bedX;
        to.bedY = from.bedY;
        to.bedZ = from.bedZ;
        to.bedFacing = from.bedFacing;
        to.bedColor = from.bedColor;
        to.bedFootUuid = from.bedFootUuid;
        to.bedHeadUuid = from.bedHeadUuid;
        to.guardDimension = from.guardDimension;
        to.guardX = from.guardX;
        to.guardY = from.guardY;
        to.guardZ = from.guardZ;
        to.storage = new java.util.ArrayList<>(from.normalized().storage);
        to.createdAt = from.createdAt;
        to.updatedAt = System.currentTimeMillis();
        to.normalized();
    }

    private void applyRecordEquipment(Mob companion, CompanionRecord record, net.minecraft.core.HolderLookup.Provider registries) {
        companion.setItemSlot(EquipmentSlot.HEAD, ownerNameHead(record));
        companion.setItemSlot(EquipmentSlot.CHEST, record.chest.toStack(registries));
        companion.setItemSlot(EquipmentSlot.LEGS, record.legs.toStack(registries));
        companion.setItemSlot(EquipmentSlot.FEET, record.boots.toStack(registries));
        companion.setItemSlot(EquipmentSlot.MAINHAND, record.mainHand.toStack(registries));
        companion.setItemSlot(EquipmentSlot.OFFHAND, record.offHand.toStack(registries));
        setManagedEquipmentDropChances(companion);
    }

    private void updateLocation(Mob companion, CompanionRecord record) {
        record.lastDimension = companion.level().dimension().identifier().toString();
        record.lastX = companion.getX();
        record.lastY = companion.getY();
        record.lastZ = companion.getZ();
        if (companion.isAlive() && !record.dead) {
            record.health = Mth.clamp(companion.getHealth(), 1.0F, companion.getMaxHealth());
        }
    }

    private void syncVisibleEquipmentToRecord(MinecraftServer server, Mob companion, CompanionRecord record) {
        if (!record.tamed || record.retired) {
            return;
        }
        net.minecraft.core.HolderLookup.Provider registries = server.registryAccess();
        ItemStack head = companion.getItemBySlot(EquipmentSlot.HEAD);
        if (!head.isEmpty() && !head.is(Items.PLAYER_HEAD)) {
            record.hiddenHelmet = ItemRecord.fromStack(head, registries);
            companion.setItemSlot(EquipmentSlot.HEAD, ownerNameHead(record));
        }
        setManagedEquipmentDropChances(companion);
        record.chest = ItemRecord.fromStack(companion.getItemBySlot(EquipmentSlot.CHEST), registries);
        record.legs = ItemRecord.fromStack(companion.getItemBySlot(EquipmentSlot.LEGS), registries);
        record.boots = ItemRecord.fromStack(companion.getItemBySlot(EquipmentSlot.FEET), registries);
        record.mainHand = ItemRecord.fromStack(companion.getItemBySlot(EquipmentSlot.MAINHAND), registries);
        record.offHand = ItemRecord.fromStack(companion.getItemBySlot(EquipmentSlot.OFFHAND), registries);
    }

    private void followOwner(ServerPlayer owner, Mob companion, CompanionRole role) {
        double distance = companion.distanceToSqr(owner);
        double teleport = config().teleport_distance * config().teleport_distance;
        if (distance > teleport || stuckTicks.getOrDefault(companion.getUUID(), 0) > config().stuck_teleport_ticks) {
            if (owner.isFallFlying()) {
                companion.getNavigation().stop();
                return;
            }
            teleportCompanionTo(companion, owner.getX(), owner.getY(), owner.getZ());
            companion.getNavigation().stop();
            stuckTicks.remove(companion.getUUID());
            return;
        }
        if (distance > config().follow_start_distance * config().follow_start_distance) {
            CompanionRecord record = record(companion);
            double baseSpeed = roleSpeed(config().tamed_speed, record, role);
            double catchUpSpeed = roleSpeed(config().catch_up_speed, record, role);
            double speed = distance > config().catch_up_distance * config().catch_up_distance ? catchUpSpeed : baseSpeed;
            boolean moving = companion.getNavigation().moveTo(owner, speed);
            if (moving) {
                stuckTicks.put(companion.getUUID(), 0);
            } else {
                stuckTicks.merge(companion.getUUID(), 1, Integer::sum);
            }
        } else if (distance < config().follow_stop_distance * config().follow_stop_distance) {
            companion.getNavigation().stop();
            stuckTicks.remove(companion.getUUID());
        }
    }

    private void ensureGuardPost(CompanionRecord record, Mob companion) {
        if (record.guardDimension == null || record.guardDimension.isBlank()) {
            setGuardPost(record, companion);
        }
    }

    private void returnToGuardPost(ServerLevel level, Mob companion, CompanionRecord record) {
        if (record.guardDimension == null || record.guardDimension.isBlank()) {
            setGuardPost(record, companion);
        }
        if (!record.guardDimension.equals(level.dimension().identifier().toString())) {
            companion.getNavigation().stop();
            return;
        }
        double distance = companion.distanceToSqr(record.guardX, record.guardY, record.guardZ);
        double returnDistance = Math.max(1.5D, config().companion_bed_return_distance + 2.0D);
        double teleportDistance = Math.max(config().teleport_distance, returnDistance + 8.0D);
        if (distance > teleportDistance * teleportDistance || stuckTicks.getOrDefault(companion.getUUID(), 0) > config().stuck_teleport_ticks) {
            companion.teleportTo(record.guardX, record.guardY, record.guardZ);
            companion.getNavigation().stop();
            stuckTicks.remove(companion.getUUID());
            return;
        }
        if (distance > returnDistance * returnDistance) {
            boolean moving = companion.getNavigation().moveTo(record.guardX, record.guardY, record.guardZ, roleSpeed(config().tamed_speed, record, CompanionRole.GUARD_CLAIM));
            if (moving) {
                stuckTicks.put(companion.getUUID(), 0);
            } else {
                stuckTicks.merge(companion.getUUID(), 1, Integer::sum);
            }
        } else {
            companion.getNavigation().stop();
            stuckTicks.remove(companion.getUUID());
        }
    }

    private void patrolGuardArea(ServerLevel level, Mob companion, CompanionRecord record) {
        if (!config().companion_guard_patrol_enabled) {
            returnToGuardPost(level, companion, record);
            return;
        }
        if (record.guardDimension == null || record.guardDimension.isBlank()) {
            setGuardPost(record, companion);
        }
        if (!record.guardDimension.equals(level.dimension().identifier().toString())) {
            companion.getNavigation().stop();
            return;
        }

        double distanceToPost = companion.distanceToSqr(record.guardX, record.guardY, record.guardZ);
        double teleportDistance = Math.max(config().teleport_distance, config().companion_guard_patrol_radius + 10.0D);
        if (distanceToPost > teleportDistance * teleportDistance || stuckTicks.getOrDefault(companion.getUUID(), 0) > config().stuck_teleport_ticks) {
            companion.teleportTo(record.guardX, record.guardY, record.guardZ);
            companion.getNavigation().stop();
            guardPatrolTargets.remove(companion.getUUID());
            guardPatrolCooldowns.remove(companion.getUUID());
            stuckTicks.remove(companion.getUUID());
            return;
        }

        UUID id = companion.getUUID();
        BlockPos target = guardPatrolTargets.get(id);
        if (target == null || blockDistanceSqr(companion.blockPosition(), target) < 4.0D || !validGuardPatrolTarget(level, record, target)) {
            int cooldown = guardPatrolCooldowns.getOrDefault(id, 0);
            if (cooldown > 0) {
                guardPatrolCooldowns.put(id, cooldown - 1);
                if (distanceToPost > config().companion_guard_patrol_radius * config().companion_guard_patrol_radius) {
                    returnToGuardPost(level, companion, record);
                } else {
                    companion.getNavigation().stop();
                }
                return;
            }
            target = chooseGuardPatrolTarget(level, record);
            guardPatrolTargets.put(id, target);
            guardPatrolCooldowns.put(id, randomInt(config().companion_guard_patrol_interval_ticks / 2, config().companion_guard_patrol_interval_ticks));
        }

        boolean moving = companion.getNavigation().moveTo(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D, roleSpeed(config().tamed_speed, record, CompanionRole.GUARD_CLAIM));
        if (moving) {
            stuckTicks.put(id, 0);
        } else {
            stuckTicks.merge(id, 1, Integer::sum);
            guardPatrolTargets.remove(id);
        }
    }

    private BlockPos chooseGuardPatrolTarget(ServerLevel level, CompanionRecord record) {
        Optional<ClaimBounds> claimBounds = guardClaimBounds(level, record);
        for (int attempt = 0; attempt < 10; attempt++) {
            int x;
            int z;
            if (claimBounds.isPresent()) {
                ClaimBounds bounds = claimBounds.get();
                x = randomInt(bounds.minX() + 1, bounds.maxX() - 1);
                z = randomInt(bounds.minZ() + 1, bounds.maxZ() - 1);
            } else {
                int radius = Mth.ceil(config().companion_guard_patrol_radius);
                x = Mth.floor(record.guardX) + randomInt(-radius, radius);
                z = Mth.floor(record.guardZ) + randomInt(-radius, radius);
            }
            BlockPos target = surfacePatrolPos(level, x, z);
            if (validGuardPatrolTarget(level, record, target)) {
                return target;
            }
        }
        return surfacePatrolPos(level, Mth.floor(record.guardX), Mth.floor(record.guardZ));
    }

    private BlockPos surfacePatrolPos(ServerLevel level, int x, int z) {
        BlockPos surface = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, level.getMinY(), z));
        if (!level.getBlockState(surface).isAir() && level.getBlockState(surface.above()).isAir()) {
            return surface.above();
        }
        return surface;
    }

    private boolean validGuardPatrolTarget(ServerLevel level, CompanionRecord record, BlockPos target) {
        if (target == null || !record.guardDimension.equals(level.dimension().identifier().toString())) {
            return false;
        }
        Optional<ClaimBounds> claimBounds = guardClaimBounds(level, record);
        if (claimBounds.isPresent()) {
            ClaimBounds bounds = claimBounds.get();
            return target.getX() >= bounds.minX()
                    && target.getX() <= bounds.maxX()
                    && target.getZ() >= bounds.minZ()
                    && target.getZ() <= bounds.maxZ();
        }
        double dx = target.getX() + 0.5D - record.guardX;
        double dz = target.getZ() + 0.5D - record.guardZ;
        return dx * dx + dz * dz <= config().companion_guard_patrol_radius * config().companion_guard_patrol_radius;
    }

    private Optional<ClaimBounds> guardClaimBounds(ServerLevel level, CompanionRecord record) {
        if (!config().claim_aware_combat_enabled || record.guardDimension == null || record.guardDimension.isBlank()) {
            return Optional.empty();
        }
        try {
            Object claims = claimsService();
            if (claims == null) {
                return Optional.empty();
            }
            java.lang.reflect.Method claimAt = claims.getClass().getMethod("claimAt", Level.class, BlockPos.class);
            Object optionalClaim = claimAt.invoke(claims, level, new BlockPos(Mth.floor(record.guardX), Mth.floor(record.guardY), Mth.floor(record.guardZ)));
            if (!(optionalClaim instanceof Optional<?> optional) || optional.isEmpty()) {
                return Optional.empty();
            }
            Object claim = optional.get();
            Object dimension = claim.getClass().getField("dimension").get(claim);
            if (dimension == null || !record.guardDimension.equals(dimension.toString())) {
                return Optional.empty();
            }
            int chunkX = claim.getClass().getField("chunkX").getInt(claim);
            int chunkZ = claim.getClass().getField("chunkZ").getInt(claim);
            return Optional.of(new ClaimBounds(chunkX * 16, chunkZ * 16, chunkX * 16 + 15, chunkZ * 16 + 15));
        } catch (ReflectiveOperationException ignored) {
            return Optional.empty();
        }
    }

    private double blockDistanceSqr(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dy = first.getY() - second.getY();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private LivingEntity chooseDefensiveTarget(ServerLevel level, ServerPlayer owner, Mob companion, CompanionRecord record, CompanionRole role) {
        if (role == CompanionRole.PASSIVE || (role == CompanionRole.SCOUT && random.nextDouble() < 0.60D)) {
            return null;
        }
        LivingEntity ownerTarget = owner.getLastHurtMob();
        if (validTarget(ownerTarget, owner, companion, record, role)) {
            return ownerTarget;
        }
        LivingEntity attacker = owner.getLastHurtByMob();
        if (validTarget(attacker, owner, companion, record, role)) {
            return attacker;
        }
        double radius = roleDefendRadius(role);
        AABB area = role == CompanionRole.GUARD_CLAIM
                ? new AABB(record.guardX, record.guardY, record.guardZ, record.guardX, record.guardY, record.guardZ).inflate(radius)
                : owner.getBoundingBox().inflate(radius);
        return level.getEntitiesOfClass(Monster.class, area, monster -> validTarget(monster, owner, companion, record, role)).stream()
                .min(Comparator.comparingDouble(target -> role == CompanionRole.GUARD_CLAIM ? distanceToGuardPostSqr(record, target, companion) : owner.distanceToSqr(target)))
                .orElse(null);
    }

    private boolean validTarget(LivingEntity target, ServerPlayer owner, Mob companion, CompanionRecord record, CompanionRole role) {
        if (target == null
                || !target.isAlive()
                || target.level() != owner.level()
                || target == owner
                || target == companion
                || isCompanion(target)) {
            return false;
        }
        double radius = roleDefendRadius(role);
        if (role == CompanionRole.GUARD_CLAIM && distanceToGuardPostSqr(record, target, companion) > radius * radius) {
            return false;
        }
        if (role != CompanionRole.GUARD_CLAIM && owner.distanceToSqr(target) > radius * radius) {
            return false;
        }
        if (target instanceof NeutralMob neutral && !neutralMobThreatensOwner(neutral, owner, companion)) {
            return false;
        }
        if (target instanceof Monster) {
            return role != CompanionRole.GUARD_CLAIM || sameClaimBoundary(companion, target);
        }
        if (target instanceof ServerPlayer targetPlayer) {
            return config().allow_companion_player_targets && claimAllowsCompanionPlayerTarget(owner, targetPlayer);
        }
        return false;
    }

    private boolean neutralMobThreatensOwner(NeutralMob neutral, ServerPlayer owner, Mob companion) {
        if (!(owner.level() instanceof ServerLevel level)) {
            return false;
        }
        LivingEntity neutralTarget = neutral.getTarget();
        return neutralTarget == owner
                || neutralTarget == companion
                || neutral.isAngryAt(owner, level)
                || neutral.isAngryAt(companion, level);
    }

    private double distanceToGuardPostSqr(CompanionRecord record, Entity target, Mob companion) {
        if (record == null || record.guardDimension == null || record.guardDimension.isBlank()) {
            return companion.distanceToSqr(target);
        }
        if (!record.guardDimension.equals(target.level().dimension().identifier().toString())) {
            return Double.MAX_VALUE;
        }
        double dx = target.getX() - record.guardX;
        double dy = target.getY() - record.guardY;
        double dz = target.getZ() - record.guardZ;
        return dx * dx + dy * dy + dz * dz;
    }

    private boolean sameClaimBoundary(Entity first, Entity second) {
        if (!config().claim_aware_combat_enabled) {
            return true;
        }
        try {
            Object claims = claimsService();
            if (claims == null) {
                return true;
            }
            java.lang.reflect.Method claimAt = claims.getClass().getMethod("claimAt", Level.class, BlockPos.class);
            Object firstClaim = claimAt.invoke(claims, first.level(), first.blockPosition());
            Object secondClaim = claimAt.invoke(claims, second.level(), second.blockPosition());
            if (!(firstClaim instanceof Optional<?> firstOptional) || !(secondClaim instanceof Optional<?> secondOptional)) {
                return true;
            }
            if (firstOptional.isEmpty() && secondOptional.isEmpty()) {
                return true;
            }
            if (firstOptional.isPresent() && secondOptional.isPresent()) {
                return firstOptional.get().equals(secondOptional.get());
            }
            return false;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private boolean claimAllowsCompanionPlayerTarget(ServerPlayer owner, ServerPlayer target) {
        if (guildWarAllowsCompanionPlayerTarget(owner, target)) {
            return true;
        }
        if (!config().claim_aware_combat_enabled) {
            return false;
        }
        try {
            Object claims = claimsService();
            if (claims == null) {
                return false;
            }
            Class<?> actionClass = Class.forName("com.echoesofaegis.claims.claim.ClaimAction");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object interactEntity = Enum.valueOf((Class<? extends Enum>) actionClass.asSubclass(Enum.class), "INTERACT_ENTITY");
            java.lang.reflect.Method can = claims.getClass().getMethod("can", ServerPlayer.class, Level.class, BlockPos.class, actionClass);
            Object result = can.invoke(claims, target, owner.level(), owner.blockPosition(), interactEntity);
            return result instanceof Boolean canInteract && !canInteract;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private Object claimsService() throws ReflectiveOperationException {
        Class<?> mod = Class.forName("com.echoesofaegis.claims.EchoesClaimsMod");
        return mod.getMethod("claims").invoke(null);
    }

    private boolean guildWarAllowsCompanionPlayerTarget(ServerPlayer owner, ServerPlayer target) {
        try {
            Class<?> mod = Class.forName("com.echoesofaegis.guildhall.EchoesGuildhallMod");
            Object service = mod.getMethod("guildService").invoke(null);
            if (service == null) {
                return false;
            }
            java.lang.reflect.Method guildFor = service.getClass().getMethod("guildFor", UUID.class);
            Optional<?> targetGuild = (Optional<?>) guildFor.invoke(service, target.getUUID());
            if (targetGuild.isEmpty()) {
                return false;
            }
            Object guild = targetGuild.get();
            Object targetGuildName = guild.getClass().getMethod("name").invoke(guild);
            java.lang.reflect.Method warSnapshot = service.getClass().getMethod("warSnapshot", UUID.class);
            Object snapshot = warSnapshot.invoke(service, owner.getUUID());
            if (snapshot == null || !(Boolean) snapshot.getClass().getMethod("active").invoke(snapshot)) {
                return false;
            }
            Object enemyName = snapshot.getClass().getMethod("enemyName").invoke(snapshot);
            return targetGuildName != null && targetGuildName.equals(enemyName);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return false;
        }
    }

    private void fightTarget(ServerLevel level, Mob companion, LivingEntity target, CompanionRecord record) {
        companion.setTarget(target);
        companion.getLookControl().setLookAt(target, 30.0F, 30.0F);
        double distance = companion.distanceToSqr(target);
        if (distance > 3.0D) {
            companion.getNavigation().moveTo(target, roleSpeed(config().tamed_speed + 0.04D, record, role(record)));
            return;
        }
        int cooldown = attackCooldowns.getOrDefault(companion.getUUID(), 0);
        if (cooldown > 0) {
            attackCooldowns.put(companion.getUUID(), cooldown - 1);
            return;
        }
        companion.swing(InteractionHand.MAIN_HAND);
        ItemStack weapon = companion.getItemBySlot(EquipmentSlot.MAINHAND);
        float before = target.getHealth();
        target.hurtServer(level, level.damageSources().mobAttack(companion), companionAttackDamage(weapon, record));
        if (!weapon.isEmpty()) {
            weapon.hurtEnemy(target, companion);
            weapon.postHurtEnemy(target, companion);
        }
        if (before > target.getHealth()) {
            awardXp(companion, record, config().companion_xp_per_hit, "combat");
        }
        if (!target.isAlive()) {
            awardXp(companion, record, config().companion_xp_per_mob_kill, "mob defeated");
        }
        target.knockback(0.18D, companion.getX() - target.getX(), companion.getZ() - target.getZ());
        attackCooldowns.put(companion.getUUID(), config().melee_cooldown_ticks);
    }

    private void tickAmbientSound(ServerLevel level, Mob companion, CompanionRecord record) {
        int cooldown = ambientSoundCooldowns.getOrDefault(companion.getUUID(), randomInt(80, 260));
        if (cooldown > 0) {
            ambientSoundCooldowns.put(companion.getUUID(), cooldown - 1);
            return;
        }
        if (random.nextDouble() < 0.65D) {
            level.playSound(
                    null,
                    companion.getX(),
                    companion.getY(),
                    companion.getZ(),
                    randomCompanionSound(),
                    SoundSource.NEUTRAL,
                    record.tamed ? 0.34F : 0.24F,
                    1.55F + random.nextFloat() * 0.35F);
        }
        ambientSoundCooldowns.put(companion.getUUID(), randomInt(240, 720));
    }

    private SoundEvent randomCompanionSound() {
        return switch (random.nextInt(4)) {
            case 0 -> SoundEvents.VILLAGER_YES;
            case 1 -> SoundEvents.VILLAGER_NO;
            case 2 -> SoundEvents.VILLAGER_TRADE;
            default -> SoundEvents.VILLAGER_AMBIENT;
        };
    }

    private float companionAttackDamage(ItemStack weapon, CompanionRecord record) {
        double damage = Math.max(0.0D, config().tamed_attack_damage);
        if (weapon == null || weapon.isEmpty()) {
            return (float) companionDamageWithProgression(damage, record);
        }
        ItemAttributeModifiers modifiers = weapon.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (modifiers != null) {
            damage = Math.max(damage, modifiers.compute(Attributes.ATTACK_DAMAGE, 0.0D, EquipmentSlot.MAINHAND));
        }
        ItemEnchantments enchantments = weapon.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null && enchantments.size() > 0) {
            int totalLevels = 0;
            for (var entry : enchantments.entrySet()) {
                totalLevels += Math.max(0, entry.getIntValue());
            }
            damage += Math.min(4.0D, totalLevels * 0.35D);
        }
        return (float) Math.max(0.0D, companionDamageWithProgression(damage, record));
    }

    private InteractionResult useItem(Player player, Level world, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isCompanionWhistleItem(stack)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Optional<Mob> companion = recallOwned(serverPlayer);
        if (serverPlayer.isShiftKeyDown() && companion.isPresent()) {
            storageOpener.accept(serverPlayer, companion.get());
        }
        if (world instanceof ServerLevel level) {
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(), SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(0).value(), SoundSource.PLAYERS, 0.5F, 1.8F);
        }
        return InteractionResult.SUCCESS;
    }

    private boolean isCompanionWhistleItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.GOAT_HORN)) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null
                && !customData.isEmpty()
                && COMPANION_WHISTLE_MARKER.equals(customData.copyTag().getStringOr(COMPANION_WHISTLE_TYPE, ""));
    }

    private InteractionResult useBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(world instanceof ServerLevel level)) {
            return InteractionResult.PASS;
        }
        if (hand == InteractionHand.MAIN_HAND && serverPlayer.isShiftKeyDown()) {
            Optional<CompanionRecord> bed = ownedBedRecordNear(serverPlayer, hitResult.getLocation());
            if (bed.isPresent()) {
                return pickUpCompanionBed(serverPlayer, bed.get(), false) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
        }
        ItemStack stack = serverPlayer.getItemInHand(hand);
        if (!isCompanionBedItem(stack)) {
            return InteractionResult.PASS;
        }
        if (!config().companion_beds_enabled) {
            serverPlayer.sendSystemMessage(Component.literal("Companion beds are disabled on this server.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        CompanionRecord record = latestAnyOwnedRecord(serverPlayer).orElse(null);
        if (record == null) {
            serverPlayer.sendSystemMessage(Component.literal("Bond with a companion before placing a Little Companion Bed.").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }

        BlockPos base = hitResult.getBlockPos().relative(hitResult.getDirection());
        if (hitResult.getDirection() != Direction.UP && level.getBlockState(base).isAir()) {
            base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, base);
        }
        if (!level.getWorldBorder().isWithinBounds(base) || !level.getBlockState(base).isAir()) {
            serverPlayer.sendSystemMessage(Component.literal("Place the Little Companion Bed in an open space.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (level.getBlockState(base.below()).isAir()) {
            serverPlayer.sendSystemMessage(Component.literal("The Little Companion Bed needs solid ground under it.").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        Direction facing = serverPlayer.getDirection();
        String color = readCompanionBedColor(stack);
        placeCompanionBed(level, serverPlayer, record, base, facing, color);
        if (!serverPlayer.getAbilities().instabuild) {
            stack.shrink(1);
            serverPlayer.setItemInHand(hand, stack);
        }
        serverPlayer.sendSystemMessage(Component.literal("Placed a Little Companion Bed. Set your companion to stay when you want it to rest there.").withStyle(ChatFormatting.AQUA));
        return InteractionResult.SUCCESS;
    }

    private boolean isCompanionBedItem(ItemStack stack) {
        if (stack == null || stack.isEmpty() || readBedItemColor(stack).isBlank()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        return customData != null
                && !customData.isEmpty()
                && COMPANION_BED_MARKER.equals(customData.copyTag().getStringOr(COMPANION_BED_TYPE, ""));
    }

    private String readCompanionBedColor(ItemStack stack) {
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            String color = customData.copyTag().getStringOr(COMPANION_BED_COLOR, "");
            if (!color.isBlank()) {
                return normalizedBedColor(color);
            }
        }
        return normalizedBedColor(readBedItemColor(stack));
    }

    private String readBedItemColor(ItemStack stack) {
        if (stack.is(Items.WHITE_BED)) return "white";
        if (stack.is(Items.ORANGE_BED)) return "orange";
        if (stack.is(Items.MAGENTA_BED)) return "magenta";
        if (stack.is(Items.LIGHT_BLUE_BED)) return "light_blue";
        if (stack.is(Items.YELLOW_BED)) return "yellow";
        if (stack.is(Items.LIME_BED)) return "lime";
        if (stack.is(Items.PINK_BED)) return "pink";
        if (stack.is(Items.GRAY_BED)) return "gray";
        if (stack.is(Items.LIGHT_GRAY_BED)) return "light_gray";
        if (stack.is(Items.CYAN_BED)) return "cyan";
        if (stack.is(Items.PURPLE_BED)) return "purple";
        if (stack.is(Items.BLUE_BED)) return "blue";
        if (stack.is(Items.BROWN_BED)) return "brown";
        if (stack.is(Items.GREEN_BED)) return "green";
        if (stack.is(Items.RED_BED)) return "red";
        if (stack.is(Items.BLACK_BED)) return "black";
        return "";
    }

    private String normalizedBedColor(String color) {
        String normalized = color == null ? "red" : color.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_').replace(' ', '_');
        return bedColors().contains(normalized) ? normalized : "red";
    }

    private net.minecraft.world.item.Item bedItem(String color) {
        return switch (normalizedBedColor(color)) {
            case "white" -> Items.WHITE_BED;
            case "orange" -> Items.ORANGE_BED;
            case "magenta" -> Items.MAGENTA_BED;
            case "light_blue" -> Items.LIGHT_BLUE_BED;
            case "yellow" -> Items.YELLOW_BED;
            case "lime" -> Items.LIME_BED;
            case "pink" -> Items.PINK_BED;
            case "gray" -> Items.GRAY_BED;
            case "light_gray" -> Items.LIGHT_GRAY_BED;
            case "cyan" -> Items.CYAN_BED;
            case "purple" -> Items.PURPLE_BED;
            case "blue" -> Items.BLUE_BED;
            case "brown" -> Items.BROWN_BED;
            case "green" -> Items.GREEN_BED;
            case "black" -> Items.BLACK_BED;
            default -> Items.RED_BED;
        };
    }

    private InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world, InteractionHand hand, Entity entity, net.minecraft.world.phys.EntityHitResult hitResult) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (entity.entityTags().contains(TAG_BED_DISPLAY)) {
            if (!serverPlayer.isShiftKeyDown()) {
                serverPlayer.sendSystemMessage(Component.literal("Sneak-right-click the Little Companion Bed to pick it up.").withStyle(ChatFormatting.GRAY));
                return InteractionResult.SUCCESS;
            }
            Optional<CompanionRecord> bed = ownedBedRecordForDisplay(serverPlayer, entity);
            if (bed.isPresent()) {
                return pickUpCompanionBed(serverPlayer, bed.get(), false) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
            }
            serverPlayer.sendSystemMessage(Component.literal("That Little Companion Bed is not yours.").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.FAIL;
        }
        if (!(entity instanceof Mob companion) || !isCompanion(entity)) {
            return InteractionResult.PASS;
        }
        CompanionRecord record = record(companion);
        if (record == null) {
            return InteractionResult.PASS;
        }
        ItemStack held = serverPlayer.getItemInHand(hand);
        if (!record.tamed) {
            serverPlayer.sendSystemMessage(Component.literal("This tiny wanderer looks hungry. Drop food nearby and let it pick the food up.").withStyle(ChatFormatting.YELLOW));
            return InteractionResult.SUCCESS;
        }
        if (!serverPlayer.getUUID().toString().equals(record.ownerUuid)) {
            serverPlayer.sendSystemMessage(Component.literal("Little " + record.ownerName + " is already bonded to someone else.").withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        InteractionHand foodHand = foodHand(serverPlayer, hand);
        if (foodHand != null) {
            return healCompanion(serverPlayer, companion, serverPlayer.getItemInHand(foodHand), foodHand);
        }
        if (serverPlayer.isShiftKeyDown()) {
            storageOpener.accept(serverPlayer, companion);
            return InteractionResult.SUCCESS;
        }
        if (held.isEmpty()) {
            CompanionMode next = record.mode == CompanionMode.FOLLOW ? CompanionMode.STAY : CompanionMode.FOLLOW;
            setMode(serverPlayer, companion, next);
            return InteractionResult.SUCCESS;
        }
        serverPlayer.sendSystemMessage(Component.literal("Sneak-right-click this companion to manage gear and storage.").withStyle(ChatFormatting.GRAY));
        return InteractionResult.SUCCESS;
    }

    private InteractionHand foodHand(ServerPlayer player, InteractionHand preferredHand) {
        if (isFood(player.getItemInHand(preferredHand))) {
            return preferredHand;
        }
        InteractionHand otherHand = preferredHand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        return isFood(player.getItemInHand(otherHand)) ? otherHand : null;
    }

    private InteractionResult healCompanion(ServerPlayer player, Mob companion, ItemStack held, InteractionHand hand) {
        if (companion.getHealth() >= companion.getMaxHealth()) {
            player.sendSystemMessage(Component.literal("Your companion is already fully healed.").withStyle(ChatFormatting.GRAY));
            return InteractionResult.SUCCESS;
        }
        int nutrition = foodNutrition(held);
        float before = companion.getHealth();
        float amount = (float) Math.max(config().minimum_heal_amount, nutrition * config().healing_per_food_nutrition);
        companion.heal(amount);
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            player.setItemInHand(hand, held);
        }
        companion.setTarget(null);
        companion.setAggressive(false);
        if (companion.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.HEART, companion.getX(), companion.getY() + 0.8D, companion.getZ(), 5, 0.25D, 0.25D, 0.25D, 0.02D);
            level.playSound(null, companion.getX(), companion.getY(), companion.getZ(), SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL, 0.55F, 1.35F);
        }
        int healed = Mth.ceil(companion.getHealth() - before);
        player.sendSystemMessage(Component.literal("Your companion recovered " + healed + " health.").withStyle(ChatFormatting.GREEN));
        awardXp(companion, record(companion), config().companion_xp_feed, "feeding");
        return InteractionResult.SUCCESS;
    }

    public boolean equipFromHand(ServerPlayer player, Mob companion, InteractionHand hand, ItemStack held) {
        EquipmentSlot slot = equipmentSlotFor(held);
        if (slot == null) {
            return false;
        }
        ItemStack equipped = held.copyWithCount(1);
        held.shrink(1);
        ItemStack previous = companion.getItemBySlot(slot).copy();
        CompanionRecord record = record(companion);
        if (slot == EquipmentSlot.HEAD) {
            if (record != null) {
                record.hiddenHelmet = ItemRecord.fromStack(equipped, player.level().getServer().registryAccess());
                record.updatedAt = System.currentTimeMillis();
            }
            companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(player));
            setManagedEquipmentDropChances(companion);
            player.setItemInHand(hand, held);
            player.sendSystemMessage(Component.literal("Stored companion helmet. Open the companion screen to remove it.").withStyle(ChatFormatting.GREEN));
            save();
            return true;
        }
        companion.setItemSlot(slot, equipped);
        setManagedEquipmentDropChances(companion);
        if (!previous.isEmpty() && !(slot == EquipmentSlot.HEAD && previous.is(Items.PLAYER_HEAD))) {
            giveOrDrop(player, previous);
        }
        player.setItemInHand(hand, held);
        player.sendSystemMessage(Component.literal("Equipped companion " + slot.getName() + ".").withStyle(ChatFormatting.GREEN));
        return true;
    }

    public EquipmentSlot equipmentSlotFor(ItemStack stack) {
        if (stack.is(Items.PLAYER_HEAD)) {
            return null;
        }
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null) {
            EquipmentSlot slot = equippable.slot();
            if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                return slot;
            }
        }
        if (stack.get(DataComponents.WEAPON) != null || isWeaponItem(stack)) {
            return EquipmentSlot.MAINHAND;
        }
        if (stack.is(Items.SHIELD) || stack.is(Items.TOTEM_OF_UNDYING)) {
            return EquipmentSlot.OFFHAND;
        }
        return null;
    }

    private boolean isWeaponItem(ItemStack stack) {
        return stack.is(Items.WOODEN_SWORD) || stack.is(Items.STONE_SWORD) || stack.is(Items.COPPER_SWORD)
                || stack.is(Items.IRON_SWORD) || stack.is(Items.GOLDEN_SWORD) || stack.is(Items.DIAMOND_SWORD)
                || stack.is(Items.NETHERITE_SWORD) || stack.is(Items.WOODEN_AXE) || stack.is(Items.STONE_AXE)
                || stack.is(Items.COPPER_AXE) || stack.is(Items.IRON_AXE) || stack.is(Items.GOLDEN_AXE)
                || stack.is(Items.DIAMOND_AXE) || stack.is(Items.NETHERITE_AXE) || stack.is(Items.TRIDENT)
                || stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
    }

    private void normalizeCompanion(Mob companion, CompanionRecord record) {
        companion.setPersistenceRequired();
        companion.setSilent(true);
        companion.setCanPickUpLoot(false);
        setManagedEquipmentDropChances(companion);
        companion.removeAllGoals(goal -> true);
        setTinyNpcShape(companion);
        syncCompanionName(companion, record);
        companion.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false));
        if (companion.getAttribute(Attributes.SCALE) != null) {
            companion.getAttribute(Attributes.SCALE).setBaseValue(record.tamed ? config().tamed_scale : config().wild_scale);
        }
        if (companion.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            companion.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(record.tamed ? roleSpeed(config().tamed_speed, record, role(record)) : config().wild_speed);
        }
        if (companion.getAttribute(Attributes.MAX_HEALTH) != null) {
            float healthBefore = companion.getHealth();
            companion.getAttribute(Attributes.MAX_HEALTH).setBaseValue(record.tamed ? companionHealthWithProgression(config().tamed_max_health, record) : 12.0D);
            if (companion.getHealth() > companion.getMaxHealth()) {
                companion.setHealth(companion.getMaxHealth());
            } else if (companion.isAlive() && healthBefore > companion.getHealth()) {
                companion.setHealth(Mth.clamp(healthBefore, 1.0F, companion.getMaxHealth()));
            }
        }
        if (companion.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            companion.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0D);
        }
        if (companion.getAttribute(Attributes.FOLLOW_RANGE) != null) {
            companion.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(record.tamed ? 32.0D : 12.0D);
        }
    }

    private void restoreHealth(Mob companion, CompanionRecord record, float preferredHealth, boolean reviveFull) {
        float maxHealth = companion.getMaxHealth();
        float restored = reviveFull || preferredHealth <= 0.0F ? maxHealth : preferredHealth;
        companion.setHealth(Mth.clamp(restored, 1.0F, maxHealth));
        if (!record.dead) {
            record.health = companion.getHealth();
        }
    }

    private void setTinyNpcShape(Mob companion) {
        if (companion instanceof Zombie zombie) {
            zombie.setBaby(true);
            zombie.setCanBreakDoors(false);
            return;
        }
        if (companion instanceof AgeableMob ageable) {
            ageable.setBaby(true);
        }
    }

    private void syncCompanionName(Mob companion, CompanionRecord record) {
        String name = record.tamed
                ? "Little " + (record.ownerName == null || record.ownerName.isBlank() ? "Companion" : record.ownerName)
                : "Little Wanderer";
        ChatFormatting color = record.tamed ? ChatFormatting.AQUA : ChatFormatting.GREEN;
        if (companion.getCustomName() == null || !name.equals(companion.getCustomName().getString())) {
            companion.setCustomName(Component.literal(name).withStyle(color));
        }
        companion.setCustomNameVisible(true);
    }

    private ServerPlayer resolveOwner(MinecraftServer server, CompanionRecord record) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(record.ownerUuid));
        } catch (Exception ignored) {
            return null;
        }
    }

    private ServerPlayer resolveFeeder(ServerLevel level, ItemEntity food) {
        Entity owner = food.getOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            return serverPlayer;
        }
        return level.getEntitiesOfClass(ServerPlayer.class, food.getBoundingBox().inflate(6.0D), player -> !player.isSpectator()).stream()
                .min(Comparator.comparingDouble(food::distanceToSqr))
                .orElse(null);
    }

    private boolean isFood(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.get(DataComponents.FOOD) != null;
    }

    private int foodNutrition(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 1 : Math.max(1, food.nutrition());
    }

    private int nearbyWildCount(ServerLevel level, BlockPos center, int radius) {
        AABB area = new AABB(center).inflate(radius);
        return level.getEntitiesOfClass(Mob.class, area, companion -> isCompanion(companion) && companion.entityTags().contains(TAG_WILD)).size();
    }

    private BlockPos findSpawnNear(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 10; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            int distance = randomInt(config().spawn_min_distance, config().spawn_max_distance);
            int x = player.getBlockX() + Mth.floor(Math.cos(angle) * distance);
            int z = player.getBlockZ() + Mth.floor(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos) && isGoodSpawnArea(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    private boolean isGoodSpawnArea(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSkyFromBelowWater(pos) || !level.getBlockState(pos).isAir()) {
            return false;
        }
        BlockPos ground = pos.below();
        boolean plainsLike = level.getBlockState(ground).is(Blocks.GRASS_BLOCK) || level.getBlockState(ground).is(Blocks.DIRT_PATH);
        return plainsLike || nearVillageBlock(level, pos);
    }

    private boolean nearVillageBlock(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-24, -4, -24), center.offset(24, 4, 24))) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos).is(Blocks.BELL) || level.getBlockState(pos).is(BlockTags.BEDS)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack steveHead() {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved("Steve"));
        return stack;
    }

    private ItemStack ownerHead(ServerPlayer player) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        stack.set(DataComponents.PROFILE, ResolvableProfile.createResolved(player.getGameProfile()));
        return stack;
    }

    private ItemStack ownerNameHead(CompanionRecord record) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        String name = record.ownerName == null || record.ownerName.isBlank() ? "Steve" : record.ownerName;
        stack.set(DataComponents.PROFILE, ResolvableProfile.createUnresolved(name));
        return stack;
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private void save() {
        CompanionDataStore store = dataStore.get();
        if (store != null) {
            store.save();
        }
    }

    private int randomInt(int minInclusive, int maxInclusive) {
        int min = Math.min(minInclusive, maxInclusive);
        int max = Math.max(minInclusive, maxInclusive);
        return min + random.nextInt(max - min + 1);
    }

    private record ClaimBounds(int minX, int minZ, int maxX, int maxZ) {
    }

    private static final class OwnerFollowRequest {
        private int remainingTicks;
        private final boolean allowSameLevelMove;
        private final String liveMessage;
        private final String rebuildMessage;

        private OwnerFollowRequest(int remainingTicks, boolean allowSameLevelMove, String liveMessage, String rebuildMessage) {
            this.remainingTicks = remainingTicks;
            this.allowSameLevelMove = allowSameLevelMove;
            this.liveMessage = liveMessage;
            this.rebuildMessage = rebuildMessage;
        }
    }

    private record OwnerSnapshot(String dimension, double x, double y, double z) {
        private double distanceToSqr(OwnerSnapshot other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private record RespawnEgg(UUID recordUuid, String ownerUuid, String eggId, long readyAt) {
    }
}

