/* Java and Android Analysis Framework
 * Copyright (C) 2019 Jingbo Lu and Jingling Xue
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA 02111-1307, USA.
 */

package driver;

import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.io.output.NullOutputStream;

import soot.G;
import soot.options.SparkOptions;

public class PTAOptions extends org.apache.commons.cli.Options {
	private static final long serialVersionUID = 1L;

	public static boolean hack = false;
	
	//=========PTA settings====================
	/** Pointer Analysis: see PTAPattern */
	public static PTAPattern ptaPattern;
	/** A lightweight mode with only one main method entry */
	public static boolean singleentry = false;
	/** clinit mode */
	public static final int FULL = 0, ONFLY = 1;
	public static int clinit = ONFLY;
	/** handle static invocation */
	public static final int CALLER = 0, EMPTY = 1, THIS = 2;
	public static int staticcontext = THIS;
	
	//=========PATH settings====================
	/**
	 * Path for the root folder for the application classes or for the
	 * application jar file.
	 */
	public static String APP_PATH = ".";
	/** Main class for the application. */
	public static String MAIN_CLASS = null;
	/** Path for the JRE to be used for whole program analysis. */
	public static String JRE = null;
	/** Path for the root folder for the library jars. */
	public static String LIB_PATH = null;
	/** Path for the reflection log file for the application. */
	public static String REFLECTION_LOG = null;
	/** include selected packages which are not analyzed by default */
	public static List<String> INCLUDE = null;
	/** exclude selected packages */
	public static List<String> EXCLUDE = null;
	/** include packages which are not analyzed by default */
	public static boolean INCLUDE_ALL = false;
	
	//=======MODE settings==============
	/** A debug mode for testing which load only the minimum of classes needed*/
	public static boolean debug =false;
	/** use original Java names in jimples */
	public static boolean originalName = false;
	
	//======APPROX settings===============
	/**
	 * depth to traverse into API for call graph building, -1 is follow all
	 * edges
	 */
	public static int apicalldepth = -1;
	
	/** in spark propagate all string constants
	 * false means merge all string constants
	 * */
	public static boolean stringConstants = false;
	
	//======OUTPUT settings============
	/** dump appclasses to jimple */
	public static boolean dumpJimple = false;
	/** if true, dump pta to a file */
	public static boolean dumppts = false;
	/** if true, dump pts of vars in library */
	public static boolean dumplibpts = false;
	/** print a CG graph */
	public static boolean dumpCallGraph = false;
	/** print a PAG graph */
	public static boolean dumppag = false;
	
	/**reset all pta options to default value*/
	public static void reset(){
		APP_PATH = ".";
		MAIN_CLASS = null;
		JRE = null;
		LIB_PATH = null;
		REFLECTION_LOG = null;
		singleentry = false;
		debug =false;
		originalName = false;
		apicalldepth = -1;
		INCLUDE = null;
		EXCLUDE = null;
		INCLUDE_ALL = false;
		stringConstants = false;
		dumpJimple = false;
		dumppts = false;
		dumplibpts = false;
		dumpCallGraph = false;
		dumppag = false;
	}
	
	PTAOptions() {
		addOption(null, "help", "h", "print this message");
		
		addOption(null, "pointstoanalysis", "pta", "pta", "Specify Pointer Analysis defined in driver.PTAPattern. e.g. 2o1h or 2o -> 2obj+1heap (default value: insens; default hk: k-1.)");
		addOption("singleentry", "singleentry", "se", "A lightweight mode with only one main method entry. (default value: false)");
		addOption(null, "clinitmode", "clinit", "full/onfly; 0/1","clinit mode. (default value: onfly)");
		addOption(null, "staticcontext", "sctx", "caller/empty/this; 0/1/2","handle static calls. (default value: this)");
		
		addOption("APP_PATH", "apppath", "app", "dir or jar","The directory containing the classes for the application or the application jar file (default: .)");
		addOption("MAIN_CLASS", "mainclass", "main", "class name", "Name of the main class for the application (must be specified when appmode)");
		addOption("JRE", "jre", null, "dir", "The directory containing the version of JRE to be used for whole program analysis");
		addOption("LIB_PATH", "libpath", "lib", "dir or jar","The directory containing the library jar files for the application or the library jar file");
		addOption("REFLECTION_LOG", "reflectionlog", "reflog", "file","The reflection log file for the application for resolving reflective call sites");
		addOption(null, "include", null, "package", "Include selected packages which are not analyzed by default");
		addOption("INCLUDE_ALL", "includeall", null, "Include packages which are not analyzed by default. (default value: false)");
		addOption(null, "exclude", null, "package", "Exclude selected packages");
		
		addOption("debug", "debug", null, "enable debug mode");
		addOption(null, "verbose", null, "print out all verbose information");
		addOption("originalName", "originalname", null, "Keep original Java names. (default value: false)");
		
		addOption("apicalldepth", "apicalldepth", "api", "depth","Depth to traverse into API for call graph building, -1 is follow all edges (default value: -1");
		addOption("stringConstants", "stringconstants", "sc", "Propagate all string constants (default value: false)");

		addOption("dumpCallGraph", "dumpcallgraph", "callgraph", "Output .dot callgraph file (default value: false)");
		addOption("dumpJimple", "dumpjimple", "jimple", "Dump appclasses to jimple. (default value: false)");
		addOption("dumplibpts", "dumplibpts", "ptsall", "Dump points-to of lib vars results to ./output/pta.txt (default value: false)");
		addOption("dumppag", "dumppag", "pag", "Print PAG to terminal. (default value: false)");
		addOption("dumppts", "dumppts", "pts", "Dump points-to results to ./output/pta.txt (default value: false)");	
	}

	/** add option "-brief -option" with description */
	private void addOption(String fieldName, String option, String brief, String description) {
		addOption(new Option(brief, option, false, description));
		if(fieldName!=null)
			registerOption(fieldName, option, false);
	}

	/** add option "-brief -option <arg>" with description */
	@SuppressWarnings("static-access")
	private void addOption(String fieldName, String option, String brief, String arg, String description) {
		addOption(OptionBuilder.withLongOpt(option).withArgName(arg).hasArg().withDescription(description).create(brief));
		if(fieldName!=null)
			registerOption(fieldName, option, true);
	}

	static Map<Field, String> biOptions=new HashMap<>(), options=new HashMap<>();
	/**register normal options*/
	static void registerOption(String field, String cmd, boolean hasArg){
		Map<Field, String> optionMap = hasArg?biOptions:options;
		try {
			Field f = PTAOptions.class.getField(field);
			optionMap.put(f, cmd);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	static void setPackages(String[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i] == "-include") {
				if (INCLUDE == null)
					INCLUDE = new LinkedList<String>();
				i++;
				INCLUDE.add(args[i]);
			} else if (args[i] == "-exclude") {
				if (EXCLUDE == null)
					EXCLUDE = new LinkedList<String>();
				i++;
				EXCLUDE.add(args[i]);
			}
		}
	}
	
	/**
	 * Set all variables from the command line arguments.
	 * @param cmd
	 */
	static void setOptions(CommandLine cmd) {
		biOptions.forEach((f, option)->{
			if (cmd.hasOption(option))
				try {
					if(f.getType() == int.class)
						f.setInt(null, Integer.parseInt(cmd.getOptionValue(option)));
					else
						f.set(null, cmd.getOptionValue(option));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
		});
		options.forEach((f, option)->{
			if (cmd.hasOption(option))
				try {
					f.setBoolean(null, true);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					e.printStackTrace();
				}
		});
		
		String ptacmd = cmd.hasOption("pta")?cmd.getOptionValue("pta"):"insens";
		ptaPattern = new PTAPattern(ptacmd);
		if(!ptaPattern.isInsensitive())
			if(cmd.hasOption("staticcontext")){
				String sc = cmd.getOptionValue("staticcontext");
				if(sc.equalsIgnoreCase("caller")|| sc.equalsIgnoreCase(Integer.valueOf(CALLER).toString()))
					staticcontext = CALLER;
				else if(sc.equalsIgnoreCase("empty")|| sc.equalsIgnoreCase(Integer.valueOf(EMPTY).toString()))
					staticcontext = EMPTY;
				else if(sc.equalsIgnoreCase("this")|| sc.equalsIgnoreCase(Integer.valueOf(THIS).toString()))
					staticcontext = THIS;
				else
					throw new RuntimeException("Wrong argument for static context!");
			}
		
		if(cmd.hasOption("clinit")){
			String cm = cmd.getOptionValue("clinit");
			if(cm.equalsIgnoreCase("full")|| cm.equalsIgnoreCase(Integer.valueOf(FULL).toString()))
				clinit = FULL;
			else if(cm.equalsIgnoreCase("onfly")|| cm.equalsIgnoreCase(Integer.valueOf(ONFLY).toString()))
				clinit = ONFLY;
			else
				throw new RuntimeException("Wrong argument for clinit mode!");
		}
		
		G.v().out = cmd.hasOption("verbose")?System.out:new PrintStream(NullOutputStream.NULL_OUTPUT_STREAM);
	}

	public final static SparkOptions sparkOpts;
	static {
		HashMap<String, String> opt = new HashMap<String, String>();

		opt.put("add-tags", "false");
		opt.put("class-method-var", "true");
		opt.put("double-set-new", "hybrid");
		opt.put("double-set-old", "hybrid");
		opt.put("dump-answer", "false");
		opt.put("dump-types", "true");
		opt.put("enabled", "true");
		opt.put("field-based", "false");
		opt.put("force-gc", "false");
		opt.put("ignore-types", "false");
		opt.put("ignore-types-for-sccs", "false");
		opt.put("on-fly-cg", "true");
		opt.put("pre-jimplify", "false");
		opt.put("propagator", "worklist");
		opt.put("rta", "false");
		opt.put("set-impl", "double");
		opt.put("set-mass", "false");
		opt.put("simple-edges-bidirectional", "false");
		opt.put("simplify-offline", "false");
		opt.put("simplify-sccs", "false");
		opt.put("simulate-natives", "true");
		opt.put("topo-sort", "false");
		opt.put("types-for-sites", "false");
		opt.put("verbose", "false");
		opt.put("vta", "false");

		sparkOpts = new SparkOptions(opt);
	}
}
