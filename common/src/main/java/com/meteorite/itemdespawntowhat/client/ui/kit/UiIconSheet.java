package com.meteorite.itemdespawntowhat.client.ui.kit;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * 图标图集切片：把规则网格贴图按行列切成 {@link UiIcon.Sprite}，供图标条等控件使用。
 *
 * <p>行自上而下、列自左而右：{@code u = column * cellWidth}、{@code v = row * cellHeight}。
 * 行数与列数同时参与切片，因为 1.21.1 的 blit 用贴图真实宽高做 UV 除数——只知列数会让 V
 * 落到错误的行，因此本记录必须携带行数。</p>
 */
public record UiIconSheet(ResourceLocation texture, int cellWidth, int cellHeight, int columns, int rows) {

    public UiIconSheet {
        Objects.requireNonNull(texture, "Sheet texture");
        if (cellWidth <= 0 || cellHeight <= 0) throw new IllegalArgumentException("Invalid cell size");
        if (columns <= 0 || rows <= 0) throw new IllegalArgumentException("Invalid sheet extent");
    }

    /** 切出第 {@code row} 行、第 {@code column} 列（均从 0 起）的图标；宽高等于单元格尺寸。 */
    public UiIcon cell(int row, int column) {
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            throw new IllegalArgumentException("Sheet cell out of range");
        }
        return new UiIcon.Sprite(texture, column * cellWidth, row * cellHeight, cellWidth, cellHeight,
                columns * cellWidth, rows * cellHeight);
    }
}
