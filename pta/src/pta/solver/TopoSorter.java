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

package pta.solver;

import soot.Type;
import util.TypeMask;

import java.util.*;

import pag.PAG;
import pag.node.var.Var_Node;

/**
 * Performs a pseudo-topological sort on the VarNodes in a PAG.
 * 
 * @author Ondrej Lhotak
 */

public class TopoSorter {
	/** Actually perform the topological sort on the PAG. */
	public void sort() {
		if (ignoreTypes)
			for (Var_Node v : pag.getVarNodeNumberer())
				dfsVisitIgnoreType(v);
		else
			for (Var_Node v : pag.getVarNodeNumberer())
				dfsVisit(v);

		for (Var_Node v : pag.getVarNodeNumberer()) {
			dfsVisit(v);
		}
		visited = null;
	}

	public TopoSorter(PAG pag, boolean ignoreTypes) {
		this.pag = pag;
		this.simple = pag.getSimple();
		this.ignoreTypes = ignoreTypes;
		this.typeManager = pag.getTypeManager();
		this.visited = new HashSet<Var_Node>();
	}
	/* End of public methods. */

	protected boolean ignoreTypes;
	protected PAG pag;
	protected Map<Var_Node, Set<Var_Node>> simple;
	protected TypeMask typeManager;
	protected int nextFinishNumber = 1;
	protected HashSet<Var_Node> visited;

	protected void dfsVisit(Var_Node n) {
		if (visited.contains(n))
			return;
		visited.add(n);
		Set<Var_Node> elements = simple.get(n);
		if (elements != null) {
			Type nType = n.getType();
			for (Var_Node element : elements)
				if (typeManager.castNeverFails(nType, element.getType()))
					dfsVisit(element);
		}
		n.setFinishingNumber(nextFinishNumber++);
	}

	protected void dfsVisitIgnoreType(Var_Node n) {
		if (visited.contains(n))
			return;
		visited.add(n);
		Set<Var_Node> elements = simple.get(n);
		if (elements != null)
			for (Var_Node element : elements)
				dfsVisitIgnoreType(element);
		n.setFinishingNumber(nextFinishNumber++);
	}
}
