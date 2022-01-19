package driver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.jimple.JasminClass;
import soot.util.JasminOutputStream;
import soot.SootClass;
import soot.ArrayType;
import soot.Body;
import soot.EntryPoints;
import soot.Printer;
import soot.RefType;
import soot.Scene;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.jimple.InvokeExpr;
import soot.jimple.Stmt;
import soot.jimple.StmtBody;
import soot.tagkit.LineNumberTag;
import soot.util.Chain;

/**
 * Class to hold general utility methods that are helpful for Soot.
 *
 */
public class SootUtils {

	/** logger object */
	private static final Logger logger = LoggerFactory.getLogger(SootUtils.class);
	
	static List<SootMethod> getEntryPoints() {
		List<SootMethod> ret = new ArrayList<SootMethod>();
		ret.addAll(PTAOptions.singleentry?EntryPoints.v().application():EntryPoints.v().all());
		if(PTAOptions.clinit==PTAOptions.FULL)
			ret.addAll(EntryPoints.v().clinits());
		return ret;
	}
	
	/**
	 * Return the source location of a method based on its first statement.
	 */
	public static int getMethodLocation(SootMethod method) {
		if (method != null && method.isConcrete()) {
			Chain<Unit> stmts = ((StmtBody) method.retrieveActiveBody()).getUnits();
			Iterator<Unit> stmtIt = stmts.snapshotIterator();

			if (stmtIt.hasNext()) {
				return getSourceLine((Stmt) stmtIt.next());
			}
		}

		return -1;
	}

	/**
	 * Return the source line number of a Jimple statement.
	 */
	public static int getSourceLine(Stmt stmt) {
		if (stmt != null) {
			LineNumberTag lineNumberTag = (LineNumberTag) stmt.getTag("LineNumberTag");
			if (lineNumberTag != null) {
				return lineNumberTag.getLineNumber();
			}
			logger.debug("Cannot find line number tag for {}", stmt);
		}
		return -1;
	}

	public static int getNumLines(SootMethod method) {
		if (method.isAbstract() || !method.isConcrete())
			return 0;

		try {
			int startingLine = getMethodLocation(method);

			Body body = method.retrieveActiveBody();

			Chain<Unit> units = body.getUnits();

			Unit curUnit = units.getLast();
			Unit first = units.getFirst();

			while (curUnit != first) {
				Stmt curStmt = (Stmt) curUnit;
				int sl = getSourceLine(curStmt);
				if (sl >= 0)
					return sl - startingLine;

				curUnit = units.getPredOf(curUnit);
			}

		} catch (Exception e) {
			return 0;
		}

		return 0;
	}

	/**
	 * get a list of statements that a method calls the other
	 * 
	 * @param sootMethod
	 * @param callee
	 * @return
	 */
	public static List<Stmt> getInvokeStatements(SootMethod sootMethod, SootMethod callee) {
		List<Stmt> invokeStmtList = new LinkedList<Stmt>();

		if (!sootMethod.isConcrete()) {
			return invokeStmtList;
		}

		Body body;
		try {
			body = sootMethod.retrieveActiveBody();
		} catch (Exception ex) {
			logger.warn("execption trying to get ActiveBody: {} ", ex);
			return invokeStmtList;
		}

		Chain<Unit> units = body.getUnits();

		/*
		 * Note that locals are named as follows: r => reference, i=> immediate
		 * $r, $i => true local r, i => parameter passing, and r0 is for this
		 * when it is non-static
		 */

		for (Unit unit : units) {
			Stmt statement = (Stmt) unit;

			if (statement.containsInvokeExpr()) {
				InvokeExpr expr = statement.getInvokeExpr();
				SootMethod invokedMethod = expr.getMethod();
				if (invokedMethod == callee)
					invokeStmtList.add(statement);
			}

		}
		return invokeStmtList;
	}

	public static void writeByteCode(String parentDir, SootClass clz) {

		String methodThatFailed = "";
		File packageDirectory = new File(
				parentDir + File.separator + clz.getPackageName().replace(".", File.separator));

		try {
			// make package directory
			packageDirectory.mkdirs();

			FileOutputStream fos = new FileOutputStream(
					packageDirectory.toString() + File.separator + clz.getShortName() + ".class");
			OutputStream streamOut = new JasminOutputStream(fos);
			OutputStreamWriter osw = new OutputStreamWriter(streamOut);
			PrintWriter writerOut = new PrintWriter(osw);

			for (SootMethod method : clz.getMethods()) {
				methodThatFailed = method.getName();
				if (method.isConcrete())
					method.retrieveActiveBody();
			}
			try {

				JasminClass jasminClass = new soot.jimple.JasminClass(clz);
				jasminClass.print(writerOut);
				// System.out.println("Succeeded writing class: " + clz);
			} catch (Exception e) {
				logger.warn("Error writing class to file {}", clz, e);
			}

			writerOut.flush();
			streamOut.close();
			writerOut.close();
			fos.close();
			osw.close();

		} catch (Exception e) {
			logger.error("Method that failed = " + methodThatFailed);
			logger.error("Error writing class to file {}", clz, e);
		}
	}

	/**
	 * Write the jimple file for clz. ParentDir is the absolute path of parent
	 * directory.
	 */
	public static void writeJimple(String parentDir, SootClass clz) {

		File packageDirectory = new File(
				parentDir + File.separator + clz.getPackageName().replace(".", File.separator));

		try {
			packageDirectory.mkdirs();

			OutputStream streamOut = new FileOutputStream(
					packageDirectory.toString() + File.separator + clz.getShortName() + ".jimple");
			PrintWriter writerOut = new PrintWriter(new OutputStreamWriter(streamOut));
			Printer.v().printTo(clz, writerOut);
			writerOut.flush();
			writerOut.close();
			streamOut.close();

		} catch (Exception e) {
			logger.error("Error writing jimple to file {}", clz, e);
		}
	}

	public static void dumpJimple(String outputDir) {
		for (SootClass clz : Config.v().appClasses) {
			writeJimple(outputDir, clz);
		}
	}

	/**
	 * Given a file name with separators, convert them in to . so it is a legal
	 * class name. modified: Ammonia: handle .* not only .class
	 */
	public static String fromFileToClass(String name) {
		return name.substring(0, name.lastIndexOf('.')).replace(File.separatorChar, '.');
	}

	/**
	 * Given a jarFile, return a list of the classes contained in the jarfile
	 * with . replacing /.
	 */
	public static List<String> getClassesFromJar(JarFile jarFile) {
		LinkedList<String> classes = new LinkedList<String>();
		Enumeration<JarEntry> allEntries = jarFile.entries();

		while (allEntries.hasMoreElements()) {
			JarEntry entry = (JarEntry) allEntries.nextElement();
			String name = entry.getName();
			if (!name.endsWith(".class")) {
				continue;
			}

			String clsName = name.substring(0, name.length() - 6).replace('/', '.');
			classes.add(clsName);
		}
		return classes;
	}

	/**
	 * Return the terminal classname from a fully specified classname
	 */
	public static String extractClassname(String fullname) {
		return fullname.replaceFirst("^.*[.]", "");
	}

	public static SootClass getSootClass(Type type) {
		SootClass allocated = null;
		if (type instanceof RefType) {
			allocated = ((RefType) type).getSootClass();
		} else if (type instanceof ArrayType && ((ArrayType) type).getArrayElementType() instanceof RefType) {
			allocated = ((RefType) ((ArrayType) type).getArrayElementType()).getSootClass();
		}

		return allocated;
	}

	public static Collection<Type> installClassList(String[] stringList) {
		Set<Type> set = new HashSet<Type>();
		for(String string : stringList){
			Type type = RefType.v(string);
			set.add( type );
		}
		return set;
		
	}
	/**
	 * Install no context list for classes given plus all subclasses.
	 */
	public static Collection<Type> installClassListWithAncestors(String[] stringList) {
		Collection<SootClass> classList = new HashSet<SootClass>();
		for(String string : stringList){
			SootClass clz = Scene.v().getSootClass(string);
			classList.addAll( Scene.v().getActiveHierarchy().getSubclassesOfIncluding(clz) );
		}
		Set<Type> set = new HashSet<Type>();
		for(SootClass clz : classList){
			Type type = RefType.v(clz);
			set.add( type );
		}
		return set;
	}

}
