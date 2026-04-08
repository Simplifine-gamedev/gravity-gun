package com.orca.gravitygun;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GravityGunMod implements ModInitializer {
    public static final String MOD_ID = "gravitygun";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final Item GRAVITY_GUN = new GravityGunItem(new Item.Settings().maxCount(1));

    private static final Map<UUID, GrabbedObject> grabbedObjects = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TICKS = 40;
    private static final double GRAB_DISTANCE = 20.0;
    private static final double HOLD_DISTANCE = 3.0;
    private static final double LAUNCH_SPEED = 2.0;

    public static class GrabbedObject {
        public Entity entity;
        public BlockState blockState;
        public BlockPos originalBlockPos;
        public boolean isBlock;

        public GrabbedObject(Entity entity) {
            this.entity = entity;
            this.isBlock = false;
        }

        public GrabbedObject(BlockState blockState, BlockPos pos) {
            this.blockState = blockState;
            this.originalBlockPos = pos;
            this.isBlock = true;
        }
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "gravity_gun"), GRAVITY_GUN);

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(content -> {
            content.add(GRAVITY_GUN);
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient()) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (held.getItem() != GRAVITY_GUN) return ActionResult.PASS;

            if (isOnCooldown(player)) {
                return ActionResult.FAIL;
            }

            if (hasGrabbedObject(player)) {
                return ActionResult.FAIL;
            }

            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getHardness(world, pos) < 0) {
                return ActionResult.FAIL;
            }

            world.setBlockState(pos, Blocks.AIR.getDefaultState());
            grabbedObjects.put(player.getUuid(), new GrabbedObject(state, pos));
            setCooldown(player);

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.5f);

            return ActionResult.SUCCESS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient()) return ActionResult.PASS;
            ItemStack held = player.getStackInHand(hand);
            if (held.getItem() != GRAVITY_GUN) return ActionResult.PASS;

            if (isOnCooldown(player)) {
                return ActionResult.FAIL;
            }

            if (hasGrabbedObject(player)) {
                return ActionResult.FAIL;
            }

            if (entity instanceof PlayerEntity) {
                return ActionResult.FAIL;
            }

            grabbedObjects.put(player.getUuid(), new GrabbedObject(entity));
            setCooldown(player);

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0f, 1.5f);

            return ActionResult.SUCCESS;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack held = player.getStackInHand(hand);
            if (world.isClient()) return TypedActionResult.pass(held);
            if (held.getItem() != GRAVITY_GUN) return TypedActionResult.pass(held);

            if (!hasGrabbedObject(player)) {
                return TypedActionResult.pass(held);
            }

            GrabbedObject grabbed = grabbedObjects.remove(player.getUuid());
            Vec3d lookVec = player.getRotationVec(1.0f);

            if (grabbed.isBlock) {
                launchBlock(player, grabbed.blockState, lookVec);
            } else if (grabbed.entity != null && grabbed.entity.isAlive()) {
                launchEntity(grabbed.entity, lookVec);
            }

            world.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 0.5f);

            return TypedActionResult.success(held);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (!hasGrabbedObject(player)) continue;

                GrabbedObject grabbed = grabbedObjects.get(player.getUuid());
                ItemStack mainHand = player.getStackInHand(Hand.MAIN_HAND);
                ItemStack offHand = player.getStackInHand(Hand.OFF_HAND);

                if (mainHand.getItem() != GRAVITY_GUN && offHand.getItem() != GRAVITY_GUN) {
                    if (grabbed.isBlock) {
                        dropBlock(player, grabbed.blockState);
                    }
                    grabbedObjects.remove(player.getUuid());
                    continue;
                }

                Vec3d eyePos = player.getEyePos();
                Vec3d lookVec = player.getRotationVec(1.0f);
                Vec3d targetPos = eyePos.add(lookVec.multiply(HOLD_DISTANCE));

                if (grabbed.isBlock) {
                    // Block is virtual, nothing to move
                } else if (grabbed.entity != null && grabbed.entity.isAlive()) {
                    Entity entity = grabbed.entity;
                    Vec3d currentPos = entity.getPos();
                    Vec3d diff = targetPos.subtract(currentPos);
                    entity.setVelocity(diff.multiply(0.5));
                    entity.velocityModified = true;
                    entity.fallDistance = 0;
                }
            }
        });

        LOGGER.info("Gravity Gun mod initialized!");
    }

    private static boolean isOnCooldown(PlayerEntity player) {
        Long lastUse = cooldowns.get(player.getUuid());
        if (lastUse == null) return false;
        return (player.getWorld().getTime() - lastUse) < COOLDOWN_TICKS;
    }

    private static void setCooldown(PlayerEntity player) {
        cooldowns.put(player.getUuid(), player.getWorld().getTime());
    }

    private static boolean hasGrabbedObject(PlayerEntity player) {
        return grabbedObjects.containsKey(player.getUuid());
    }

    private static void launchEntity(Entity entity, Vec3d direction) {
        Vec3d velocity = direction.multiply(LAUNCH_SPEED);
        entity.setVelocity(velocity);
        entity.velocityModified = true;

        if (entity instanceof LivingEntity living) {
            living.timeUntilRegen = 0;
        }
    }

    private static void launchBlock(PlayerEntity player, BlockState state, Vec3d direction) {
        World world = player.getWorld();
        Vec3d startPos = player.getEyePos().add(direction.multiply(HOLD_DISTANCE));
        Vec3d endPos = startPos.add(direction.multiply(50));

        BlockHitResult hitResult = world.raycast(new RaycastContext(
            startPos, endPos,
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            player
        ));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos hitPos = hitResult.getBlockPos();
            Direction side = hitResult.getSide();
            BlockPos placePos = hitPos.offset(side);

            if (world.getBlockState(placePos).isAir() || world.getBlockState(placePos).isReplaceable()) {
                world.setBlockState(placePos, state);
                world.playSound(null, placePos, state.getSoundGroup().getPlaceSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
            } else {
                Block.dropStacks(state, world, player.getBlockPos());
            }
        } else {
            Block.dropStacks(state, world, player.getBlockPos());
        }
    }

    private static void dropBlock(PlayerEntity player, BlockState state) {
        Block.dropStacks(state, player.getWorld(), player.getBlockPos());
    }
}
