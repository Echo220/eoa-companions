package com.echoesofaegis.companions.gui;

import com.echoesofaegis.companions.companion.CompanionManager;
import com.echoesofaegis.companions.data.CompanionDataStore;
import com.echoesofaegis.companions.data.CompanionMode;
import com.echoesofaegis.companions.data.CompanionRecord;
import com.echoesofaegis.companions.data.CompanionRole;
import com.echoesofaegis.companions.data.ItemRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

public final class CompanionStorageGui {
    private static final String UI_ITEM_KEY = "echoes_companion_ui_item";
    private static final String UI_ITEM_VALUE = "menu_only";
    private static final int CONTAINER_ROWS = 6;
    private static final int CONTAINER_SLOTS = 54;
    private static final int FOLLOW_BUTTON = 0;
    private static final int RECALL_BUTTON = 1;
    private static final int STAY_BUTTON = 2;
    private static final int HEALTH_STATUS = 3;
    private static final int PREVIEW_SLOT = 4;
    private static final int ATTACK_STATUS = 5;
    private static final int SHIELD_STATUS = 6;
    private static final int BED_STATUS = 7;
    private static final int HELP_SLOT = 8;
    private static final int SCOUT_BUTTON = 9;
    private static final int PASSIVE_BUTTON = 10;
    private static final int AGGRESSIVE_BUTTON = 11;
    private static final int GUARD_BUTTON = 12;
    private static final int HELMET_SLOT = 13;
    private static final int MAINHAND_SLOT = 21;
    private static final int CHEST_SLOT = 22;
    private static final int OFFHAND_SLOT = 23;
    private static final int LEGS_SLOT = 31;
    private static final int BOOTS_SLOT = 40;
    private static final int STORAGE_SLOTS = 9;
    private static final int STORAGE_START = 45;
    private static final int STORAGE_END = STORAGE_START + STORAGE_SLOTS;

    private final Supplier<CompanionManager> companionManager;
    private final Supplier<CompanionDataStore> dataStore;

    public CompanionStorageGui(Supplier<CompanionManager> companionManager, Supplier<CompanionDataStore> dataStore) {
        this.companionManager = companionManager;
        this.dataStore = dataStore;
    }

    public void open(ServerPlayer player, Mob companion) {
        CompanionDataStore store = dataStore.get();
        CompanionManager manager = companionManager.get();
        if (store == null || manager == null) {
            player.sendSystemMessage(Component.literal("Companion storage is not ready yet.").withStyle(ChatFormatting.RED));
            return;
        }
        CompanionRecord record = store.get(companion.getUUID());
        if (record == null || !record.tamed || !player.getUUID().toString().equals(record.ownerUuid)) {
            player.sendSystemMessage(Component.literal("That companion is not bonded to you.").withStyle(ChatFormatting.RED));
            return;
        }
        if (!manager.config().allow_storage) {
            player.sendSystemMessage(Component.literal("Companion storage is disabled on this server.").withStyle(ChatFormatting.RED));
            return;
        }
        cleanupPlayerUiItems(player);

        CompanionContainer inventory = new CompanionContainer(manager);
        record.normalized();
        HolderLookup.Provider registries = player.level().getServer().registryAccess();
        fillLockedSlots(inventory, companion, record, manager);
        fillEquipmentSlots(inventory, companion, record, registries);
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            ItemStack stack = record.storage.get(slot).toStack(registries);
            inventory.setItem(STORAGE_START + slot, isCompanionUiItem(stack) ? ItemStack.EMPTY : stack);
        }
        String title = "Little " + (record.ownerName == null || record.ownerName.isBlank() ? "Companion" : record.ownerName);
        player.openMenu(new StorageScreenFactory(Component.literal(title), inventory, companion.getUUID(), store, manager, registries));
    }

    private static void fillLockedSlots(SimpleContainer inventory, Mob companion, CompanionRecord record, CompanionManager manager) {
        CompanionMode mode = record.mode == null ? CompanionMode.FOLLOW : record.mode;
        CompanionRole role = manager.role(record);
        for (int slot = 0; slot < CONTAINER_SLOTS; slot++) {
            if (isLockedSlot(slot)) {
                inventory.setItem(slot, button(Items.BLACK_STAINED_GLASS_PANE, " ", ChatFormatting.DARK_GRAY));
            }
        }
        inventory.setItem(FOLLOW_BUTTON, button(
                role == CompanionRole.DEFENDER ? Items.LIME_DYE : Items.SHIELD,
                role == CompanionRole.DEFENDER ? "Defender" : "Defender",
                role == CompanionRole.DEFENDER ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                "Balanced follow and protect behavior."));
        inventory.setItem(RECALL_BUTTON, button(
                Items.ENDER_PEARL,
                "Recall",
                ChatFormatting.AQUA,
                "Click to call your companion back to you."));
        inventory.setItem(STAY_BUTTON, button(
                role == CompanionRole.STAY || mode == CompanionMode.STAY ? Items.LIME_DYE : Items.GRAY_DYE,
                role == CompanionRole.STAY || mode == CompanionMode.STAY ? "Staying" : "Stay",
                role == CompanionRole.STAY || mode == CompanionMode.STAY ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                mode == CompanionMode.STAY ? "Your companion will wait here." : "Click to make your companion wait."));
        inventory.setItem(HEALTH_STATUS, status(Items.RED_DYE, "Level", levelLore(record, manager), healthLore(companion), "Trait: " + manager.personality(record).label()));
        inventory.setItem(PREVIEW_SLOT, preview(record, companion, manager));
        ItemStack mainhand = companion == null ? ItemStack.EMPTY : companion.getItemBySlot(EquipmentSlot.MAINHAND);
        double armor = companion == null ? 0.0D : manager.armorScore(companion);
        inventory.setItem(ATTACK_STATUS, status(Items.IRON_SWORD, "Damage", "Estimated: " + decimal(manager.estimatedDamage(record, mainhand)), "Weapons and levels both count."));
        inventory.setItem(SHIELD_STATUS, status(Items.SHIELD, "Defense", "Armor: " + decimal(armor), "Shield block: " + percent(manager.shieldBlockChance(record))));
        inventory.setItem(BED_STATUS, status(
                hasBed(record) ? bedIcon(record.bedColor) : Items.WHITE_BED,
                hasBed(record) ? "Bed Set" : "No Bed",
                hasBed(record) ? "Color: " + formatColor(record.bedColor) : "Craft one with a red bed, stick, and dye.",
                hasBed(record) ? "Stay mode can rest here." : "Use /companions bed to get one."));
        inventory.setItem(HELP_SLOT, status(Items.BOOK, "Paper Doll", "Gear slots are arranged like player gear.", "Bottom row is companion storage."));
        inventory.setItem(SCOUT_BUTTON, roleButton(role, CompanionRole.SCOUT, Items.SPYGLASS, "Faster follow, avoids most fights."));
        inventory.setItem(PASSIVE_BUTTON, roleButton(role, CompanionRole.PASSIVE, Items.POPPY, "Follows without attacking."));
        inventory.setItem(AGGRESSIVE_BUTTON, roleButton(role, CompanionRole.AGGRESSIVE, Items.IRON_AXE, "Longer combat reach and more damage."));
        inventory.setItem(GUARD_BUTTON, roleButton(role, CompanionRole.GUARD_CLAIM, Items.BELL, "Guards the current claim area."));
    }

    private static void fillEquipmentSlots(SimpleContainer inventory, Mob companion, CompanionRecord record, HolderLookup.Provider registries) {
        ItemStack hiddenHelmet = record.hiddenHelmet.toStack(registries);
        ItemStack visibleHead = companion.getItemBySlot(EquipmentSlot.HEAD).copy();
        inventory.setItem(HELMET_SLOT, hiddenHelmet.isEmpty() && !visibleHead.isEmpty() && !visibleHead.is(Items.PLAYER_HEAD) ? visibleHead : hiddenHelmet);
        inventory.setItem(CHEST_SLOT, companion.getItemBySlot(EquipmentSlot.CHEST).copy());
        inventory.setItem(LEGS_SLOT, companion.getItemBySlot(EquipmentSlot.LEGS).copy());
        inventory.setItem(BOOTS_SLOT, companion.getItemBySlot(EquipmentSlot.FEET).copy());
        inventory.setItem(MAINHAND_SLOT, companion.getItemBySlot(EquipmentSlot.MAINHAND).copy());
        inventory.setItem(OFFHAND_SLOT, companion.getItemBySlot(EquipmentSlot.OFFHAND).copy());
    }

    private static ItemStack button(net.minecraft.world.level.ItemLike item, String name, ChatFormatting color, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name).withStyle(color));
        if (loreLines.length > 0) {
            List<Component> lore = new ArrayList<>();
            for (String loreLine : loreLines) {
                lore.add(Component.literal(loreLine).withStyle(ChatFormatting.GRAY));
            }
            stack.set(DataComponents.LORE, new ItemLore(lore));
        }
        return markUiItem(stack);
    }

    private static ItemStack status(net.minecraft.world.level.ItemLike item, String name, String... loreLines) {
        return button(item, name, ChatFormatting.AQUA, loreLines);
    }

    private static ItemStack roleButton(CompanionRole current, CompanionRole role, net.minecraft.world.level.ItemLike item, String lore) {
        return button(
                current == role ? Items.LIME_DYE : item,
                role.label(),
                current == role ? ChatFormatting.GREEN : ChatFormatting.YELLOW,
                lore);
    }

    private static ItemStack preview(CompanionRecord record, Mob companion, CompanionManager manager) {
        ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
        String ownerName = record.ownerName == null || record.ownerName.isBlank() ? "Steve" : record.ownerName;
        stack.set(DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createUnresolved(ownerName));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Little " + ownerName).withStyle(ChatFormatting.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(healthLore(companion)).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(levelLore(record, manager)).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Role: " + manager.role(record).label()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Trait: " + manager.personality(record).label()).withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal(hasBed(record) ? "Bed: set" : "Bed: not set").withStyle(ChatFormatting.GRAY));
        lore.add(Component.literal("Respawn: " + manager.respawnStatusText(record)).withStyle(ChatFormatting.GRAY));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return markUiItem(stack);
    }

    private static ItemStack markUiItem(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString(UI_ITEM_KEY, UI_ITEM_VALUE);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        return stack;
    }

    private static boolean isCompanionUiItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty() && UI_ITEM_VALUE.equals(customData.copyTag().getStringOr(UI_ITEM_KEY, ""))) {
            return true;
        }
        return stack.is(Items.BLACK_STAINED_GLASS_PANE) && stack.has(DataComponents.CUSTOM_NAME);
    }

    private static void cleanupPlayerUiItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (isCompanionUiItem(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static String levelLore(CompanionRecord record, CompanionManager manager) {
        return "Level " + Math.max(1, record.level) + " | XP " + Math.max(0L, record.xp) + "/" + manager.xpForNextLevel(record);
    }

    private static String healthLore(Mob companion) {
        if (companion == null) {
            return "Health updates while nearby.";
        }
        return "HP: " + Math.round(companion.getHealth()) + " / " + Math.round(companion.getMaxHealth());
    }

    private static boolean hasBed(CompanionRecord record) {
        return record.bedDimension != null && !record.bedDimension.isBlank();
    }

    private static net.minecraft.world.level.ItemLike bedIcon(String color) {
        return switch (color == null ? "red" : color) {
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

    private static String formatColor(String color) {
        if (color == null || color.isBlank()) {
            return "Red";
        }
        String[] parts = color.split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.isEmpty() ? "Red" : builder.toString();
    }

    private static String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return Math.round(value * 100.0D) + "%";
    }

    private static boolean isLockedSlot(int slot) {
        return slot >= 0 && slot < CONTAINER_SLOTS && !isEquipmentContainerSlot(slot) && !isStorageContainerSlot(slot);
    }

    private static boolean isStorageContainerSlot(int slot) {
        return slot >= STORAGE_START && slot < STORAGE_END;
    }

    private static boolean isEquipmentContainerSlot(int slot) {
        return slot == HELMET_SLOT
                || slot == CHEST_SLOT
                || slot == LEGS_SLOT
                || slot == BOOTS_SLOT
                || slot == MAINHAND_SLOT
                || slot == OFFHAND_SLOT;
    }

    private static EquipmentSlot equipmentSlotForContainerSlot(int slot) {
        return switch (slot) {
            case HELMET_SLOT -> EquipmentSlot.HEAD;
            case CHEST_SLOT -> EquipmentSlot.CHEST;
            case LEGS_SLOT -> EquipmentSlot.LEGS;
            case BOOTS_SLOT -> EquipmentSlot.FEET;
            case MAINHAND_SLOT -> EquipmentSlot.MAINHAND;
            case OFFHAND_SLOT -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static int containerSlotForEquipmentSlot(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> HELMET_SLOT;
            case CHEST -> CHEST_SLOT;
            case LEGS -> LEGS_SLOT;
            case FEET -> BOOTS_SLOT;
            case MAINHAND -> MAINHAND_SLOT;
            case OFFHAND -> OFFHAND_SLOT;
            default -> -1;
        };
    }

    private record StorageScreenFactory(Component title, CompanionContainer inventory, java.util.UUID companionUuid, CompanionDataStore store, CompanionManager manager, HolderLookup.Provider registries) implements MenuProvider {
        @Override
        public Component getDisplayName() {
            return title;
        }

        @Override
        public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
            return new StorageScreenHandler(syncId, playerInventory, inventory, companionUuid, store, manager, registries);
        }
    }

    private static final class CompanionContainer extends SimpleContainer {
        private final CompanionManager manager;

        private CompanionContainer(CompanionManager manager) {
            super(CONTAINER_SLOTS);
            this.manager = manager;
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            if (isLockedSlot(slot) || isCompanionUiItem(stack)) {
                return false;
            }
            EquipmentSlot equipmentSlot = equipmentSlotForContainerSlot(slot);
            if (equipmentSlot != null) {
                return manager.equipmentSlotFor(stack) == equipmentSlot;
            }
            return isStorageContainerSlot(slot);
        }

        @Override
        public boolean canTakeItem(Container target, int slot, ItemStack stack) {
            return !isLockedSlot(slot) && !isCompanionUiItem(stack);
        }
    }

    private static final class ReadOnlySlot extends Slot {
        private ReadOnlySlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }

    private static final class ManagedCompanionSlot extends Slot {
        private final CompanionContainer companionContainer;

        private ManagedCompanionSlot(CompanionContainer companionContainer, int slot, int x, int y) {
            super(companionContainer, slot, x, y);
            this.companionContainer = companionContainer;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return companionContainer.canPlaceItem(getContainerSlot(), stack);
        }
    }

    private static final class StorageScreenHandler extends AbstractContainerMenu {
        private final CompanionContainer storage;
        private final java.util.UUID companionUuid;
        private final CompanionDataStore store;
        private final CompanionManager manager;
        private final HolderLookup.Provider registries;

        private StorageScreenHandler(int syncId, Inventory playerInventory, CompanionContainer storage, java.util.UUID companionUuid, CompanionDataStore store, CompanionManager manager, HolderLookup.Provider registries) {
            super(MenuType.GENERIC_9x6, syncId);
            this.storage = storage;
            this.companionUuid = companionUuid;
            this.store = store;
            this.manager = manager;
            this.registries = registries;
            addCompanionSlots(storage);
            addPlayerSlots(playerInventory);
        }

        private void addCompanionSlots(CompanionContainer storage) {
            for (int row = 0; row < CONTAINER_ROWS; row++) {
                for (int column = 0; column < 9; column++) {
                    int slot = column + row * 9;
                    int x = 8 + column * 18;
                    int y = 18 + row * 18;
                    if (isLockedSlot(slot)) {
                        addSlot(new ReadOnlySlot(storage, slot, x, y));
                    } else {
                        addSlot(new ManagedCompanionSlot(storage, slot, x, y));
                    }
                }
            }
        }

        private void addPlayerSlots(Inventory playerInventory) {
            int playerInventoryOffset = (CONTAINER_ROWS - 4) * 18;
            for (int row = 0; row < 3; row++) {
                for (int column = 0; column < 9; column++) {
                    addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + playerInventoryOffset));
                }
            }
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column, 8 + column * 18, 161 + playerInventoryOffset));
            }
        }

        @Override
        public void clicked(int slotId, int button, ContainerInput input, Player player) {
            cleanupUiItems(player);
            if (isControlSlot(slotId)) {
                if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                    handleControl(slotId, player);
                }
                cleanupUiItems(player);
                return;
            }
            if (isLockedSlot(slotId)) {
                cleanupUiItems(player);
                return;
            }
            super.clicked(slotId, button, input, player);
            cleanupUiItems(player);
        }

        private boolean isControlSlot(int slotId) {
            return slotId == FOLLOW_BUTTON
                    || slotId == RECALL_BUTTON
                    || slotId == STAY_BUTTON
                    || slotId == SCOUT_BUTTON
                    || slotId == PASSIVE_BUTTON
                    || slotId == AGGRESSIVE_BUTTON
                    || slotId == GUARD_BUTTON;
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            if (index < 0 || index >= slots.size()) {
                return ItemStack.EMPTY;
            }
            Slot slot = slots.get(index);
            if (!slot.hasItem() || isLockedSlot(index)) {
                return ItemStack.EMPTY;
            }

            ItemStack stack = slot.getItem();
            if (isCompanionUiItem(stack)) {
                slot.set(ItemStack.EMPTY);
                cleanupUiItems(player);
                return ItemStack.EMPTY;
            }
            ItemStack original = stack.copy();
            if (index < CONTAINER_SLOTS) {
                if (!moveItemStackTo(stack, CONTAINER_SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                EquipmentSlot equipmentSlot = manager.equipmentSlotFor(stack);
                if (equipmentSlot != null) {
                    int targetSlot = containerSlotForEquipmentSlot(equipmentSlot);
                    if (targetSlot >= 0 && moveItemStackTo(stack, targetSlot, targetSlot + 1, false)) {
                        finishQuickMove(slot, player, stack);
                        return original;
                    }
                }
                if (!moveItemStackTo(stack, STORAGE_START, STORAGE_END, false)) {
                    return ItemStack.EMPTY;
                }
            }

            finishQuickMove(slot, player, stack);
            cleanupUiItems(player);
            return original;
        }

        @Override
        public boolean canDragTo(Slot slot) {
            return (slot.container != storage || !isLockedSlot(slot.getContainerSlot())) && super.canDragTo(slot);
        }

        @Override
        public boolean stillValid(Player player) {
            return storage.stillValid(player);
        }

        private void finishQuickMove(Slot slot, Player player, ItemStack stack) {
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            slot.onTake(player, stack);
        }

        private void handleControl(int slotId, Player player) {
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            CompanionRecord record = store.get(companionUuid);
            if (record == null || !serverPlayer.getUUID().toString().equals(record.ownerUuid)) {
                serverPlayer.sendSystemMessage(Component.literal("That companion is not bonded to you.").withStyle(ChatFormatting.RED));
                return;
            }
            if (slotId == FOLLOW_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.DEFENDER);
            } else if (slotId == STAY_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.STAY);
            } else if (slotId == SCOUT_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.SCOUT);
            } else if (slotId == PASSIVE_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.PASSIVE);
            } else if (slotId == AGGRESSIVE_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.AGGRESSIVE);
            } else if (slotId == GUARD_BUTTON) {
                setRole(serverPlayer, record, CompanionRole.GUARD_CLAIM);
            } else if (slotId == RECALL_BUTTON) {
                recall(serverPlayer);
            }
            fillLockedSlots(storage, liveCompanion(player), record.normalized(), manager);
            broadcastChanges();
        }

        private Mob liveCompanion(Player player) {
            Entity entity = player.level().getEntityInAnyDimension(companionUuid);
            return entity instanceof Mob mob && manager.isCompanion(mob) ? mob : null;
        }

        private void setMode(ServerPlayer player, CompanionRecord record, CompanionMode mode) {
            record.mode = mode;
            record.updatedAt = System.currentTimeMillis();
            store.save();
            player.sendSystemMessage(Component.literal(record.ownerName + "'s companion is now set to " + mode.name().toLowerCase() + ".").withStyle(ChatFormatting.GREEN));
        }

        private void setRole(ServerPlayer player, CompanionRecord record, CompanionRole role) {
            Entity entity = player.level().getEntityInAnyDimension(companionUuid);
            if (entity instanceof Mob companion && manager.isCompanion(companion)) {
                manager.setRole(player, companion, role);
            } else {
                record.role = role.id();
                record.mode = role == CompanionRole.STAY ? CompanionMode.STAY : CompanionMode.FOLLOW;
                if (role == CompanionRole.GUARD_CLAIM && (record.guardDimension == null || record.guardDimension.isBlank())) {
                    record.guardDimension = record.lastDimension == null || record.lastDimension.isBlank() ? player.level().dimension().identifier().toString() : record.lastDimension;
                    record.guardX = record.lastX;
                    record.guardY = record.lastY;
                    record.guardZ = record.lastZ;
                }
                record.updatedAt = System.currentTimeMillis();
                store.save();
                player.sendSystemMessage(Component.literal(record.ownerName + "'s companion role is now " + role.label() + ".").withStyle(ChatFormatting.GREEN));
            }
        }

        private void recall(ServerPlayer player) {
            manager.recallOwned(player);
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            CompanionRecord record = store.get(companionUuid);
            if (record == null) {
                return;
            }
            if (player instanceof ServerPlayer serverPlayer) {
                saveEquipment(serverPlayer, record);
                cleanupUiItems(serverPlayer);
            }
            List<ItemRecord> items = new ArrayList<>();
            for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
                ItemStack stack = storage.getItem(STORAGE_START + slot);
                if (isCompanionUiItem(stack)) {
                    stack = ItemStack.EMPTY;
                    storage.setItem(STORAGE_START + slot, ItemStack.EMPTY);
                }
                items.add(ItemRecord.fromStack(stack, registries));
            }
            record.storage = items;
            record.updatedAt = System.currentTimeMillis();
            store.save();
        }

        private void saveEquipment(ServerPlayer player, CompanionRecord record) {
            Entity entity = player.level().getEntityInAnyDimension(companionUuid);
            if (!(entity instanceof Mob companion) || !manager.isCompanion(companion) || companion.level() != player.level()) {
                returnEquipmentToPlayer(player);
                return;
            }
            applyEquipment(player, companion, record, EquipmentSlot.HEAD, HELMET_SLOT);
            applyEquipment(player, companion, record, EquipmentSlot.CHEST, CHEST_SLOT);
            applyEquipment(player, companion, record, EquipmentSlot.LEGS, LEGS_SLOT);
            applyEquipment(player, companion, record, EquipmentSlot.FEET, BOOTS_SLOT);
            applyEquipment(player, companion, record, EquipmentSlot.MAINHAND, MAINHAND_SLOT);
            applyEquipment(player, companion, record, EquipmentSlot.OFFHAND, OFFHAND_SLOT);
        }

        private void applyEquipment(ServerPlayer player, Mob companion, CompanionRecord record, EquipmentSlot equipmentSlot, int containerSlot) {
            ItemStack stack = storage.getItem(containerSlot).copy();
            if (isCompanionUiItem(stack)) {
                storage.setItem(containerSlot, ItemStack.EMPTY);
                return;
            }
            if (stack.isEmpty()) {
                if (equipmentSlot == EquipmentSlot.HEAD) {
                    record.hiddenHelmet = ItemRecord.empty();
                    companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(player));
                    companion.setDropChance(EquipmentSlot.HEAD, 0.0F);
                } else {
                    companion.setItemSlot(equipmentSlot, ItemStack.EMPTY);
                    companion.setDropChance(equipmentSlot, 0.0F);
                }
                return;
            }
            if (manager.equipmentSlotFor(stack) != equipmentSlot) {
                giveOrDrop(player, stack);
                storage.setItem(containerSlot, ItemStack.EMPTY);
                if (equipmentSlot == EquipmentSlot.HEAD) {
                    record.hiddenHelmet = ItemRecord.empty();
                    companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(player));
                    companion.setDropChance(EquipmentSlot.HEAD, 0.0F);
                }
                return;
            }
            if (equipmentSlot == EquipmentSlot.HEAD) {
                record.hiddenHelmet = ItemRecord.fromStack(stack, registries);
                companion.setItemSlot(EquipmentSlot.HEAD, ownerHead(player));
                companion.setDropChance(EquipmentSlot.HEAD, 0.0F);
                return;
            }
            companion.setItemSlot(equipmentSlot, stack);
            companion.setDropChance(equipmentSlot, 0.0F);
        }

        private void returnEquipmentToPlayer(ServerPlayer player) {
            int[] equipmentSlots = {HELMET_SLOT, CHEST_SLOT, LEGS_SLOT, BOOTS_SLOT, MAINHAND_SLOT, OFFHAND_SLOT};
            for (int slot : equipmentSlots) {
                ItemStack stack = storage.getItem(slot);
                if (!stack.isEmpty()) {
                    if (!isCompanionUiItem(stack)) {
                        giveOrDrop(player, stack.copy());
                    }
                    storage.setItem(slot, ItemStack.EMPTY);
                }
            }
        }

        private static ItemStack ownerHead(ServerPlayer player) {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, net.minecraft.world.item.component.ResolvableProfile.createResolved(player.getGameProfile()));
            return stack;
        }

        private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
            if (isCompanionUiItem(stack)) {
                return;
            }
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        private void cleanupUiItems(Player player) {
            if (isCompanionUiItem(getCarried())) {
                setCarried(ItemStack.EMPTY);
            }
            cleanupPlayerUiItems(player);
            for (int slot = 0; slot < storage.getContainerSize(); slot++) {
                if (!isLockedSlot(slot) && isCompanionUiItem(storage.getItem(slot))) {
                    storage.setItem(slot, ItemStack.EMPTY);
                }
            }
        }
    }
}

