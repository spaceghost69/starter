/**
 *
 */
package io.starter.ignite.generator;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.squareup.javapoet.MethodSpec;

import io.starter.ignite.model.DataField;
import io.starter.ignite.security.securefield.SecureField;
import io.starter.toolkit.StringTool;
import io.swagger.annotations.ApiModelProperty;

/**
 * @author John McMahon ~ github: SpaceGhost69 | twitter: @TechnoCharms
 *
 */
public class Gen {

	private static final Logger logger = LoggerFactory.getLogger(Gen.class);
	
	
	static StackGenConfigurator config  = new StackGenConfigurator();
	
	public String LINE_FEED = "\r\n";
	public StackGenConfigurator getConfig() {
		if(config == null) {
			config = new StackGenConfigurator();
		}
		return config;
	}
	
	public Gen() {
		 // config = new StackGenConfigurator();
	}
	
	public Gen(StackGenConfigurator cfg) {
		config= cfg;
	}
	
	/**
	 * iterate over the Class heirarchy and build a list of public classes and
	 * methods
	 *
	 * @param ob
	 * @throws Exception
	 */
	public synchronized Map<String, Object> processClasses(Class<?> ob, Map<String, Object> results, Generator impl)
			throws Exception {
		String className = ob.getName();
		final int dotpos = className.lastIndexOf(".");
		String packageName = className;
		if (dotpos > -1) {
			packageName = className.substring(0, dotpos);
		}

		if (!packageName.toUpperCase().startsWith("IO")) {
			return null;
		}

		if (className.toUpperCase().contains("ENUM")) {
			return null;
		}

		className = className.substring(dotpos + 1);
		className = StringTool.replaceChars(";", className, "");

		final String packageDir = config.getJavaGenSourceFolder() + "/"
				+ StringTool.replaceChars(".", packageName, "/");

		final java.io.File pkg = new java.io.File(packageDir);
		pkg.mkdirs();

		if (results == null) {
			results = new HashMap<>();
		}
		if (results.get(className) != null) {
			return null;
		}

		Gen.logger.info("Crawling Class Heirarchy for Root Class: " + packageName + "." + className);

		results.put(className, ob);

		final java.lang.reflect.Field[] fields = ob.getDeclaredFields();
		final List<Object> fieldList = new ArrayList<Object>();
		final List<MethodSpec> getters = new ArrayList<MethodSpec>();
		final List<MethodSpec> setters = new ArrayList<MethodSpec>();

		// recursively crawl the member objects
		for (final Field f : fields) {
			final Class<?> retval = f.getType();
			if (!retval.isPrimitive() && (!retval.getName().equals(className))) {
				if (!retval.getName().startsWith("L[java.") && !retval.getName().startsWith("ajc$")
						&& !retval.getName().startsWith("[C")) {
					processClasses(retval, results, impl);
				}
			}

			// Uses the appropriate adapter:
			if (!f.getName().startsWith("ajc$")) { // skip aspects
				// logger.trace(this.toString() + " generating Field : "
				// + f.getName() + " Type: " + f.getType());

				final Object fldObj = impl.createMember(f);
				if ((fldObj != null) && (impl instanceof DBGen)) {
					fieldList.add(fldObj);
				}

				final MethodSpec fldAccess = (MethodSpec) impl.createAccessor(f);
				if (fldAccess != null) {
					getters.add(fldAccess);
				}

				final MethodSpec setter = (MethodSpec) impl.createSetter(f);
				if (setter != null) {
					setters.add(setter);
				}
			}
		}

		//
		impl.generate(packageName + "." + className, fieldList, getters, setters);

		return results;
	}

	/**
	 * returns the "base" model file name aka "User'
	 *
	 * <h4>This is the file generated by Swagger Template file:
	 * JavaSpring/pojo.mustache</h4>
	 *
	 * @return list of generated model file names
	 */
	public String[] getModelFileNames() {
		// convert dots to slashes (package names)
		final String mc = config.getModelClasses().replace(".", "/");
		final File modelDir = new File(mc);

		if (!modelDir.exists()) {
			throw new IllegalStateException("getModelFileNames Failure: no path here " + mc);
		}
		final String[] modelFiles = modelDir.list((dir, name) -> {
			if (name.contains("DynamicSqlSupport")) {
				return false;
			}
			if (name.contains("Mapper")) {
				return false;
			}
			if (name.contains(config.ADD_GEN_CLASS_NAME)) {
				return false;
			}
			return name.toLowerCase().endsWith(".java");
		});

		if ((modelFiles != null) && (modelFiles.length < 1)) {
			throw new IllegalStateException("Gen.getModleFileNames Failure: no model classfiles found: " + mc
					+ ". Check the MODEL_CLASSES config value.");
		}
		return modelFiles;
	}

	public static File[] getJavaFiles(String path, boolean recursive) throws FileNotFoundException {
		// ie: config.MODEL_CLASSES

		// convert dots to slashes (package names)
		path = path.replace(".", "/");
		final File modelDir = new File(path);

		if (!modelDir.exists()) {
			throw new FileNotFoundException("Gen.getJavaFiles Failure: no path here " + path);
		}
		final File[] modelFiles = modelDir.listFiles((FilenameFilter) (dir, name) -> {
			if (new File(dir.getPath() + "/" + name).isDirectory() || name.toLowerCase().endsWith(".java")) {
				return true;
			}
			return false;
		});

		final List<File> folderFiles = new ArrayList<>();
		for (final File fx : modelFiles) {
			if (fx.isDirectory() && recursive) {
				final File[] subdirFiles = Gen.getJavaFiles(fx.getAbsolutePath(), true);
				folderFiles.addAll(Arrays.asList(subdirFiles));
			} else {
				// if (!fx.isDirectory()) {
				folderFiles.add(fx);
				// }
			}
		}

		return folderFiles.toArray(new File[folderFiles.size()]);
	}

	public File[] getSourceFilesInFolder(File f, List<String> skipList) {

		if (!f.exists()) {
			throw new IllegalStateException("getSourceFilesInFolder Failure: no path here " + f);
		}

		final File[] modelFiles = f.listFiles((FilenameFilter) (dir, name) -> {
			if (skipList.contains(name)) {
				return false;
			}
			if (new File(dir.getPath() + "/" + name).isDirectory()) {
				return true;
			}
			if (name.toLowerCase().startsWith(".")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".java")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".xml")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".htm")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".html")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".css")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".scss")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".jsx")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".js")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".json")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".properties")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".info")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".md")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".txt")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".md")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".sh")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".yml")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".yaml")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".png")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".svg")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".ico")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".gif")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".jpg")) {
				return true;
			}
			if (name.toLowerCase().endsWith(".jpeg")) {
				return true;
			}

			return false;
		});
		final List<File> folderFiles = new ArrayList<>();
		for (final File fx : modelFiles) {
			if (fx.isDirectory()) {
				final File[] subdirFiles = getSourceFilesInFolder(fx, config.FOLDER_SKIP_LIST);
				folderFiles.addAll(Arrays.asList(subdirFiles));
			} else {
				if (!folderFiles.contains(fx)) { // DEDUPES NOT WORK??!
					folderFiles.add(fx);
				} else {
					System.err.print("DUPE:  " + fx.toString());
				}
			}
		}

		return folderFiles.toArray(new File[folderFiles.size()]);

	}

	/**
	 * returns fields from superclasses as well
	 *
	 * @param type
	 * @return
	 */
	public static Object[] getAllFields(Class<?> type) {
		final List<Field> fields = new ArrayList<>();
		for (Class<?> c = type; c != null; c = c.getSuperclass()) {
			fields.addAll(Arrays.asList(c.getDeclaredFields()));
		}
		return fields.toArray();
	}

	public static DataField getDataFieldAnnotation(Field f) throws NoSuchMethodException, SecurityException {
		// get the annotation
		final DataField anno = f.getDeclaredAnnotation(DataField.class);
		
		// TODO:     @Cacheable(value = ORGANIZATION_DETAILS_CACHE, key = "#organization.uuid")
		return anno;
	}

	public static SecureField getSecureFieldAnnotation(Field f) throws NoSuchMethodException, SecurityException {
		// get the annotation
		final SecureField anno = f.getDeclaredAnnotation(SecureField.class);
		return anno;
	}

	public static ApiModelProperty getApiModelPropertyAnnotation(Field f)
			throws NoSuchMethodException, SecurityException {
		final String methodName = "get" + StringTool.getUpperCaseFirstLetter(f.getName());
		final Method getter = f.getDeclaringClass().getMethod(methodName);
		// get the annotation
		final ApiModelProperty anno = getter.getDeclaredAnnotation(ApiModelProperty.class);
		return anno;
	}

}
