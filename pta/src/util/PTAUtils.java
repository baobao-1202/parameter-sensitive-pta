/* Java and Android Analysis Framework
 * Copyright (C) 2017 Jingbo Lu and Yulei Sui
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

package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import driver.Config;
import pag.PAG;
import pag.node.GNode;
import pag.node.alloc.Alloc_Node;
import pag.node.var.AllocDotField_Node;
import pag.node.var.ContextVar_Node;
import pag.node.var.FieldRef_Node;
import pag.node.var.GlobalVar_Node;
import pag.node.var.LocalVar_Node;
import pag.node.var.Var_Node;
import pta.ContextSensPTA;
import pta.PTA;
import pta.pts.PTSetInternal;
import pta.pts.PTSetVisitor;
import soot.Context;
import soot.Local;
import soot.MethodOrMethodContext;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.SourceLocator;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.util.dot.DotGraph;
import soot.util.dot.DotGraphConstants;
import soot.util.dot.DotGraphNode;
import soot.util.queue.QueueReader;

public final class PTAUtils {
	static final String output_dir = SourceLocator.v().getOutputDir();
	static TreeMap<String, GNode> nodes = new TreeMap<>();

	// print pts
	public static void printAppPts(PTA pta) {
		PAG pag = pta.getPag();
		System.out.println("Globals: ");
		for (Object global : pag.getGlobalPointers()) {
			if (!Config.v().isAppClass(pag.findGlobalVarNode(global).getDeclaringClass()))
				continue;
			System.out.println(global + ":");
			printPts((PTSetInternal) pta.reachingObjects((SootField) global));
		}
		System.out.println("\nLocals: ");
		for (Local local : pag.getLocalPointers()) {
			if (PTAEvaluator.isExceptionType(local.getType()))
				continue;
			LocalVar_Node varNode = pag.findLocalVarNode(local);
			if (!Config.v().isAppClass(varNode.getMethod().getDeclaringClass()))
				continue;
			if (pta instanceof ContextSensPTA) {
				Map<Context, ContextVar_Node> cvns = ((ContextSensPTA) pta).getContextVarNodeMap().get(varNode);
				if (cvns == null)
					continue;
				cvns.values().forEach(new Consumer<ContextVar_Node>() {
					public void accept(ContextVar_Node cvn) {
						System.out.println(cvn + ":");
						printPts(cvn.getP2Set());
					}
				});
			} else {
				System.out.println(varNode + ":");
				printPts(varNode.getP2Set());
			}
		}
	}

	private static void printPts(PTSetInternal pts) {
		final StringBuffer ret = new StringBuffer();
		pts.forall(new PTSetVisitor() {
			public final void visit(GNode n) {
				ret.append("\t" + n + "\n");
			}
		});
		System.out.print(ret);
	}

	/**
	 * dump callgraph to sootoutput/callgraph.dot
	 */
	public static void dumpCallGraph(Iterable<Edge> callgraph, boolean appOnly) {
		String filename = "callgraph";
		DotGraph canvas = setDotGraph(filename);
		
		int mn = -1;
		Set<String> methodSet = new HashSet<>();
		List<String> methodList = new ArrayList<>();
		
		for (Edge edge : callgraph) {
			MethodOrMethodContext srcmtd = edge.getSrc();
			if(appOnly&&!srcmtd.method().getDeclaringClass().isApplicationClass())continue;
			MethodOrMethodContext dstmtd = edge.getTgt();
			String srcName = srcmtd.toString();
			
			if(methodSet.add(srcName)){
				canvas.drawNode(srcName).setLabel("" + ++mn);
				methodList.add(mn, srcName);
			}
			String dstName = dstmtd.toString();
			
			if(methodSet.add(dstName)){
				canvas.drawNode(dstName).setLabel("" + ++mn);
				methodList.add(mn, dstName);
			}
			canvas.drawEdge(srcName,dstName);
		}
		
		plotDotGraph(canvas, filename);
		try {
			PrintWriter out = new PrintWriter(new File(output_dir, "callgraphnodes"));
			for (int i = 0; i < methodList.size(); i++)
				out.println(i + ":" + methodList.get(i));
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}

	/**
	 * slice a callgrph, put nodes &edges related to method into set1&set2
	 */
	private static void slice(CallGraph callgraph, MethodOrMethodContext method, Set<MethodOrMethodContext> set1, Set<Edge> set2) {
		for (Iterator<Edge> it = callgraph.edgesInto(method); it.hasNext();) {
			Edge edge = it.next();
			set2.add(edge);
			MethodOrMethodContext src = edge.getSrc();
			if (set1.add(src))
				slice(callgraph, src, set1, set2);
		}
	}
	
	/**
	 * dump callgraph strench to method
	 */
	public static void dumpSlicedCallGraph(CallGraph callgraph, MethodOrMethodContext method) {
		Set<MethodOrMethodContext> tgts = new HashSet<>();
		Set<Edge> edges = new HashSet<>();
		tgts.add(method);
		slice(callgraph, method, tgts, edges);
		
		dumpCallGraph(edges, false);
	}

	/**
	 * dump pts to sootoutput/pts
	 */
	public static void dumpPts(PTA pta, boolean appOnly) {
		try {
			PrintWriter file = new PrintWriter(new File(output_dir, "pts"));
			file.println("Points-to results:");
			for (Iterator<Var_Node> vnIt = pta.getPag().getVarNodeNumberer().iterator(); vnIt.hasNext();) {
				final Var_Node vn = vnIt.next();
				SootClass clz;
				if (vn instanceof LocalVar_Node)
					clz = ((LocalVar_Node) vn).getMethod().getDeclaringClass();
				else if (vn instanceof GlobalVar_Node)
					clz = ((GlobalVar_Node) vn).getDeclaringClass();
				else // AllocDotField_Node (on PAG, but we don't care its pts)
					continue;
				if (appOnly && !Config.v().isAppClass(clz))
					continue;

				String label = getNodeLabel(vn);
				nodes.put("[" + label + "]", vn);
				file.print(label + " -> {");
				PTSetInternal p2set = vn.getP2Set();
				if (p2set == null || p2set.isEmpty()) {
					file.print(" empty }\n");
					continue;
				}
				p2set.forall(new PTSetVisitor() {
					public final void visit(GNode n) {
						String label = getNodeLabel(n);
						nodes.put("[" + label + "]", n);
						file.print(" ");
						file.print(label);
					}
				});
				file.print(" }\n");
			}
			dumpNodeNames(file);
			file.close();
		} catch (IOException e) {
			throw new RuntimeException("Couldn't dump solution." + e);
		}

	}

	/**
	 * dump mPAGs to sootoutput/@filename.dot
	 */
	public static void dumpMPAGs(PTA pta, String filename) {
		DotGraph canvas = setDotGraph(filename);

		Set<SootMethod> reachables = new HashSet<>();
		for (QueueReader<MethodOrMethodContext> mReader = pta.getCgb().getReachableMethods().listener(); mReader
				.hasNext();)
			reachables.add(mReader.next().method());
		for (SootMethod m : reachables) {
			QueueReader<GNode> reader = pta.getMethodPAG(m).getInternalReader().clone();
			while (reader.hasNext()) {
				GNode src = reader.next();
				GNode dst = reader.next();
				drawNode(canvas, src);
				drawNode(canvas, dst);
				GNode from = src.getReplacement();// TODO ?
				GNode to = dst.getReplacement();
				String color = from instanceof Alloc_Node ? "green" : // alloc
						from instanceof FieldRef_Node ? "red" : // load
								to instanceof FieldRef_Node ? "blue" : // store
										"black"; // simple
				drawEdge(canvas, src, dst, color);
			}
		}
		plotDotGraph(canvas, filename);
	}
	
	private static class PAGMapDrawer{
		DotGraph canvas;
		PAGMapDrawer(DotGraph canvas){
			this.canvas=canvas;
		}
		private void drawPAGMap(Map<?extends GNode, ?extends Set<?extends GNode>> map, String color){
			map.forEach((n,elements)->{
				drawNode(canvas, n);
				elements.forEach(element->{
					drawNode(canvas, element);
					drawEdge(canvas, n, element, color);
				});
			});
		}
		private void drawInvPAGMap(Map<?extends GNode, ?extends Set<?extends GNode>> map, String color){
			map.forEach((n,elements)->{
				drawNode(canvas, n);
				elements.forEach(element->{
					drawNode(canvas, element);
					drawEdge(canvas, element, n, color);
				});
			});
		}
	}
	/**
	 * dump pag to sootoutput/@filename.dot
	 */
	public static void dumpPAG(PAG pag, String filename) {
		DotGraph canvas = setDotGraph(filename);
		PAGMapDrawer mapdrawer = new PAGMapDrawer(canvas);

		mapdrawer.drawPAGMap(pag.getAlloc(),"green");
		mapdrawer.drawPAGMap(pag.getSimple(),"black");
		mapdrawer.drawInvPAGMap(pag.getStoreInv(),"blue");
		mapdrawer.drawPAGMap(pag.getLoad(),"red");

		plotDotGraph(canvas, filename);
	}

	private static void plotDotGraph(DotGraph canvas, String filename) {
		canvas.plot(output_dir + "/" + filename + ".dot");
	}

	private static DotGraph setDotGraph(String fileName) {
		DotGraph canvas = new DotGraph(fileName);
		canvas.setNodeShape(DotGraphConstants.NODE_SHAPE_BOX);
		canvas.setGraphLabel(fileName);
		return canvas;
	}

	private static String getNodeLabel(GNode node) {
		int num = node.getNumber();
		if (node instanceof LocalVar_Node)
			return "L" + num;
		else if (node instanceof GlobalVar_Node)
			return "G" + num;
		else if (node instanceof AllocDotField_Node)
			return "OF" + num;
		else if (node instanceof FieldRef_Node)
			return "VF" + num;
		else if (node instanceof Alloc_Node)
			return "O" + num;
		else
			throw new RuntimeException("no such node type exists!");
	}

	private static void drawNode(DotGraph canvas, GNode node) {
		DotGraphNode dotNode = canvas.drawNode(node.toString());
		dotNode.setLabel("[" + getNodeLabel(node) + "]");
		nodes.put("[" + getNodeLabel(node) + "]", node);
	}

	private static void drawEdge(DotGraph canvas, GNode src, GNode dst, String color) {
		canvas.drawEdge(src.toString(), dst.toString()).setAttribute("color", color);
	}

	// TODO no caller
	// public static void CollectPointerDeferences(WholeProgPAG pag) {
	// int mass = 0;
	// int varMass = 0;
	// int adfs = 0;
	// int scalars = 0;
	// for (Iterator<Var_Node> vIt = pag.getVarNodeNumberer().iterator();
	// vIt.hasNext();) {
	// final Var_Node v = vIt.next();
	// scalars++;
	// PTSetInternal set = v.getP2Set();
	// if (set != null)
	// mass += set.size();
	// if (set != null)
	// varMass += set.size();
	// }
	// for (Iterator<Alloc_Node> anIt = pag.allocSourcesIterator();
	// anIt.hasNext();) {
	// final Alloc_Node an = anIt.next();
	// for (Iterator<AllocDotField_Node> adfIt = an.getFields().iterator();
	// adfIt.hasNext();) {
	// final AllocDotField_Node adf = adfIt.next();
	// PTSetInternal set = adf.getP2Set();
	// if (set != null)
	// mass += set.size();
	// if (set != null && set.size() > 0) {
	// adfs++;
	// }
	// }
	// }
	// G.v().out.println("Set mass: " + mass);
	// G.v().out.println("Variable mass: " + varMass);
	// G.v().out.println("Scalars: " + scalars);
	// G.v().out.println("adfs: " + adfs);
	// // Compute points-to set sizes of dereference sites BEFORE
	// // trimming sets by declared type
	// int[] deRefCounts = new int[30001];
	// for (Var_Node v : pag.getDereferences()) {
	// PTSetInternal set = v.getP2Set();
	// int size = 0;
	// if (set != null)
	// size = set.size();
	// deRefCounts[size]++;
	// }
	// int total = 0;
	// for (int element : deRefCounts)
	// total += element;
	// G.v().out.println("Dereference counts BEFORE trimming (total = " + total
	// + "):");
	// for (int i = 0; i < deRefCounts.length; i++) {
	// if (deRefCounts[i] > 0) {
	// G.v().out.println("" + i + " " + deRefCounts[i] + " " + (deRefCounts[i] *
	// 100.0 / total) + "%");
	// }
	// }
	// }
	private static void dumpNodeNames(PrintWriter file) {
		nodes.forEach((l, n) -> file.println(l + n));
	}

	public static void dumpNodeNames(String fileName) {
		try {
			PrintWriter out = new PrintWriter(new File(output_dir, fileName));
			dumpNodeNames(out);
			out.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}