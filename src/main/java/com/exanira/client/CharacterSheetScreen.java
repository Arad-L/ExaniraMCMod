package com.exanira.client;

import com.exanira.character.Stat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

@OnlyIn(Dist.CLIENT)
public class CharacterSheetScreen extends Screen {

    private static final int WIDTH = 210;
    private static final int HEIGHT = 240;

    public CharacterSheetScreen() {
        super(Component.translatable("gui.exanira.character_sheet"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        int x = (this.width - WIDTH) / 2;
        int y = (this.height - HEIGHT) / 2;

        // Background
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xCC000000);
        graphics.renderOutline(x, y, WIDTH, HEIGHT, 0xFFFFFFFF);

        // Title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, y + 8, 0xFFFFFF);

        // Divider under title
        graphics.fill(x + 8, y + 18, x + WIDTH - 8, y + 19, 0xFF555555);

        // Stats
        int curY = y + 24;
        for (Stat stat : Stat.values()) {
            String line = stat.displayName() + ":  " + ClientCharacterData.getStat(stat);
            graphics.drawString(this.font, line, x + 12, curY, 0xDDDDDD, false);
            curY += 12;
        }

        // Divider above backstory
        curY += 3;
        graphics.fill(x + 8, curY, x + WIDTH - 8, curY + 1, 0xFF555555);
        curY += 5;

        // Backstory label
        graphics.drawString(this.font, "Backstory:", x + 12, curY, 0xFFFF88, false);
        curY += 12;

        // Backstory text (word-wrapped)
        List<FormattedCharSequence> lines = this.font.split(
                Component.literal(ClientCharacterData.getBackstory()), WIDTH - 24
        );
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x + 12, curY, 0xCCCCCC, false);
            curY += 10;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
