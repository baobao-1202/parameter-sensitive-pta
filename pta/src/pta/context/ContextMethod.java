package pta.context;

import soot.Context;
import soot.MethodOrMethodContext;
import soot.SootClass;
import soot.SootMethod;

/**
 * Represents a pair of a method and a context.
 */
public final class ContextMethod implements MethodOrMethodContext {

	private SootMethod method;

	public SootMethod method() {
		return method;
	}

	public String getMtdName() {
		return method.getName();
	}

	public SootClass getClz() {
		return method.getDeclaringClass();
	}

	public String getClzName() {
		return method.getDeclaringClass().getName();
	}

	private Context context;

	public Context context() {
		return context;
	}

	public ContextMethod(Context context, SootMethod method) {
		this.context = context;
		this.method = method;
	}

	public String toString() {
		return "Method " + method + " in context " + context;
	}
	
	@Override
	public int hashCode() {
		return method.hashCode()+context.hashCode();
	}
	@Override
	public boolean equals(Object obj) {
		if(obj==null)return false;
		ContextMethod o = (ContextMethod) obj;
		return context==null?o.context==null:context.equals(o.context)
				&&method==null?o.method==null:context.equals(o.method);
	}
}
