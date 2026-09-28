package org.joinmastodon.android.fork;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import static org.junit.Assert.*;

/**
 * Golden image comparison for Robolectric tests, for layout that is easier to see than to assert.
 * Needs {@code @GraphicsMode(NATIVE)}, or nothing is drawn.
 *
 * Goldens live in {@code src/test/screenshots}. Re-record them after an intentional change with:
 *
 * <pre>./gradlew testDebugUnitTest -Dfork.screenshots.record=true</pre>
 *
 * and read the diff before committing. Failures write the actual image, the golden and a diff
 * mask to {@code build/reports/fork-screenshots}.
 *
 * These cover geometry, not pixels: the tolerance absorbs small font rendering
 * differences between machines, so a moved or clipped view fails while a re-hinted glyph doesn't.
 * Unit tests compile against android.jar, so this uses Android's own imaging rather than
 * ImageIO.
 */
class ForkScreenshot{
	/** Per-channel difference that counts as a changed pixel. */
	private static final int CHANNEL_TOLERANCE=24;
	/** Share of changed pixels that counts as a changed image. */
	private static final double MAX_CHANGED_FRACTION=0.001;

	static void assertMatchesGolden(String name, View root) throws IOException{
		Bitmap actual=draw(root);
		File golden=new File(goldenDir(), name+".png");

		if(Boolean.getBoolean("fork.screenshots.record") || !golden.exists()){
			write(actual, golden);
			if(!Boolean.getBoolean("fork.screenshots.record"))
				fail("No golden for \""+name+"\", so one was recorded at "+golden+". Look at it, and "+
						"commit it if it's right.");
			return;
		}

		Bitmap expected=BitmapFactory.decodeFile(golden.getAbsolutePath());
		assertNotNull("can't read the golden at "+golden, expected);
		if(expected.getWidth()!=actual.getWidth() || expected.getHeight()!=actual.getHeight()){
			File dir=writeFailure(name, actual, expected, null);
			fail("\""+name+"\" is "+actual.getWidth()+"x"+actual.getHeight()+", the golden is "+
					expected.getWidth()+"x"+expected.getHeight()+". Images in "+dir+".");
		}

		Bitmap diff=Bitmap.createBitmap(actual.getWidth(), actual.getHeight(), Bitmap.Config.ARGB_8888);
		int changed=0;
		for(int y=0; y<actual.getHeight(); y++){
			for(int x=0; x<actual.getWidth(); x++){
				if(differs(expected.getPixel(x, y), actual.getPixel(x, y))){
					changed++;
					diff.setPixel(x, y, 0xFFFF0000);
				}
			}
		}
		double fraction=(double) changed/(actual.getWidth()*actual.getHeight());
		if(fraction>MAX_CHANGED_FRACTION){
			File dir=writeFailure(name, actual, expected, diff);
			fail(String.format("\"%s\" differs from its golden in %.2f%% of pixels (max %.2f%%). "+
							"Images in %s. If the change is intended, re-record with "+
							"-Dfork.screenshots.record=true.",
					name, fraction*100, MAX_CHANGED_FRACTION*100, dir));
		}
	}

	private static boolean differs(int a, int b){
		for(int shift : new int[]{24, 16, 8, 0}){
			if(Math.abs(((a>>shift)&0xFF)-((b>>shift)&0xFF))>CHANNEL_TOLERANCE)
				return true;
		}
		return false;
	}

	private static Bitmap draw(View root){
		assertTrue("the view has no size; measure and lay it out first", root.getWidth()>0 && root.getHeight()>0);
		Bitmap bitmap=Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
		root.draw(new Canvas(bitmap));
		return bitmap;
	}

	private static void write(Bitmap bitmap, File file) throws IOException{
		file.getParentFile().mkdirs();
		try(FileOutputStream out=new FileOutputStream(file)){
			assertTrue("failed to encode "+file, bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
		}
	}

	private static File writeFailure(String name, Bitmap actual, Bitmap expected, Bitmap diff) throws IOException{
		File dir=new File(moduleDir(), "build/reports/fork-screenshots");
		write(actual, new File(dir, name+"-actual.png"));
		write(expected, new File(dir, name+"-golden.png"));
		if(diff!=null)
			write(diff, new File(dir, name+"-diff.png"));
		return dir;
	}

	private static File goldenDir(){
		return new File(moduleDir(), "src/test/screenshots");
	}

	/** Unit tests run with the module directory as the working directory; don't count on it. */
	private static File moduleDir(){
		File dir=new File("").getAbsoluteFile();
		for(int i=0; i<4 && dir!=null; i++, dir=dir.getParentFile()){
			if(new File(dir, "src/test/java").isDirectory())
				return dir;
			if(new File(dir, "mastodon/src/test/java").isDirectory())
				return new File(dir, "mastodon");
		}
		throw new AssertionError("can't find the module directory from "+new File("").getAbsolutePath());
	}
}
