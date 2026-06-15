package com.echoesofaegis.companions.mixin;

import com.echoesofaegis.companions.companion.CompanionManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VillagerHostilesSensor.class)
public abstract class VillagerHostilesSensorMixin {
    @Inject(method = "isMatchingEntity", at = @At("HEAD"), cancellable = true)
    private void echoescompanions$ignoreTaggedCompanionHostiles(
            ServerLevel level,
            LivingEntity villager,
            LivingEntity target,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (CompanionManager.hasCompanionTag(target)) {
            cir.setReturnValue(false);
        }
    }
}
