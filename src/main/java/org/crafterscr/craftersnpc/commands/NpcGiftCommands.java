package org.crafterscr.craftersnpc.commands;

import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.*;
import org.crafterscr.craftersnpc.entity.*;
import org.crafterscr.craftersnpc.gift.*;
import net.minecraft.commands.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.crafterscr.craftersnpc.network.OpenNpcGiftEditorPayload;

import java.util.*;

final class NpcGiftCommands {
    private static final SimpleCommandExceptionType BAD_ITEM = new SimpleCommandExceptionType(Component.literal("El ítem indicado no existe."));
    private static final DynamicCommandExceptionType NO_NPC = new DynamicCommandExceptionType(id -> Component.literal("No existe un NPC con id " + id + "."));
    private NpcGiftCommands() {}
    static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("gift").requires(s -> s.hasPermission(2))
                .then(Commands.literal("enable").then(npcArg().executes(c -> enable(c,true))))
                .then(Commands.literal("disable").then(npcArg().executes(c -> enable(c,false))))
                .then(Commands.literal("info").then(npcArg().executes(NpcGiftCommands::info)))
                .then(cat("like", NpcGiftData.Category.LIKED)).then(cat("favorite", NpcGiftData.Category.FAVORITE)).then(cat("dislike", NpcGiftData.Category.DISLIKED))
                .then(Commands.literal("reward").then(Commands.literal("add").then(npcArg().then(itemArg().then(Commands.argument("min", IntegerArgumentType.integer(1)).then(Commands.argument("max", IntegerArgumentType.integer(1)).then(Commands.argument("weight", IntegerArgumentType.integer(1)).executes(NpcGiftCommands::rewardAdd)))))))
                        .then(Commands.literal("remove").then(npcArg().then(itemArg().executes(NpcGiftCommands::rewardRemove))))
                        .then(Commands.literal("list").then(npcArg().executes(NpcGiftCommands::rewardList)))
                        .then(Commands.literal("clear").then(npcArg().executes(NpcGiftCommands::rewardClear))))
                .then(Commands.literal("config")
                        .then(Commands.literal("rewardChance").then(npcArg().then(Commands.argument("value", DoubleArgumentType.doubleArg(0,100)).executes(c -> setChance(c, DoubleArgumentType.getDouble(c,"value")) ))))
                        .then(Commands.literal("cooldown").then(npcArg().then(Commands.argument("seconds", IntegerArgumentType.integer(0)).executes(c -> setCooldown(c, IntegerArgumentType.getInteger(c,"seconds")) ))))
                        .then(boolConfig("consumeLiked")).then(boolConfig("consumeFavorite")).then(boolConfig("consumeDisliked")))
                .then(Commands.literal("message")
                        .then(Commands.literal("add").then(npcArg().then(typeArg().then(Commands.argument("text", StringArgumentType.greedyString()).executes(NpcGiftCommands::msgAdd)))))
                        .then(Commands.literal("remove").then(npcArg().then(typeArg().then(Commands.argument("index", IntegerArgumentType.integer(1)).executes(NpcGiftCommands::msgRemove)))))
                        .then(Commands.literal("list").then(npcArg().then(typeArg().executes(NpcGiftCommands::msgList))))
                        .then(Commands.literal("clear").then(npcArg().then(typeArg().executes(NpcGiftCommands::msgClear)))))
                .then(Commands.literal("gui").then(npcArg().executes(NpcGiftCommands::gui)));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,String> npcArg(){return Commands.argument("npcId", StringArgumentType.word()).suggests(CnpcCommandSuggestions::suggestNpcIds);}
    private static RequiredArgumentBuilder<CommandSourceStack,String> itemArg(){return Commands.argument("item", StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.keySet().stream().map(ResourceLocation::toString), b));}
    private static RequiredArgumentBuilder<CommandSourceStack,String> typeArg(){return Commands.argument("type", StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(NpcGiftMessages.TYPES,b));}
    private static LiteralArgumentBuilder<CommandSourceStack> cat(String name, NpcGiftData.Category cat){ return Commands.literal(name).then(Commands.literal("add").then(npcArg().then(itemArg().executes(c->catAdd(c,cat,name))))).then(Commands.literal("remove").then(npcArg().then(itemArg().executes(c->catRemove(c,cat,name))))).then(Commands.literal("list").then(npcArg().executes(c->catList(c,cat,name)))); }
    private static LiteralArgumentBuilder<CommandSourceStack> boolConfig(String name){ return Commands.literal(name).then(npcArg().then(Commands.argument("value", BoolArgumentType.bool()).suggests((c,b)->SharedSuggestionProvider.suggest(List.of("true","false"),b)).executes(c -> setBool(c,name,BoolArgumentType.getBool(c,"value"))))); }
    private static CnpcEntity npc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException { String id=StringArgumentType.getString(c,"npcId"); return NpcRegistry.findById(c.getSource().getServer(), id).orElseThrow(() -> NO_NPC.create(id)); }
    private static String item(CommandContext<CommandSourceStack> c) throws CommandSyntaxException { String raw=StringArgumentType.getString(c,"item"); ResourceLocation id=ResourceLocation.tryParse(raw); if(id==null||!BuiltInRegistries.ITEM.containsKey(id)) throw BAD_ITEM.create(); return id.toString(); }
    private static int save(CnpcEntity npc){ npc.markGiftDataChanged(); return 1; }
    private static int enable(CommandContext<CommandSourceStack> c, boolean v) throws CommandSyntaxException { CnpcEntity n=npc(c); n.getGiftData().setEnabled(v); c.getSource().sendSuccess(()->Component.literal("Regalos "+(v?"activados":"desactivados")+" para el NPC "+n.getNpcId()+"."), true); return save(n); }
    private static int catAdd(CommandContext<CommandSourceStack> c,NpcGiftData.Category cat,String label)throws CommandSyntaxException{ CnpcEntity n=npc(c); String it=item(c); n.getGiftData().addToCategory(cat,it); c.getSource().sendSuccess(()->Component.literal("Se agregó "+it+" a "+label+" de "+n.getNpcId()+"."),true); return save(n);}
    private static int catRemove(CommandContext<CommandSourceStack> c,NpcGiftData.Category cat,String label)throws CommandSyntaxException{ CnpcEntity n=npc(c); String it=item(c); boolean ok=n.getGiftData().removeFromCategory(cat,it); c.getSource().sendSuccess(()->Component.literal((ok?"Se removió ":"No estaba ")+it+" en "+label+" de "+n.getNpcId()+"."),true); return save(n);}
    private static int catList(CommandContext<CommandSourceStack> c,NpcGiftData.Category cat,String label)throws CommandSyntaxException{ CnpcEntity n=npc(c); Set<String> s=switch(cat){case FAVORITE->n.getGiftData().favoriteItems();case LIKED->n.getGiftData().likedItems();case DISLIKED->n.getGiftData().dislikedItems();default->Set.of();}; c.getSource().sendSuccess(()->Component.literal(label+" de "+n.getNpcId()+": "+(s.isEmpty()?"(vacío)":String.join(", ",s))),false); return s.size();}
    private static int rewardAdd(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String it=item(c); int min=IntegerArgumentType.getInteger(c,"min"), max=IntegerArgumentType.getInteger(c,"max"), w=IntegerArgumentType.getInteger(c,"weight"); if(max<min) throw new SimpleCommandExceptionType(Component.literal("max debe ser igual o mayor que min.")).create(); n.getGiftData().removeReward(it); n.getGiftData().rewardPool().add(new NpcGiftReward(it,min,max,w)); c.getSource().sendSuccess(()->Component.literal("Se agregó recompensa "+it+" x"+min+"-"+max+" con peso "+w+"."),true); return save(n);}
    private static int rewardRemove(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String it=item(c); boolean ok=n.getGiftData().removeReward(it); c.getSource().sendSuccess(()->Component.literal((ok?"Se removió":"No existe")+" recompensa "+it+"."),true); return save(n);}
    private static int rewardList(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String out=n.getGiftData().rewardPool().isEmpty()?"(vacío)":String.join(", ", n.getGiftData().rewardPool().stream().map(r->r.item()+" x"+r.min()+"-"+r.max()+" peso "+r.weight()).toList()); c.getSource().sendSuccess(()->Component.literal("Recompensas de "+n.getNpcId()+": "+out),false); return n.getGiftData().rewardPool().size();}
    private static int rewardClear(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); n.getGiftData().rewardPool().clear(); c.getSource().sendSuccess(()->Component.literal("Recompensas limpiadas para "+n.getNpcId()+"."),true); return save(n);}
    private static int setChance(CommandContext<CommandSourceStack> c,double v)throws CommandSyntaxException{ CnpcEntity n=npc(c); n.getGiftData().setRewardChance(v); c.getSource().sendSuccess(()->Component.literal("Probabilidad de recompensa de "+n.getNpcId()+" configurada a "+v+"%."),true); return save(n);}
    private static int setCooldown(CommandContext<CommandSourceStack> c,int v)throws CommandSyntaxException{ CnpcEntity n=npc(c); n.getGiftData().setCooldownSeconds(v); c.getSource().sendSuccess(()->Component.literal("Cooldown de regalos de "+n.getNpcId()+" configurado a "+v+" segundos."),true); return save(n);}
    private static int setBool(CommandContext<CommandSourceStack> c,String name,boolean v)throws CommandSyntaxException{ CnpcEntity n=npc(c); if(name.equals("consumeLiked"))n.getGiftData().setConsumeLiked(v); else if(name.equals("consumeFavorite"))n.getGiftData().setConsumeFavorite(v); else n.getGiftData().setConsumeDisliked(v); c.getSource().sendSuccess(()->Component.literal(name+" de "+n.getNpcId()+" configurado a "+v+"."),true); return save(n);}
    private static int msgAdd(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String t=StringArgumentType.getString(c,"type"); n.getGiftData().messages().add(t,StringArgumentType.getString(c,"text")); c.getSource().sendSuccess(()->Component.literal("Se agregó una frase "+t+" para "+n.getNpcId()+"."),true); return save(n);}
    private static int msgRemove(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String t=StringArgumentType.getString(c,"type"); boolean ok=n.getGiftData().messages().remove(t,IntegerArgumentType.getInteger(c,"index")); c.getSource().sendSuccess(()->Component.literal(ok?"Frase removida.":"No existe esa frase."),true); return save(n);}
    private static int msgList(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String t=StringArgumentType.getString(c,"type"); List<String> list=n.getGiftData().messages().get(t); c.getSource().sendSuccess(()->Component.literal(list.isEmpty()?"El tipo de mensaje "+t+" no tiene frases configuradas.":t+": "+list),false); return list.size();}
    private static int msgClear(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); String t=StringArgumentType.getString(c,"type"); n.getGiftData().messages().clear(t); c.getSource().sendSuccess(()->Component.literal("Frases "+t+" limpiadas para "+n.getNpcId()+"."),true); return save(n);}
    private static int info(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ CnpcEntity n=npc(c); NpcGiftData d=n.getGiftData(); c.getSource().sendSuccess(()->Component.literal("Regalos de "+n.getNpcId()+": enabled="+d.isEnabled()+", liked="+d.likedItems().size()+", favorite="+d.favoriteItems().size()+", disliked="+d.dislikedItems().size()+", recompensas="+d.rewardPool().size()+", rewardChance="+d.rewardChance()+", cooldown="+d.cooldownSeconds()+", consumeLiked="+d.consumeLiked()+", consumeFavorite="+d.consumeFavorite()+", consumeDisliked="+d.consumeDisliked()+", mensajes="+NpcGiftMessages.TYPES.stream().map(t->t+":"+d.messages().count(t)).toList()),false); return 1; }
    private static int gui(CommandContext<CommandSourceStack> c)throws CommandSyntaxException{ ServerPlayer p=c.getSource().getPlayerOrException(); CnpcEntity n=npc(c); NpcGiftData d=n.getGiftData(); PacketDistributor.sendToPlayer(p, new OpenNpcGiftEditorPayload(n.getId(), n.getNpcId(), d.isEnabled(), new ArrayList<>(d.favoriteItems()), new ArrayList<>(d.likedItems()), new ArrayList<>(d.dislikedItems()), new ArrayList<>(d.rewardPool()), d.rewardChance(), d.cooldownSeconds(), d.consumeFavorite(), d.consumeLiked(), d.consumeDisliked(), d.messages().get("favorite"), d.messages().get("liked"), d.messages().get("disliked"), d.messages().get("unknown"), d.messages().get("cooldown"), d.messages().get("reward"), d.messages().get("noReward"))); return 1; }
}
