package com.deathreturn.client.screen;

import com.deathreturn.client.RewindController;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 回溯过场屏：标题 + 阶段文字 + 进度条；出错时显示错误与返回按钮。
 */
public class RewindScreen extends Screen {
	private static final int BAR_WIDTH = 220;
	private static final int BAR_HEIGHT = 6;
	private static final int BAR_BG = 0xFF3A3A3A;
	private static final int BAR_FG = 0xFFE8A33D; // 琥珀色，呼应“时间回溯”

	private final String reason;

	public RewindScreen(String reason) {
		super(Component.translatable("deathreturn.screen.title"));
		this.reason = reason;
	}

	@Override
	protected void init() {
		super.init();
		if (RewindController.phase() == RewindController.Phase.ERROR) {
			addRenderableWidget(Button.builder(
							Component.translatable("deathreturn.screen.back_to_title"),
							button -> this.minecraft.setScreenAndShow(new net.minecraft.client.gui.screens.TitleScreen()))
					.bounds(this.width / 2 - 100, this.height / 2 + 60, 200, 20)
					.build());
		}
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false; // 回溯过程中不允许 ESC 取消
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		var text = graphics.textRenderer(GuiGraphicsExtractor.HoveredTextEffects.TOOLTIP_AND_CURSOR);
		int cx = this.width / 2;
		int y = this.height / 2 - 36;

		// 主标题（死亡触发时用红色调副标题区分）
		text.accept(TextAlignment.CENTER, cx, y, this.getTitle());

		RewindController.Phase phase = RewindController.phase();
		String subtitleKey = switch (phase) {
			case STOPPING -> "deathreturn.screen.phase.stopping";
			case RESTORING -> "deathreturn.screen.phase.restoring";
			case REENTERING -> "deathreturn.screen.phase.reentering";
			case DONE -> "deathreturn.screen.phase.done";
			case ERROR -> "deathreturn.screen.phase.error";
		};
		if ("death".equals(reason) && phase != RewindController.Phase.ERROR) {
			text.accept(TextAlignment.CENTER, cx, y + 16,
					Component.translatable("deathreturn.screen.death_note"));
		}
		text.accept(TextAlignment.CENTER, cx, y + 34, Component.translatable(subtitleKey));

		if (phase == RewindController.Phase.ERROR) {
			String err = RewindController.error();
			if (err != null) {
				text.accept(TextAlignment.CENTER, cx, y + 52, Component.literal(err));
			}
			return;
		}

		// 进度条（RESTORING 阶段为确定进度，其余阶段显示滚动感的静态条）
		float p = RewindController.progress();
		int x = cx - BAR_WIDTH / 2;
		int by = y + 56;
		graphics.fill(x, by, x + BAR_WIDTH, by + BAR_HEIGHT, BAR_BG);
		if (p >= 0f) {
			int fw = (int) (BAR_WIDTH * Mth.clamp(p, 0f, 1f));
			graphics.fill(x, by, x + fw, by + BAR_HEIGHT, BAR_FG);
		} else {
			// 不确定进度：一个来回滑动的短条
			long t = System.currentTimeMillis() % 1600;
			float frac = t < 800 ? t / 800f : (1600 - t) / 800f;
			int seg = 40;
			int fx = x + (int) ((BAR_WIDTH - seg) * frac);
			graphics.fill(fx, by, fx + seg, by + BAR_HEIGHT, BAR_FG);
		}
	}
}
