package site.mcrelicworld.relicprison.internal;

import site.mcrelicworld.relicprison.api.BackupService;
import site.mcrelicworld.relicprison.api.BoosterService;
import site.mcrelicworld.relicprison.api.DiagnosticService;
import site.mcrelicworld.relicprison.api.MineAccessService;
import site.mcrelicworld.relicprison.api.MineResetService;
import site.mcrelicworld.relicprison.api.MineService;
import site.mcrelicworld.relicprison.api.MiningService;
import site.mcrelicworld.relicprison.api.MultiplierService;
import site.mcrelicworld.relicprison.api.NumberFormatService;
import site.mcrelicworld.relicprison.api.PlayerDataService;
import site.mcrelicworld.relicprison.api.PrestigeService;
import site.mcrelicworld.relicprison.api.ProgressionService;
import site.mcrelicworld.relicprison.api.RankService;
import site.mcrelicworld.relicprison.api.RelicPrisonApi;
import site.mcrelicworld.relicprison.api.SellService;
import site.mcrelicworld.relicprison.api.StatisticsService;

public record RelicPrisonApiService(
        MineService mines,
        MineResetService resets,
        MineAccessService mineAccess,
        RankService ranks,
        PrestigeService prestiges,
        ProgressionService progression,
        PlayerDataService players,
        SellService selling,
        MultiplierService multipliers,
        BoosterService boosters,
        MiningService mining,
        StatisticsService statistics,
        BackupService backups,
        DiagnosticService diagnostics,
        NumberFormatService numbers,
        String version
) implements RelicPrisonApi {}
