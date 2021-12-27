package ninjabrainbot.gui;

import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsDevice.WindowTranslucency;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import ninjabrainbot.Main;
import ninjabrainbot.calculator.BlindPosition;
import ninjabrainbot.calculator.BlindResult;
import ninjabrainbot.calculator.Calculator;
import ninjabrainbot.calculator.CalculatorResult;
import ninjabrainbot.calculator.Throw;
import ninjabrainbot.gui.components.CalibrationPanel;
import ninjabrainbot.gui.components.EnderEyePanel;
import ninjabrainbot.gui.components.MainButtonPanel;
import ninjabrainbot.gui.components.MainTextArea;
import ninjabrainbot.gui.components.NinjabrainBotFrame;
import ninjabrainbot.gui.components.ThemedComponent;
import ninjabrainbot.io.VersionURL;
import ninjabrainbot.util.Profiler;

/**
 * Main class for the user interface.
 */
public class GUI {

	private final MainTextArea mainTextArea;
	private final EnderEyePanel enderEyePanel;

	public NinjabrainBotFrame frame;
	public OptionsFrame optionsFrame;
	private final NotificationsFrame notificationsFrame;
	private final CalibrationPanel calibrationPanel;

	public Theme theme;
	public SizePreference size;
	private final ArrayList<ThemedComponent> themedComponents;

	public Timer autoResetTimer;
	private static final int AUTO_RESET_DELAY = 15 * 60 * 1000;

	public static final int MAX_THROWS = 10;
	private final Calculator calculator;
	private ArrayList<Throw> eyeThrows;
	private ArrayList<Throw> eyeThrowsLast;

	private final Font font;

	public GUI() {
		theme = Theme.get(Main.preferences.theme.get());
		size = SizePreference.get(Main.preferences.size.get());
		font = new Font("雅黑", Font.BOLD, 25);
		Locale.setDefault(Locale.US);
		themedComponents = new ArrayList<>();
		calculator = new Calculator();
		eyeThrows = new ArrayList<>();
		eyeThrowsLast = new ArrayList<>();

		Profiler.start("Create frame");
		frame = new NinjabrainBotFrame(this);
		notificationsFrame = frame.getNotificationsFrame();

		// Set application icon
		Profiler.stopAndStart("Set app icon");
		URL iconURL = Main.class.getResource("/resources/icon.png");
		assert iconURL != null;
		ImageIcon img = new ImageIcon(iconURL);
		frame.setIconImage(img.getImage());

		Profiler.stopAndStart("Create gui components");
		// Main text
		Profiler.stopAndStart("Create main text area");
		mainTextArea = new MainTextArea(this);
		frame.add(mainTextArea);

		// "Throws" text
		Profiler.stopAndStart("Create main button area");
		MainButtonPanel mainButtonPanel = new MainButtonPanel(this);
		frame.add(mainButtonPanel);

		// Throw panels
		Profiler.stopAndStart("Create throw panels");
		enderEyePanel = new EnderEyePanel(this);
		frame.add(enderEyePanel);

		// Settings window
		Profiler.stopAndStart("Create settings window");
		optionsFrame = new OptionsFrame(this);
		calibrationPanel = optionsFrame.getCalibrationPanel();
		Profiler.stop();

		Profiler.stopAndStart("Update fonts and colors");
		updateFontsAndColors();
		Profiler.stopAndStart("Update bounds");
		updateBounds();
		checkIfOffScreen(frame);
		Profiler.stopAndStart("Set visible");
		frame.setVisible(true);
		Profiler.stopAndStart("Set translucency");
		setTranslucent(Main.preferences.translucent.get());

		// Auto reset timer
		autoResetTimer = new Timer(AUTO_RESET_DELAY, p -> {
			resetThrows();
			autoResetTimer.stop();
		});
	}

	public void setTranslucent(boolean t) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		GraphicsDevice gd = ge.getDefaultScreenDevice();
		if (gd.isWindowTranslucencySupported(WindowTranslucency.TRANSLUCENT)) {
			frame.setOpacity(t ? 0.75f : 1.0f);
		}
	}

	public void setAlwaysOnTop(boolean b) {
		frame.setAlwaysOnTop(b);
		optionsFrame.setAlwaysOnTop(b);
		notificationsFrame.setAlwaysOnTop(b);
	}

	public void setNotificationsEnabled(boolean b) {
		frame.getNotificationsButton().setVisible(b && frame.getNotificationsButton().hasURL());
	}

	public void updateTheme() {
		theme = Theme.get(Main.preferences.theme.get());
		updateFontsAndColors();
	}

	public void updateSizePreference() {
		size = SizePreference.get(Main.preferences.size.get());
		updateFontsAndColors();
		updateBounds();
	}

	public void setNetherCoordsEnabled(boolean b) {
		mainTextArea.setNetherCoordsEnabled(b);
	}

	public void setAdvancedOptionsEnabled(boolean b) {
		optionsFrame.setAdvancedOptionsEnabled(b);
	}

	public void setAngleErrorsEnabled(boolean b) {
		enderEyePanel.setAngleErrorsEnabled(b);
		updateBounds();
	}

	public Font fontSize(float size, boolean light) {
		return font.deriveFont(Font.BOLD, size);
	}

	public void registerThemedComponent(ThemedComponent c) {
		themedComponents.add(c);
	}

	private void updateBounds() {
		for (ThemedComponent tc : themedComponents) {
			tc.updateSize(this);
		}
		updateFontsAndColors();
		frame.updateBounds(this);
		optionsFrame.updateBounds(this);
		notificationsFrame.updateBounds(this);
		frame.setSize(size.WIDTH, frame.getPreferredSize().height);
		frame.setShape(new RoundRectangle2D.Double(0, 0, frame.getWidth(), frame.getHeight(), size.WINDOW_ROUNDING, size.WINDOW_ROUNDING));
	}

	private void updateFontsAndColors() {
		// Color and font
		frame.getContentPane().setBackground(theme.COLOR_NEUTRAL);
		frame.setBackground(theme.COLOR_NEUTRAL);
		optionsFrame.updateFontsAndColors();
		notificationsFrame.updateFontsAndColors();
		for (ThemedComponent tc : themedComponents) {
			tc.updateColors(this);
			tc.updateSize(this);
		}
	}

	private final FontRenderContext frc = new FontRenderContext(null, true, false);
	public int getTextWidth(String text, Font font) {
		return (int) font.getStringBounds(text, frc).getWidth();
	}

	public void toggleOptionsWindow() {
		if (optionsFrame.isVisible()) {
			optionsFrame.close();
		} else {
			optionsFrame.setVisible(true);
			Rectangle bounds = frame.getBounds();
			optionsFrame.setLocation(bounds.x + 40, bounds.y + 30);
		}
	}

	public void toggleMinimized() {
		frame.toggleMinimized();
	}

	public void resetThrows() {
		if (eyeThrows.size() > 0) {
			ArrayList<Throw> temp = eyeThrowsLast;
			eyeThrowsLast = eyeThrows;
			eyeThrows = temp;
			eyeThrows.clear();
			onThrowsUpdated();
		}
		mainTextArea.onReset();
	}

	public void undo() {
		ArrayList<Throw> temp = eyeThrowsLast;
		eyeThrowsLast = eyeThrows;
		eyeThrows = temp;
		onThrowsUpdated();
	}

	public void removeThrow(Throw t) {
		if (eyeThrows.contains(t)) {
			saveThrowsForUndo();
			eyeThrows.remove(t);
			onThrowsUpdated();
		}
	}

	private void processClipboardUpdate(String clipboard) {
		Throw t = Throw.parseF3C(clipboard);
		if (!calibrationPanel.isCalibrating()) {
			int i = eyeThrows.size();
			if (t != null) {
				if (i < MAX_THROWS) {
					saveThrowsForUndo();
					eyeThrows.add(t);
					enderEyePanel.setThrow(i, t);
					onThrowsUpdated();
				}
			} else if (eyeThrows.size() == 0) {
				BlindPosition b = BlindPosition.parseF3C(clipboard);
				if (b != null) {
					BlindResult result = calculator.blind(b, true);
					mainTextArea.setResult(result, this);
				}
			}
		} else {
			if (t != null) {
				try {
					calibrationPanel.add(t);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public void changeLastAngle(double delta) {
		if (!calibrationPanel.isCalibrating()) {
			int i = eyeThrows.size() - 1;
			if (i == -1) {
				return;
			}
			Throw last = eyeThrows.get(i);
			Throw t = new Throw(last.x, last.z, last.alpha + delta, last.correction + delta);
			saveThrowsForUndo();
			eyeThrows.remove(last);
			eyeThrows.add(t);
			enderEyePanel.setThrow(i, t);
			onThrowsUpdated();
		} else {
			calibrationPanel.changeLastAngle(delta);
		}
	}

	public void toggleLastSTD() {
		if (!calibrationPanel.isCalibrating()) {
			int i = eyeThrows.size() - 1;
			if (i == -1) {
				return;
			}
			Throw last = eyeThrows.get(i);
			Throw t = last.withToggledSTD();
			saveThrowsForUndo();
			eyeThrows.remove(last);
			eyeThrows.add(t);
			enderEyePanel.setThrow(i, t);
			onThrowsUpdated();
		}
	}

	private void setUpdateURL(VersionURL url) {
		frame.setURL(url);
	}

	public void recalculateStronghold() {
		onThrowsUpdated();
	}

	private void onThrowsUpdated() {
		CalculatorResult result = null;
		double[] errors = null;
		if (eyeThrows.size() >= 1) {
			result = calculator.triangulate(eyeThrows);
			if (result.success()) {
				errors = result.getAngleErrors();
			}
		}
		mainTextArea.setResult(result, this);
		enderEyePanel.setErrors(errors);
		// Update throw panels
		enderEyePanel.setThrows(eyeThrows);
		// Update auto reset timer
		if (Main.preferences.autoReset.get()) {
			autoResetTimer.restart();
		}
		// Update bounds
		updateBounds();
	}

	public void onClipboardUpdated(String newClipboard) {
		SwingUtilities.invokeLater(() -> processClipboardUpdate(newClipboard));
	}

	public void onNewUpdateAvailable(VersionURL url) {
		SwingUtilities.invokeLater(() -> setUpdateURL(url));
	}

	private void saveThrowsForUndo() {
		eyeThrowsLast.clear();
		eyeThrowsLast.addAll(eyeThrows);
	}

	public Calculator getTriangulator() {
		return this.calculator;
	}

	private void checkIfOffScreen(JFrame frame) {
		GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
		for (GraphicsDevice gd : ge.getScreenDevices()) {
			if (gd.getDefaultConfiguration().getBounds().contains(frame.getBounds())) {
				return;
			}
		}
		frame.setLocation(100, 100);
	}

}
