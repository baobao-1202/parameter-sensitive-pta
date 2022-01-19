/* Soot - a J*va Optimization Framework
 * Copyright (C) 2010 Eric Bodden
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

package reflection;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import soot.Body;
import soot.G;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.tagkit.Host;
import soot.tagkit.LineNumberTag;
import soot.tagkit.SourceLnPosTag;

public class ReflectionTrace {
	public enum Kind {
		ClassForName, ClassNewInstance, ConstructorNewInstance, MethodInvoke, FieldSet, FieldGet;
		public static Kind parseRefKind(String kindStr) {
			switch (kindStr) {
			case "Class.forName":
				return ClassForName;
			case "Class.newInstance":
				return Kind.ClassNewInstance;
			case "Constructor.newInstance":
				return Kind.ConstructorNewInstance;
			case "Method.invoke":
				return Kind.MethodInvoke;
			case "Field.set*":
				return Kind.FieldSet;
			case "Field.get*":
				return Kind.FieldGet;
			default:
				return null;
			}
		}
	}
	
	protected Map<SootMethod, Set<String>> classForNameReceivers;
	protected Map<SootMethod, Set<String>> classNewInstanceReceivers;
	protected Map<SootMethod, Set<String>> constructorNewInstanceReceivers;
	protected Map<SootMethod, Set<String>> methodInvokeReceivers;
	protected Map<SootMethod, Set<String>> fieldSetReceivers;
	protected Map<SootMethod, Set<String>> fieldGetReceivers;

	public ReflectionTrace(String logFile) {
		classForNameReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		classNewInstanceReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		constructorNewInstanceReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		methodInvokeReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		fieldSetReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		fieldGetReceivers = new LinkedHashMap<SootMethod, Set<String>>();
		
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)));
			String line;
			Set<Kind> ignoredKinds = new HashSet<>();
			while ((line = reader.readLine()) != null) {
				if (line.length() == 0)
					continue;
				String[] portions = line.split(";", -1);
				Kind kind = Kind.parseRefKind(portions[0]);
				String target = portions[1];
				String source = portions[2];
				int lineNumber = portions[3].length() == 0 ? -1 : Integer.parseInt(portions[3]);
				Set<SootMethod> possibleSourceMethods = inferSource(source, lineNumber);
				if (possibleSourceMethods == null)
					continue;
				for (SootMethod sourceMethod : possibleSourceMethods) {
					Set<String> receiverNames;
					switch (kind) {
//					case ClassForName:
//						if ((receiverNames = classForNameReceivers.get(sourceMethod)) == null) {
//							classForNameReceivers.put(sourceMethod, receiverNames = new LinkedHashSet<String>());
//						}
//						receiverNames.add(target);
//						break;
					case ClassNewInstance:
						if ((receiverNames = classNewInstanceReceivers.get(sourceMethod)) == null) {
							classNewInstanceReceivers.put(sourceMethod,
									receiverNames = new LinkedHashSet<String>());
						}
						receiverNames.add(target);
						break;
					case ConstructorNewInstance:
						if (!Scene.v().containsMethod(target)) {
							// DA: skip unknown method rather than throws exception
							// throw new RuntimeException("Unknown method for signature: "+target);
							G.v().out.println("Warning: Unknown method for signature: " + target);
							continue;
						}
						if ((receiverNames = constructorNewInstanceReceivers.get(sourceMethod)) == null) {
							constructorNewInstanceReceivers.put(sourceMethod,
									receiverNames = new LinkedHashSet<String>());
						}
						receiverNames.add(target);
						break;
					case MethodInvoke:
						if (!Scene.v().containsMethod(target)) {
							// DA: skip unknown method rather than throw exception
							// throw new RuntimeException("Unknown method for signature: "+target);
							G.v().out.println("Warning: Unknown method for signature: " + target);
							continue;
						}
						if ((receiverNames = methodInvokeReceivers.get(sourceMethod)) == null) {
							methodInvokeReceivers.put(sourceMethod, receiverNames = new LinkedHashSet<String>());
						}
						receiverNames.add(target);
						break;
					case FieldSet:
						if (!Scene.v().containsField(target)) {
							// DA: skip unknown field rather than throws exception
							// throw new RuntimeException("Unknown method for signature: "+target);
							G.v().out.println("Warning: Unknown field for signature: " + target);
							continue;
						}
						if ((receiverNames = fieldSetReceivers.get(sourceMethod)) == null) {
							fieldSetReceivers.put(sourceMethod, receiverNames = new LinkedHashSet<String>());
						}
						receiverNames.add(target);
						break;
					case FieldGet:
						if (!Scene.v().containsField(target)) {
							// DA: skip unknown method rather than throws exception
							// throw new RuntimeException("Unknown method for signature: "+target);
							G.v().out.println("Warning: Unknown field for signature: " + target);
							continue;
						}
						if ((receiverNames = fieldGetReceivers.get(sourceMethod)) == null) {
							fieldGetReceivers.put(sourceMethod, receiverNames = new LinkedHashSet<String>());
						}
						receiverNames.add(target);
						break;
					default:
						ignoredKinds.add(kind);
						break;
					}
				}
			}
			reader.close();
			if (!ignoredKinds.isEmpty()) {
				G.v().out.println("Encountered reflective calls entries of the following kinds that\n"
						+ "cannot currently be handled:");
				for (Kind kind : ignoredKinds) {
					G.v().out.println(kind);
				}
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Trace file not found.", e);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private Set<SootMethod> inferSource(String source, int lineNumber) {
		String className = source.substring(0, source.lastIndexOf("."));
		String methodName = source.substring(source.lastIndexOf(".") + 1);
		if (!Scene.v().containsClass(className)) {
			Scene.v().addBasicClass(className, SootClass.BODIES);
			Scene.v().loadBasicClasses();
			if (!Scene.v().containsClass(className)) {
				// DA: skip unknown method rather than throws exception
				// throw new RuntimeException("Trace file refers to unknown class: "+className);
				G.v().out.println("Warning: Trace file refers to unknown class: " + className);
			}
		}

		SootClass sootClass = Scene.v().getSootClass(className);
		Set<SootMethod> methodsWithRightName = new LinkedHashSet<SootMethod>();
		for (SootMethod m : sootClass.getMethods()) {
			if (m.method().isConcrete() && m.method().getName().equals(methodName)) {
				methodsWithRightName.add(m);
			}
		}

		if (methodsWithRightName.isEmpty()) {
			// DA: skip unknown method rather than throws exception
			// throw new RuntimeException("Trace file refers to unknown method with name "+methodName+" in Class "+className);
			G.v().out.println("Warning: Trace file refers to unknown method with name " + methodName + " in Class " + className);
			return null;
		} else if (methodsWithRightName.size() == 1) {
			return Collections.singleton(methodsWithRightName.iterator().next());
		} else {
			// more than one method with that name
			for (SootMethod momc : methodsWithRightName) {
				if (coversLineNumber(lineNumber, momc.method())) {
					return Collections.singleton(momc);
				}
				if (momc.method().isConcrete()) {
					if (!momc.method().hasActiveBody())
						momc.method().retrieveActiveBody();
					Body body = momc.method().getActiveBody();
					if (coversLineNumber(lineNumber, body)) {
						return Collections.singleton(momc);
					}
					for (Unit u : body.getUnits()) {
						if (coversLineNumber(lineNumber, u)) {
							return Collections.singleton(momc);
						}
					}
				}
			}

			// if we get here then we found no method with the right line number information;
			// be conservative and return all method that we found
			return methodsWithRightName;
		}
	}

	private boolean coversLineNumber(int lineNumber, Host host) {
		{
			SourceLnPosTag tag = (SourceLnPosTag) host.getTag("SourceLnPosTag");
			if (tag != null) {
				if (tag.startLn() <= lineNumber && tag.endLn() >= lineNumber) {
					return true;
				}
			}
		}
		{
			LineNumberTag tag = (LineNumberTag) host.getTag("LineNumberTag");
			if (tag != null) {
				if (tag.getLineNumber() == lineNumber) {
					return true;
				}
			}
		}
		return false;
	}

	public Set<String> classForNameClassNames(SootMethod container) {
		if (!classForNameReceivers.containsKey(container))
			return Collections.emptySet();
		return classForNameReceivers.get(container);
	}

	public Set<SootClass> classForNameClasses(SootMethod container) {
		Set<SootClass> result = new LinkedHashSet<SootClass>();
		for (String className : classForNameClassNames(container)) {
			result.add(Scene.v().getSootClass(className));
		}
		return result;
	}

	public Set<String> classNewInstanceClassNames(SootMethod container) {
		if (!classNewInstanceReceivers.containsKey(container))
			return Collections.emptySet();
		return classNewInstanceReceivers.get(container);
	}

	public Set<SootClass> classNewInstanceClasses(SootMethod container) {
		Set<SootClass> result = new LinkedHashSet<SootClass>();
		for (String className : classNewInstanceClassNames(container)) {
			result.add(Scene.v().getSootClass(className));
		}
		return result;
	}

	public Set<String> constructorNewInstanceSignatures(SootMethod container) {
		if (!constructorNewInstanceReceivers.containsKey(container))
			return Collections.emptySet();
		return constructorNewInstanceReceivers.get(container);
	}

	public Set<SootMethod> constructorNewInstanceConstructors(SootMethod container) {
		Set<SootMethod> result = new LinkedHashSet<SootMethod>();
		for (String signature : constructorNewInstanceSignatures(container)) {
			result.add(Scene.v().getMethod(signature));
		}
		return result;
	}

	public Set<String> methodInvokeSignatures(SootMethod container) {
		if (!methodInvokeReceivers.containsKey(container))
			return Collections.emptySet();
		return methodInvokeReceivers.get(container);
	}

	public Set<SootMethod> methodInvokeMethods(SootMethod container) {
		Set<SootMethod> result = new LinkedHashSet<SootMethod>();
		for (String signature : methodInvokeSignatures(container)) {
			result.add(Scene.v().getMethod(signature));
		}
		return result;
	}

	public Set<SootMethod> methodsContainingReflectiveCalls() {
		Set<SootMethod> res = new LinkedHashSet<SootMethod>();
		res.addAll(classForNameReceivers.keySet());
		res.addAll(classNewInstanceReceivers.keySet());
		res.addAll(constructorNewInstanceReceivers.keySet());
		res.addAll(methodInvokeReceivers.keySet());
		return res;
	}

	public Set<String> fieldSetSignatures(SootMethod container) {
		if (!fieldSetReceivers.containsKey(container))
			return Collections.emptySet();
		return fieldSetReceivers.get(container);
	}

	public Set<String> fieldGetSignatures(SootMethod container) {
		if (!fieldGetReceivers.containsKey(container))
			return Collections.emptySet();
		return fieldGetReceivers.get(container);
	}
}