package com.xulai.seedseasons.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import com.xulai.seedseasons.IrrigatorBlockEntity;

/**
 * 灌溉机的水位条界面：一根蓝白横条，蓝色是水、白色是空余，
 * 每条刻度线对应 1 桶水，悬停显示当前水量与总储水量。
 * 没有 Menu，直接读客户端方块实体的同步数据。
 */
public class IrrigatorScreen extends Screen {

    private static final int GAUGE_HEIGHT = 16;
    private static final int FRAME = 2;
    private static final int PIXELS_PER_BUCKET = 3;
    private static final int MIN_PIXELS_PER_BUCKET = 1;

    private static final int WATER_COLOR = 0xFF3F76E4;
    private static final int WATER_SURFACE = 0xFF6FA8FF;
    private static final int EMPTY_COLOR = 0xFFF4F4F4;
    private static final int TICK_COLOR = 0xFF9A9A9A;
    private static final int MAJOR_TICK_COLOR = 0xFF5A5A5A;
    private static final int FRAME_COLOR = 0xFF373737;
    private static final int FRAME_LIGHT = 0xFF8B8B8B;

    private final IrrigatorBlockEntity blockEntity;
    private int gaugeLeft;
    private int gaugeTop;
    private int pixelsPerBucket;

    public IrrigatorScreen(IrrigatorBlockEntity blockEntity) {
        super(Component.translatable("block.seedseasons.irrigator"));
        this.blockEntity = blockEntity;
    }

    public static void open(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof IrrigatorBlockEntity blockEntity) {
            Minecraft.getInstance().setScreen(new IrrigatorScreen(blockEntity));
        }
    }

    @Override
    protected void init() {
        super.init();
        int buckets = blockEntity.getCapacity() / IrrigatorBlockEntity.BUCKET_MILLIBUCKETS;
        pixelsPerBucket = PIXELS_PER_BUCKET;
        while (pixelsPerBucket > MIN_PIXELS_PER_BUCKET && buckets * pixelsPerBucket > this.width - 48) {
            pixelsPerBucket--;
        }
        int gaugeWidth = buckets * pixelsPerBucket;
        gaugeLeft = (this.width - gaugeWidth) / 2;
        gaugeTop = (this.height - GAUGE_HEIGHT) / 2;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int buckets = blockEntity.getCapacity() / IrrigatorBlockEntity.BUCKET_MILLIBUCKETS;
        int gaugeWidth = buckets * pixelsPerBucket;
        int gaugeRight = gaugeLeft + gaugeWidth;
        int gaugeBottom = gaugeTop + GAUGE_HEIGHT;
        int innerTop = gaugeTop + 1;
        int innerBottom = gaugeBottom - 1;

        graphics.drawCenteredString(this.font, this.title, this.width / 2, gaugeTop - 14, 0xFFE0E0E0);

        // 外框
        graphics.fill(gaugeLeft - FRAME, gaugeTop - FRAME, gaugeRight + FRAME, gaugeBottom + FRAME, FRAME_LIGHT);
        graphics.fill(gaugeLeft - FRAME + 1, gaugeTop - FRAME + 1, gaugeRight + FRAME - 1, gaugeBottom + FRAME - 1, FRAME_COLOR);

        // 白色空余底，再从左向右画蓝色水量
        graphics.fill(gaugeLeft, innerTop, gaugeRight, innerBottom, EMPTY_COLOR);
        int water = blockEntity.getWater();
        int capacity = blockEntity.getCapacity();
        int blueWidth = capacity > 0 ? (int) Math.round((double) gaugeWidth * Math.min(1.0D, (double) water / capacity)) : 0;
        if (blueWidth > 0) {
            int waterRight = gaugeLeft + blueWidth;
            graphics.fill(gaugeLeft, innerTop, waterRight, innerBottom, WATER_COLOR);
            graphics.fill(gaugeLeft, innerTop, gaugeLeft + 1, innerBottom, WATER_SURFACE);
        }

        // 刻度线：一条线 = 1 桶水；每 10 桶画一条更深的刻度
        for (int bucket = 1; bucket < buckets; bucket++) {
            int x = gaugeLeft + bucket * pixelsPerBucket;
            if (x >= gaugeRight) {
                break;
            }
            boolean major = bucket % 10 == 0;
            int color = major ? MAJOR_TICK_COLOR : TICK_COLOR;
            int y1 = major ? gaugeTop : innerTop + 1;
            graphics.fill(x, y1, x + 1, innerBottom, color);
        }

        if (mouseX >= gaugeLeft && mouseX < gaugeRight && mouseY >= gaugeTop && mouseY < gaugeBottom) {
            graphics.renderComponentTooltip(this.font, List.of(
                    Component.translatable("seedseasons.gui.water", format(water), water / IrrigatorBlockEntity.BUCKET_MILLIBUCKETS),
                    Component.translatable("seedseasons.gui.capacity", format(capacity), capacity / IrrigatorBlockEntity.BUCKET_MILLIBUCKETS)),
                    mouseX, mouseY);
        }
    }

    private static String format(int millibuckets) {
        return String.format("%,d", millibuckets);
    }

    /**
     * 按背包键（默认 E，尊重改键）直接关闭水位条界面；ESC 也可关闭。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            this.onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
