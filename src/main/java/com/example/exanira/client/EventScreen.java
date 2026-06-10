package com.example.exanira.client;

import com.example.exanira.network.EventChoicePacket;
import com.example.exanira.network.EventStartPacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Proper Screen for event interactions — fully captures the mouse.
 * Opened by right-clicking the Radio item when {@link ClientEventState#isActive()}.
 * Player can close with Escape and reopen later; the event remains active on the server.
 */
@OnlyIn(Dist.CLIENT)
public class EventScreen extends Screen {

    private static final int PANEL_W = 370;
    private static final int DIALOGUE_H = 90;
    private static final int BUTTON_H = 24;
    private static final int BUTTON_GAP = 5;
    private static final int PAD = 12;

    // Computed in init(), used in renderables
    private int panelX, panelY, panelH;

    public EventScreen() {
        super(Component.translatable("gui.exanira.event"));
    }

    @Override
    protected void init() {
        List<EventStartPacket.ChoiceData> choices = ClientEventState.getChoices();
        panelH = DIALOGUE_H + PAD + choices.size() * (BUTTON_H + BUTTON_GAP) - BUTTON_GAP + PAD;
        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;

        int btnW = PANEL_W - PAD * 2;
        int btnStartY = panelY + DIALOGUE_H + PAD;

        // 1. Panel background (drawn first — behind everything)
        addRenderableOnly((g, mx, my, pt) -> drawPanel(g));

        // 2. Title + dialogue text (behind buttons)
        addRenderableOnly((g, mx, my, pt) -> renderDialogue(g));

        // 3. Choice buttons, OR a "Continue" button for terminal scenes
        if (choices.isEmpty()) {
            // Terminal scene — just a dismiss button, sends choiceIndex -1 to server
            Button continueBtn = Button.builder(Component.literal("[ Continue ]"),
                            b -> onChoiceClicked(-1))
                    .bounds(panelX + PAD, btnStartY, btnW, BUTTON_H)
                    .build();
            addRenderableWidget(continueBtn);
        } else {
            for (int i = 0; i < choices.size(); i++) {
                EventStartPacket.ChoiceData c = choices.get(i);
                int by = btnStartY + i * (BUTTON_H + BUTTON_GAP);
                String label = buildLabel(c);
                final int idx = i;

                Button btn = Button.builder(Component.literal(label), b -> onChoiceClicked(idx))
                        .bounds(panelX + PAD, by, btnW, BUTTON_H)
                        .build();
                btn.active = c.available();

                if (!c.available() && !c.lockedText().isEmpty()) {
                    btn.setTooltip(Tooltip.create(Component.literal(c.lockedText())));
                }

                addRenderableWidget(btn);
            }
        }
    }

    // ─── Rendering ──────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt); // dim overlay → panel → dialogue → buttons (in registration order)
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xEE000000);
        g.renderOutline(panelX, panelY, PANEL_W, panelH, 0xFF888888);
        // Title divider
        g.fill(panelX + 8, panelY + 18, panelX + PANEL_W - 8, panelY + 19, 0xFF444444);
        // Choices divider
        g.fill(panelX + 8, panelY + DIALOGUE_H - 2, panelX + PANEL_W - 8, panelY + DIALOGUE_H - 1, 0xFF444444);
    }

    private void renderDialogue(GuiGraphics g) {
        // Title
        g.drawCenteredString(font, "§b§l✉ INCOMING TRANSMISSION", width / 2, panelY + 5, 0xFFFFFFFF);

        // Dialogue lines
        int ty = panelY + 24;
        int maxY = panelY + DIALOGUE_H - 6;
        for (String line : ClientEventState.getDialogue()) {
            if (ty >= maxY) break;
            for (FormattedCharSequence seq : font.split(Component.literal(line), PANEL_W - PAD * 2)) {
                if (ty >= maxY) break;
                g.drawString(font, seq, panelX + PAD, ty, 0xFFCCCCCC, false);
                ty += font.lineHeight + 2;
            }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Prefixes the requirement tag (e.g. "[PER 3+]") to the button label so
     * players see what stat they are using even when the check passes.
     */
    private String buildLabel(EventStartPacket.ChoiceData c) {
        String req = c.requirementText();
        return req.isEmpty() ? c.text() : req + " " + c.text();
    }

    private void onChoiceClicked(int index) {
        PacketDistributor.sendToServer(
                new EventChoicePacket(ClientEventState.getInstanceKey(), index));
        // For scene advances (index >= 0 with nextScene), the server will push a new
        // EventStartPacket which triggers rebuildWidgets(). For end/terminal (index == -1
        // or no nextScene), EventEndPacket closes the screen via ExaniraModClient.
        if (index == -1) onClose(); // terminal dismiss — server sends EventEndPacket
    }

    /** Called by ExaniraModClient when a new EventStartPacket arrives mid-scene-chain. */
    public void refresh() {
        rebuildWidgets();
    }

    // ─── Screen behaviour ───────────────────────────────────────────────────────

    @Override
    public boolean isPauseScreen() {
        return false; // world keeps ticking while the event UI is open
    }
}
