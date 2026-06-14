package com.echoesofaegis.companions.mixin;

import com.echoesofaegis.companions.companion.CompanionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Monster.class)
public abstract class MonsterSleepMixin {
    @Inject(method = "isPreventingPlayerRest", at = @At("HEAD"), cancellable = true)
    private void echoescompanions$allowTaggedCompanionsNearBeds(ServerLevel level, Player player, CallbackInfoReturnable<Boolean> cir) {
        Monster monster = (Monster) (Object) this;
        if (monster.entityTags().contains(CompanionManager.TAG_COMPANION)) {
            cir.setReturnValue(false);
        }
    }
}
