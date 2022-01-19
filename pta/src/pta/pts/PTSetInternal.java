 /* Soot - a J*va Optimization Framework
 * Copyright (C) 2002 Ondrej Lhotak
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

package pta.pts;

import soot.jimple.ClassConstant;
import soot.jimple.spark.sets.EqualsSupportingPointsToSet;
import soot.util.BitVector;
import util.TypeMask;
import soot.*;
import java.util.*;

import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.ClassConstant_Node;
import pag.node.alloc.StringConstant_Node;

/**
 * Abstract base class for implementations of points-to sets.
 * 
 * @author Ondrej Lhotak
 */
public abstract class PTSetInternal implements PointsToSet, EqualsSupportingPointsToSet {
	/**
	 * Adds contents of other minus the contents of exclude into this set;
	 * returns true if this set changed.
	 */
	public boolean addAll(PTSetInternal other, final PTSetInternal exclude) {
		if (other instanceof DoublePTSet) {
			return addAll(other.getNewSet(), exclude) | addAll(other.getOldSet(), exclude);
		} else if (other instanceof EmptyPTSet) {
			return false;
		} else if (exclude instanceof EmptyPTSet) {
			return addAll(other, null);
		}
		if (!G.v().PointsToSetInternal_warnedAlready) {
			G.v().out.println(
					"Warning: using default implementation of addAll. You should implement a faster specialized implementation.");
			G.v().out.println("this is of type " + getClass().getName());
			G.v().out.println("other is of type " + other.getClass().getName());
			if (exclude == null) {
				G.v().out.println("exclude is null");
			} else {
				G.v().out.println("exclude is of type " + exclude.getClass().getName());
			}
			G.v().PointsToSetInternal_warnedAlready = true;
		}
		return other.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				if (exclude == null || !exclude.contains(n))
					returnValue = add(n) | returnValue;
			}
		});
	}

	/** Calls v's visit method on all nodes in this set. */
	public abstract boolean forall(PTSetVisitor v);

	/** Adds n to this set, returns true if n was not already in this set. */
	public abstract boolean add(GNode n);

	/** Returns set of newly-added nodes since last call to flushNew. */
	public PTSetInternal getNewSet() {
		return this;
	}

	/** Returns set of nodes already present before last call to flushNew. */
	public PTSetInternal getOldSet() {
		return EmptyPTSet.v();
	}

	/** Sets all newly-added nodes to old nodes. */
	public void flushNew() {
	}

	/** Sets all nodes to newly-added nodes. */
	public void unFlushNew() {
	}

	/** Merges other into this set. */
	public void mergeWith(PTSetInternal other) {
		addAll(other, null);
	}

	/** Returns true iff the set contains n. */
	public abstract boolean contains(GNode n);

	public PTSetInternal(Type type) {
		this.type = type;
	}

	public boolean hasNonEmptyIntersection(PointsToSet other) {
		final PTSetInternal o = (PTSetInternal) other;
		return forall(new PTSetVisitor() {
			public void visit(GNode n) {
				if (o.contains(n))
					returnValue = true;
			}
		});
	}

	public Set<Type> possibleTypes() {
		final HashSet<Type> ret = new HashSet<Type>();
		forall(new PTSetVisitor() {
			public void visit(GNode n) {
				Type t = n.getType();
				if (t instanceof RefType) {
					RefType rt = (RefType) t;
					if (rt.getSootClass().isAbstract())
						return;
				}
				ret.add(t);
			}
		});
		return ret;
	}

	public Type getType() {
		return type;
	}

	public void setType(Type type) {
		this.type = type;
	}

	public int size() {
		final int[] ret = new int[1];
		forall(new PTSetVisitor() {
			public void visit(GNode n) {
				ret[0]++;
			}
		});
		return ret[0];
	}

	public String toString() {
		final StringBuffer ret = new StringBuffer();
		this.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				ret.append("" + n + ",");
			}
		});
		return ret.toString();
	}

	public Set<String> possibleStringConstants() {
		final HashSet<String> ret = new HashSet<String>();
		return this.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				if (n instanceof StringConstant_Node) {
					ret.add(((StringConstant_Node) n).getString());
				} else {
					returnValue = true;
				}
			}
		}) ? null : ret;
	}

	public Set<ClassConstant> possibleClassConstants() {
		final HashSet<ClassConstant> ret = new HashSet<ClassConstant>();
		return this.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				if (n instanceof ClassConstant_Node) {
					ret.add(((ClassConstant_Node) n).getClassConstant());
				} else {
					returnValue = true;
				}
			}
		}) ? null : ret;
	}

	/* End of public methods. */
	/* End of package methods. */

	protected Type type;

	// Added by Adam Richard
	protected BitVector getBitMask(PTSetInternal other, PAG pag) {
		/*
		 * Prevents propogating points-to sets of inappropriate type. E.g. if
		 * you have in the code being analyzed: Shape s = (Circle)c; then the
		 * points-to set of s is only the elements in the points-to set of c
		 * that have type Circle.
		 */
		// Code ripped from BitPointsToSet

		BitVector mask = null;
		TypeMask typeManager = pag.getTypeManager();
		if (!typeManager.castNeverFails(other.getType(), this.getType())) {
			mask = typeManager.get(this.getType());
		}
		return mask;
	}

	/**
	 * {@inheritDoc}
	 */
	public int pointsToSetHashCode() {
		P2SetVisitorInt visitor = new P2SetVisitorInt(1) {

			final int PRIME = 31;

			public void visit(GNode n) {
				intValue = PRIME * intValue + n.hashCode();
			}

		};
		this.forall(visitor);
		return visitor.intValue;
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean pointsToSetEquals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof PTSetInternal)) {
			return false;
		}
		PTSetInternal otherPts = (PTSetInternal) other;

		// both sets are equal if they are supersets of each other
		return superSetOf(otherPts, this) && superSetOf(this, otherPts);
	}

	/**
	 * Returns <code>true</code> if <code>onePts</code> is a (non-strict)
	 * superset of <code>otherPts</code>.
	 */
	private boolean superSetOf(PTSetInternal onePts, final PTSetInternal otherPts) {
		return onePts.forall(new P2SetVisitorDefaultTrue() {

			public final void visit(GNode n) {
				returnValue = returnValue && otherPts.contains(n);
			}

		});
	}

	/**
	 * A P2SetVisitor with a default return value of <code>true</code>.
	 *
	 * @author Eric Bodden
	 */
	public static abstract class P2SetVisitorDefaultTrue extends PTSetVisitor {

		public P2SetVisitorDefaultTrue() {
			returnValue = true;
		}

	}

	/**
	 * A P2SetVisitor with an int value.
	 *
	 * @author Eric Bodden
	 */
	public static abstract class P2SetVisitorInt extends PTSetVisitor {

		protected int intValue;

		public P2SetVisitorInt(int i) {
			intValue = 1;
		}

	}
}
