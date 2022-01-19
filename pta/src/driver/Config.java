package driver;

import static driver.PTAOptions.sparkOpts;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.SourceLocator;
import soot.options.Options;
import soot.util.Chain;
import soot.util.HashChain;

public class Config {
	private final static Logger logger = LoggerFactory.getLogger(Config.class);
	private final static Config config = new Config();

	public Set<String> appClasseNames;
	public Set<SootClass> appClasses;

	public boolean isAppClass(SootClass clz) {
		return appClasses.contains(clz);
	}

	public static Config v() {
		return config;
	}

	public void init(String[] args) {
		PTAOptions.reset();
		PTAOptions.setPackages(args);
		PTAOptions ptaOptions = new PTAOptions();
		try {
			CommandLine cmd = new GnuParser().parse(ptaOptions, args);
			if (cmd.hasOption("help")) {
				new HelpFormatter().printHelp("pta", ptaOptions);
				System.exit(0);
			}
			PTAOptions.setOptions(cmd);
		} catch (Exception e) {
			logger.error("Error parsing command line options", e);
			System.exit(1);
		}
		setSootOptions();
		setSootClassPath();
		appClasseNames = setAppClassesNames();
		loadNecessaryClasses();
		appClasses = Scene.v().getApplicationClasses().stream().collect(Collectors.toSet());
		
		if (sparkOpts.pre_jimplify())
			preJimplify();
	}
	
	private static void preJimplify() {
		boolean change = true;
		while (change) {
			change = false;
			for (Iterator<SootClass> cIt = new ArrayList<SootClass>(Scene.v().getClasses()).iterator(); cIt
					.hasNext();) {
				final SootClass c = cIt.next();
				for (Iterator<?> mIt = c.methodIterator(); mIt.hasNext();) {
					final SootMethod m = (SootMethod) mIt.next();
					if (!m.isConcrete())
						continue;
					if (m.isNative())
						continue;
					if (m.isPhantom())
						continue;
					if (!m.hasActiveBody()) {
						change = true;
						m.retrieveActiveBody();
					}
				}
			}
		}
	}
	// ===============================class===============================
	/**
	 * Add all classes from in bin/classes to the appClasses
	 */
	private Set<String> setAppClassesNames() {
		Set<String> appClasseNames = new LinkedHashSet<String>();
		File appPath = new File(PTAOptions.APP_PATH);
		logger.info("Setting application path to {}.", appPath.toString());
		if (!appPath.exists()) {
			logger.error("Project not configured properly. Application path {} does not exist: ", appPath);
			System.exit(1);
		}
		if (appPath.isDirectory()) {
			for (File clazz : FileUtils.listFiles(appPath, new String[] { "class" }, true)) {
				String clzName = SootUtils.fromFileToClass(clazz.toString().substring(appPath.toString().length() + 1));
				logger.info("Application class: {}", clzName);
				appClasseNames.add(clzName);
			}
		} else {
			try {
				JarFile jar = new JarFile(appPath);
				for (String clzName : SootUtils.getClassesFromJar(jar)) {
					logger.info("Application class: {}", clzName);
					appClasseNames.add(clzName);
				}
			} catch (Exception e) {
				logger.error("Error in processing jar file {}", appPath, e);
				System.exit(1);
			}
		}
		return appClasseNames;
	}

	private void loadNecessaryClasses() {
		Scene scene = Scene.v();
		scene.loadBasicClasses();
		
		for (final String path : Options.v().process_dir()) {
			for (String cl : SourceLocator.v().getClassesUnder(path)) {
				SootClass theClass = scene.loadClassAndSupport(cl);
				if (!theClass.isPhantom())
					theClass.setApplicationClass();
			}
		}
		
		// Remove/add all classes from packageInclusionMask as per -i option
		Chain<SootClass> processedClasses = new HashChain<SootClass>();
		while (true) {
			Chain<SootClass> unprocessedClasses = new HashChain<SootClass>(scene.getClasses());
			unprocessedClasses.removeAll(processedClasses);
			if (unprocessedClasses.isEmpty())
				break;
			processedClasses.addAll(unprocessedClasses);
			for (SootClass s : unprocessedClasses) {
				if (s.isPhantom())
					continue;
				if (Config.v().appClasseNames.contains(s.getName())) {
					s.setApplicationClass();
					continue;
				}
				if (s.isApplicationClass()) {
					// make sure we have the support
					scene.loadClassAndSupport(s.getName());
				}
			}
		}
		scene.setDoneResolving();
	}

	// ===============================soot====================================
	/**
	 * Set command line options for soot.
	 */
	private static void setSootOptions() {
		List<String> dirs = new ArrayList<String>();
		dirs.add(PTAOptions.APP_PATH);
		
		soot.options.Options.v().set_process_dir(dirs);

		if(PTAOptions.MAIN_CLASS == null)
			findMainFromMetaInfo();
		
		if (PTAOptions.MAIN_CLASS != null)
			soot.options.Options.v().set_main_class(PTAOptions.MAIN_CLASS);

		if (PTAOptions.INCLUDE_ALL)
			soot.options.Options.v().set_include_all(true);

		if (PTAOptions.INCLUDE != null)
			soot.options.Options.v().set_include(PTAOptions.INCLUDE);

		if (PTAOptions.EXCLUDE != null)
			soot.options.Options.v().set_include(PTAOptions.EXCLUDE);

		if (PTAOptions.originalName)
			soot.options.Options.v().setPhaseOption("jb", "use-original-names:true");
		
		if (PTAOptions.REFLECTION_LOG!=null)
            soot.options.Options.v().setPhaseOption("cg", "reflection-log:" + PTAOptions.REFLECTION_LOG);

		soot.options.Options.v().set_keep_line_number(true);
		soot.options.Options.v().set_whole_program(true);
		soot.options.Options.v().setPhaseOption("cg", "verbose:false");
		soot.options.Options.v().setPhaseOption("cg", "trim-clinit:true");
		soot.options.Options.v().setPhaseOption("wjop", "enabled:false");// don't
																			// optimize
																			// the
																			// program
		soot.options.Options.v().set_allow_phantom_refs(true);// allow for the
																// absence of
																// some classes
	}

	private static void findMainFromMetaInfo() {
		try {
			JarFile jar = new JarFile(PTAOptions.APP_PATH);
			Enumeration<JarEntry> allEntries = jar.entries();
			while (allEntries.hasMoreElements()) {
				JarEntry entry = (JarEntry) allEntries.nextElement();
				String name = entry.getName();
				if (!name.endsWith(".MF")) {
					continue;
				}
				String urlstring = "jar:file:"+PTAOptions.APP_PATH+"!/"+name;
				URL url = new URL(urlstring);
				Scanner scanner = new Scanner(url.openStream());
				for(String string=null;scanner.hasNext()&&!"Main-Class:".equals(string);string = scanner.next());
				if(scanner.hasNext())
					PTAOptions.MAIN_CLASS = scanner.next();
				else
					System.out.println("cannot find meta info.");
				scanner.close();
				jar.close();
				break;
			}
		} catch (IOException e) {
			System.out.println("cannot find meta info.");
		}
	}

	/**
	 * Set the soot class path to point to the default class path appended with
	 * the app path (the classes dir or the application jar) and jar files in
	 * the library dir of the application.
	 */
	private static void setSootClassPath() {
		List<String> cps = new ArrayList<>();
		

		cps.add(PTAOptions.APP_PATH);
		cps.addAll(getJreJars(PTAOptions.JRE));
		cps.addAll(getLibJars(PTAOptions.LIB_PATH));
		if (PTAOptions.REFLECTION_LOG != null)
			cps.add(new File(PTAOptions.REFLECTION_LOG).getParent());
		final String classpath = String.join(File.pathSeparator, cps);
		logger.info("Setting Soot ClassPath: {}", classpath);
		System.setProperty("soot.class.path", classpath);
		Scene.v().setSootClassPath(classpath);
	}

	private static Collection<String> getJreJars(String JRE) {
		if (JRE == null)
			return Collections.emptyList();
		List<String> entries = new ArrayList<>();
		
		final String jreLibDir = JRE + File.separator + "lib" + File.separator;
		entries.add(jreLibDir + "rt.jar");
		entries.add(jreLibDir + "jce.jar");
		entries.add(jreLibDir + "jsse.jar");
		
		return entries;
	}

	/**
	 * Returns a collection of files, one for each of the jar files in the app's
	 * lib folder
	 */
	private static Collection<String> getLibJars(String LIB_PATH) {
		if (LIB_PATH == null)
			return Collections.emptyList();
		File libFile = new File(LIB_PATH);
		if (libFile.exists()) {
			if (libFile.isDirectory()) {
				return FileUtils.listFiles(libFile, new String[] { "jar" }, true).stream().map(File::toString).collect(Collectors.toList());
			} else if (libFile.isFile()) {
				if (libFile.getName().endsWith(".jar")) {
					return Collections.singletonList(LIB_PATH);
				}
				logger.error("Project not configured properly. Application library path {} is not a jar file.",libFile);
				System.exit(1);
			}
		}
		logger.error("Project not configured properly. Application library path {} is not correct.", libFile);
		System.exit(1);
		return null;
	}

	// =================================Context===================================

}
