package com.example.exanira.client;

import com.example.exanira.network.EventChoicePacket;
import com.example.exanira.network.EventStartPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Proper Screen for event interactions — fully captures the mouse.
 * Opened by right-clicking the Radio item when {@link ClientEventState#isActive()}.
 * Player can close with Escape and reopen later; the event remains active on the server.
 */

/* MADE USING CHATGPT, REVIEW USING CLAUDE */

@OnlyIn(Dist.CLIENT)
public class EventScreen extends Screen {

    private static final int PANEL_W = 370;
    private static final int DIALOGUE_H = 90;
    private static final int BUTTON_H = 24;
    private static final int BUTTON_GAP = 5;
    private static final int PAD = 12;
    private static final int SCROLLBAR_W = 6;

    // Computed in init(), used in renderables
    private int panelX, panelY, panelH;

    // Dialogue scrolling
    private int scrollLines = 0;
    private int maxScrollLines = 0;

    public EventScreen() {
        super(Component.translatable("gui.exanira.event"));
    }

    @Override
    protected void init() {
        List<EventStartPacket.ChoiceData> choices = ClientEventState.getChoices();

        panelH = DIALOGUE_H
                + PAD
                + choices.size() * (BUTTON_H + BUTTON_GAP)
                - BUTTON_GAP
                + PAD;

        panelX = (width - PANEL_W) / 2;
        panelY = (height - panelH) / 2;

        // Compute dialogue scroll limits
        List<FormattedCharSequence> wrapped = getWrappedDialogue();

        int visibleLines =
                (DIALOGUE_H - 30) / (font.lineHeight + 2);

        maxScrollLines =
                Math.max(0, wrapped.size() - visibleLines);

        scrollLines = Math.min(scrollLines, maxScrollLines);

        int btnW = PANEL_W - PAD * 2;
        int btnStartY = panelY + DIALOGUE_H + PAD;

        // 1. Panel background
        addRenderableOnly((g, mx, my, pt) -> drawPanel(g));

        // 2. Title + dialogue
        addRenderableOnly((g, mx, my, pt) -> renderDialogue(g));

        // 3. Choice buttons
        if (choices.isEmpty()) {
            Button continueBtn = Button.builder(
                            Component.literal("[ Continue ]"),
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

                Button btn = Button.builder(
                                Component.literal(label),
                                b -> onChoiceClicked(idx))
                        .bounds(panelX + PAD, by, btnW, BUTTON_H)
                        .build();

                btn.active = c.available();

                if (!c.available() && !c.lockedText().isEmpty()) {
                    btn.setTooltip(
                            Tooltip.create(
                                    Component.literal(c.lockedText())
                            )
                    );
                }

                addRenderableWidget(btn);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Rendering
    // ─────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
    }

    private void drawPanel(GuiGraphics g) {
        g.fill(
                panelX,
                panelY,
                panelX + PANEL_W,
                panelY + panelH,
                0xEE000000
        );

        g.renderOutline(
                panelX,
                panelY,
                PANEL_W,
                panelH,
                0xFF888888
        );

        // Title divider
        g.fill(
                panelX + 8,
                panelY + 18,
                panelX + PANEL_W - 8,
                panelY + 19,
                0xFF444444
        );

        // Choices divider
        g.fill(
                panelX + 8,
                panelY + DIALOGUE_H - 2,
                panelX + PANEL_W - 8,
                panelY + DIALOGUE_H - 1,
                0xFF444444
        );
    }

    private void renderDialogue(GuiGraphics g) {
        g.drawCenteredString(
                font,
                "§b§l✉ INCOMING TRANSMISSION",
                width / 2,
                panelY + 5,
                0xFFFFFFFF
        );

        List<FormattedCharSequence> wrapped = getWrappedDialogue();

        int startY = panelY + 24;
        int lineHeight = font.lineHeight + 2;

        int visibleLines =
                (DIALOGUE_H - 30) / lineHeight;

        int end =
                Math.min(
                        wrapped.size(),
                        scrollLines + visibleLines
                );

        int y = startY;

        for (int i = scrollLines; i < end; i++) {
            g.drawString(
                    font,
                    wrapped.get(i),
                    panelX + PAD,
                    y,
                    0xFFCCCCCC,
                    false
            );

            y += lineHeight;
        }

        drawScrollbar(
                g,
                wrapped.size(),
                visibleLines
        );
    }

    private void drawScrollbar(
            GuiGraphics g,
            int totalLines,
            int visibleLines
    ) {
        if (totalLines <= visibleLines) {
            return;
        }

        int trackX =
                panelX + PANEL_W - PAD;

        int trackY =
                panelY + 24;

        int trackH =
                DIALOGUE_H - 30;

        g.fill(
                trackX,
                trackY,
                trackX + SCROLLBAR_W,
                trackY + trackH,
                0xFF222222
        );

        int thumbH =
                Math.max(
                        12,
                        (trackH * visibleLines) / totalLines
                );

        int thumbY =
                trackY +
                        ((trackH - thumbH) * scrollLines)
                                / Math.max(1, maxScrollLines);

        g.fill(
                trackX,
                thumbY,
                trackX + SCROLLBAR_W,
                thumbY + thumbH,
                0xFFAAAAAA
        );
    }

    private List<FormattedCharSequence> getWrappedDialogue() {
        List<FormattedCharSequence> lines = new ArrayList<>();

        for (String text : ClientEventState.getDialogue()) {
            lines.addAll(
                    font.split(
                            Component.literal(text),
                            PANEL_W - PAD * 2 - SCROLLBAR_W - 4
                    )
            );
        }

        return lines;
    }

    // ─────────────────────────────────────────────────────────────
    // Input
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double scrollX,
            double scrollY
    ) {
        if (maxScrollLines > 0) {
            scrollLines -= (int) Math.signum(scrollY);

            if (scrollLines < 0) {
                scrollLines = 0;
            }

            if (scrollLines > maxScrollLines) {
                scrollLines = maxScrollLines;
            }

            return true;
        }

        return super.mouseScrolled(
                mouseX,
                mouseY,
                scrollX,
                scrollY
        );
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    /**
     * Prefixes the requirement tag (e.g. "[PER 3+]") to the button label so
     * players see what stat they are using even when the check passes.
     */
    private String buildLabel(EventStartPacket.ChoiceData c) {
        String req = c.requirementText();
        return req.isEmpty()
                ? c.text()
                : req + " " + c.text();
    }

    private void onChoiceClicked(int index) {
        PacketDistributor.sendToServer(
                new EventChoicePacket(
                        ClientEventState.getInstanceKey(),
                        index
                )
        );

        // For scene advances (index >= 0 with nextScene), the server will
        // push a new EventStartPacket which triggers rebuildWidgets().
        // For end/terminal (index == -1 or no nextScene), EventEndPacket
        // closes the screen via ExaniraModClient.
        if (index == -1) {
            onClose();
        }
    }

    /**
     * Called by ExaniraModClient when a new EventStartPacket arrives
     * mid-scene-chain.
     */
    public void refresh() {
        scrollLines = 0;
        maxScrollLines = 0;
        rebuildWidgets();
    }

    // ─────────────────────────────────────────────────────────────
    // Screen behaviour
    // ─────────────────────────────────────────────────────────────

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}