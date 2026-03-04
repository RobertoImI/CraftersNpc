package org.crafterscr.craftersnpc;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, CraftersNpc.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CnpcEntity>> CNPC = ENTITY_TYPES.register("cnpc",
            () -> EntityType.Builder.of(CnpcEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.8F)
                    .build("cnpc"));

    private ModEntities() {
    }

    public static void registerAttributes(IEventBus modBus) {
        modBus.addListener((EntityAttributeCreationEvent event) -> event.put(CNPC.get(), CnpcEntity.createAttributes().build()));
    }
}
