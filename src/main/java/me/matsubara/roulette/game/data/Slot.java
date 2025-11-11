package me.matsubara.roulette.game.data;

import lombok.AccessLevel;
import lombok.Getter;
import me.matsubara.roulette.RoulettePlugin;
import me.matsubara.roulette.file.Config;
import me.matsubara.roulette.game.Game;
import me.matsubara.roulette.game.GameType;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Axis;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Slots of the table, from 0 (including 00 for american table) to 36.
 * NOTE: 3 zeros (0, 0, 0) means 0 & 00. One for 0, 2 for 00.
 */
@SuppressWarnings("unused")
@Getter
public enum Slot {
    // Singles.
    SLOT_0("24581d3955e9acd513d28dd32257ae51ff7fd6df05b5f4b921f1deae49b2172", 0),
    SLOT_00("cfd2c0e8b50c5cf6efbb68472792ea1f0601850068565ee58eef66b66f37f34a", 0, 0),
    SLOT_1("8d2454e4c67b323d5be953b5b3d54174aa271460374ee28410c5aeae2c11f5", 1),
    SLOT_2("96fab991d083993cb83e4bcf44a0b6cefac647d4189ee9cb823e9cc1571e38", 2),
    SLOT_3("031f66be0950588598feeea7e6c6779355e57cc6de8b91a44391b2e9fd72", 3),
    SLOT_4("d198d56216156114265973c258f57fc79d246bb65e3c77bbe8312ee35db6", 4),
    SLOT_5("df3f565a88928ee5a9d6843d982d78eae6b41d9077f2a1e526af867d78fb", 5),
    SLOT_6("9c613f80a554918c7ab2cd4a278752f151412a44a73d7a286d61d45be4eaae1", 6),
    SLOT_7("af4e7a5cf5b5a4d2ff4fb0433b1a68751aa12e9a021d3918e92e219a953b", 7),
    SLOT_8("84ad12c2f21a1972f3d2f381ed05a6cc088489fcfdf68a713b387482fe91e2", 8),
    SLOT_9("f8977adedfa6c81a67f825ea37c4d5aa90dfe3c2a72dd98791f4521e1da36", 9),
    SLOT_10("b0cf9794fbc089dab037141f67875ab37fadd12f3b92dba7dd2288f1e98836", 10),
    SLOT_11("3997e7c194c4702cd214428e1f5e64615726a52f7c6e3a337893091e786722a", 11),
    SLOT_12("8bedfe349bc32cfd961abe40a856197b59e5d45e69ce9715589dc8e820be29f", 12),
    SLOT_13("ed3d5a31819af5665e1ce396bbf8f1e4d98ffd18222da46fadb61cf79562f8", 13),
    SLOT_14("3e8117e66a79ae470549f2ab34c9689fe8c7e7a986691765825ac9127240d", 14),
    SLOT_15("f6bec38d26c02f43dcbf9b1d48b34f1bc4737a6938f2664d4e764272a9b39b61", 15),
    SLOT_16("ae3e4bc71e3ac330836181eda96bc6f128e5c5313ab952c8ff6ded549e13a5", 16),
    SLOT_17("7be20edf7c2ee65251f771d8673d5ba72adf8945d3eb27d79b9ba97407f76", 17),
    SLOT_18("57d89883df7fd41f496e9a41b729639456b28decaadd121e932e48fbb9dd86c", 18),
    SLOT_19("55d6848abc5e7181edc1ba14f7eb7555bd8fb6c9710c7ae5161ef9c3d66f6d8", 19),
    SLOT_20("f7b29a1bb25b2ad8ff3a7a38228189c9461f457a4da98dae29384c5c25d85", 20),
    SLOT_21("6cf961853566e2e6f731fc53d2fe21db24fb246983639ee37157677e2b1170", 21),
    SLOT_22("404d561e3bd9e416114782975f50eee7771c251e5c6d3113c8674fdc8ffdf60", 22),
    SLOT_23("4d501dee5e9331f6fe524e2c7feef8adb4a01fce567c58bbde63a79f9ed7", 23),
    SLOT_24("994891a593b030c4fe608f653fb9760d4ffab13b8fc78ed1af46d6f55a92516", 24),
    SLOT_25("8da20df87bd78ab6e1d67850451fbe83aec67578c6e0ff94f466f15b1b204e", 25),
    SLOT_26("c30d6d308683ffb7ccebb27e5a84ae7e3d8531675450dcdc6489efe89085967", 26),
    SLOT_27("3a34c6ba013fbe3157dfa7422723c2e3e77a20e8d8b1cfb48449934100eb599b", 27),
    SLOT_28("b5192b09af11d43faa91eec274869e93dc615ba449cace89ca31ea1d40da15fe", 28),
    SLOT_29("3c08f0ee69e93022a0c489796a2a72f51c425bc22a16bfbf97bc8631253e6ed4", 29),
    SLOT_30("a1ed67db595ef96c7a10d88db108b8e2b411b7aca754285ce6d1514c9b8e4952", 30),
    SLOT_31("4146e0f1cb6bc050b7fc030d4798e745079d0c05b7d1ba6d6e770e045db45aec", 31),
    SLOT_32("a7bdc77a22693af9e54303d730c460842d9215440ed569f6b2b8abcbed8d9720", 32),
    SLOT_33("eb43a66d4436330a43be6b3bd68178535df84acbea395f2033ea77757d6fb52b", 33),
    SLOT_34("71965e68b67895b4165573c8ab377b5e256f79dc7aa781049474aac0d20dbce7", 34),
    SLOT_35("998c952b2ab20c4bcbc3d9e3154ba82ef9bb62362c1661bf586bbd4633ba1d1f", 35),
    SLOT_36("fb74daf3aba7c56daed039baa457c2b39dea795f1b524c472c6f2c4e1522b2f3", 36),

    // Columns.
    SLOT_COLUMN_1(1, 4, 7, 10, 13, 16, 19, 22, 25, 28, 31, 34),
    SLOT_COLUMN_2(2, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32, 35),
    SLOT_COLUMN_3(3, 6, 9, 12, 15, 18, 21, 24, 27, 30, 33, 36),

    // Dozens.
    SLOT_DOZEN_1(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
    SLOT_DOZEN_2(13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24),
    SLOT_DOZEN_3(25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36),

    // Low numbers (1 to 18) and evens.
    SLOT_LOW("d4affa455b7f58217de8acbbd9201c9ea87c134aa35625494f5d3f65ce946",
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18),
    SLOT_EVEN("1aeef88e2c928b466c6ed5deaa4e1975a9436c2b1b498f9f7cbf92a9b599a6",
            2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32, 34, 36),

    // Colors.
    SLOT_RED("35f45c875eff538fcec98f6acc1df2aecae28f4860aecd24e2dbdf13924b327",
            1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36),
    SLOT_BLACK("9eca98befd0d7efca9b11ebf4b2da459cc19a378114b3cdde67d4067afb896",
            2, 4, 6, 8, 10, 11, 13, 15, 17, 20, 22, 24, 26, 28, 29, 31, 33, 35),

    // Odds and high numbers (19 to 36).
    SLOT_ODD("cbb1d17cebc5f0ecc987b80efc03e32ecb1cb40dbc5bce2faf3e60542a40",
            1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31, 33, 35),
    SLOT_HIGH("7ba9c33a95fa1e519f85a41ca56799384db41fe7e1d7a791751ece9bbae5d27f",
            19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36);

    private final @Getter(AccessLevel.NONE) String url;
    private final int[] childs;

    private static final double[] OFFSET_FOR_7 = {0.0d, -0.15d, 0.15d, -0.3d, 0.3d, -0.45d, 0.45d};
    private static final double[] OFFSET_FOR_3 = {0.0d, -0.15d, 0.15d};
    private static final double[] OFFSET_FOR_1 = {0.0d};

    // The colors are the same as the ones on our table (https://excalidraw.com/#json=YGFFqm_cxWBomeg1mEy8d,BF3eBgysCfVt7iCq43tCjA).
    private static final Map<SlotType, String> SLOT_TYPE_URL = Map.of(
            SlotType.COLUMN, "62a5876113322f39aa2bbef4bd6b79ec6b52a97bb6fab674bddbd7b6eab3ba", // WHITE C
            SlotType.DOZEN, "fa661419de49ff4a2c97b27f868014fbdaeb8dd7f4392777830b2714caafd1f"); // WHITE D

    Slot(String url, int... childs) {
        this.url = url;
        this.childs = childs;
    }

    Slot(int... childs) {
        this(null, childs);
    }

    public int getMaxBets(boolean european) {
        return getOffsets(european).getRight().length;
    }

    public Pair<Axis, double[]> getOffsets(boolean european) {
        return switch (this) {
            case SLOT_DOZEN_1, SLOT_DOZEN_2, SLOT_DOZEN_3 -> Pair.of(Axis.X, OFFSET_FOR_7);
            case SLOT_0 -> Pair.of(Axis.Z, european ? OFFSET_FOR_7 : OFFSET_FOR_3);
            case SLOT_00 -> Pair.of(Axis.Z, european ? new double[]{} : OFFSET_FOR_3);
            case SLOT_LOW, SLOT_EVEN, SLOT_RED, SLOT_BLACK, SLOT_ODD, SLOT_HIGH -> Pair.of(Axis.X, OFFSET_FOR_3);
            default -> {
                if (isSingle()) yield Pair.of(Axis.Z, OFFSET_FOR_3);
                yield Pair.of(Axis.X, OFFSET_FOR_1);
            }
        };
    }

    public SlotColor getColor() {
        return switch (this) {
            case SLOT_RED,
                 SLOT_1,
                 SLOT_3,
                 SLOT_5,
                 SLOT_7,
                 SLOT_9,
                 SLOT_12,
                 SLOT_14,
                 SLOT_16,
                 SLOT_18,
                 SLOT_19,
                 SLOT_21,
                 SLOT_23,
                 SLOT_25,
                 SLOT_27,
                 SLOT_30,
                 SLOT_32,
                 SLOT_34,
                 SLOT_36 -> SlotColor.RED;
            case SLOT_BLACK -> SlotColor.BLACK;
            case SLOT_0, SLOT_00 -> SlotColor.GREEN;
            default -> amountOfNumbers() > 1 ? SlotColor.MIXED : SlotColor.BLACK;
        };
    }

    public String getNumbersAsString(@NotNull GameType type) {
        String numbers = Arrays.stream(childs)
                .filter(value -> value != 0)
                .mapToObj(String::valueOf)
                .collect(Collectors.joining(", "));

        boolean european = type.isEuropean();

        long zeros = amountOfZeros();

        if (zeros >= 2 && !european) {
            numbers = "00" + (numbers.isBlank() ? "" : ", " + numbers);
        }

        if (zeros == 1 || zeros == 3) {
            numbers = "0" + (numbers.isBlank() ? "" : ", " + numbers);
        }

        return numbers;
    }

    private double getDefaultMultiplier(GameType type) {
        if (isSingle()) {
            return 36.0d;
        }

        if (applyForRules()) {
            return 2.0d;
        }

        if (isColumn() || isDozen()) {
            return 3.0d;
        }

        // This won't happen.
        return 0.0d;
    }

    public double getMultiplier(GameType type, RoulettePlugin plugin) {
        double defaultValue = getDefaultMultiplier(type);
        if (!Config.CUSTOM_WIN_MULTIPLIER_ENABLED.asBool()) return defaultValue;
        return plugin.getConfig().getDouble("custom-win-multiplier.slots." + name(), defaultValue);
    }

    public @NotNull String getChance(boolean european) {
        int numbers = amountOfNumbers();
        for (SlotType type : SlotType.values()) {
            Slot[] gameType = type.getSlots(european ? GameType.EUROPEAN : GameType.AMERICAN);
            if (!ArrayUtils.contains(gameType, this)) continue;
            return getChance(numbers, european);
        }
        return getChance(numbers, european);
    }

    @Contract(pure = true)
    private @NotNull String getChance(int amount, boolean european) {
        int max = european ? 37 : 38;
        return "%d/%d (%.1f%%)".formatted(amount, max, (amount * 100.0f) / max);
    }

    public boolean isZero() {
        return this == SLOT_0;
    }

    public boolean isDoubleZero() {
        return this == SLOT_00;
    }

    public boolean isAnyZero() {
        return isZero() || isDoubleZero();
    }

    public boolean isSingle() {
        return amountOfNumbers() == 1;
    }

    public long amountOfZeros() {
        return Arrays.stream(childs)
                .filter(number -> number == 0)
                .count();
    }

    public int amountOfNumbers() {
        int length = childs.length;
        return amountOfZeros() <= 1 ? length : length - 1;
    }

    public boolean contains(@NotNull Slot slot) {
        // Won't happen for now, maybe when adding more slots.
        if (slot.isAnyZero()) return false;

        for (int number : slot.getChilds()) {
            if (ArrayUtils.contains(childs, number)) return true;
        }

        return false;
    }

    public boolean applyForRules() {
        return this == SLOT_LOW
                || this == SLOT_EVEN
                || this == SLOT_RED
                || this == SLOT_BLACK
                || this == SLOT_ODD
                || this == SLOT_HIGH;
    }

    public boolean isColumn() {
        return name().contains("_COLUMN_");
    }

    public boolean isDozen() {
        return name().contains("_DOZEN_");
    }

    public boolean isLow() {
        return this == SLOT_LOW;
    }

    public boolean isEven() {
        return this == SLOT_EVEN;
    }

    public boolean isOdd() {
        return this == SLOT_ODD;
    }

    public boolean isHigh() {
        return this == SLOT_HIGH;
    }

    public int getColumnOrDozen() {
        if (!isColumn() && !isDozen()) return -1;
        return name().charAt(name().length() - 1) - '0';
    }

    public boolean isRed() {
        return this == SLOT_RED;
    }

    public boolean isBlack() {
        return this == SLOT_BLACK;
    }

    public static Slot[] values(@NotNull Game game) {
        Slot[] values = values(game.getType());

        for (Slot slot : game.getDisabledSlots()) {
            values = ArrayUtils.removeElement(values, slot);
        }

        return values;
    }

    public static Slot[] values(@NotNull GameType type) {
        return type.isEuropean() ? ArrayUtils.removeElements(values(), SLOT_00) : values();
    }

    public static Stream<Slot> singleValues(@NotNull Game game) {
        return Arrays.stream(values(game)).filter(Slot::isSingle);
    }

    public @Nullable String getUrl(GameType gameType) {
        if (url != null) return url;

        for (SlotType type : SlotType.values()) {
            if (!ArrayUtils.contains(type.getSlots(gameType), this)) continue;
            return SLOT_TYPE_URL.get(type);
        }

        // Shouldn't be null.
        return null;
    }

    public enum SlotColor {
        RED,
        BLACK,
        GREEN,
        MIXED
    }
}