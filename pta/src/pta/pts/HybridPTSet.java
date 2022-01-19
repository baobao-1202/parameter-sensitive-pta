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

import pag.PAG;
import pag.node.GNode;
import soot.Scene;
import soot.Type;
import soot.util.BitSetIterator;
import soot.util.BitVector;
import util.TypeMask;

/**
 * Hybrid implementation of points-to set, which uses an explicit array for
 * small sets, and a bit vector for large sets.
 * 
 * @author Ondrej Lhotak
 */
public final class HybridPTSet extends PTSetInternal {
	public HybridPTSet(Type type, PAG pag) {
		super(type);
		this.pag = pag;
	}

	/** Returns true if this set contains no run-time objects. */
	public final boolean isEmpty() {
		return empty;
	}

	private boolean superAddAll(PTSetInternal other, PTSetInternal exclude) {
		boolean ret = super.addAll(other, exclude);
		if (ret)
			empty = false;
		return ret;
	}

	private boolean nativeAddAll(HybridPTSet other, HybridPTSet exclude) {
		boolean ret = false;
		TypeMask typeManager = pag.getTypeManager();
		if (other.bits != null) {
			convertToBits();
			if (exclude != null) {
				exclude.convertToBits();
			}
			BitVector mask = null;
			if (!typeManager.castNeverFails(other.getType(), this.getType())) {
				mask = typeManager.get(this.getType());
			}

			BitVector ebits = (exclude == null ? null : exclude.bits);
			ret = bits.orAndAndNot(other.bits, mask, ebits);
		} else {
			for (int i = 0; i < nodes.length; i++) {
				if (other.nodes[i] == null)
					break;
				if (exclude == null || !exclude.contains(other.nodes[i])) {
					ret = add(other.nodes[i]) | ret;
				}
			}
		}
		if (ret)
			empty = false;
		return ret;
	}

	/**
	 * Adds contents of other into this set, returns true if this set changed.
	 */
	public final boolean addAll(final PTSetInternal other, final PTSetInternal exclude) {
		if (other != null && !(other instanceof HybridPTSet))
			return superAddAll(other, exclude);
		if (exclude != null && !(exclude instanceof HybridPTSet))
			return superAddAll(other, exclude);
		return nativeAddAll((HybridPTSet) other, (HybridPTSet) exclude);
	}

	/** Calls v's visit method on all nodes in this set. */
	public final boolean forall(PTSetVisitor v) {
		if (bits == null) {
			for (GNode node : nodes) {
				if (node == null)
					return v.getReturnValue();
				v.visit(node);
			}
		} else {
			for (BitSetIterator it = bits.iterator(); it.hasNext();) {
				v.visit(pag.getAllocNodeNumberer().get(it.next()));
			}
		}
		return v.getReturnValue();
	}

	/** Adds n to this set, returns true if n was not already in this set. */
	public final boolean add(GNode n) {
		if (pag.getTypeManager().castNeverFails(n.getType(), type)) {
			return fastAdd(n);
		}
		return false;
	}

	/** Returns true iff the set contains n. */
	public final boolean contains(GNode n) {
		if (bits == null) {
			for (GNode node : nodes) {
				if (node == n)
					return true;
				if (node == null) {
					break;
				}
			}
			return false;
		} else {
			return bits.get(n.getNumber());
		}
	}

	public static PTSetFactory getFactory() {
		return new PTSetFactory() {
			public final PTSetInternal newSet(Type type, PAG pag) {
				return new HybridPTSet(type, pag);
			}
		};
	}

	/* End of public methods. */
	/* End of package methods. */

	protected final boolean fastAdd(GNode n) {
		if (bits == null) {
			for (int i = 0; i < nodes.length; i++) {
				if (nodes[i] == null) {
					empty = false;
					nodes[i] = n;
					return true;
				} else if (nodes[i] == n) {
					return false;
				}
			}
			convertToBits();
		}
		boolean ret = bits.set(n.getNumber());
		if (ret)
			empty = false;
		return ret;
	}

	protected final void convertToBits() {
		if (bits != null)
			return;
		// ++numBitVectors;
		bits = new BitVector(pag.getAllocNodeNumberer().size());
		for (GNode node : nodes) {
			if (node != null) {
				fastAdd(node);
			}
		}
	}

	// public static int numBitVectors = 0;
	private GNode[] nodes = new GNode[16];
	private BitVector bits = null;
	private PAG pag;
	private boolean empty = true;

	public static HybridPTSet intersection(final HybridPTSet set1, final HybridPTSet set2, PAG pag) {
		final HybridPTSet ret = new HybridPTSet(Scene.v().getObjectType(), pag);
		BitVector s1Bits = set1.bits;
		BitVector s2Bits = set2.bits;
		if (s1Bits == null || s2Bits == null) {
			if (s1Bits != null) {
				// set2 is smaller
				set2.forall(new PTSetVisitor() {
					@Override
					public void visit(GNode n) {
						if (set1.contains(n))
							ret.add(n);
					}
				});
			} else {
				// set1 smaller, or both small
				set1.forall(new PTSetVisitor() {
					@Override
					public void visit(GNode n) {
						if (set2.contains(n))
							ret.add(n);
					}
				});
			}
		} else {
			// both big; do bit-vector operation
			// potential issue: if intersection is small, might
			// use inefficient bit-vector operations later
			ret.bits = BitVector.and(s1Bits, s2Bits);
			ret.empty = false;
		}
		return ret;
	}

}
