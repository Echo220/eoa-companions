package com.echoesofaegis.companions.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ItemRecord {
    public String id = "minecraft:air";
    public int count = 0;
    public String data = "";

    public static ItemRecord empty() {
        return new ItemRecord();
    }

    public static ItemRecord fromStack(ItemStack stack, HolderLookup.Provider registries) {
        ItemRecord record = new ItemRecord();
        if (stack == null || stack.isEmpty()) {
            return record;
        }
        record.id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        record.count = Math.max(1, Math.min(stack.getCount(), stack.getMaxStackSize()));
        try {
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            record.data = ItemStack.CODEC.encodeStart(ops, stack.copy()).getOrThrow().toString();
        } catch (Exception ignored) {
            record.data = "";
        }
        return record;
    }

    public ItemStack toStack(HolderLookup.Provider registries) {
        if (data != null && !data.isBlank()) {
            try {
                RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
                ItemStack stack = ItemStack.CODEC.parse(ops, TagParser.create(NbtOps.INSTANCE).parseFully(data)).getOrThrow();
                return stack.isEmpty() ? legacyStack() : stack;
            } catch (Exception ignored) {
                return legacyStack();
            }
        }
        return legacyStack();
    }

    private ItemStack legacyStack() {
        if (count <= 0 || id == null || id.isBlank() || "minecraft:air".equals(id)) {
            return ItemStack.EMPTY;
        }
        try {
            Identifier identifier = Identifier.parse(id);
            Item item = BuiltInRegistries.ITEM.getOptional(identifier).orElse(Items.AIR);
            if (item == Items.AIR) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item, Math.max(1, Math.min(count, item.getDefaultMaxStackSize())));
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
    }
}
