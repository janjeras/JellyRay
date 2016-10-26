package io.fishb6nes.jellyray;

import com.jogamp.opencl.CLBuffer;
import com.jogamp.opencl.CLCommandQueue;
import com.jogamp.opencl.CLContext;
import com.jogamp.opencl.CLDevice;
import com.jogamp.opencl.CLKernel;
import com.jogamp.opencl.CLMemory;
import com.jogamp.opencl.CLProgram;
import io.fishb6nes.jellyray.util.CameraMatrix;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;

public class JellyRay extends Window {
	private static final int VERSION_MAJOR = 1;
	private static final int VERSION_MINOR = 1;
	private static final int VERSION_PATCH = 0;
	private static final String TITLE = String.format("JellyRay %d.%d.%d", VERSION_MAJOR, VERSION_MINOR, VERSION_PATCH);

	private static final CLContext context = CLContext.create();

	private final CLCommandQueue queue;
	private final CLKernel kernel;

	private CLBuffer<FloatBuffer> output;

	private long localWorkSize;
	private long localWorkSizeX;
	private long localWorkSizeY;
	private long globalWorkSize;
	private long globalWorkSizeX;
	private long globalWorkSizeY;

	private boolean paused;
	private boolean shutdown;
	private double fps;

	public JellyRay() throws IOException {
		super(TITLE, new CameraMatrix(context));

		queue = context.getMaxFlopsDevice().createCommandQueue();
		InputStream raytracer = this.getClass().getResourceAsStream("kernels/RayTracer.cl");
		CLProgram program = context.createProgram(raytracer).build();
		kernel = program.createCLKernel("RayTracer");

		initialize();
		printDebug();
	}

	public static void main(String[] args) throws IOException {
		JellyRay jellyray = new JellyRay();
		while (!jellyray.shutdown) {
			while (jellyray.paused) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException ex) {
					ex.printStackTrace();
				}
			}
			jellyray.setTitle(TITLE + " @ " + jellyray.fps + " FPS");
			jellyray.execute();
		}
	}

	private void initialize() {
		localWorkSize = Math.min(256, queue.getDevice().getMaxWorkGroupSize());
		localWorkSizeX = (long) Math.sqrt(localWorkSize);
		localWorkSizeY = (long) Math.sqrt(localWorkSize);

		globalWorkSizeX = getDisplayWidth();
		globalWorkSizeY = getDisplayHeight();
		globalWorkSize = globalWorkSizeX * globalWorkSizeY;
		long remainder = globalWorkSize % localWorkSize;
		if (remainder != 0)
			globalWorkSize = globalWorkSize + localWorkSize - remainder;

		output = context.createFloatBuffer((int) globalWorkSize * 3, CLMemory.Mem.READ_ONLY, CLMemory.Mem.USE_BUFFER);
	}

	private void execute() {
		kernel.putArg(getDisplayWidth())
				.putArg(getDisplayHeight())
				.putArg((float) getAspectRatio())
				.putArg((float) Math.tan(Math.toRadians(getFieldOfView() * 0.5)))
				.putArg(getCameraMatrix().getBuffer())
				.putArg(output)
				.rewind();

		long time = System.nanoTime();
		queue.putWriteBuffer(getCameraMatrix().getBuffer(), true)
				.put2DRangeKernel(kernel, 0, 0, globalWorkSizeX, globalWorkSizeY, localWorkSizeX, localWorkSizeY)
				.putBarrier()
				.putReadBuffer(output, true);
		time = System.nanoTime() - time;

		// Only print on first frame
		if (fps == 0) {
			System.out.println("Used device memory: " + (2 * output.getCLSize()) / 1000000 + "MB");
			System.out.println("computation took: " + (time / 1000000) + "ms (" + time + "ns)");
		}

		fps += 1000000000 / time;
		fps *= 0.5;
	}

	private void printDebug() {
		String out = "Available OpenCL Devices:\n";
		for (CLDevice d : context.getDevices())
			out += " " + d + "\n";
		System.out.println(out);
		System.out.println("Using OpenCL device:\n " + queue.getDevice());
		System.out.println("\tMax work group size: " + queue.getDevice().getMaxWorkGroupSize());
		System.out.println("\tMax work item dimensions: " + queue.getDevice().getMaxWorkItemDimensions());
		System.out.println("\tMax Local Memory: " + queue.getDevice().getLocalMemSize() + " (type: " + queue.getDevice().getLocalMemType() + ")");
		System.out.println("\tMax Global Memory: " + queue.getDevice().getGlobalMemSize() + " (cache size: " + queue.getDevice().getGlobalMemCacheSize() + ")");
		System.out.println("");
		System.out.println("Computing " + getDisplayWidth() + " x " + getDisplayHeight() + " (" + getDisplayWidth() * getDisplayHeight() + ") pixels");
		System.out.println("Using a local work group size of: " + localWorkSize);
		System.out.println("Using a global work group size of: " + globalWorkSize);
	}

	@Override
	FloatBuffer getRender() {
		return output != null ? output.getBuffer() : null;
	}

	@Override
	void pause(boolean flag) {
		paused = flag;
	}

	@Override
	void shutdown() {
		shutdown = true;
	}
}
