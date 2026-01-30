package com.hellwaves.hellwavesmod.HWMobs;

import com.hellwaves.hellwavesmod.inventory.GuardianInventory;

public interface IGuardian {
    int getGuardianLevel();
    void setGuardianLevel(int level);
    GuardianInventory getGuardianInventory();
    void setRestoringFromCage(boolean restoring);
    boolean isRestoringFromCage();
}