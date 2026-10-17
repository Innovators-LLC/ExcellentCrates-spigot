package su.nightexpress.excellentcrates.hooks.impl;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import su.nightexpress.excellentcrates.CratesPlugin;
import su.nightexpress.excellentcrates.api.crate.Reward;
import su.nightexpress.excellentcrates.config.Config;
import su.nightexpress.excellentcrates.config.Lang;
import su.nightexpress.excellentcrates.crate.impl.Crate;
import su.nightexpress.excellentcrates.crate.impl.Milestone;
import su.nightexpress.excellentcrates.data.crate.UserCrateData;
import su.nightexpress.excellentcrates.user.CrateUser;
import su.nightexpress.nightcore.bridge.wrap.NightProfile;
import su.nightexpress.nightcore.core.config.CoreLang;
import su.nightexpress.nightcore.util.NumberUtil;
import su.nightexpress.nightcore.util.profile.CachedProfile;
import su.nightexpress.nightcore.util.time.TimeFormats;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class PlaceholderHook {

    private static Expansion expansion;
    private static ItemExpansion itemExpansion;

    public static void setup(@NotNull CratesPlugin plugin) {
        if (expansion == null) {
            expansion = new Expansion(plugin);
            expansion.register();
        }
        if (itemExpansion == null) {
            itemExpansion = new ItemExpansion(plugin);
            itemExpansion.register();
        }
    }

    public static void shutdown() {
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
        if (itemExpansion != null) {
            itemExpansion.unregister();
            itemExpansion = null;
        }
    }

    private static class Expansion extends PlaceholderExpansion {

        private final CratesPlugin                                   plugin;
        private final Map<String, BiFunction<Player, Crate, String>> userPlaceholders;

        public Expansion(@NotNull CratesPlugin plugin) {
            this.plugin = plugin;
            this.userPlaceholders = new LinkedHashMap<>();

            this.userPlaceholders.put("keys", (player, crate) -> {
                if (player == null) return null;

                int amount = crate.countMaxOpenings(player);
                return amount < 0 ? CoreLang.OTHER_INFINITY.text() : String.valueOf(amount);
            });

            this.userPlaceholders.put("openings_available", (player, crate) -> {
                if (player == null) return null;

                int amount = crate.countMaxOpenings(player);
                return amount < 0 ? CoreLang.OTHER_INFINITY.text() : String.valueOf(amount);
            });

            this.userPlaceholders.put("openings_raw", (player, crate) -> {
                if (player == null) return null;

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                return String.valueOf(user.getCrateData(crate).getOpenings());
            });

            this.userPlaceholders.put("openings_remaining", (player, crate) -> {
                if (player == null) return null;
                if (!crate.isOpeningCooldownEnabled()) return CoreLang.OTHER_INFINITY.text();

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                UserCrateData data = user.getCrateData(crate);

                int openLimit = crate.getOpeningLimitAmount();
                int openStreak = data.queryOpeningStreak();

                return String.valueOf(Math.max(0, openLimit - openStreak));
            });

            this.userPlaceholders.put("openings", (player, crate) -> {
                if (player == null) return null;

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                return NumberUtil.format(user.getCrateData(crate).getOpenings());
            });

            this.userPlaceholders.put("cooldown", (player, crate) -> {
                if (player == null) return null;
                if (!crate.isOpeningCooldownEnabled()) return Lang.OTHER_COOLDOWN_READY.text();

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                UserCrateData data = user.getCrateData(crate);

                if (!data.isOnCooldown()) return Lang.OTHER_COOLDOWN_READY.text();
                if (data.isCooldownPermanent()) return CoreLang.OTHER_NEVER.text();

                return TimeFormats.formatDuration(data.getCooldownTimestamp(), Config.CRATE_COOLDOWN_FORMAT_TYPE.get());
            });

            this.userPlaceholders.put("next_milestone_openings", (player, crate) -> {
                if (player == null) return null;

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                int milestones = user.getCrateData(crate).getMilestone();
                Milestone milestone = crate.getNextMilestone(milestones);
                if (milestone == null) return Lang.OTHER_NEXT_MILESTONE_EMPTY.text();

                return NumberUtil.format(milestone.getOpenings() - milestones);
            });

            this.userPlaceholders.put("next_milestone_reward", (player, crate) -> {
                if (player == null) return null;

                CrateUser user = plugin.getUserManager().getOrFetch(player);
                int milestones = user.getCrateData(crate).getMilestone();
                Milestone milestone = crate.getNextMilestone(milestones);
                Reward reward = milestone == null ? null : milestone.getReward();
                if (reward == null) return Lang.OTHER_NEXT_MILESTONE_EMPTY.text();

                return reward.getName();
            });

            this.userPlaceholders.put("latest_opener", (player, crate) -> crate.getLastOpener().map(CachedProfile::query).map(NightProfile::getName).orElse(Lang.OTHER_LAST_OPENER_EMPTY.text()));
            this.userPlaceholders.put("latest_rolled_reward", (player, crate) -> crate.getLastRewardName());
        }

        @Override
        @NotNull
        public String getIdentifier() {
            return this.plugin.getName().toLowerCase();
        }

        @Override
        @NotNull
        public String getAuthor() {
            return this.plugin.getDescription().getAuthors().getFirst();
        }

        @Override
        @NotNull
        public String getVersion() {
            return this.plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
            for (var entry : this.userPlaceholders.entrySet()) {
                String prefix = entry.getKey() + "_";
                if (!params.startsWith(prefix)) continue;

                String id = params.substring(prefix.length());
                Crate crate = plugin.getCrateManager().getCrateById(id);
                if (crate == null) return null;

                return entry.getValue().apply(player, crate);
            }

            return null;
        }
    }

    private static class ItemExpansion extends PlaceholderExpansion {

        private static final long ROTATE_INTERVAL = 2000L;
        private static final long FADE_DURATION   = 500L;
        private static final String[] FADE_COLORS = {"&#404040", "&#606060", "&#909090", "&#C0C0C0", "&#FFFFFF"};

        private final CratesPlugin plugin;

        public ItemExpansion(@NotNull CratesPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        @NotNull
        public String getIdentifier() {
            return "item";
        }

        @Override
        @NotNull
        public String getAuthor() {
            return this.plugin.getDescription().getAuthors().getFirst();
        }

        @Override
        @NotNull
        public String getVersion() {
            return this.plugin.getDescription().getVersion();
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Nullable
        private String getRotatingRewardName(@NotNull String crateId) {
            Crate crate = this.plugin.getCrateManager().getCrateById(crateId);
            if (crate == null) return null;

            List<String> names = crate.getRewards().stream()
                .map(Reward::getName)
                .filter(name -> !name.isBlank())
                .toList();
            if (names.isEmpty()) return null;

            long time = System.currentTimeMillis();
            int index = (int) ((time / ROTATE_INTERVAL) % names.size());
            String name = names.get(index);

            long elapsed = time % ROTATE_INTERVAL;
            if (elapsed >= FADE_DURATION) return name;

            int fadeStep = (int) (elapsed / (FADE_DURATION / FADE_COLORS.length));
            return FADE_COLORS[fadeStep] + stripColors(name);
        }

        @NotNull
        private String stripColors(@NotNull String text) {
            return text
                .replaceAll("(?i)(?:&|§)#[0-9a-f]{6}", "")
                .replaceAll("(?i)(?:&|§)[0-9a-fk-orx]", "");
        }

        @Override
        public String onPlaceholderRequest(@Nullable Player player, @NotNull String params) {
            String prefix = "rotate_";
            if (!params.startsWith(prefix)) return null;

            return this.getRotatingRewardName(params.substring(prefix.length()));
        }
    }
}
