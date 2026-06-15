package com.echoesofaegis.companions.mixin;

import com.echoesofaegis.companions.companion.CompanionManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AvoidEntityGoal.class)
public abstract class AvoidEntityGoalMixin {
    @Shadow
    protected LivingEntity toAvoid;

    @Inject(method = "canUse", at = @At("RETURN"), cancellable = true)
    private void echoescompanions$ignoreTaggedCompanionFearSources(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && CompanionManager.hasCompanionTag(toAvoid)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true)
    private void echoescompanions$stopAvoidingTaggedCompanions(CallbackInfoReturnable<Boolean> cir) {
        if (CompanionManager.hasCompanionTag(toAvoid)) {
            cir.setReturnValue(false);
        }
    }
}
