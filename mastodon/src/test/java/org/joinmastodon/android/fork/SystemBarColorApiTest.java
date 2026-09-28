package org.joinmastodon.android.fork;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import me.grishka.appkit.views.FragmentRootLinearLayout;

import static org.junit.Assert.*;

/**
 * Guards the appkit patch in third_party/appkit, which renames FragmentRootLinearLayout's system
 * bar color accessors away from the names Play Console flags. See FORK.md, "Patched appkit".
 *
 * The compiler covers direct calls. It does not cover ObjectAnimator, which resolves property
 * names by reflection: a stale name compiles and then animates nothing at runtime, silently. So
 * the animator property names are read back out of the app's own sources here.
 */
public class SystemBarColorApiTest{
	/** The names Play Console flags, and which the patch renames away from. */
	private static final String[] FLAGGED_NAMES={"setStatusBarColor", "setNavigationBarColor", "getStatusBarColor", "getNavigationBarColor"};

	/** ObjectAnimator.ofInt/ofArgb(someView, "propertyName", ...) with a bar color property. */
	private static final Pattern ANIMATOR_PROPERTY=Pattern.compile(
			"ObjectAnimator\\.of(?:Int|Argb)\\s*\\(\\s*[A-Za-z0-9_.()]+\\s*,\\s*\"([A-Za-z]*[Bb]ar[A-Za-z]*Color)\"");

	@Test
	public void renamed_accessors_exist(){
		assertAccessors("StatusBarBackgroundColor");
		assertAccessors("NavigationBarBackgroundColor");
	}

	@Test
	public void flagged_names_are_gone(){
		for(String name : FLAGGED_NAMES){
			for(Method m : FragmentRootLinearLayout.class.getMethods()){
				assertNotEquals("FragmentRootLinearLayout."+name+" is back, so the appkit patch was lost "+
						"when appkit was re-vendored, or appkit reintroduced it. Play Console flags this "+
						"name. See third_party/appkit/README.md.", name, m.getName());
			}
		}
	}

	@Test
	public void animated_bar_color_properties_resolve_to_setters() throws IOException{
		Map<String, String> properties=barColorPropertiesInAppSources();
		assertFalse("Found no ObjectAnimator bar color properties in the app sources at all. Either the "+
				"sources moved, or this test is no longer scanning what it thinks it is.", properties.isEmpty());
		for(Map.Entry<String, String> e : properties.entrySet()){
			String property=e.getKey();
			String setter="set"+Character.toUpperCase(property.charAt(0))+property.substring(1);
			Method m=findMethod(setter, int.class);
			assertNotNull(e.getValue()+" animates \""+property+"\", and ObjectAnimator looks up "+setter+
					"(int) by reflection. No such method, so the animation would silently do nothing.", m);
			assertTrue(setter+" must be public for ObjectAnimator to find it", Modifier.isPublic(m.getModifiers()));
		}
	}

	/** Property name to the file it was found in, for the failure message. */
	private Map<String, String> barColorPropertiesInAppSources() throws IOException{
		Path root=sourceRoot();
		Map<String, String> found=new LinkedHashMap<>();
		try(Stream<Path> files=Files.walk(root)){
			for(Path p : (Iterable<Path>) files.filter(f->f.toString().endsWith(".java"))::iterator){
				Matcher m=ANIMATOR_PROPERTY.matcher(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
				while(m.find()){
					found.putIfAbsent(m.group(1), p.getFileName().toString());
				}
			}
		}
		return found;
	}

	private Path sourceRoot(){
		// Gradle runs unit tests with the module directory as the working directory, but don't rely
		// on it: walk up until the source tree turns up.
		File dir=new File("").getAbsoluteFile();
		for(int i=0; i<4 && dir!=null; i++, dir=dir.getParentFile()){
			File src=new File(dir, "src/main/java/org/joinmastodon/android");
			if(src.isDirectory())
				return src.toPath();
			File fromRepoRoot=new File(dir, "mastodon/src/main/java/org/joinmastodon/android");
			if(fromRepoRoot.isDirectory())
				return fromRepoRoot.toPath();
		}
		throw new AssertionError("can't find the app sources from "+new File("").getAbsolutePath());
	}

	private void assertAccessors(String suffix){
		Method setter=findMethod("set"+suffix, int.class);
		assertNotNull("FragmentRootLinearLayout.set"+suffix+"(int) is missing, so the appkit patch is not "+
				"applied to the vendored copy. See third_party/appkit/README.md.", setter);
		Method getter=findMethod("get"+suffix);
		assertNotNull("FragmentRootLinearLayout.get"+suffix+"() is missing, so the appkit patch is not "+
				"applied to the vendored copy. See third_party/appkit/README.md.", getter);
		assertEquals(int.class, getter.getReturnType());
	}

	private Method findMethod(String name, Class<?>... params){
		try{
			return FragmentRootLinearLayout.class.getMethod(name, params);
		}catch(NoSuchMethodException x){
			return null;
		}
	}
}
