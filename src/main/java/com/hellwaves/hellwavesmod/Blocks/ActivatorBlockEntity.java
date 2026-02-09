package com.hellwaves.hellwavesmod.Blocks;

import com.hellwaves.hellwavesmod.WavesManager.WaveManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ExperienceOrb;
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

public class ActivatorBlockEntity extends BlockEntity {

    public static final int MAX_WAVES = 3;
    private static final int GLOWING_DELAY = 2000; // 100 segundos

    public int nextWave = 1;
    public int tickCountdown = 0;
    public int glowingCountdown = 0; //contador para glowing
    public final List<Mob> activeMobs = new ArrayList<>();
    private final List<UUID> activeMobUUIDs = new ArrayList<>(); // For persistence

    public ActivatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACTIVATOR_BLOCK_ENTITY.get(), pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("NextWave", nextWave);
        tag.putInt("TickCountdown", tickCountdown);

        // Save mob UUIDs for persistence
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

        // Load mob UUIDs
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
        activeMobUUIDs.clear(); // Clear after resolving
        setChanged(); // Mark as changed to save the cleared UUID list
    }

    public void checkCompletion(ServerLevel world) {
        // Se todas as waves terminaram e não há mobs ativos
        if (nextWave > MAX_WAVES && activeMobs.isEmpty()) {
            if (!world.isClientSide) {
                // --- Drops de itens existentes ---
                ItemStack[] drops = {
                        new ItemStack(Items.EMERALD_BLOCK, 5),
                        new ItemStack(Items.DIAMOND_BLOCK, 3),
                        new ItemStack(Items.ANCIENT_DEBRIS, 2),
                        new ItemStack(Items.ECHO_SHARD, 1)
                };

                for (ItemStack stack : drops) {
                    world.addFreshEntity(new ItemEntity(
                            world,
                            worldPosition.getX() + 0.5,
                            worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5,
                            stack
                    ));
                }

                // --- Drop de XP orbs
                int minLevels = 35;
                int maxLevels = 40;
                int levels = minLevels + world.random.nextInt(maxLevels - minLevels + 1);


                int totalXP = levels * 24;

                while (totalXP > 0) {

                    int orbXP = ExperienceOrb.getExperienceValue(totalXP);
                    totalXP -= orbXP;

                    world.addFreshEntity(new ExperienceOrb(
                            world,
                            worldPosition.getX() + 0.5,
                            worldPosition.getY() + 0.5,
                            worldPosition.getZ() + 0.5,
                            orbXP
                    ));
                }

                // Remove o bloco após dropar itens e XP
                world.removeBlock(worldPosition, false);
            }
        }
    }


    public void tick(ServerLevel world) {
        // If we have UUIDs to resolve (from loading), resolve them first
        if (!activeMobUUIDs.isEmpty()) {
            resolveMobsFromUUIDs(world);
        }

        // Remove dead mobs
        activeMobs.removeIf(mob -> mob.level() != world || !mob.isAlive() || mob.isRemoved());

        // Check for mobs nearby
        for (Mob mob : activeMobs) {
            if (mob.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 4) {
                world.explode(null,
                        worldPosition.getX() + 0.5,
                        worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5,
                        25f,
                        Level.ExplosionInteraction.BLOCK);

                nextWave = 1;
                activeMobs.clear();
                tickCountdown = 0;
                setChanged(); // Save changes
                return;
            }
        }

        // Automatic wave activation
        if (activeMobs.isEmpty() && nextWave <= MAX_WAVES) {
            if (tickCountdown <= 0) {
                activeMobs.addAll(WaveManager.activateWave(world, worldPosition, null, nextWave));

                world.getServer().getPlayerList().getPlayers().forEach(p ->
                        p.sendSystemMessage(Component.literal("Wave " + nextWave + " has started!"))
                );

                nextWave++;
                tickCountdown = 2400;
                setChanged(); // Save changes
            } else {
                tickCountdown--;
                // Only mark as changed occasionally to reduce disk I/O
                if (tickCountdown % 20 == 0) {
                    setChanged();
                }
            }
        }

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

        checkCompletion(world);
    }

    private void applyGlowingToAllMobs(ServerLevel world) {
        for (Mob mob : activeMobs) {
            if (mob.isAlive() && !mob.isRemoved()) {
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
    // Helper method to add mob and mark as changed
    public void addMob(Mob mob) {
        activeMobs.add(mob);
        setChanged();
    }

    // Helper method to clear mobs and mark as changed
    public void clearMobs() {
        activeMobs.clear();
        setChanged();
    }
}