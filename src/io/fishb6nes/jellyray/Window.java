package io.fishb6nes.jellyray;

import com.jogamp.newt.event.KeyEvent;
import com.jogamp.newt.event.KeyListener;
import com.jogamp.newt.event.MouseEvent;
import com.jogamp.newt.event.MouseListener;
import com.jogamp.newt.event.WindowEvent;
import com.jogamp.newt.event.WindowListener;
import com.jogamp.newt.event.WindowUpdateEvent;
import com.jogamp.newt.opengl.GLWindow;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.fixedfunc.GLMatrixFunc;
import com.jogamp.opengl.util.FPSAnimator;
import io.fishb6nes.jellyray.util.CameraMatrix;
import io.fishb6nes.jellyray.util.Vector3f;

import java.nio.FloatBuffer;
import java.util.HashSet;

abstract class Window implements GLEventListener, WindowListener, MouseListener, KeyListener {
	private static final int DEFAULT_DISPLAY_WIDTH = 1024;
	private static final int DEFAULT_DISPLAY_HEIGHT = 768;
	private static final int DEFAULT_FRAMES_PER_SECOND = 60;
	private static final int DEFAULT_FIELD_OF_VIEW = 45;

	private final GLWindow window;
	private final FPSAnimator animator;
	private final CameraMatrix cameraMatrix;
	private final HashSet<Short> keys;

	private double aspectRatio;
	private double fieldOfView;

	private Vector3f mouseAnchor;

	Window(String title, CameraMatrix matrix) {
		window = GLWindow.create(new GLCapabilities(GLProfile.getDefault()));
		animator = new FPSAnimator(window, DEFAULT_FRAMES_PER_SECOND);
		cameraMatrix = matrix;
		keys = new HashSet<>();

		aspectRatio = (double) DEFAULT_DISPLAY_WIDTH / (double) DEFAULT_DISPLAY_HEIGHT;
		fieldOfView = (double) DEFAULT_FIELD_OF_VIEW;

		window.setTitle(title);
		window.setSurfaceSize(DEFAULT_DISPLAY_WIDTH, DEFAULT_DISPLAY_HEIGHT);

		window.addGLEventListener(this);
		window.addWindowListener(this);
		window.addMouseListener(this);
		window.addKeyListener(this);

		window.setVisible(true);
		animator.start();
	}

	CameraMatrix getCameraMatrix() {
		return cameraMatrix;
	}

	int getDisplayWidth() {
		return window.getSurfaceWidth();
	}

	int getDisplayHeight() {
		return window.getSurfaceHeight();
	}

	double getAspectRatio() {
		return aspectRatio;
	}

	double getFieldOfView() {
		return fieldOfView;
	}

	void setTitle(String title) {
		window.setTitle(title);
	}

	abstract FloatBuffer getRender();

	abstract void pause(boolean flag);

	abstract void shutdown();


	// ------------------------------------------------------------
	// GLEventListener
	// ------------------------------------------------------------
	@Override
	public void init(GLAutoDrawable drawable) {
		drawable.getGL().getGL2().glMatrixMode(GLMatrixFunc.GL_PROJECTION);
	}

	@Override
	public void dispose(GLAutoDrawable drawable) {

	}

	@Override
	public void display(GLAutoDrawable drawable) {
		updateCameraPosition();

		GL2 gl2 = drawable.getGL().getGL2();
		gl2.glClear(GL.GL_COLOR_BUFFER_BIT);

		if (getRender() != null)
			gl2.glDrawPixels(window.getSurfaceWidth(), window.getSurfaceHeight(), GL.GL_RGB, GL.GL_FLOAT, getRender());
	}

	@Override
	public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {

	}


	// ------------------------------------------------------------
	// WindowListener
	// ------------------------------------------------------------
	@Override
	public void windowResized(WindowEvent event) {

	}

	@Override
	public void windowMoved(WindowEvent event) {

	}

	@Override
	public void windowDestroyNotify(WindowEvent event) {
		animator.stop();
		shutdown();
		System.exit(0);
	}

	@Override
	public void windowDestroyed(WindowEvent event) {

	}

	@Override
	public void windowGainedFocus(WindowEvent event) {
		pause(false);
	}

	@Override
	public void windowLostFocus(WindowEvent event) {
		pause(true);
	}

	@Override
	public void windowRepaint(WindowUpdateEvent event) {

	}


	// ------------------------------------------------------------
	// MouseListener
	// ------------------------------------------------------------
	@Override
	public void mouseClicked(MouseEvent event) {

	}

	@Override
	public void mouseEntered(MouseEvent event) {

	}

	@Override
	public void mouseExited(MouseEvent event) {
		if (mouseAnchor != null) {
			cameraMatrix.rotate(event.getX() - mouseAnchor.x, mouseAnchor.y - event.getY());
			mouseAnchor = null;
		}
	}

	@Override
	public void mousePressed(MouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON3)
			mouseAnchor = new Vector3f(event.getX(), event.getY(), 0);
	}

	@Override
	public void mouseReleased(MouseEvent event) {
		if (event.getButton() == MouseEvent.BUTTON3 && mouseAnchor != null) {
			cameraMatrix.rotate(event.getX() - mouseAnchor.x, mouseAnchor.y - event.getY());
			mouseAnchor = null;
		}
	}

	@Override
	public void mouseMoved(MouseEvent event) {

	}

	@Override
	public void mouseDragged(MouseEvent event) {
		if (mouseAnchor != null) {
			cameraMatrix.rotate(event.getX() - mouseAnchor.x, mouseAnchor.y - event.getY());
			mouseAnchor = new Vector3f(event.getX(), event.getY(), 0);
		}
	}

	@Override
	public void mouseWheelMoved(MouseEvent event) {

	}


	// ------------------------------------------------------------
	// KeyListener
	// ------------------------------------------------------------
	@Override
	public void keyPressed(KeyEvent event) {
		if (event.isAutoRepeat())
			return;
		keys.add(event.getKeyCode());
	}

	@Override
	public void keyReleased(KeyEvent event) {
		if (event.isAutoRepeat())
			return;
		keys.remove(event.getKeyCode());
	}

	private void updateCameraPosition() {
		if (keys.contains(KeyEvent.VK_W) || keys.contains(KeyEvent.VK_UP))
			cameraMatrix.moveForward();
		if (keys.contains(KeyEvent.VK_A) || keys.contains(KeyEvent.VK_LEFT))
			cameraMatrix.moveLeft();
		if (keys.contains(KeyEvent.VK_S) || keys.contains(KeyEvent.VK_DOWN))
			cameraMatrix.moveBackward();
		if (keys.contains(KeyEvent.VK_D) || keys.contains(KeyEvent.VK_RIGHT))
			cameraMatrix.moveRight();
		if (keys.contains(KeyEvent.VK_SPACE))
			cameraMatrix.moveUp();
		if (keys.contains(KeyEvent.VK_SHIFT))
			cameraMatrix.moveDown();
	}
}
