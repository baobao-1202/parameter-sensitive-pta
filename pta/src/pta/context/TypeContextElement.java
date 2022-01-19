package pta.context;

import java.util.HashMap;
import java.util.Map;

import pag.node.alloc.Alloc_Node;
import soot.SootMethod;
import soot.Type;

/**
 * Type based context element in the points to analysis.
 *
 */
public class TypeContextElement implements ContextElement {

	private static Map<Type, TypeContextElement> universe = new HashMap<Type, TypeContextElement>();

	public static TypeContextElement v(Type type) {
		TypeContextElement ret = universe.get(type);
		if (ret == null)
			universe.put(type, ret = new TypeContextElement(type));
		return ret;
	}
	
	public static TypeContextElement getTypeContextElement(Alloc_Node a) {
		SootMethod declaringMethod = a.getMethod();
		Type type = declaringMethod==null?null:declaringMethod.getDeclaringClass().getType();
		return v(type);
	}

	private Type type;
	
	private TypeContextElement(Type type) {
		this.type = type;
	}

	public Type getType() {
		return type;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TypeContextElement other = (TypeContextElement) obj;
		if (type == null && other.type != null)
			return false;
		else
			return type.equals(other.type);
	}

	public String toString() {
		return "TypeContext: " + type;
	}
}
