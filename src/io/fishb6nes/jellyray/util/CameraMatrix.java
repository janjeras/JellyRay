package io.fishb6nes.jellyray.util;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLMemory;

import java.nio.FloatBuffer;

public class CameraMatrix {
	private static final int MATRIX_SIZE = 16;
	private static final int M_11 = 0;
	private static final int M_12 = 1;
	private static final int M_13 = 2;
	private static final int M_14 = 3;
	private static final int M_21 = 4;
	private static final int M_22 = 5;
	private static final int M_23 = 6;
	private static final int M_24 = 7;
	private static final int M_31 = 8;
	private static final int M_32 = 9;
	private static final int M_33 = 10;
	private static final int M_34 = 11;
	private static final int M_41 = 12;
	private static final int M_42 = 13;
	private static final int M_43 = 14;
	private static final int M_44 = 15;

	private static final float MOVE_SPEED = 1f;
	private static final double SENSITIVITY = 0.005;
	private static final double MAX_PITCH = 1;
	private static final double MIN_PITCH = -1;
	private static final double MAX_YAW = 2 * Math.PI;

	private final CLBuffer<FloatBuffer> buffer;

	private Vector3f position;
	private Vector3f direction;
	private double yaw;
	private double pitch;

	public CameraMatrix(CLContext context) {
		buffer = context.createFloatBuffer(MATRIX_SIZE, CLMemory.Mem.WRITE_ONLY);
		update(getIdentityMatrix());

		position = new Vector3f();
		direction = new Vector3f(0, 0, -1);
	}

	public CLBuffer<FloatBuffer> getBuffer() {
		return buffer;
	}

	private float[] getIdentityMatrix() {
		return new float[]{
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0,
				0, 0, 0, 1};
	}

	private float[] getTranslationMatrix() {
		float[] matrix = getIdentityMatrix();
		matrix[M_14] = position.x;
		matrix[M_24] = position.y;
		matrix[M_34] = position.z;
		return matrix;
	}

	private float[] getYawRotationMatrix() {
		float cos = (float) Math.cos(yaw);
		float sin = (float) Math.sin(yaw);
		float[] matrix = getIdentityMatrix();
		matrix[M_11] = cos;
		matrix[M_13] = sin;
		matrix[M_31] = -sin;
		matrix[M_33] = cos;
		return matrix;
	}

	private float[] getPitchRotationMatrix() {
		float cos = (float) Math.cos(pitch);
		float sin = (float) Math.sin(pitch);
		float[] matrix = getIdentityMatrix();
		matrix[M_22] = cos;
		matrix[M_23] = -sin;
		matrix[M_32] = sin;
		matrix[M_33] = cos;
		return matrix;
	}

	private Vector3f getDirectionVector() {
		Vector3f v = new Vector3f(0, 0, -1);
		float[] m = multiply(getYawRotationMatrix(), getPitchRotationMatrix());

		Vector3f result = new Vector3f();
		result.x = v.x * m[M_11] + v.y * m[M_12] + v.z * m[M_13] + m[M_14];
		result.y = v.x * m[M_21] + v.y * m[M_22] + v.z * m[M_23] + m[M_24];
		result.z = v.x * m[M_31] + v.y * m[M_32] + v.z * m[M_33] + m[M_34];
		return result;
	}

	private float[] getCameraMatrix() {
		return multiply(multiply(getTranslationMatrix(), getYawRotationMatrix()), getPitchRotationMatrix());
	}

	private float[] multiply(float[] m1, float[] m2) {
		float[] result = new float[16];
		result[M_11] = m1[M_11] * m2[M_11] + m1[M_12] * m2[M_21] + m1[M_13] * m2[M_31] + m1[M_14] * m2[M_41];
		result[M_12] = m1[M_11] * m2[M_12] + m1[M_12] * m2[M_22] + m1[M_13] * m2[M_32] + m1[M_14] * m2[M_42];
		result[M_13] = m1[M_11] * m2[M_13] + m1[M_12] * m2[M_23] + m1[M_13] * m2[M_33] + m1[M_14] * m2[M_43];
		result[M_14] = m1[M_11] * m2[M_14] + m1[M_12] * m2[M_24] + m1[M_13] * m2[M_34] + m1[M_14] * m2[M_44];

		result[M_21] = m1[M_21] * m2[M_11] + m1[M_22] * m2[M_21] + m1[M_23] * m2[M_31] + m1[M_24] * m2[M_41];
		result[M_22] = m1[M_21] * m2[M_12] + m1[M_22] * m2[M_22] + m1[M_23] * m2[M_32] + m1[M_24] * m2[M_42];
		result[M_23] = m1[M_21] * m2[M_13] + m1[M_22] * m2[M_23] + m1[M_23] * m2[M_33] + m1[M_24] * m2[M_43];
		result[M_24] = m1[M_21] * m2[M_14] + m1[M_22] * m2[M_24] + m1[M_23] * m2[M_34] + m1[M_24] * m2[M_44];

		result[M_31] = m1[M_31] * m2[M_11] + m1[M_32] * m2[M_21] + m1[M_33] * m2[M_31] + m1[M_34] * m2[M_41];
		result[M_32] = m1[M_31] * m2[M_12] + m1[M_32] * m2[M_22] + m1[M_33] * m2[M_32] + m1[M_34] * m2[M_42];
		result[M_33] = m1[M_31] * m2[M_13] + m1[M_32] * m2[M_23] + m1[M_33] * m2[M_33] + m1[M_34] * m2[M_43];
		result[M_34] = m1[M_31] * m2[M_14] + m1[M_32] * m2[M_24] + m1[M_33] * m2[M_34] + m1[M_34] * m2[M_44];

		result[M_41] = m1[M_41] * m2[M_11] + m1[M_42] * m2[M_21] + m1[M_43] * m2[M_31] + m1[M_44] * m2[M_41];
		result[M_42] = m1[M_41] * m2[M_12] + m1[M_42] * m2[M_22] + m1[M_43] * m2[M_32] + m1[M_44] * m2[M_42];
		result[M_43] = m1[M_41] * m2[M_13] + m1[M_42] * m2[M_23] + m1[M_43] * m2[M_33] + m1[M_44] * m2[M_43];
		result[M_44] = m1[M_41] * m2[M_14] + m1[M_42] * m2[M_24] + m1[M_43] * m2[M_34] + m1[M_44] * m2[M_44];
		return result;
	}

	private void update(float[] matrix) {
		buffer.getBuffer().rewind();
		for (float f : matrix)
			buffer.getBuffer().put(f);
		buffer.getBuffer().rewind();
	}

	public void moveLeft() {
		position.x -= direction.z * MOVE_SPEED;
		position.z += direction.x * MOVE_SPEED;
		buffer.getBuffer().put(M_14, position.x);
		buffer.getBuffer().put(M_34, position.z);
	}

	public void moveRight() {
		position.x += direction.z * MOVE_SPEED;
		position.z -= direction.x * MOVE_SPEED;
		buffer.getBuffer().put(M_14, position.x);
		buffer.getBuffer().put(M_34, position.z);
	}

	public void moveUp() {
		position.y += MOVE_SPEED;
		buffer.getBuffer().put(M_24, position.y);
	}

	public void moveDown() {
		position.y -= MOVE_SPEED;
		buffer.getBuffer().put(M_24, position.y);
	}

	public void moveForward() {
		position.x += direction.x * MOVE_SPEED;
		position.z += direction.z * MOVE_SPEED;
		buffer.getBuffer().put(M_14, position.x);
		buffer.getBuffer().put(M_34, position.z);
	}

	public void moveBackward() {
		position.x -= direction.x * MOVE_SPEED;
		position.z -= direction.z * MOVE_SPEED;
		buffer.getBuffer().put(M_14, position.x);
		buffer.getBuffer().put(M_34, position.z);
	}

	public void rotate(double yaw, double pitch) {
		this.yaw += yaw * SENSITIVITY;
		if (this.yaw > MAX_YAW)
			this.yaw -= MAX_YAW;
		else if (this.yaw < -MAX_YAW)
			this.yaw += MAX_YAW;
		this.pitch = Math.max(Math.min(this.pitch + pitch * SENSITIVITY, MAX_PITCH), MIN_PITCH);
		direction = getDirectionVector();
		update(getCameraMatrix());
	}
}
