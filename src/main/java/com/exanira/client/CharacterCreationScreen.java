package com.exanira.client;

import com.exanira.character.CharacterCreationDefs;
import com.exanira.character.LifestyleOption;
import com.exanira.character.LifestyleQuestion;
import com.exanira.character.Profession;
import com.exanira.network.CharacterCreationSubmitPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Multi-step character creation screen shown on first login.
 *
 * Step 0   : Profession selection (8 options in a 2-column grid)
 * Steps 1–5: Lifestyle questions (3 options each)
 * Step 6   : Confirmation — "Begin Your Story" button submits to server
 *
 * Escape is blocked; the screen must be completed to proceed.
 * No live stat preview is shown during selection (reveal happens via CharacterSheetScreen after creation).
 */
@OnlyIn(Dist.CLIENT)
public class CharacterCreationScreen extends Screen {

    private static final int BG_W = 300;
    private static final int BG_H = 240;
    private static final int TOTAL_STEPS = CharacterCreationDefs.QUESTIONS.size(); // 5

    private int step = 0;
    private int professionChoice = -1;
    private final List<Integer> lifestyleChoices = new ArrayList<>();

    public CharacterCreationScreen() {
        super(Component.translatable("gui.exanira.character_creation"));
    }

    // -------------------------------------------------------------------------
    // Widget setup (called by init() and rebuildWidgets())
    // -------------------------------------------------------------------------

    @Override
    protected void init() {
        int bx = (this.width - BG_W) / 2;
        int by = (this.height - BG_H) / 2;

        // Add background panel FIRST — renders below all buttons in widget draw order
        this.addRenderableOnly((g, mx, my, pt) -> drawPanel(g, bx, by));

        // Add labels SECOND — renders above panel, below buttons (no positional overlap)
        this.addRenderableOnly((g, mx, my, pt) -> renderLabels(g));

        // Add interactive buttons LAST
        if (step == 0) {
            addProfessionButtons(bx, by);
        } else if (step >= 1 && step <= TOTAL_STEPS) {
            addLifestyleButtons(bx, by);
        } else {
            addConfirmButton(bx, by);
        }
    }

    private void addProfessionButtons(int bx, int by) {
        int btnW = 135, btnH = 20, btnGap = 4, colGap = 10;
        int startX = bx + 10;
        int startY = by + 40;
        Profession[] profs = Profession.values();
        for (int i = 0; i < profs.length; i++) {
            final int idx = i;
            int col = i % 2;
            int row = i / 2;
            int x = startX + col * (btnW + colGap);
            int y = startY + row * (btnH + btnGap);
            this.addRenderableWidget(Button.builder(
                    Component.literal(profs[i].displayName()),
                    btn -> selectProfession(idx)
            ).bounds(x, y, btnW, btnH).build());
        }
    }

    private void addLifestyleButtons(int bx, int by) {
        LifestyleQuestion q = CharacterCreationDefs.QUESTIONS.get(step - 1);
        int btnW = 260, btnH = 26, btnGap = 8;
        int startX = bx + (BG_W - btnW) / 2;
        int startY = by + 92;
        List<LifestyleOption> opts = q.options();
        for (int i = 0; i < opts.size(); i++) {
            final int optIdx = i;
            this.addRenderableWidget(Button.builder(
                    Component.literal(opts.get(i).buttonText()),
                    btn -> selectLifestyle(optIdx)
            ).bounds(startX, startY + i * (btnH + btnGap), btnW, btnH).build());
        }
    }

    private void addConfirmButton(int bx, int by) {
        int btnW = 160, btnH = 24;
        this.addRenderableWidget(Button.builder(
                Component.translatable("gui.exanira.character_creation.begin"),
                btn -> confirm()
        ).bounds(bx + (BG_W - btnW) / 2, by + 168, btnW, btnH).build());
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    private void selectProfession(int idx) {
        professionChoice = idx;
        step = 1;
        this.rebuildWidgets();
    }

    private void selectLifestyle(int optIdx) {
        lifestyleChoices.add(optIdx);
        step++;
        this.rebuildWidgets();
    }

    private void confirm() {
        PacketDistributor.sendToServer(
                new CharacterCreationSubmitPacket(professionChoice, new ArrayList<>(lifestyleChoices))
        );
        this.onClose();
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /** Render order: dim overlay (from super) → panel → labels → buttons */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // All custom drawing is handled by addRenderableOnly entries in init()
    }

    private void drawPanel(GuiGraphics g, int bx, int by) {
        g.fill(bx, by, bx + BG_W, by + BG_H, 0xCC000000);
        g.renderOutline(bx, by, BG_W, BG_H, 0xFFFFFFFF);
        g.fill(bx + 8, by + 35, bx + BG_W - 8, by + 36, 0xFF555555);
    }

    private void renderLabels(GuiGraphics graphics) {
        int bx = (this.width - BG_W) / 2;
        int by = (this.height - BG_H) / 2;
        int cx = this.width / 2;

        if (step == 0) {
            graphics.drawCenteredString(font, "Who were you?", cx, by + 10, 0xFFFFFF);
            graphics.drawCenteredString(font, "Choose your profession.", cx, by + 24, 0xAAAAAA);

        } else if (step >= 1 && step <= TOTAL_STEPS) {
            LifestyleQuestion q = CharacterCreationDefs.QUESTIONS.get(step - 1);
            graphics.drawCenteredString(font, q.title(), cx, by + 10, 0xFFFFFF);
            graphics.drawCenteredString(font,
                    "Question " + step + " of " + TOTAL_STEPS,
                    cx, by + 24, 0x888888);
            // Wrapped body text
            List<FormattedCharSequence> lines = font.split(
                    Component.literal(q.bodyText()), BG_W - 24);
            int ty = by + 44;
            for (FormattedCharSequence line : lines) {
                graphics.drawCenteredString(font, line, cx, ty, 0xAAAAAA);
                ty += 10;
            }

        } else {
            // Confirmation step
            graphics.drawCenteredString(font, "Your story has been written.", cx, by + 100, 0xFFFFFF);
            graphics.drawCenteredString(font, "Step into the world.", cx, by + 118, 0xAAAAAA);
        }
    }

    // -------------------------------------------------------------------------
    // Behaviour
    // -------------------------------------------------------------------------

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    /** Block Escape — the player must complete character creation to proceed. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
