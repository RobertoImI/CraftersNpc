package org.crafterscr.craftersnpc.gift;

import org.crafterscr.craftersnpc.CraftersNpc;
import org.crafterscr.craftersnpc.entity.CnpcEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class NpcGiftHandler {
    private NpcGiftHandler() {}
    public static InteractionResult tryHandle(CnpcEntity npc, ServerPlayer player, ItemStack stack) {
        NpcGiftData data = npc.getGiftData();
        if (!data.isEnabled() || stack.isEmpty()) return InteractionResult.PASS;
        if (NpcGiftCooldownManager.isCoolingDown(npc.getUUID())) { speak(npc, player, data, "cooldown"); return InteractionResult.CONSUME; }
        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        NpcGiftData.Category cat = data.categoryOf(itemId);
        if (cat == NpcGiftData.Category.UNKNOWN) { speak(npc, player, data, "unknown"); return InteractionResult.CONSUME; }
        if (!player.getAbilities().instabuild && data.shouldConsume(cat)) stack.shrink(1);
        String type = cat == NpcGiftData.Category.FAVORITE ? "favorite" : cat == NpcGiftData.Category.LIKED ? "liked" : "disliked";
        speak(npc, player, data, type);
        effects(npc, cat);
        NpcGiftCooldownManager.start(npc.getUUID(), data.cooldownSeconds());
        if (cat == NpcGiftData.Category.FAVORITE || cat == NpcGiftData.Category.LIKED) reward(npc, player, data);
        return InteractionResult.CONSUME;
    }
    private static void speak(CnpcEntity npc, ServerPlayer player, NpcGiftData data, String type) { npc.startDialogue(data.messages().random(type, npc.getRandom()), 60, player); }
    private static void effects(CnpcEntity npc, NpcGiftData.Category cat) {
        if (!(npc.level() instanceof ServerLevel level)) return;
        if (cat == NpcGiftData.Category.FAVORITE) { level.sendParticles(ParticleTypes.HEART, npc.getX(), npc.getY()+1.8, npc.getZ(), 6, .4, .4, .4, .02); level.playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1f, 1.2f); }
        else if (cat == NpcGiftData.Category.LIKED) { level.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY()+1.5, npc.getZ(), 8, .4, .4, .4, .02); level.playSound(null, npc.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.NEUTRAL, .6f, 1.1f); }
        else if (cat == NpcGiftData.Category.DISLIKED) { level.sendParticles(ParticleTypes.ANGRY_VILLAGER, npc.getX(), npc.getY()+1.6, npc.getZ(), 4, .3, .3, .3, .01); level.playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1f, .8f); }
    }
    private static void reward(CnpcEntity npc, ServerPlayer player, NpcGiftData data) {
        if (data.rewardChance() <= 0 || data.rewardPool().isEmpty() || npc.getRandom().nextDouble() * 100D >= data.rewardChance()) { speak(npc, player, data, "noReward"); return; }
        int total = data.rewardPool().stream().filter(r -> r.weight() > 0).mapToInt(NpcGiftReward::weight).sum();
        if (total <= 0) return;
        int roll = npc.getRandom().nextInt(total);
        for (NpcGiftReward r : data.rewardPool()) {
            roll -= r.weight();
            if (roll < 0) {
                ResourceLocation id = ResourceLocation.tryParse(r.item());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) { CraftersNpc.LOGGER.warn("Recompensa inválida en NPC {}: {}", npc.getNpcId(), r.item()); return; }
                Item item = BuiltInRegistries.ITEM.get(id);
                int count = r.min() + npc.getRandom().nextInt(Math.max(1, r.max() - r.min() + 1));
                ItemStack reward = new ItemStack(item, count);
                if (!player.getInventory().add(reward)) player.drop(reward, false);
                speak(npc, player, data, "reward");
                npc.level().playSound(null, player.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .8f, 1f);
                return;
            }
        }
    }
}
