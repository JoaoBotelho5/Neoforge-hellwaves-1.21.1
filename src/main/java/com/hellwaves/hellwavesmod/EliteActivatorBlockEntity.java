package com.hellwaves.hellwavesmod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EliteActivatorBlockEntity extends BlockEntity {
    public static final int MAX_WAVES = 5; // More waves for elite version
    private static final int GLOWING_DELAY = 2000; // 100 segundos (20 ticks por segundo * 100)


    public int nextWave = 1;
    public int tickCountdown = 0;
    public final List<Mob> activeMobs = new ArrayList<>();
    public int glowingCountdown = 0; // Novo contador para glowing
    private final List<UUID> activeMobUUIDs = new ArrayList<>();

    public EliteActivatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELITE_ACTIVATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("NextWave", nextWave);
        tag.putInt("TickCountdown", tickCountdown);

        ListTag mobList = new ListTag();
        for (Mob mob : activeMobs) {
            if (mob != null && mob.isAlive()) {
                CompoundTag mobTag = new CompoundTag();
                mobTag.putUUID("UUID", mob.getUUID());
                mobList.add(mobTag);
            }
        }
        tag.put("ActiveMobs", mobList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        nextWave = tag.getInt("NextWave");
        tickCountdown = tag.getInt("TickCountdown");

        activeMobUUIDs.clear();
        ListTag mobList = tag.getList("ActiveMobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < mobList.size(); i++) {
            CompoundTag mobTag = mobList.getCompound(i);
            activeMobUUIDs.add(mobTag.getUUID("UUID"));
        }
    }

    public void resolveMobsFromUUIDs(ServerLevel world) {
        activeMobs.clear();
        for (UUID uuid : activeMobUUIDs) {
            if (world.getEntity(uuid) instanceof Mob mob && mob.isAlive()) {
                activeMobs.add(mob);
            }
        }
        activeMobUUIDs.clear();
        setChanged();
    }

    public void checkCompletion(ServerLevel world) {
        if (nextWave > MAX_WAVES && activeMobs.isEmpty()) {
            if (!world.isClientSide) {
                // Better rewards for elite version
                ItemStack[] drops = {
                        new ItemStack(Items.NETHER_STAR, 1),
                        new ItemStack(Items.DIAMOND_BLOCK, 10),
                        new ItemStack(Items.EMERALD_BLOCK, 10),
                        new ItemStack(Items.ANCIENT_DEBRIS, 5)
                };

                for (ItemStack stack : drops) {
                    world.addFreshEntity(new ItemEntity(world, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack));
                }

                world.removeBlock(worldPosition, false);
            }
        }
    }

    public void tick(ServerLevel world) {
        if (!activeMobUUIDs.isEmpty()) {
            resolveMobsFromUUIDs(world);
        }

        activeMobs.removeIf(mob -> mob.level() != world || !mob.isAlive() || mob.isRemoved());

        if (nextWave > MAX_WAVES && !activeMobs.isEmpty() && glowingCountdown == 0) {
            glowingCountdown = GLOWING_DELAY;
            setChanged();
        }

        // Aplicar glowing effect quando o contador chegar a 0
        if (glowingCountdown > 0) {
            glowingCountdown--;

            if (glowingCountdown <= 0) {
                applyGlowingToAllMobs(world);
                glowingCountdown = -1; // Marcar como já aplicado
                setChanged();
            }
        }


        for (Mob mob : activeMobs) {
            if (mob.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 4) {
                world.explode(null,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5,
                        50f,
                        Level.ExplosionInteraction.BLOCK);

                nextWave = 1;
                activeMobs.clear();
                tickCountdown = 0;
                setChanged();
                return;
            }
        }

        if (activeMobs.isEmpty() && nextWave <= MAX_WAVES) {
            if (tickCountdown <= 0) {
                activeMobs.addAll(EliteWaveManager.activateWave(world, worldPosition, null, nextWave));

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("§6Elite Wave " + nextWave + " has started!§r"))
                );

                nextWave++;
                tickCountdown = 1000;
                setChanged();


            } else {
                tickCountdown--;
                if (tickCountdown % 20 == 0) {
                    setChanged();
                }
            }
        }

        checkCompletion(world);
    }

    private void applyGlowingToAllMobs(ServerLevel world) {
        for (Mob mob : activeMobs) {
            if (mob.isAlive() && !mob.isRemoved()) {
                // Versão mais direta
                mob.addEffect(new MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.GLOWING,
                        999999,
                        0,
                        false,
                        false
                ));
            }
        }
    }

    public void addMob(Mob mob) {
        activeMobs.add(mob);
        setChanged();
    }

    public void clearMobs() {
        activeMobs.clear();
        setChanged();
    }
}