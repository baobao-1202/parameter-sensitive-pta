package util;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import soot.AnySubType;
import soot.ArrayType;
import soot.NullType;
import soot.RefLikeType;
import soot.RefType;
import soot.SootClass;
import soot.Type;

public class FastHierarchy extends soot.FastHierarchy{
	@Override
	public boolean canStoreType(Type child, Type parent) {
		if (child.equals(parent)) {
		      return true;
		    }
		    if ((parent instanceof NullType)) {
		      return false;
		    }
		    if ((child instanceof NullType)) {
		      return parent instanceof RefLikeType;
		    }
		    if ((child instanceof RefType))
		    {
		      if (parent.equals(this.sc.getObjectType())) {
		        return true;
		      }
		      if ((parent instanceof RefType)) {
		        return canStoreClass(((RefType)child).getSootClass(), ((RefType)parent).getSootClass());
		      }
		      return false;
		    }
		    if ((child instanceof AnySubType))
		    {
		      if (!(parent instanceof RefLikeType)) {
		        throw new RuntimeException("Unhandled type " + parent);
		      }
		      if ((parent instanceof ArrayType))
		      {
		        Type base = ((AnySubType)child).getBase();
		        
		        return (base.equals(this.sc.getObjectType())) || (base.equals(RefType.v("java.io.Serializable"))) || 
		          (base.equals(RefType.v("java.lang.Cloneable")));
		      }
		      SootClass base = ((AnySubType)child).getBase().getSootClass();
		      SootClass parentClass = ((RefType)parent).getSootClass();
		      LinkedList<SootClass> worklist = new LinkedList<SootClass>();
		      if (base.isInterface()) {
		        worklist.addAll(getAllImplementersOfInterface(base));
		      } else {
		        worklist.add(base);
		      }
		      Set<SootClass> workset = new HashSet<SootClass>();
		      while (!worklist.isEmpty())
		      {
		        SootClass cl = (SootClass)worklist.removeFirst();
		        if (workset.add(cl))
		        {
		          if ((canStoreClass(cl, parentClass))) {//modified for special instance of any abstract class
		            return true;
		          }
		          worklist.addAll(getSubclassesOf(cl));
		        }
		      }
		      return false;
		    }
		    if ((child instanceof ArrayType))
		    {
		      ArrayType achild = (ArrayType)child;
		      if ((parent instanceof RefType)) {
		        return (parent.equals(this.sc.getObjectType())) || (parent.equals(RefType.v("java.io.Serializable"))) || 
		          (parent.equals(RefType.v("java.lang.Cloneable")));
		      }
		      if (!(parent instanceof ArrayType)) {
		        return false;
		      }
		      ArrayType aparent = (ArrayType)parent;
		      if (achild.numDimensions == aparent.numDimensions)
		      {
		        if (achild.baseType.equals(aparent.baseType)) {
		          return true;
		        }
		        if (!(achild.baseType instanceof RefType)) {
		          return false;
		        }
		        if (!(aparent.baseType instanceof RefType)) {
		          return false;
		        }
		        return canStoreType(achild.baseType, aparent.baseType);
		      }
		      if (achild.numDimensions > aparent.numDimensions)
		      {
		        if (aparent.baseType.equals(this.sc.getObjectType())) {
		          return true;
		        }
		        if (aparent.baseType.equals(RefType.v("java.io.Serializable"))) {
		          return true;
		        }
		        if (aparent.baseType.equals(RefType.v("java.lang.Cloneable"))) {
		          return true;
		        }
		        return false;
		      }
		      return false;
		    }
		    return false;
	}
}
