package com.example;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class ExampleMod implements ModInitializer {
    public static final String MOD_ID = "modid";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Config options matching your exact specifications
    private static final String WARDEN_NAME = "§fWarden Shot";
    private static final int WARDEN_RANGE = 10;
    private static final float WARDEN_DAMAGE = 22.0f;

    // Track {wardenuses::%player's uuid%} natively
    private final HashMap<UUID, Integer> wardenUses = new HashMap<>();

    @Override
    public void onInitialize() {
        LOGGER.info("Warden Shot mod initialized and fixed successfully!");

        // 1. REGISTER THE COMMAND (/wardenshot)
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(CommandManager.literal("wardenshot")
                .requires(source -> source.hasPermissionLevel(4)) // permission: op
                .executes(context -> {
                    if (context.getSource().getEntity() instanceof ServerPlayerEntity player) {
                        ItemStack rod = new ItemStack(Items.FISHING_ROD);
                        rod.setCustomName(Text.literal(WARDEN_NAME));
                        
                        player.getInventory().insertStack(rod);
                        wardenUses.put(player.getUuid(), 0);
                        
                        player.sendMessage(Text.literal("§3[Warden] §7You received the " + WARDEN_NAME + "§7."), false);
                    }
                    return 1;
                })
            );
        });

        // 2. REGISTER THE RIGHT-CLICK TRIGGER
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);

            // Double check it's the right item
            if (stack.isOf(Items.FISHING_ROD) && stack.hasCustomName() && stack.getName().getString().equals(WARDEN_NAME)) {
                if (!world.isClient) {
                    UUID uuid = player.getUuid();
                    int currentUses = wardenUses.getOrDefault(uuid, 0) + 1;
                    wardenUses.put(uuid, currentUses);

                    // FIRST CLICK: Heartbeat sound only
                    if (currentUses == 1) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0f, 1.0f);
                    } 
                    // SECOND CLICK: Sonic Boom Beam
                    else if (currentUses >= 2) {
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 2.0f, 1.0f);

                        Vec3d eyeLoc = player.getEyePos();
                        Vec3d lookVec = player.getRotationVec(1.0f);
                        HashSet<UUID> hitEntities = new HashSet<>();

                        // FIXED LOOP: Steps along the looking direction 1.0 blocks at a time up to range
                        for (int i = 0; i < WARDEN_RANGE; i++) {
                            Vec3d point = eyeLoc.add(lookVec.multiply(i * 1.0));
                            BlockPos checkPos = BlockPos.ofFloored(point.x, point.y, point.z);

                            // Stop the beam instantly if it hits a solid block/wall
                            if (world.getBlockState(checkPos).isSolidBlock(world, checkPos)) {
                                break;
                            }

                            // Show the particle trail
                            if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                                serverWorld.spawnParticles(ParticleTypes.SONIC_BOOM, point.x, point.y, point.z, 1, 0, 0, 0, 0);
                            }

                            // Create the search box area around the current point segment
                            Box searchBox = new Box(point, point).expand(1.5);
                            List<Entity> targets = world.getOtherEntities(player, searchBox);

                            for (Entity entity : targets) {
                                if (entity instanceof LivingEntity livingEntity && !hitEntities.contains(livingEntity.getUuid())) {
                                    // Deal the 22 Magic Damage
                                    livingEntity.damage(world.getDamageSources().magic(), WARDEN_DAMAGE);

                                    // Custom calculated knockback vector pushing away from player
                                    Vec3d playerPos = player.getPos();
                                    Vec3d enemyPos = livingEntity.getPos();
                                    Vec3d velocityVector = enemyPos.subtract(playerPos).normalize().multiply(1.5);
                                    
                                    livingEntity.setVelocity(velocityVector);
                                    livingEntity.velocityModified = true;

                                    hitEntities.add(livingEntity.getUuid());
                                }
                            }
                        }

                        // Remove item from hand, reset data, play snap sound
                        stack.setCount(0);
                        wardenUses.remove(uuid);
                        world.playSound(null, player.getX(), player.getY(), player.getZ(), 
                            SoundEvents.ENTITY_ITEM_BREAK, SoundCategory.PLAYERS, 1.0f, 1.0f);
                    }
                }
                return TypedActionResult.success(stack);
            }
            return TypedActionResult.pass(stack);
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}

