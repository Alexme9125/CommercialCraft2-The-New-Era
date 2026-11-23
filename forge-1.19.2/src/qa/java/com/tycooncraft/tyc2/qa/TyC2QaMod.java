package com.tycooncraft.tyc2.qa;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Isolated Forge 1.19.2 QA &amp; live client-server field-test mod for TycoonCraft² ({@code tyc2_qa}).
 * Strictly kept in {@code forge-1.19.2/src/qa/} and never packaged into {@code tyc2-forge-1.19.2-1.0.0.jar}.
 */
@Mod(TyC2QaMod.MOD_ID)
public final class TyC2QaMod {

    public static final String MOD_ID = "tyc2_qa";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DeferredRegister<Item> QA_ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);

    /** QA-only extractable Forge FE battery item for verifying Standalone electric battery discharge in Slot 3. */
    public static final RegistryObject<Item> QA_FE_BATTERY =
            QA_ITEMS.register("qa_fe_battery", () -> new QaFeBatteryItem(new Item.Properties().stacksTo(1)));

    public TyC2QaMod() {
        QA_ITEMS.register(FMLJavaModLoadingContext.get().getModEventBus());
        TyC2LiveNetwork.register();
        String mode = System.getProperty("tyc2.qa.mode", "").trim().toLowerCase();
        String profile = System.getProperty("tyc2.qa.profile", "standalone").trim().toLowerCase();
        switch (mode) {
            case "server_write", "server_read" -> {
                String phase = mode.endsWith("write") ? "write" : "read";
                LOGGER.info("[TYC2-QA] Registering Forge 1.19.2 server QA runner (profile={}, phase={})", profile, phase);
                MinecraftForge.EVENT_BUS.register(new TyC2QaServerRunner(profile, phase));
            }
            case "live_server" -> {
                LOGGER.info("[TYC2-QA] Registering Forge 1.19.2 live client-server orchestrator (profile={})", profile);
                MinecraftForge.EVENT_BUS.register(new TyC2LiveServerRunner(profile));
            }
            case "live_client" -> {
                LOGGER.info("[TYC2-QA] Registering Forge 1.19.2 live graphical client driver (profile={})", profile);
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TyC2LiveClientDriver.install(profile));
            }
            default -> LOGGER.info("[TYC2-QA] Idle (tyc2.qa.mode='{}')", mode);
        }
    }

    /** Creates a charged QA FE battery holding {@code storedFe} out of {@code 40_000} FE. */
    public static ItemStack createQaFeBattery(int storedFe) {
        ItemStack stack = new ItemStack(QA_FE_BATTERY.get());
        stack.getOrCreateTag().putInt("qa_fe", Math.max(0, Math.min(40_000, storedFe)));
        return stack;
    }

    public static int getQaFeStored(ItemStack stack) {
        return stack.hasTag() && stack.getTag() != null ? stack.getTag().getInt("qa_fe") : 0;
    }

    private static final class QaFeBatteryItem extends Item {
        QaFeBatteryItem(Properties props) {
            super(props);
        }

        @Override
        @Nullable
        public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
            return new ICapabilityProvider() {
                private final IEnergyStorage storage = new IEnergyStorage() {
                    @Override
                    public int receiveEnergy(int maxReceive, boolean simulate) {
                        int cur = getQaFeStored(stack);
                        int accepted = Math.min(40_000 - cur, Math.max(0, maxReceive));
                        if (!simulate && accepted > 0) {
                            stack.getOrCreateTag().putInt("qa_fe", cur + accepted);
                        }
                        return accepted;
                    }

                    @Override
                    public int extractEnergy(int maxExtract, boolean simulate) {
                        int cur = getQaFeStored(stack);
                        int extracted = Math.min(cur, Math.max(0, maxExtract));
                        if (!simulate && extracted > 0) {
                            stack.getOrCreateTag().putInt("qa_fe", cur - extracted);
                        }
                        return extracted;
                    }

                    @Override
                    public int getEnergyStored() {
                        return getQaFeStored(stack);
                    }

                    @Override
                    public int getMaxEnergyStored() {
                        return 40_000;
                    }

                    @Override
                    public boolean canExtract() {
                        return true;
                    }

                    @Override
                    public boolean canReceive() {
                        return true;
                    }
                };
                private final LazyOptional<IEnergyStorage> holder = LazyOptional.of(() -> storage);

                @Override
                public <T> LazyOptional<T> getCapability(Capability<T> cap, @Nullable Direction side) {
                    return ForgeCapabilities.ENERGY.orEmpty(cap, holder);
                }
            };
        }
    }
}
