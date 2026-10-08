package org.joinmastodon.android.fork;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.joinmastodon.android.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import me.grishka.appkit.utils.V;

import static org.junit.Assert.*;

/**
 * The welcome screen's text sits on a painting that runs from pale sky to dark towers, so nothing
 * about the background can be assumed and the letters have to carry their own contrast. They were
 * shipped twice without enough of it: first as plain dark text, then with a blurred shadow, which
 * softens the background rather than separating the glyphs from it.
 *
 * This fails if either line loses its outline. It cannot judge whether the result is readable --
 * only a person looking at it can -- so it also writes the rendered screen to
 * {@code build/reports/splash-render/} for exactly that. The screen is only reachable when signed
 * out, which on a test device means giving up the session, so rendering it here is the only
 * practical way to see it at all.
 */
@Config(sdk=35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner.class)
public class SplashLegibilityTest{
	private static final int W=360, H=800;

	private Context context;

	@Before
	public void setUp(){
		context=new ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_Mastodon_Dark);
		V.setApplicationContext(context);
	}

	private View laidOut(){
		View root=LayoutInflater.from(context).inflate(R.layout.fragment_splash, null);
		root.measure(View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
				View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY));
		root.layout(0, 0, W, H);
		return root;
	}

	/** Every line of text drawn over the artwork, which is everything above the buttons. */
	private List<TextView> textOverArt(View root){
		List<TextView> found=new ArrayList<>();
		collect(root, found);
		return found;
	}

	private void collect(View view, List<TextView> into){
		if(view instanceof TextView text && text.getText().length()>0
				&& !(view instanceof android.widget.Button)){
			into.add(text);
		}
		if(view instanceof ViewGroup group){
			for(int i=0; i<group.getChildCount(); i++)
				collect(group.getChildAt(i), into);
		}
	}

	@Test
	public void every_line_over_the_artwork_is_outlined() throws Exception{
		List<TextView> lines=textOverArt(laidOut());
		assertFalse("no text found on the welcome screen; if the layout changed, so must this test",
				lines.isEmpty());
		for(TextView line : lines){
			assertTrue("\""+line.getText()+"\" on the welcome screen is a plain "
							+line.getClass().getSimpleName()+". It is drawn over a painting that goes "
							+"from pale sky to dark towers, so it needs an outline to stay readable "
							+"wherever it lands",
					line instanceof OutlinedTextView);
			assertTrue("\""+line.getText()+"\" is an OutlinedTextView with no outline width set, "
							+"which draws exactly like a plain one",
					outlineWidth((OutlinedTextView) line)>0f);
		}
	}

	/** Not an assertion: somewhere to look at what the above is only describing. */
	@Test
	public void render_the_screen_to_look_at() throws Exception{
		View root=laidOut();
		Bitmap bmp=Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
		root.draw(new Canvas(bmp));
		File dir=new File("build/reports/splash-render");
		assertTrue(dir.mkdirs() || dir.isDirectory());
		File out=new File(dir, "welcome.png");
		try(FileOutputStream f=new FileOutputStream(out)){
			bmp.compress(Bitmap.CompressFormat.PNG, 100, f);
		}
		assertTrue("nothing was written to "+out, out.length()>0);
	}

	private float outlineWidth(OutlinedTextView view) throws Exception{
		Field field=OutlinedTextView.class.getDeclaredField("outlineWidth");
		field.setAccessible(true);
		return field.getFloat(view);
	}
}
