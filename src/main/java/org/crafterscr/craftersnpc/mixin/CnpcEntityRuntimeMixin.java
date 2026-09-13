package org.crafterscr.craftersnpc.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.phys.Vec3;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import org.crafterscr.craftersnpc.entity.FixedPositionNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Correcciones de runtime muy localizadas para CnpcEntity.
 *
 * <p>1) Permite anclar un NPC a una posición exacta sin desactivar sus
 * animaciones ni sus diálogos.</p>
 *
 * <p>2) Mantiene avanzando el temporizador del texto cuando hay un emote
 * activo. CnpcEntity retorna antes de tickDialogue() durante un emote, por
 * lo que sin esta corrección el texto quedaba congelado indefinidamente.</p>
 */
@Mixin(CnpcEntity.class)
public abstract class CnpcEntityRuntimeMixin implements FixedPositionNpc {
    @Unique
    private static final String CRAFTERSNPC_FIXED_TAG = "FixedPosition";
    @Unique
    private static final String CRAFTERSNPC_FIXED_X_TAG = "FixedPositionX";
    @Unique
    private static final String CRAFTERSNPC_FIXED_Y_TAG = "FixedPositionY";
    @Unique
    private static final String CRAFTERSNPC_FIXED_Z_TAG = "FixedPositionZ";

    @Unique
    private boolean craftersnpc$fixedPosition;
    @Unique
    private Vec3 craftersnpc$fixedAnchor = Vec3.ZERO;

    @Shadow
    public abstract boolean isAnimationPlaying();

    @Shadow
    public abstract int getDialogueTicks();

    @Shadow
    public abstract void finishCurrentAction();

    @Shadow
    public abstract void reengageRouteNavigation();

    @Shadow
    private void setDialogueTicks(int ticks) {
        throw new AssertionError();
    }

    @Shadow
    private void clearDialogue() {
        throw new AssertionError();
    }

    @Override
    public boolean isFixedPosition() {
        return craftersnpc$fixedPosition;
    }

    @Override
    public void setFixedPosition(boolean fixed) {
        CnpcEntity npc = (CnpcEntity) (Object) this;

        if (fixed == craftersnpc$fixedPosition) {
            if (fixed && !npc.level().isClientSide) {
                craftersnpc$enforceAnchor(npc);
            }
            return;
        }

        if (fixed) {
            /*
             * Si estaba ejecutando una acción de ruta, la cerramos antes
             * de congelar la ruta. Un emote iniciado manualmente no tiene
             * activeRouteAction y por tanto continúa con normalidad.
             */
            finishCurrentAction();
            craftersnpc$fixedAnchor = npc.position();
            craftersnpc$fixedPosition = true;
            npc.getNavigation().stop();
            npc.setDeltaMovement(Vec3.ZERO);
            craftersnpc$enforceAnchor(npc);
            return;
        }

        craftersnpc$fixedPosition = false;
        npc.setDeltaMovement(Vec3.ZERO);
        reengageRouteNavigation();
    }

    /**
     * Antes del tick normal detenemos cualquier navegación pendiente y,
     * cuando existe un emote, hacemos avanzar únicamente el reloj del
     * diálogo. No llamamos reengageRouteNavigation aquí porque el emote
     * todavía debe conservar prioridad sobre la IA.
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void craftersnpc$beforeTick(CallbackInfo ci) {
        CnpcEntity npc = (CnpcEntity) (Object) this;

        if (npc.level().isClientSide) {
            return;
        }

        if (craftersnpc$fixedPosition) {
            npc.getNavigation().stop();
            npc.setDeltaMovement(Vec3.ZERO);
        }

        if (!isAnimationPlaying()) {
            return;
        }

        int remainingTicks = getDialogueTicks();
        if (remainingTicks <= 0) {
            return;
        }

        setDialogueTicks(remainingTicks - 1);
        if (remainingTicks == 1) {
            clearDialogue();
        }
    }

    /**
     * La colisión vanilla se conserva: el jugador sigue chocando con el
     * NPC y recibe el empuje normal. Al terminar el tick descartamos la
     * parte del empuje que habría desplazado al NPC y restauramos su ancla.
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void craftersnpc$afterTick(CallbackInfo ci) {
        CnpcEntity npc = (CnpcEntity) (Object) this;
        if (!npc.level().isClientSide && craftersnpc$fixedPosition) {
            craftersnpc$enforceAnchor(npc);
        }
    }

    /**
     * Aunque se asigne accidentalmente una ruta a un NPC fijo, no dejamos
     * que el controlador avance índices, puertas o acciones mientras siga
     * anclado. Al poner fixed off, la navegación puede retomarse.
     */
    @Inject(method = "tickRouteInternal", at = @At("HEAD"), cancellable = true)
    private void craftersnpc$blockRouteWhileFixed(CallbackInfo ci) {
        if (!craftersnpc$fixedPosition) {
            return;
        }

        CnpcEntity npc = (CnpcEntity) (Object) this;
        npc.getNavigation().stop();
        ci.cancel();
    }

    @Inject(method = "reengageRouteNavigation", at = @At("HEAD"), cancellable = true)
    private void craftersnpc$blockRouteRestartWhileFixed(CallbackInfo ci) {
        if (craftersnpc$fixedPosition) {
            ((CnpcEntity) (Object) this).getNavigation().stop();
            ci.cancel();
        }
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void craftersnpc$saveFixedPosition(CompoundTag tag, CallbackInfo ci) {
        tag.putBoolean(CRAFTERSNPC_FIXED_TAG, craftersnpc$fixedPosition);
        if (!craftersnpc$fixedPosition) {
            return;
        }

        tag.putDouble(CRAFTERSNPC_FIXED_X_TAG, craftersnpc$fixedAnchor.x);
        tag.putDouble(CRAFTERSNPC_FIXED_Y_TAG, craftersnpc$fixedAnchor.y);
        tag.putDouble(CRAFTERSNPC_FIXED_Z_TAG, craftersnpc$fixedAnchor.z);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void craftersnpc$loadFixedPosition(CompoundTag tag, CallbackInfo ci) {
        CnpcEntity npc = (CnpcEntity) (Object) this;
        craftersnpc$fixedPosition = tag.getBoolean(CRAFTERSNPC_FIXED_TAG);

        if (!craftersnpc$fixedPosition) {
            craftersnpc$fixedAnchor = npc.position();
            return;
        }

        if (tag.contains(CRAFTERSNPC_FIXED_X_TAG, Tag.TAG_DOUBLE)
                && tag.contains(CRAFTERSNPC_FIXED_Y_TAG, Tag.TAG_DOUBLE)
                && tag.contains(CRAFTERSNPC_FIXED_Z_TAG, Tag.TAG_DOUBLE)) {
            Vec3 savedAnchor = new Vec3(
                    tag.getDouble(CRAFTERSNPC_FIXED_X_TAG),
                    tag.getDouble(CRAFTERSNPC_FIXED_Y_TAG),
                    tag.getDouble(CRAFTERSNPC_FIXED_Z_TAG)
            );
            craftersnpc$fixedAnchor = craftersnpc$isFinite(savedAnchor) ? savedAnchor : npc.position();
        } else {
            /* Compatibilidad con cualquier guardado previo sin ancla. */
            craftersnpc$fixedAnchor = npc.position();
        }

        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
    }

    @Unique
    private void craftersnpc$enforceAnchor(CnpcEntity npc) {
        if (!craftersnpc$isFinite(craftersnpc$fixedAnchor)) {
            craftersnpc$fixedAnchor = npc.position();
        }

        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);

        if (npc.position().distanceToSqr(craftersnpc$fixedAnchor) > 1.0E-8D) {
            npc.setPos(
                    craftersnpc$fixedAnchor.x,
                    craftersnpc$fixedAnchor.y,
                    craftersnpc$fixedAnchor.z
            );
        }

        npc.fallDistance = 0.0F;
    }

    @Unique
    private static boolean craftersnpc$isFinite(Vec3 pos) {
        return Double.isFinite(pos.x)
                && Double.isFinite(pos.y)
                && Double.isFinite(pos.z);
    }
}
